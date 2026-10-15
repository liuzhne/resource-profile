#!/usr/bin/env python3
"""Offline K3s boundary checks with synthetic values; no cloud credentials read."""
import importlib.util
import base64
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
spec = importlib.util.spec_from_file_location("k3s_deploy", Path(__file__).with_name("k3s-deploy.py"))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


def synthetic_env():
    return {
        **{name: f"{index:02d}" + "a" * 46 for index, name in enumerate(deploy.SECRET_NAMES)},
        "CLOUD_SITE_ADDRESS": "synthetic.educare.test", "CLOUD_PUBLIC_IP": "1.1.1.1",
        "SPRING_AI_OPENAI_BASE_URL": "https://chat.educare.test", "LLM_BASE_URL": "https://chat.educare.test/v1",
        "LLM_API_KEY": "synthetic-chat-key", "LLM_MODEL": "synthetic-chat",
        "EMBEDDING_BASE_URL": "https://embedding.educare.test/v1", "EMBEDDING_API_KEY": "synthetic-embedding-key",
        "EMBEDDING_MODEL": "synthetic-embedding", "EMBEDDING_DIM": "3",
    }


def objects_by_kind(objects, kind):
    return {obj["metadata"]["name"]: obj for obj in objects if obj["kind"] == kind}


class K3sBoundaryTests(unittest.TestCase):
    def test_frontend_build_injects_public_console_at_build_time_and_does_not_apply(self):
        values = deploy.normalize_env(synthetic_env(), "kamatera")
        values["VITE_LANGFUSE_URL"] = "https://trace.educare.test/project/synthetic/"
        with patch.object(deploy, "compose") as build, patch.object(deploy, "apply") as apply, patch.object(deploy, "kubectl") as kube:
            console = deploy.build_frontend(values, Path("synthetic.env"))
            self.assertEqual(console, "https://trace.educare.test/project/synthetic")
            args = build.call_args.args
            self.assertEqual(args[0]["VITE_LANGFUSE_URL"], console)
            self.assertEqual(args[2:], ("--profile", "public", "build", "frontend"))
            apply.assert_not_called()
            kube.assert_not_called()

    def test_frontend_manifest_records_expected_public_console_without_fake_runtime_vite_env(self):
        values = synthetic_env()
        values["VITE_LANGFUSE_URL"] = "https://trace.educare.test/project/synthetic"
        frontend = objects_by_kind(deploy.manifests(values, public=True), "Deployment")["frontend"]
        self.assertEqual(frontend["spec"]["template"]["metadata"]["annotations"]["educare/frontend-console-url"], values["VITE_LANGFUSE_URL"])
        self.assertNotIn("VITE_LANGFUSE_URL", {row["name"] for row in frontend["spec"]["template"]["spec"]["containers"][0]["env"]})

    def test_nested_interpolation_and_required_failure(self):
        self.assertEqual(deploy.resolve("${EMBEDDING_API_KEY:-${LLM_API_KEY}}", {"LLM_API_KEY": "synthetic"}), "synthetic")
        with self.assertRaisesRegex(ValueError, "Configure MISSING"):
            deploy.resolve("${MISSING:?required}", {})

    def test_private_stack_has_no_public_listener_or_frontend(self):
        objects = deploy.manifests(synthetic_env())
        deployments = objects_by_kind(objects, "Deployment")
        self.assertEqual(len(deployments), 16)
        self.assertNotIn("frontend", deployments)
        self.assertNotIn("nacos", deployments)
        for obj in deployments.values():
            pod = obj["spec"]["template"]["spec"]
            self.assertFalse(pod["automountServiceAccountToken"])
            self.assertFalse(pod.get("hostNetwork", False))
            self.assertTrue(all("hostPort" not in port for port in pod["containers"][0]["ports"]))
        self.assertTrue(all(obj["spec"]["type"] == "ClusterIP" for obj in objects_by_kind(objects, "Service").values()))

    def test_infra_phase_has_no_model_or_domain_values(self):
        values = {name: synthetic_env()[name] for name in deploy.INFRA_SECRETS}
        deploy.validate_infra_env(values)
        objects = deploy.manifests(values, infra=True)
        self.assertEqual(set(objects_by_kind(objects, "Deployment")), deploy.INFRA_SERVICES)
        self.assertNotIn("HorizontalPodAutoscaler", {obj["kind"] for obj in objects})
        rendered = json.dumps(objects)
        for name in ("LLM_API_KEY", "LLM_BASE_URL", "EMBEDDING_API_KEY", "CLOUD_SITE_ADDRESS"):
            self.assertNotIn(name, rendered)
        values["REDIS_PASSWORD"] = "change-me"
        with self.assertRaises(ValueError):
            deploy.validate_infra_env(values)

    def test_business_phase_is_complete_without_ai_credentials(self):
        values = {name: synthetic_env()[name] for name in deploy.BUSINESS_SECRETS}
        deploy.validate_business_env(values)
        objects = deploy.manifests(values, business=True)
        self.assertEqual(set(objects_by_kind(objects, "Deployment")), deploy.BUSINESS_SERVICES)
        self.assertEqual(set(objects_by_kind(objects, "HorizontalPodAutoscaler")), {"data-service"})
        secret = objects_by_kind(objects, "Secret")["workload-env"]["data"]
        for name in deploy.INFRA_SERVICES:
            expected = {key: value for key, value in objects_by_kind(deploy.manifests(values, infra=True), "Secret")["workload-env"]["data"].items() if key.startswith(name + ".")}
            self.assertTrue(all(secret[key] == value for key, value in expected.items()))
        rendered = json.dumps(objects)
        for name in ("LLM_API_KEY", "LLM_BASE_URL", "EMBEDDING_API_KEY", "EDUCARE_MCP_TOKEN", "CLOUD_SITE_ADDRESS"):
            self.assertNotIn(name, rendered)
        values["JWT_SECRET"] = values["MYSQL_PASSWORD"]
        with self.assertRaisesRegex(ValueError, "must differ"):
            deploy.validate_business_env(values)

    def test_public_frontend_is_only_hostport_workload(self):
        objects = deploy.manifests(synthetic_env(), public=True)
        public = [obj for obj in objects if obj["kind"] == "Deployment" and any("hostPort" in port for port in obj["spec"]["template"]["spec"]["containers"][0]["ports"])]
        self.assertEqual([obj["metadata"]["name"] for obj in public], ["frontend"])
        self.assertEqual({port["hostPort"] for port in public[0]["spec"]["template"]["spec"]["containers"][0]["ports"]}, {80, 443})

    def test_credentials_are_only_secret_refs(self):
        values = synthetic_env()
        objects = deploy.manifests(values, public=True)
        non_secrets = [obj for obj in objects if obj["kind"] != "Secret"]
        text = json.dumps(non_secrets)
        for name in deploy.SECRET_NAMES + ("LLM_API_KEY", "EMBEDDING_API_KEY"):
            self.assertNotIn(values[name], text)
        for obj in objects_by_kind(objects, "Deployment").values():
            self.assertTrue(all("valueFrom" in env for env in obj["spec"]["template"]["spec"]["containers"][0]["env"]))
        secret = objects_by_kind(objects, "Secret")["milvus-config"]
        self.assertIn(values["MINIO_SECRET_KEY"], base64.b64decode(secret["data"]["user.yaml"]).decode())
        self.assertTrue(all("data" in obj and "stringData" not in obj for obj in objects_by_kind(objects, "Secret").values()))
        config_mount = objects_by_kind(objects, "Deployment")["milvus-standalone"]["spec"]["template"]["spec"]["volumes"][-1]
        self.assertEqual(config_mount["secret"]["defaultMode"], 0o600)

    def test_hpa_is_bounded_and_singleton_services_remain_singletons(self):
        objects = deploy.manifests(synthetic_env())
        hpas = objects_by_kind(objects, "HorizontalPodAutoscaler")
        self.assertEqual(set(hpas), {"data-service"})
        self.assertEqual((hpas["data-service"]["spec"]["minReplicas"], hpas["data-service"]["spec"]["maxReplicas"]), (1, 2))
        self.assertEqual(hpas["data-service"]["spec"]["metrics"][0]["resource"]["target"]["averageUtilization"], 70)
        for name, obj in objects_by_kind(objects, "Deployment").items():
            if name == "data-service":
                self.assertNotIn("replicas", obj["spec"])
            else:
                self.assertEqual(obj["spec"]["replicas"], 1)

    def test_local_storage_and_readonly_sources(self):
        objects = deploy.manifests(synthetic_env(), public=True)
        self.assertEqual(len(objects_by_kind(objects, "PersistentVolumeClaim")), 8)
        for obj in objects_by_kind(objects, "Deployment").values():
            pod = obj["spec"]["template"]["spec"]
            self.assertEqual(obj["spec"]["strategy"]["type"], "Recreate")
            self.assertFalse(pod["containers"][0]["securityContext"].get("privileged", False))
            mounts = {mount["name"]: mount for mount in pod["containers"][0].get("volumeMounts", [])}
            for volume in pod.get("volumes", []):
                if "hostPath" in volume:
                    self.assertTrue(mounts[volume["name"]]["readOnly"])
                    self.assertIn(volume["hostPath"]["type"], ("Directory", "File"))

    def test_known_factory_password_rehash_is_still_blocked(self):
        import bcrypt
        rows = {name: bcrypt.hashpw(name.encode(), bcrypt.gensalt(rounds=4)).decode() for name in ("admin", "teacher", "student")}
        with patch.object(deploy, "seed_accounts", return_value=rows):
            with self.assertRaisesRegex(ValueError, "Factory seed"):
                deploy.assert_no_factory_passwords()

    def test_migrated_credentials_are_not_overwritten(self):
        import bcrypt
        rows = {name: bcrypt.hashpw(b"existing-migrated-password", bcrypt.gensalt(rounds=4)).decode() for name in ("admin", "teacher", "student")}
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "seed.json"
            deploy.private_write(path, json.dumps({name: "fresh-synthetic-password-123456" for name in rows}))
            with patch.object(deploy, "seed_accounts", return_value=rows), patch.object(deploy, "mysql") as database:
                with self.assertRaisesRegex(ValueError, "refusing to overwrite"):
                    deploy.bootstrap_seeds(path)
                database.assert_not_called()

    def test_actual_embedding_dimension_probe_rejects_mismatch(self):
        with patch.object(deploy, "provider_request", side_effect=[{"choices": [{"message": {"content": "OK"}}]}, {"data": [{"embedding": [1, 2]}]}]):
            with self.assertRaisesRegex(ValueError, "dimension"):
                deploy.probe_models(synthetic_env())

    def test_private_render_never_overwrites(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "manifest.json"
            deploy.private_write(path, "synthetic")
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError):
                deploy.private_write(path, "overwrite")

    def test_frontend_rollout_failure_removes_public_listener(self):
        def kube(*arguments, **kwargs):
            if "deployment/frontend" in arguments and "rollout" in arguments:
                raise RuntimeError("synthetic rollout failure")
            return ""
        with patch.object(sys, "argv", ["k3s-deploy.py", "publish"]), \
                patch.object(deploy, "ensure_untracked"), patch.object(deploy, "read_env", return_value=synthetic_env()), \
                patch.object(deploy, "ensure_capacity"), patch.object(deploy, "ensure_host_mounts"), \
                patch.object(deploy, "assert_no_factory_passwords"), patch.object(deploy, "ensure_dns"), \
                patch.object(deploy, "probe_models"), patch.object(deploy, "apply"), \
                patch.object(deploy, "kubectl", side_effect=kube) as cli:
            with self.assertRaisesRegex(ValueError, "public frontend removed"):
                deploy.main()
            cli.assert_any_call("-n", deploy.NAMESPACE, "delete", "deployment", "frontend", "--ignore-not-found", "--wait=true")


if __name__ == "__main__":
    unittest.main()
