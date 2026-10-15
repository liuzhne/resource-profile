#!/usr/bin/env python3
"""Single-VM cloud deployment gates; never print secrets or seed login credentials."""
import argparse
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import socket
import stat
import struct
import subprocess
import sys
import tarfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ROOT / "docker/docker-compose.cloud.yml"
DEFAULT_ENV = ROOT / "docker/.env.cloud"
CREDENTIALS = ROOT / ".local-run/cloud/seed-credentials.json"
SECRET_NAMES = (
    "MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET",
    "EDUCARE_INTERNAL_TOKEN", "EDUCARE_MCP_TOKEN", "EDUCARE_ADMIN_TOKEN",
    "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY",
)
DEFAULT_LANGFUSE_CONSOLE_URL = "https://jp.cloud.langfuse.com"


def frontend_console_url(values):
    value = values.get("VITE_LANGFUSE_URL", "").strip() or DEFAULT_LANGFUSE_CONSOLE_URL
    url = urlsplit(value)
    try:
        port = url.port
    except ValueError:
        raise ValueError("VITE_LANGFUSE_URL has an invalid HTTPS port") from None
    if (url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment
            or re.search(r"[\x00-\x20]", value) or (port is not None and not 1 <= port <= 65535)):
        raise ValueError("VITE_LANGFUSE_URL must be a public HTTPS console URL without credentials/query")
    return value.rstrip("/")


def private_write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(text)


def read_env(path):
    if path.is_symlink() or not stat.S_ISREG(path.lstat().st_mode):
        raise ValueError("Private env must be a regular file, not a symlink")
    if path.stat().st_mode & (stat.S_IRWXG | stat.S_IRWXO):
        raise ValueError("Private env file must have mode 0600: chmod 600 <env-file>")
    result = {}
    for number, line in enumerate(path.read_text().splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, separator, value = line.partition("=")
        if not separator or not re.fullmatch(r"[A-Z][A-Z0-9_]*", name) or name in result:
            raise ValueError(f"Invalid or duplicate env assignment at line {number}")
        parsed = shlex.split(value, comments=True)
        if len(parsed) > 1:
            raise ValueError(f"Quote the env value at line {number}")
        result[name] = parsed[0] if parsed else ""
    return result


def normalize_env(values, provider="cloud"):
    values = dict(values)
    for legacy, current in (("OCI_SITE_ADDRESS", "CLOUD_SITE_ADDRESS"),
                            ("OCI_PUBLIC_IP", "CLOUD_PUBLIC_IP")):
        if legacy in values:
            if values.get(current) and values[current] != values[legacy]:
                raise ValueError(f"Conflicting {legacy} and {current} values")
            values.setdefault(current, values[legacy])
    values.setdefault("CLOUD_PROJECT_NAME", f"edu-portrait-{provider}")
    values["VITE_LANGFUSE_URL"] = frontend_console_url(values)
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", values["CLOUD_PROJECT_NAME"]):
        raise ValueError("CLOUD_PROJECT_NAME must use lower-case letters, digits, hyphen or underscore")
    return values


def validate_env(values):
    frontend_console_url(values)
    required = SECRET_NAMES + (
        "CLOUD_SITE_ADDRESS", "SPRING_AI_OPENAI_BASE_URL", "LLM_BASE_URL",
        "LLM_API_KEY", "LLM_MODEL", "EMBEDDING_BASE_URL", "EMBEDDING_MODEL",
    )
    for name in required:
        value = values.get(name, "")
        if not value or "change-me" in value or value == "dummy":
            raise ValueError(f"Configure {name}; placeholder or empty value is not deployable")
    for name in SECRET_NAMES:
        if not re.fullmatch(r"[A-Za-z0-9_-]{32,}", values[name]):
            raise ValueError(f"{name} must contain at least 32 safe random characters")
    if len({values[name] for name in SECRET_NAMES}) != len(SECRET_NAMES):
        raise ValueError("Use distinct secrets for database, Redis, JWT, MCP and internal access")
    embedding_key = values.get("EMBEDDING_API_KEY", "")
    if "change-me" in embedding_key or embedding_key == "dummy":
        raise ValueError("Configure EMBEDDING_API_KEY or leave it empty to share the chat key")
    hostname = values["CLOUD_SITE_ADDRESS"]
    if not re.fullmatch(r"(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}", hostname):
        raise ValueError("CLOUD_SITE_ADDRESS must be a DNS hostname without scheme, port or path")
    if hostname.lower().endswith((".localhost", ".local", ".internal", ".example.com")):
        raise ValueError("CLOUD_SITE_ADDRESS must be a public hostname with valid DNS and TLS")
    for name in ("LLM_BASE_URL", "SPRING_AI_OPENAI_BASE_URL", "EMBEDDING_BASE_URL"):
        url = urlsplit(values[name])
        if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError(f"{name} must be an HTTPS provider URL without credentials/query")
    if values["LLM_BASE_URL"].rstrip("/") != values["SPRING_AI_OPENAI_BASE_URL"].rstrip("/") + "/v1":
        raise ValueError("Python LLM_BASE_URL must equal Java provider root followed by /v1")
    if not values["EMBEDDING_BASE_URL"].rstrip("/").endswith("/v1"):
        raise ValueError("EMBEDDING_BASE_URL must include /v1")
    if int(values.get("EMBEDDING_DIM", "1024")) <= 0:
        raise ValueError("EMBEDDING_DIM must be a positive model output dimension")


def ensure_untracked(path):
    tracked = subprocess.run(["git", "-C", str(ROOT), "ls-files", "--error-unmatch", str(path)],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if tracked.returncode == 0:
        raise ValueError("Deployment env is tracked by Git; remove it and rotate its secrets")


def compose(values, path, *arguments, capture=False, input_text=None):
    environment = dict(os.environ, **values, COMPOSE_PARALLEL_LIMIT="1")
    environment.pop("COMPOSE_PROFILES", None)
    result = subprocess.run(
        ["docker", "compose", "--project-name", values.get("CLOUD_PROJECT_NAME", "edu-portrait-cloud"),
         "--env-file", str(path), "-f", str(COMPOSE), *arguments],
        env=environment, text=True, input=input_text,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )
    if result.returncode:
        # Captured output could contain resolved secrets or SQL hashes: keep it private.
        raise RuntimeError(f"Docker Compose command failed (exit {result.returncode})")
    return result.stdout if capture else ""


def mysql(values, path, sql):
    return compose(values, path, "exec", "-T", "mysql", "sh", "-c",
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names edu_portrait',
                   capture=True, input_text=sql)


def seed_hashes():
    content = (ROOT / "sql/init/01_init.sql").read_text()
    hashes = re.findall(r"\('(admin|teacher|student)',\s*'([^']+)'", content)
    if {name for name, _ in hashes} != {"admin", "teacher", "student"}:
        raise RuntimeError("Cannot identify all three factory seed hashes")
    return dict(hashes)


def seed_accounts(values, path):
    current = mysql(values, path, "SELECT username,password FROM sys_user WHERE username IN ('admin','teacher','student');\n")
    rows = dict(line.split("\t", 1) for line in current.splitlines() if "\t" in line)
    if set(rows) != set(seed_hashes()):
        raise ValueError("Expected seed accounts are missing; inspect the database before proceeding")
    if any(not re.fullmatch(r"\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}", hashed) for hashed in rows.values()):
        raise ValueError("Seed account password hash is not valid bcrypt; inspect the database")
    return rows


def assert_no_factory_passwords(values, path):
    import bcrypt
    defaults = seed_hashes()
    for name, hashed in seed_accounts(values, path).items():
        # Rehashing a known password changes its salt/hash, not its security.
        if hashed == defaults[name] or bcrypt.checkpw(name.encode(), hashed.encode()):
            raise ValueError("Factory login credentials remain; rotate them before opening the public site")


def ensure_capacity():
    memory = Path("/proc/meminfo")
    if not memory.exists():
        raise ValueError("Run deployment startup on the Linux cloud VM; local check/build is still available")
    match = re.search(r"MemTotal:\s+(\d+) kB", memory.read_text())
    if not match or int(match[1]) < 20 * 1024 * 1024:
        raise ValueError("Full stack needs a validated VM with at least 20 GiB RAM; 24 GiB is the target")


def ensure_export_font(values):
    configured = Path(values.get("CLOUD_EXPORT_FONT_FILE") or "../.local-run/cloud/fonts/NotoSansSC-Regular.ttf")
    path = configured if configured.is_absolute() else ROOT / "docker" / configured
    if not path.exists() or path.is_symlink() or not stat.S_ISREG(path.lstat().st_mode):
        raise ValueError("Install a static Noto Sans SC TTF at CLOUD_EXPORT_FONT_FILE before starting services")
    # PDFBox's TrueType path must not receive an OTF renamed to .ttf or an
    # uninstantiated variable TTF. Actual Chinese PDF rendering is still required.
    with path.open("rb") as font:
        header = font.read(12)
        if len(header) != 12 or header[:4] not in (b"\x00\x01\x00\x00", b"true"):
            raise ValueError("Export font must be a genuine static TrueType file, not renamed OTF")
        count = struct.unpack(">H", header[4:6])[0]
        if not 1 <= count <= 128:
            raise ValueError("Export font table directory is invalid")
        directory = font.read(count * 16)
    if len(directory) != count * 16:
        raise ValueError("Export font is truncated")
    tables = {directory[index:index + 4] for index in range(0, len(directory), 16)}
    if b"glyf" not in tables or b"fvar" in tables:
        raise ValueError("Export font must have TrueType outlines and be instantiated as static Regular")


def ensure_bind_sources(values):
    # Docker Compose's legacy bind path supports SELinux Z but can create a
    # missing source directory. Fail before up rather than relying on Mount API
    # create_host_path:false, which currently drops SELinux relabeling.
    for path, kind in ((ROOT / "sql/init", "directory"),
                       (ROOT / "docker/caddy/Caddyfile.cloud", "file")):
        valid_type = path.is_dir() if kind == "directory" else path.is_file()
        if path.is_symlink() or not valid_type:
            raise ValueError(f"Required bind source must be an existing {kind}: {path.relative_to(ROOT)}")
    ensure_export_font(values)


def bootstrap_seeds(values, path, credentials_path=CREDENTIALS):
    import bcrypt
    if credentials_path.lstat().st_mode & (stat.S_IRWXG | stat.S_IRWXO):
        raise ValueError("Seed credential file must remain private (mode 0600)")
    if credentials_path.is_symlink():
        raise ValueError("Seed credential file must not be a symlink")
    credentials = json.loads(credentials_path.read_text())
    defaults = seed_hashes()
    rows = seed_accounts(values, path)
    statements = []
    for name, old_hash in defaults.items():
        password = credentials[name].encode()
        if rows[name] == old_hash or bcrypt.checkpw(name.encode(), rows[name].encode()):
            new_hash = bcrypt.hashpw(password, bcrypt.gensalt(rounds=12)).decode()
            statements.append(f"UPDATE sys_user SET password='{new_hash}' WHERE username='{name}' AND password='{rows[name]}';")
        elif not bcrypt.checkpw(password, rows[name].encode()):
            raise ValueError("Existing account credentials differ; this utility will not overwrite migrated passwords")
    if statements:
        mysql(values, path, "START TRANSACTION;\n" + "\n".join(statements) + "\nCOMMIT;\n")
    assert_no_factory_passwords(values, path)
    print("Seed credentials rotated; retrieve passwords from the private seed-credentials.json file")


# Only deployment/build inputs can leave the workstation. This intentionally excludes
# all documentation, interviews, knowledge corpora, exports and local configuration.
BACKEND_MODULES = frozenset(("common", "gateway", "auth-service", "user-service", "teacher-service",
                             "student-service", "mental-service", "data-service", "agent-service", "mcp-student-data", "knowledge-service"))
EXACT_RUNTIME_FILES = frozenset((
    "backend/pom.xml", "backend/.dockerignore",
    "frontend/package.json", "frontend/package-lock.json", "frontend/vite.config.js",
    "frontend/index.html", "frontend/.dockerignore", "frontend/scripts/check-bundle-size.mjs",
    "docker/Dockerfile.knowledge",
    "docker/docker-compose.cloud.yml", "docker/docker-compose.oci.yml", "docker/cloud.env.example", "docker/oci.env.example",
    "docker/Dockerfile.frontend.cloud", "docker/caddy/Caddyfile.cloud",
    "scripts/cloud_deploy.py", "scripts/cloud-deploy.py", "scripts/kamatera-deploy.py", "scripts/oci-deploy.py",
    "scripts/requirements-cloud.txt", "scripts/requirements-oci.txt",
    "k8s/workloads.json", "scripts/k3s-deploy.py",
    "scripts/apply-sql-migrations.py",
    "sql/init/01_init.sql", "sql/init/02_questionnaire_extension.sql", "sql/init/03_agent_init.sql",
    "sql/init/04_student_extras.sql", "sql/init/05_intervention_feedback.sql",
    "sql/init/06_portrait_contract.sql", "sql/init/07_mental_contract.sql", "sql/init/09_identity_contract.sql", "sql/init/10_agent_diagnostics.sql",
    "backend/knowledge-service/pom.xml",
    "backend/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
))
REQUIRED_RUNTIME_FILES = frozenset((
    "backend/pom.xml", "frontend/package-lock.json", "docker/Dockerfile.knowledge",
    "docker/docker-compose.cloud.yml", "scripts/cloud_deploy.py", "sql/init/01_init.sql",
    "k8s/workloads.json", "scripts/k3s-deploy.py",
    "scripts/apply-sql-migrations.py",
    "sql/init/06_portrait_contract.sql", "sql/init/07_mental_contract.sql", "sql/init/09_identity_contract.sql", "sql/init/10_agent_diagnostics.sql",
    "sql/init/02_questionnaire_extension.sql", "sql/init/03_agent_init.sql", "sql/init/04_student_extras.sql", "sql/init/05_intervention_feedback.sql",
    "sql/migrations/20261007_01_portrait_contract.sql", "sql/migrations/20261007_02_mental_contract.sql",
    "sql/migrations/20261007_04_identity_contract.sql",
    "backend/knowledge-service/pom.xml",
    "backend/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
    "backend/agent-service/src/main/resources/prompts/risk-analyze.system.md",
    "backend/agent-service/src/main/resources/prompts/agent-loop.system.md",
    "backend/agent-service/src/main/resources/skills/risk-assessment.md",
    "backend/agent-service/src/main/resources/skills/psychological-screening.md",
    "backend/agent-service/src/main/resources/skills/intervention-design.md",
    "backend/agent-service/src/main/resources/skills/compliance-audit.md",
))
EXCLUDED_PARTS = frozenset((".git", ".ssh", ".aws", ".local-run", "docs", "volumes", "backups",
                            "uploads", "exports", "node_modules", "target", "dist", ".venv", "venv", "__pycache__",
                            "师生画像系统", "知识库数据", "deliverables"))
PRIVATE_SUFFIXES = frozenset((".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".db", ".sqlite", ".dump",
                              ".csv", ".tsv", ".xlsx", ".xls", ".doc", ".docx", ".pdf", ".jsonl", ".log"))


def runtime_file_allowed(name):
    path = Path(name)
    parts = path.parts
    if path.is_absolute() or ".." in parts or not parts or set(parts) & EXCLUDED_PARTS:
        return False
    if name in EXACT_RUNTIME_FILES:
        return True
    if len(parts) >= 3 and parts[:3] == ("ai-inference-service", "app", "data"):
        return False
    if path.suffix.lower() in PRIVATE_SUFFIXES or path.name.startswith(".env"):
        return False
    if re.search(r"(?:^|[-_.])(secret|credentials|backup|dump|local)(?:[-_.]|$)", path.name, re.I):
        return False
    if re.fullmatch(r"sql/migrations/20261007_[0-9]{2}_[a-z0-9_]+\.sql", name):
        return True
    if len(parts) == 2 and parts[0] == "docker":
        return path.name in {f"Dockerfile.{name}" for name in
                             ("auth", "user", "teacher", "student", "mental", "data", "agent", "gateway", "mcp-student-data")}
    if parts[0] == "backend" and len(parts) >= 3 and parts[1] in BACKEND_MODULES:
        if len(parts) == 3 and parts[2] == "pom.xml":
            return True
        if len(parts) > 5 and parts[2:4] == ("src", "main"):
            if parts[4] == "java":
                return path.suffix == ".java"
            if parts[4] == "resources":
                if len(parts) == 7 and parts[5] in ("prompts", "skills") and path.suffix == ".md":
                    return True
                return path.suffix.lower() in {".yml", ".yaml", ".properties", ".json", ".xml", ".html", ".ftl", ".css", ".js"}
    if parts[0] == "frontend" and len(parts) >= 3 and parts[1] in ("src", "public"):
        return path.suffix.lower() in {".vue", ".js", ".mjs", ".ts", ".css", ".scss", ".sass", ".svg", ".png", ".jpg", ".jpeg", ".webp", ".ico", ".woff", ".woff2"}
    return False


def source_manifest(root=ROOT):
    result = subprocess.run(["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if result.returncode:
        raise ValueError("Package source from the Git workstation checkout, not the extracted VM archive")
    candidates = sorted(set(os.fsdecode(name) for name in result.stdout.split(b"\0") if name))
    # Also exclude accidentally tracked files that now match .gitignore.
    ignored = subprocess.run(["git", "-C", str(root), "check-ignore", "--no-index", "-z", "--stdin"],
                             input=b"\0".join(os.fsencode(name) for name in candidates) + b"\0",
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if ignored.returncode not in (0, 1):
        raise RuntimeError("Cannot verify source ignore rules")
    excluded = {os.fsdecode(name) for name in ignored.stdout.split(b"\0") if name}
    manifest = []
    for name in candidates:
        if name in excluded or not runtime_file_allowed(name):
            continue
        path = root / name
        # Never follow either a final symlink or a symlinked parent directory.
        if any((root.joinpath(*Path(name).parts[:depth])).is_symlink()
               for depth in range(1, len(Path(name).parts) + 1)):
            raise ValueError(f"Runtime source contains a symlink; review {name} before packaging")
        if not path.exists():  # Deleted tracked file.
            continue
        if not stat.S_ISREG(path.lstat().st_mode):
            raise ValueError(f"Runtime source is not a regular file: {name}")
        manifest.append(name)
    return manifest


def package_source(output, root=ROOT):
    manifest = source_manifest(root)
    if not REQUIRED_RUNTIME_FILES.issubset(manifest):
        raise ValueError("Required runtime inputs are absent from the safe source manifest")
    output.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "wb") as stream, tarfile.open(fileobj=stream, mode="w:gz", format=tarfile.PAX_FORMAT) as archive:
            for name in manifest:
                path = root / name
                source_fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
                with os.fdopen(source_fd, "rb") as source:
                    before = os.fstat(source.fileno())
                    if not stat.S_ISREG(before.st_mode):
                        raise ValueError("Source changed while packaging; review and retry")
                    info = tarfile.TarInfo(name)
                    info.size = before.st_size
                    info.mode = 0o644
                    info.uid = info.gid = 0
                    info.mtime = int(before.st_mtime)
                    archive.addfile(info, source)
                    after = os.fstat(source.fileno())
                    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                        raise ValueError("Source changed while packaging; review and retry")
    except Exception:
        output.unlink(missing_ok=True)
        raise
    print(f"Private source archive created ({len(manifest)} runtime files); env, SSH keys, seed credentials and business data excluded")


def main(default_provider="cloud"):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("init-env", "package-source", "check", "build", "start-private", "bootstrap-seeds", "init-rag", "publish", "status", "stop"))
    parser.add_argument("--provider", choices=("cloud", "kamatera", "oci"), default=default_provider)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--seed-file", type=Path)
    parser.add_argument("--output", type=Path, help="New private archive path; package-source only, never overwrites")
    args = parser.parse_args()
    path = (args.env_file or ROOT / f"docker/.env.{args.provider}").expanduser().resolve()
    credentials_path = (args.seed_file or ROOT / f".local-run/{args.provider}/seed-credentials.json").expanduser().resolve()
    if args.command == "package-source":
        output = (args.output or ROOT / f".local-run/{args.provider}/source.tar.gz").expanduser().absolute()
        package_source(output)
        return
    if args.output:
        raise ValueError("--output is valid only for package-source")
    if args.command == "init-env":
        ensure_untracked(path)
        ensure_untracked(credentials_path)
        if path.exists() or credentials_path.exists():
            raise ValueError("Env/seed credentials already exist; refusing to overwrite or rotate them")
        template = (ROOT / "docker/cloud.env.example").read_text()
        template = template.replace("../.local-run/cloud/fonts/NotoSansSC-Regular.ttf",
                                    f"../.local-run/{args.provider}/fonts/NotoSansSC-Regular.ttf")
        template = f"CLOUD_PROJECT_NAME=edu-portrait-{args.provider}\n" + template
        for name in SECRET_NAMES:
            template = template.replace(f"{name}=change-me\n", f"{name}={secrets.token_hex(24)}\n")
        private_write(path, template)
        private_write(credentials_path, json.dumps({name: secrets.token_urlsafe(24) for name in ("admin", "teacher", "student")}, indent=2) + "\n")
        print("Created private env and seed credential files; fill hostname/model fields before check")
        return
    values = normalize_env(read_env(path), args.provider)
    ensure_untracked(path)
    validate_env(values)
    compose(values, path, "config", "--quiet", capture=True)
    if args.command == "check":
        print("Cloud env and Compose static checks passed; no cloud resource or public listener was created")
    elif args.command == "build":
        compose(values, path, "--profile", "public", "build", "--pull")
    elif args.command == "start-private":
        ensure_capacity()
        ensure_bind_sources(values)
        compose(values, path, "stop", "frontend")
        compose(values, path, "up", "-d", "--wait", "--wait-timeout", "600", "mysql", "redis")
        import importlib.util
        migration_spec = importlib.util.spec_from_file_location("educare_sql_migrations", ROOT / "scripts/apply-sql-migrations.py")
        migration = importlib.util.module_from_spec(migration_spec)
        migration_spec.loader.exec_module(migration)
        run = lambda sql: mysql(values, path, sql).strip()
        migration.validate_base_schema(run)
        migration.apply_migrations(run, "edu_portrait")
        compose(values, path, "up", "-d", "--wait", "--wait-timeout", "600")
        print("Private services started; public frontend remains stopped")
    elif args.command == "bootstrap-seeds":
        ensure_untracked(credentials_path)
        bootstrap_seeds(values, path, credentials_path)
    elif args.command == "init-rag":
        compose(values, path, "exec", "-T", "ai-inference-service", 'sh', '-c', 'curl --fail --silent --show-error -X POST -H "X-Admin-Token: $EDUCARE_ADMIN_TOKEN" http://127.0.0.1:8090/api/v1/rag/initialize')
        print("RAG collections initialized; ingestion/retrieval acceptance is still required")
    elif args.command == "publish":
        ensure_capacity()
        ensure_bind_sources(values)
        assert_no_factory_passwords(values, path)
        try:
            public_ip = ipaddress.IPv4Address(values.get("CLOUD_PUBLIC_IP", ""))
        except ipaddress.AddressValueError:
            raise ValueError("Configure CLOUD_PUBLIC_IP with the created VM's public IPv4") from None
        if not public_ip.is_global:
            raise ValueError("CLOUD_PUBLIC_IP must be the created VM's public IPv4")
        addresses = socket.getaddrinfo(values["CLOUD_SITE_ADDRESS"], 443, family=socket.AF_INET)
        if str(public_ip) not in {address[4][0] for address in addresses}:
            raise ValueError("Site DNS does not point at CLOUD_PUBLIC_IP; refusing to open the frontend")
        compose(values, path, "up", "-d", "frontend")
        print("Frontend started; verify trusted HTTPS and login before declaring deployment complete")
    elif args.command == "status":
        compose(values, path, "--profile", "public", "ps")
    elif args.command == "stop":
        compose(values, path, "--profile", "public", "down")
        print("Containers stopped and volumes retained; terminate Kamatera resources to stop platform billing")


def cli(default_provider="cloud"):
    try:
        main(default_provider)
    except (ValueError, RuntimeError, OSError, KeyError, ImportError) as error:
        print(f"Cloud deployment stopped: {error}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    cli()
