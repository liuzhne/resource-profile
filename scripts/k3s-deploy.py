#!/usr/bin/env python3
"""Single-node K3s gates. Resolved manifests and credentials never go to stdout."""
import argparse
import base64
import hashlib
import ipaddress
import json
import math
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import sys
import urllib.error
import urllib.request

from cloud_deploy import (ROOT, SECRET_NAMES, ensure_capacity, ensure_export_font,
                          ensure_untracked, normalize_env, private_write, read_env,
                          seed_hashes, validate_env, compose, frontend_console_url)

NAMESPACE = "educare"
SPECS = ROOT / "k8s/workloads.json"
HPA_SERVICE = "data-service"
INFRA_SERVICES = frozenset(("mysql", "redis", "etcd", "minio", "milvus-standalone"))
INFRA_SECRETS = ("MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD", "REDIS_PASSWORD", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY")
BUSINESS_SERVICES = INFRA_SERVICES | frozenset(("gateway", "auth-service", "user-service", "teacher-service", "student-service", "mental-service", "data-service"))
BUSINESS_SECRETS = INFRA_SECRETS + ("JWT_SECRET", "EDUCARE_INTERNAL_TOKEN")
DEPLOY_ORDER = (
    ("mysql", "redis", "etcd", "minio"),
    ("milvus-standalone",),
    ("auth-service", "user-service", "teacher-service", "student-service", "mental-service", "data-service"),
    ("ai-inference-service", "knowledge-rag-mcp", "mcp-student-data"),
    ("agent-service",), ("gateway",),
)


def kubectl(*arguments, payload=None):
    result = subprocess.run(["k3s", "kubectl", *arguments], text=True, input=payload,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Kubernetes errors may echo a Secret, SQL hash or model response.
        raise RuntimeError(f"kubectl {arguments[0]} failed (exit {result.returncode}); inspect privately on the VM")
    return result.stdout


def apply(objects):
    if objects:
        kubectl("apply", "--server-side", "--field-manager=educare-deploy", "-f", "-",
                payload=json.dumps({"apiVersion": "v1", "kind": "List", "items": objects}))


def build_frontend(values, env_path):
    """Vite values must enter the Docker build; Pod environment cannot change compiled assets."""
    build_values = dict(values, VITE_LANGFUSE_URL=frontend_console_url(values))
    compose(build_values, env_path, "--profile", "public", "build", "frontend")
    return build_values["VITE_LANGFUSE_URL"]


def resource(kind, name, spec=None, api="v1"):
    obj = {"apiVersion": api, "kind": kind, "metadata": {"name": name, "namespace": NAMESPACE}}
    if spec is not None:
        obj["spec"] = spec
    return obj


def resolve(text, values):
    """Resolve the public Compose-derived ${NAME}, ${NAME:-fallback}, ${NAME:?gate}."""
    pattern = re.compile(r"\$\{([A-Z][A-Z0-9_]*)(?:(:-|:\?)([^{}]*))?\}")
    text = str(text)
    for _ in range(8):
        def substitute(match):
            name, operator, default = match.groups()
            value = values.get(name, "")
            if value:
                return str(value)
            if operator == ":-":
                return default or ""
            if operator == ":?":
                raise ValueError(f"Configure {name} before rendering K3s workloads")
            return ""
        updated = pattern.sub(substitute, text)
        if updated == text:
            break
        text = updated
    if "${" in text:
        raise ValueError("Unresolved workload environment expression")
    return text


def probe(spec, name):
    if name in ("gateway", "auth-service", "user-service", "teacher-service", "student-service",
                "mental-service", "data-service", "agent-service", "mcp-student-data"):
        action = {"httpGet": {"path": "/actuator/health", "port": spec["ports"][0]}}
    elif name == "ai-inference-service":
        action = {"httpGet": {"path": "/health", "port": 8090}}
    elif name == "frontend":
        action = {"tcpSocket": {"port": 443}}
    else:
        command = spec["healthcheck"]
        if command[0] == "CMD-SHELL":
            action = {"exec": {"command": ["sh", "-c", command[1].replace("$$", "$" )]}}
        else:
            action = {"exec": {"command": command[1:]}}
    return dict(action, periodSeconds=15, timeoutSeconds=6, failureThreshold=3)


def host_font(values):
    font = Path(values.get("CLOUD_EXPORT_FONT_FILE", "../.local-run/kamatera/fonts/NotoSansSC-Regular.ttf"))
    return str((font if font.is_absolute() else ROOT / "docker" / font).resolve())


def validate_infra_env(values):
    for name in INFRA_SECRETS:
        if not re.fullmatch(r"[A-Za-z0-9_-]{32,}", values.get(name, "")) or "change-me" in values[name]:
            raise ValueError(f"{name} must contain at least 32 safe random characters")
    if len({values[name] for name in INFRA_SECRETS}) != len(INFRA_SECRETS):
        raise ValueError("Database, Redis and MinIO must have distinct fresh secrets")


def validate_business_env(values):
    validate_infra_env(values)
    for name in BUSINESS_SECRETS:
        if not re.fullmatch(r"[A-Za-z0-9_-]{32,}", values.get(name, "")) or "change-me" in values[name]:
            raise ValueError(f"{name} must contain at least 32 safe random characters")
    if len({values[name] for name in BUSINESS_SECRETS}) != len(BUSINESS_SECRETS):
        raise ValueError("Business JWT/internal credentials must differ from infrastructure secrets")


def manifests(values, public=False, infra=False, business=False):
    specs = json.loads(SPECS.read_text())
    namespace = {"apiVersion": "v1", "kind": "Namespace", "metadata": {"name": NAMESPACE}}
    objects = [namespace]
    secret = resource("Secret", "workload-env")
    secret.update(type="Opaque", data={})
    workloads = []
    for name, spec in specs.items():
        if infra and name not in INFRA_SERVICES:
            continue
        if business and name not in BUSINESS_SERVICES:
            continue
        if name == "frontend" and not public:
            continue
        labels = {"app.kubernetes.io/name": name, "app.kubernetes.io/part-of": "educare"}
        env = {key: resolve(value, values) for key, value in spec["environment"].items()
               if not (business and key == "EDUCARE_MCP_TOKEN")}
        secret["data"].update({f"{name}.{key}": base64.b64encode(value.encode()).decode() for key, value in env.items()})
        container = {
            "name": name, "image": spec["image"], "imagePullPolicy": "IfNotPresent",
            "env": [{"name": key, "valueFrom": {"secretKeyRef": {"name": "workload-env", "key": f"{name}.{key}"}}}
                    for key in sorted(env)],
            "ports": [{"containerPort": port, "name": f"p{port}"} for port in spec["ports"]],
            "resources": {"requests": spec["requests"], "limits": {"memory": spec["limitMemory"]}},
            "securityContext": {"allowPrivilegeEscalation": False, "capabilities": {"drop": ["NET_RAW"]}},
        }
        if "args" in spec:
            container["args"] = [argument.replace("$$", "$") for argument in spec["args"]]
        check = probe(spec, name)
        container["readinessProbe"] = check
        container["livenessProbe"] = dict(check, failureThreshold=6)
        container["startupProbe"] = dict(check, failureThreshold=60)
        volumes, mounts = [], []
        for volume in spec["volumes"]:
            objects.append(resource("PersistentVolumeClaim", volume["name"], {
                "accessModes": ["ReadWriteOnce"], "storageClassName": "local-path",
                "resources": {"requests": {"storage": volume["size"]}},
            }))
            volumes.append({"name": volume["name"], "persistentVolumeClaim": {"claimName": volume["name"]}})
            mounts.append({"name": volume["name"], "mountPath": volume["mountPath"]})
        if name == "mysql":
            volumes.append({"name": "sql-init", "hostPath": {"path": str(ROOT / "sql/init"), "type": "Directory"}})
            mounts.append({"name": "sql-init", "mountPath": "/docker-entrypoint-initdb.d", "readOnly": True})
        if name == "agent-service":
            volumes.append({"name": "export-font", "hostPath": {"path": host_font(values), "type": "File"}})
            mounts.append({"name": "export-font", "mountPath": "/opt/educare-fonts/NotoSansSC-Regular.ttf", "readOnly": True})
        if name == "milvus-standalone":
            volumes.append({"name": "milvus-config", "secret": {"secretName": "milvus-config", "defaultMode": 384}})
            mounts.append({"name": "milvus-config", "mountPath": "/milvus/configs/user.yaml", "subPath": "user.yaml", "readOnly": True})
            container["securityContext"]["seccompProfile"] = {"type": "Unconfined"}
        if name == "frontend":
            volumes.append({"name": "caddyfile", "configMap": {"name": "caddyfile"}})
            mounts.append({"name": "caddyfile", "mountPath": "/etc/caddy/Caddyfile", "subPath": "Caddyfile", "readOnly": True})
            # Single-node public ingress. Every other service stays ClusterIP-only.
            for port in container["ports"]:
                port["hostPort"] = port["containerPort"]
        if volumes:
            container["volumeMounts"] = mounts
        pod = {"automountServiceAccountToken": False, "containers": [container], "terminationGracePeriodSeconds": 60}
        if volumes:
            pod["volumes"] = volumes
        # RWO stateful volumes and fixed SSE/async services must not overlap old/new Pods.
        deployment_spec = {"selector": {"matchLabels": labels}, "strategy": {"type": "Recreate"},
                           "template": {"metadata": {"labels": labels, "annotations": {
                               "educare/env-sha256": hashlib.sha256(json.dumps(env, sort_keys=True).encode()).hexdigest()}}, "spec": pod}}
        if name != HPA_SERVICE:
            deployment_spec["replicas"] = 1
        if name == "frontend":
            # Expected build setting; the actual image records this URL in its OCI label.
            deployment_spec["template"]["metadata"]["annotations"]["educare/frontend-console-url"] = frontend_console_url(values)
        workloads.append(resource("Deployment", name, deployment_spec, "apps/v1"))
        objects.append(resource("Service", name, {"type": "ClusterIP", "selector": labels,
            "ports": [{"name": f"p{port}", "port": port, "targetPort": port} for port in spec["ports"]]}))
    objects.append(secret)
    milvus = resource("Secret", "milvus-config")
    milvus_content = "minio:\n  address: minio\n  port: 9000\n" + (
        f"  accessKeyID: \"{values['MINIO_ACCESS_KEY']}\"\n  secretAccessKey: \"{values['MINIO_SECRET_KEY']}\"\n"
        "  useSSL: false\n  bucketName: a-bucket\n")
    milvus.update(type="Opaque", data={"user.yaml": base64.b64encode(milvus_content.encode()).decode()})
    objects.append(milvus)
    if public:
        caddy = resource("ConfigMap", "caddyfile")
        caddy["data"] = {"Caddyfile": (ROOT / "docker/caddy/Caddyfile.cloud").read_text()}
        objects.append(caddy)
    objects.extend(workloads)
    if not infra:
        objects.append(resource("HorizontalPodAutoscaler", HPA_SERVICE, {
        "scaleTargetRef": {"apiVersion": "apps/v1", "kind": "Deployment", "name": HPA_SERVICE},
        "minReplicas": 1, "maxReplicas": 2,
        "metrics": [{"type": "Resource", "resource": {"name": "cpu", "target": {"type": "Utilization", "averageUtilization": 70}}}],
        "behavior": {"scaleDown": {"stabilizationWindowSeconds": 300},
                     "scaleUp": {"stabilizationWindowSeconds": 30, "policies": [{"type": "Pods", "value": 1, "periodSeconds": 60}]}},
        }, "autoscaling/v2"))
    return objects


def ensure_host_mounts(values, font_required=True):
    if font_required:
        ensure_export_font(values)
    directory = ROOT / "sql/init"
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError("SQL initialization source must be an existing regular directory")
    enforcing = subprocess.run(["getenforce"], capture_output=True, text=True, check=True).stdout.strip()
    if enforcing != "Enforcing":
        raise ValueError("Rocky K3s deployment requires SELinux Enforcing")
    paths = [directory, *directory.glob("*.sql")]
    if font_required:
        paths.append(Path(host_font(values)))
    for item in paths:
        context = subprocess.run(["ls", "-Zd", str(item)], capture_output=True, text=True, check=True).stdout
        fields = context.split(None, 1)[0].split(":") if context.strip() else []
        if len(fields) != 4 or fields[2:] != ["container_file_t", "s0"]:
            raise ValueError("SQL/font hostPath requires persistent container_file_t:s0 labels; consult k8s/README.md")


def mysql(sql):
    return kubectl("-n", NAMESPACE, "exec", "-i", "deployment/mysql", "--", "sh", "-c",
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names edu_portrait', payload=sql)


def seed_accounts():
    rows = dict(line.split("\t", 1) for line in mysql(
        "SELECT username,password FROM sys_user WHERE username IN ('admin','teacher','student');\n").splitlines() if "\t" in line)
    if set(rows) != set(seed_hashes()) or any(not re.fullmatch(r"\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}", value) for value in rows.values()):
        raise ValueError("Expected valid bcrypt seed accounts are missing")
    return rows


def assert_no_factory_passwords():
    import bcrypt
    defaults = seed_hashes()
    for name, hashed in seed_accounts().items():
        if hashed == defaults[name] or bcrypt.checkpw(name.encode(), hashed.encode()):
            raise ValueError("Factory seed credentials remain; public frontend is blocked")


def bootstrap_seeds(path):
    import bcrypt
    if path.is_symlink() or not stat.S_ISREG(path.lstat().st_mode) or path.stat().st_mode & 0o077:
        raise ValueError("Seed credential file must be regular and mode 0600")
    credentials = json.loads(path.read_text())
    if set(credentials) != {"admin", "teacher", "student"} or any(not isinstance(value, str) or len(value) < 24 for value in credentials.values()):
        raise ValueError("Seed credentials must contain three fresh passwords of at least 24 characters")
    statements = []
    for name, hashed in seed_accounts().items():
        password = credentials[name].encode()
        if bcrypt.checkpw(name.encode(), hashed.encode()):
            new_hash = bcrypt.hashpw(password, bcrypt.gensalt(rounds=12)).decode()
            statements.append(f"UPDATE sys_user SET password='{new_hash}' WHERE username='{name}' AND password='{hashed}';")
        elif not bcrypt.checkpw(password, hashed.encode()):
            raise ValueError("Existing seed password differs; refusing to overwrite migrated credentials")
    if statements:
        mysql("START TRANSACTION;\n" + "\n".join(statements) + "\nCOMMIT;\n")
    assert_no_factory_passwords()


def provider_request(url, key, payload):
    request = urllib.request.Request(url, data=json.dumps(payload).encode(), method="POST",
                                    headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}",
                                             "User-Agent": "ResourceProfile-DeploymentCheck/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            result = json.load(response)
        if not isinstance(result, dict):
            raise ValueError("Actual provider probe did not return an object")
        return result
    except (urllib.error.URLError, json.JSONDecodeError):
        raise ValueError("Actual model/embedding provider probe failed; inspect private provider configuration") from None


def probe_models(values):
    chat = {"model": values["LLM_MODEL"], "messages": [{"role": "user", "content": "Reply with the word OK."}],
            "max_tokens": int(values.get("LLM_MAX_TOKENS", "2048"))}
    effort = values.get("SPRING_AI_OPENAI_CHAT_OPTIONS_REASONING_EFFORT", "")
    if effort:
        chat["reasoning_effort"] = effort
    result = provider_request(values["LLM_BASE_URL"].rstrip("/") + "/chat/completions", values["LLM_API_KEY"], chat)
    choices = result.get("choices")
    message = choices[0].get("message") if isinstance(choices, list) and choices and isinstance(choices[0], dict) else None
    content = message.get("content") if isinstance(message, dict) else None
    if not isinstance(content, str) or not content.strip():
        raise ValueError("Chat provider returned no usable content")
    result = provider_request(values["EMBEDDING_BASE_URL"].rstrip("/") + "/embeddings",
                              values.get("EMBEDDING_API_KEY") or values["LLM_API_KEY"],
                              {"model": values["EMBEDDING_MODEL"], "input": ["Deployment synthetic acceptance probe."]})
    vectors = result.get("data")
    vector = vectors[0].get("embedding") if isinstance(vectors, list) and len(vectors) == 1 and isinstance(vectors[0], dict) else None
    if not isinstance(vector, list) or len(vector) != int(values.get("EMBEDDING_DIM", "1024")) or any(
            isinstance(number, bool) or not isinstance(number, (int, float)) or not math.isfinite(number) for number in vector):
        raise ValueError("Embedding provider output dimension does not match EMBEDDING_DIM")


def ensure_dns(values):
    try:
        address = ipaddress.IPv4Address(values.get("CLOUD_PUBLIC_IP", ""))
    except ipaddress.AddressValueError:
        raise ValueError("Set CLOUD_PUBLIC_IP to the created VM public IPv4") from None
    if not address.is_global or str(address) not in {item[4][0] for item in socket.getaddrinfo(values["CLOUD_SITE_ADDRESS"], 443, family=socket.AF_INET)}:
        raise ValueError("Public hostname DNS must resolve to the created VM public IPv4")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("check", "render", "build-frontend", "apply-infra", "apply-business", "apply-private", "bootstrap-seeds", "init-rag", "probe-models", "publish", "status", "stop-public"))
    parser.add_argument("--env-file", type=Path, default=ROOT / "docker/.env.kamatera")
    parser.add_argument("--seed-file", type=Path, default=ROOT / ".local-run/kamatera/seed-credentials.json")
    parser.add_argument("--output", type=Path, default=ROOT / ".local-run/kamatera/k3s-private.json")
    args = parser.parse_args()
    if args.command == "status":
        print(kubectl("-n", NAMESPACE, "get", "pods,svc,hpa,pvc", "-o", "wide"))
        return
    if args.command == "stop-public":
        kubectl("-n", NAMESPACE, "delete", "deployment", "frontend", "--ignore-not-found")
        print("Public frontend stopped; PVCs retained and Kamatera billing continues")
        return
    if args.env_file.expanduser().is_symlink():
        raise ValueError("Private env must not be a symlink")
    env_path = args.env_file.expanduser().resolve()
    ensure_untracked(env_path)
    values = normalize_env(read_env(env_path), "kamatera")
    if args.command == "build-frontend":
        validate_env(values)
        console = build_frontend(values, env_path)
        print(f"Frontend image built for {console}; import the image into K3s before rollout")
        return
    if args.command in ("apply-infra", "apply-business", "bootstrap-seeds"):
        if args.command == "apply-business":
            validate_business_env(values)
        else:
            validate_infra_env(values)
        if args.command in ("apply-infra", "apply-business"):
            ensure_capacity()
            ensure_host_mounts(values, font_required=False)
            business = args.command == "apply-business"
            objects = manifests(values, infra=not business, business=business)
            # Infra updates and factory seeds must never coexist with an old public frontend.
            existing = kubectl("get", "namespaces", "-o", "json")
            if any(item["metadata"]["name"] == NAMESPACE for item in json.loads(existing)["items"]):
                deployed = json.loads(kubectl("-n", NAMESPACE, "get", "deployments", "-o", "json"))
                allowed = BUSINESS_SERVICES if business else INFRA_SERVICES
                if any(item["metadata"]["name"] not in allowed | {"frontend"} for item in deployed["items"]):
                    raise ValueError("Reduced deployment phase cannot replace env after a wider phase; use apply-private for the complete stack")
                kubectl("-n", NAMESPACE, "delete", "deployment", "frontend", "--ignore-not-found", "--wait=true")
            apply([obj for obj in objects if obj["kind"] != "Deployment"])
            groups = DEPLOY_ORDER[:3] + (("gateway",),) if business else DEPLOY_ORDER[:2]
            for group in groups:
                apply([obj for obj in objects if obj["kind"] == "Deployment" and obj["metadata"]["name"] in group])
                for name in group:
                    kubectl("-n", NAMESPACE, "rollout", "status", f"deployment/{name}", "--timeout=900s")
                if "mysql" in group:
                    subprocess.run([sys.executable, str(ROOT / "scripts/apply-sql-migrations.py"), "--transport", "k3s", "--namespace", NAMESPACE], check=True)
            print("Private business services ready and bounded data HPA configured; no AI or public frontend was rendered" if business
                  else "Five private infrastructure services ready; no model key or public frontend was rendered")
        else:
            ensure_untracked(args.seed_file)
            bootstrap_seeds(args.seed_file)
            print("Seed passwords rotated and checked with bcrypt; credentials remain in the private file")
        return
    validate_env(values)
    objects = manifests(values)
    if args.command == "render":
        ensure_untracked(args.output)
        private_write(args.output, json.dumps({"apiVersion": "v1", "kind": "List", "items": objects}, indent=2) + "\n")
        print("Private K3s manifest written mode 0600; public frontend excluded")
    elif args.command == "check":
        print("K3s static rendering passed: 16 private services, bounded data-service HPA; no resource created")
    elif args.command == "apply-private":
        ensure_capacity()
        ensure_host_mounts(values)
        # Remove prior public listener before modifying private services or seed state.
        existing = kubectl("get", "namespaces", "-o", "json")
        if any(item["metadata"]["name"] == NAMESPACE for item in json.loads(existing)["items"]):
            kubectl("-n", NAMESPACE, "delete", "deployment", "frontend", "--ignore-not-found", "--wait=true")
        apply([obj for obj in objects if obj["kind"] != "Deployment"])
        for group in DEPLOY_ORDER:
            apply([obj for obj in objects if obj["kind"] == "Deployment" and obj["metadata"]["name"] in group])
            for name in group:
                kubectl("-n", NAMESPACE, "rollout", "status", f"deployment/{name}", "--timeout=900s")
            if "mysql" in group:
                subprocess.run([sys.executable, str(ROOT / "scripts/apply-sql-migrations.py"), "--transport", "k3s", "--namespace", NAMESPACE], check=True)
        print("Private stack ready; frontend remains absent")
    elif args.command == "init-rag":
        kubectl("-n", NAMESPACE, "exec", "deployment/ai-inference-service", "--", 'sh', '-c', 'curl --fail --silent --show-error -X POST -H "X-Admin-Token: $EDUCARE_ADMIN_TOKEN" http://127.0.0.1:8090/api/v1/rag/initialize')
        print("RAG collections initialized; ingestion and retrieval acceptance remain required")
    elif args.command == "probe-models":
        probe_models(values)
        print("Actual chat response and embedding output dimension accepted; probe used synthetic text")
    elif args.command == "publish":
        ensure_capacity()
        ensure_host_mounts(values)
        assert_no_factory_passwords()
        ensure_dns(values)
        probe_models(values)
        for group in DEPLOY_ORDER:
            for name in group:
                kubectl("-n", NAMESPACE, "rollout", "status", f"deployment/{name}", "--timeout=120s")
        # Leave existing HPA-owned scale and private workloads untouched.
        public_objects = manifests(values, public=True)
        try:
            apply([obj for obj in public_objects if obj["kind"] in ("Secret", "ConfigMap") or obj["metadata"]["name"].startswith("caddy-") or obj["metadata"]["name"] == "frontend"])
            kubectl("-n", NAMESPACE, "rollout", "status", "deployment/frontend", "--timeout=600s")
            with urllib.request.urlopen(f"https://{values['CLOUD_SITE_ADDRESS']}/healthz", timeout=30) as response:
                if response.status != 200:
                    raise ValueError("HTTPS health check did not return 200")
        except (ValueError, RuntimeError, OSError, urllib.error.URLError):
            try:
                kubectl("-n", NAMESPACE, "delete", "deployment", "frontend", "--ignore-not-found", "--wait=true")
            except (RuntimeError, OSError):
                raise RuntimeError("Publication failed and frontend cleanup failed; remove deployment/frontend privately on the VM") from None
            raise ValueError("Publication or trusted HTTPS verification failed; public frontend removed") from None
        print("Frontend published with trusted HTTPS; full login/SSE/RAG/HPA acceptance is still required")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, KeyError, ImportError) as error:
        # Exception text is deliberately controlled; provider/CLI errors are suppressed.
        print(f"K3s deployment stopped: {error}", file=sys.stderr)
        sys.exit(1)
