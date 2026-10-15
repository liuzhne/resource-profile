#!/usr/bin/env python3
"""Deployment security regressions; uses no cloud account, real secrets or containers."""
import importlib.util
import os
from pathlib import Path
import tempfile
import subprocess
import struct
import tarfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("cloud_deploy", Path(__file__).with_name("cloud_deploy.py"))
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)


def configured_values():
    values = {name: str(number).zfill(48) for number, name in enumerate(deployment.SECRET_NAMES, 1)}
    values.update({
        "CLOUD_SITE_ADDRESS": "school.deployment.test",
        "LLM_API_KEY": "unit-test-provider-key",
        "LLM_MODEL": "chat-model",
        "SPRING_AI_OPENAI_BASE_URL": "https://provider.deployment.test",
        "LLM_BASE_URL": "https://provider.deployment.test/v1",
        "EMBEDDING_BASE_URL": "https://embedding.deployment.test/v1",
        "EMBEDDING_MODEL": "embedding-model",
        "EMBEDDING_API_KEY": "unit-test-embedding-key",
        "EMBEDDING_DIM": "1024",
    })
    return values


class CloudDeploymentSecurityTest(unittest.TestCase):
    def test_java_knowledge_runtime_replaces_python_and_render_mcp_path(self):
        import json
        spec = json.loads((deployment.ROOT / "k8s/workloads.json").read_text())
        for name in ("ai-inference-service", "knowledge-rag-mcp"):
            item = spec[name]
            self.assertEqual(item["image"], "edu-portrait-knowledge:cloud")
            self.assertNotIn("python", str(item.get("args")) + str(item["healthcheck"]))
            self.assertIn("EDUCARE_INTERNAL_TOKEN", item["environment"])
            self.assertNotIn("LLM_API_KEY", item["environment"])
        self.assertEqual(spec["knowledge-rag-mcp"]["environment"]["KNOWLEDGE_API_ENABLED"], "false")
        self.assertFalse(deployment.runtime_file_allowed("ai-inference-service/app/main.py"))
        self.assertTrue(deployment.runtime_file_allowed("backend/knowledge-service/src/main/java/com/edu/knowledge/RagService.java"))
        render = (deployment.ROOT / "render.yaml").read_text()
        self.assertIn("key: MCP_KNOWLEDGE_RAG_ENDPOINT\n        value: /mcp\n", render)
        self.assertIn("key: KNOWLEDGE_MCP_ENABLED\n        value: \"true\"", render)

    def test_env_parser_preserves_literals_without_executing_shell(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            deployment.private_write(path, "MODEL_KEY='literal-$(touch NO_EXECUTION)-`id`'\nEMPTY=\n")
            self.assertEqual(deployment.read_env(path)["MODEL_KEY"], "literal-$(touch NO_EXECUTION)-`id`")
            self.assertEqual(deployment.read_env(path)["EMPTY"], "")
            self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)

    def test_world_readable_env_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            deployment.private_write(path, "KEY=private\n")
            path.chmod(0o644)
            with self.assertRaisesRegex(ValueError, "0600"):
                deployment.read_env(path)

    def test_weak_or_reused_internal_credentials_are_rejected(self):
        values = configured_values()
        deployment.validate_env(values)
        values["EDUCARE_INTERNAL_TOKEN"] = "short"
        with self.assertRaisesRegex(ValueError, "EDUCARE_INTERNAL_TOKEN"):
            deployment.validate_env(values)
        values["EDUCARE_INTERNAL_TOKEN"] = values["JWT_SECRET"]
        with self.assertRaisesRegex(ValueError, "distinct secrets"):
            deployment.validate_env(values)

    def test_http_site_or_provider_cannot_enable_public_login(self):
        values = configured_values()
        values["CLOUD_SITE_ADDRESS"] = "http://school.deployment.test"
        with self.assertRaises(ValueError):
            deployment.validate_env(values)
        values = configured_values()
        values["EMBEDDING_BASE_URL"] = "http://embedding.deployment.test/v1"
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            deployment.validate_env(values)

    def test_secret_template_cannot_pass_deployment_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            deployment.private_write(path, (deployment.ROOT / "docker/cloud.env.example").read_text())
            with self.assertRaisesRegex(ValueError, "placeholder"):
                deployment.validate_env(deployment.read_env(path))

    def test_factory_password_blocks_publication(self):
        import bcrypt
        rows = deployment.seed_hashes()
        def output(accounts):
            return "\n".join(f"{name}\t{hashed}" for name, hashed in accounts.items()) + "\n"
        with patch.object(deployment, "mysql", return_value=output(rows)):
            with self.assertRaisesRegex(ValueError, "Factory login credentials"):
                deployment.assert_no_factory_passwords(configured_values(), Path("unused.env"))
        rows = {name: bcrypt.hashpw(b"synthetic-random-login", bcrypt.gensalt(rounds=4)).decode() for name in rows}
        with patch.object(deployment, "mysql", return_value=output(rows)):
            deployment.assert_no_factory_passwords(configured_values(), Path("unused.env"))
        rows["admin"] = bcrypt.hashpw(b"admin", bcrypt.gensalt(rounds=4)).decode()
        with patch.object(deployment, "mysql", return_value=output(rows)):
            with self.assertRaisesRegex(ValueError, "Factory login credentials"):
                deployment.assert_no_factory_passwords(configured_values(), Path("unused.env"))

    def test_wrong_python_api_root_is_rejected_before_deploy(self):
        values = configured_values()
        values["LLM_BASE_URL"] = values["SPRING_AI_OPENAI_BASE_URL"]
        with self.assertRaisesRegex(ValueError, "followed by /v1"):
            deployment.validate_env(values)

    def test_legacy_oci_settings_preserve_project_and_reject_conflicts(self):
        values = configured_values()
        values["OCI_SITE_ADDRESS"] = values.pop("CLOUD_SITE_ADDRESS")
        adapted = deployment.normalize_env(values, "oci")
        deployment.validate_env(adapted)
        self.assertEqual(adapted["CLOUD_PROJECT_NAME"], "edu-portrait-oci")
        values["CLOUD_SITE_ADDRESS"] = "different.deployment.test"
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            deployment.normalize_env(values, "oci")

    def test_frontend_console_default_and_https_override_are_public_build_inputs(self):
        values = deployment.normalize_env(configured_values(), "kamatera")
        self.assertEqual(values["VITE_LANGFUSE_URL"], "https://jp.cloud.langfuse.com")
        values["VITE_LANGFUSE_URL"] = "https://trace.educare.test/project/synthetic/"
        normalized = deployment.normalize_env(values, "kamatera")
        self.assertEqual(normalized["VITE_LANGFUSE_URL"], "https://trace.educare.test/project/synthetic")
        deployment.validate_env(normalized)
        for bad in ("javascript:alert(1)", "http://trace.educare.test", "https://user:password@trace.educare.test", "https://trace.educare.test?key=secret", "https://trace.educare.test#token"):
            with self.subTest(url=bad), self.assertRaisesRegex(ValueError, "VITE_LANGFUSE_URL"):
                deployment.normalize_env(dict(values, VITE_LANGFUSE_URL=bad), "kamatera")

    def test_runtime_whitelist_excludes_interviews_backups_keys_and_private_env(self):
        for name in ("docs/interview.txt", "backend/common/src/main/resources/application-local.yml",
                     "docker/.env.audit", "docker/.env.kamatera", ".local-run/kamatera/ssh/id_ed25519",
                     "frontend/public/student.csv", "ai-inference-service/app/data/student.json",
                     "sql/init/99_student_backup.sql", "师生画像系统/访谈.docx"):
            with self.subTest(name=name):
                self.assertFalse(deployment.runtime_file_allowed(name))

    def test_contract_migrations_and_executor_enter_manifest_but_private_sql_does_not(self):
        required = {"scripts/apply-sql-migrations.py", "sql/init/06_portrait_contract.sql", "sql/init/07_mental_contract.sql", "sql/init/09_identity_contract.sql",
                    "sql/migrations/20261007_01_portrait_contract.sql", "sql/migrations/20261007_02_mental_contract.sql", "sql/migrations/20261007_04_identity_contract.sql"}
        manifest = set(deployment.source_manifest())
        self.assertTrue(required <= deployment.REQUIRED_RUNTIME_FILES)
        self.assertTrue(required <= manifest)
        for name in ("sql/migrations/20261007_99_student_backup.sql", "sql/migrations/20261007_99_credentials.sql", "sql/migrations/private-dump.sql", "sql/migrations/20261007_01_portrait_contract.sql/../../private.sql"):
            with self.subTest(name=name):
                self.assertFalse(deployment.runtime_file_allowed(name))

    def test_current_runtime_prompts_skills_autoconfiguration_and_schema_are_packaged(self):
        manifest = set(deployment.source_manifest())
        paths = ["backend/knowledge-service/pom.xml", "docker/Dockerfile.knowledge",
                 "backend/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"]
        resources = deployment.ROOT / "backend/agent-service/src/main/resources"
        paths += [str(path.relative_to(deployment.ROOT)) for directory in ("prompts", "skills")
                  for path in (resources / directory).glob("*.md")]
        paths += [str(path.relative_to(deployment.ROOT)) for path in (deployment.ROOT / "sql/init").glob("*.sql")]
        for name in paths:
            with self.subTest(name=name):
                self.assertIn(name, manifest)

    def test_all_current_java_production_sources_poms_and_runtime_resources_are_packaged(self):
        manifest = set(deployment.source_manifest())
        backend = deployment.ROOT / "backend"
        paths = list(backend.glob("*/pom.xml")) + [backend / "pom.xml"]
        paths += list(backend.glob("*/src/main/java/**/*.java"))
        runtime_suffixes = {".yml", ".yaml", ".properties", ".json", ".xml", ".html", ".ftl", ".css", ".js", ".imports"}
        paths += [path for path in backend.glob("*/src/main/resources/**/*")
                  if path.is_file() and (path.suffix in runtime_suffixes
                     or (path.suffix == ".md" and path.parent.name in ("prompts", "skills")))
                  and "local" not in path.stem.lower()]
        for path in paths:
            with self.subTest(path=path.relative_to(deployment.ROOT)):
                self.assertIn(str(path.relative_to(deployment.ROOT)), manifest)
        self.assertTrue(any("com/edu/data/" in name for name in manifest))

    def test_missing_renamed_otf_and_variable_export_fonts_block_startup(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "NotoSansSC-Regular.ttf"
            values = {"CLOUD_EXPORT_FONT_FILE": str(path)}
            with self.assertRaisesRegex(ValueError, "Install"):
                deployment.ensure_export_font(values)
            path.write_bytes(b"OTTO" + b"\0" * 12)
            with self.assertRaisesRegex(ValueError, "genuine static TrueType"):
                deployment.ensure_export_font(values)
            path.write_bytes(b"\x00\x01\x00\x00" + struct.pack(">HHHH", 2, 0, 0, 0)
                             + b"glyf" + b"\0" * 12 + b"fvar" + b"\0" * 12)
            with self.assertRaisesRegex(ValueError, "static Regular"):
                deployment.ensure_export_font(values)

    def test_missing_or_wrong_bind_sources_block_start_before_docker_can_create_directories(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(deployment, "ROOT", Path(directory)), \
                patch.object(deployment, "ensure_export_font") as font_check:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "sql/init"):
                deployment.ensure_bind_sources({})
            (root / "sql/init").mkdir(parents=True)
            caddy = root / "docker/caddy/Caddyfile.cloud"
            caddy.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, "Caddyfile.cloud"):
                deployment.ensure_bind_sources({})
            caddy.rmdir()
            caddy.write_text("synthetic Caddy fixture\n")
            deployment.ensure_bind_sources({})
            font_check.assert_called_once_with({})
            caddy.unlink()
            target = root / "outside.conf"
            target.write_text("not an approved source\n")
            caddy.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "Caddyfile.cloud"):
                deployment.ensure_bind_sources({})

    def test_archive_includes_untracked_runtime_but_not_ignored_tracked_or_symlinked_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            required = deployment.REQUIRED_RUNTIME_FILES
            for name in (*required, "ai-inference-service/app/live.py", "ai-inference-service/app/ignored.py",
                         "docs/interview.md", "docker/.env.audit"):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("synthetic fixture\n")
            # Ignoring after staging simulates an accidentally tracked file.
            subprocess.run(["git", "-C", str(root), "add", "ai-inference-service/app/ignored.py"], check=True)
            (root / ".gitignore").write_text("ai-inference-service/app/ignored.py\ndocker/.env.*\n")
            output = root / "source.tar.gz"
            deployment.package_source(output, root)
            self.assertEqual(output.stat().st_mode & 0o777, 0o600)
            with tarfile.open(output) as archive:
                names = set(archive.getnames())
                self.assertNotIn("ai-inference-service/app/live.py", names)
                self.assertTrue(set(required) <= names)
                self.assertNotIn("ai-inference-service/app/ignored.py", names)
                self.assertNotIn("docs/interview.md", names)
                self.assertNotIn("docker/.env.audit", names)
                self.assertTrue(all(member.isfile() and not member.issym() for member in archive.getmembers()))
            with self.assertRaises(FileExistsError):
                deployment.package_source(output, root)
            (root / "backend/knowledge-service/src/main/java/Symlink.java").parent.mkdir(parents=True, exist_ok=True)
            (root / "backend/knowledge-service/src/main/java/Symlink.java").symlink_to(root / "docs/interview.md")
            with self.assertRaisesRegex(ValueError, "symlink"):
                deployment.source_manifest(root)


if __name__ == "__main__":
    unittest.main(verbosity=2)
