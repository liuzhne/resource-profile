#!/usr/bin/env python3
"""Schema/startup gates using real scripts and mocked transports; no containers or business rows."""
import contextlib
import importlib.util
import io
from pathlib import Path
import re
import subprocess
import sys
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parent.parent

def load(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

migration = load('sql_gate_test', 'apply-sql-migrations.py')
cloud = load('cloud_gate_test', 'cloud_deploy.py')

class BaseSchemaGateTest(unittest.TestCase):
    def test_catalog_covers_all_twenty_legacy_tables_and_no_new_migration_tables(self):
        tables = set()
        for file in (ROOT / 'sql/init').glob('0[1-5]*.sql'):
            tables.update(re.findall(r'CREATE TABLE IF NOT EXISTS\s+(\w+)', file.read_text()))
        self.assertEqual(len(tables), 20)
        self.assertEqual(migration.BASE_TABLES, tables)
        run = Mock(return_value='\n'.join(sorted(tables)))
        migration.validate_base_schema(run)
        self.assertEqual(run.call_count, 1)

    def test_each_missing_legacy_table_blocks_without_writing_schema_or_seeds(self):
        for missing in migration.BASE_TABLES:
            with self.subTest(missing=missing):
                run = Mock(return_value='\n'.join(sorted(migration.BASE_TABLES - {missing})))
                with self.assertRaisesRegex(ValueError, missing):
                    migration.validate_base_schema(run)
                self.assertEqual(run.call_count, 1)
                self.assertTrue(run.call_args.args[0].startswith('SELECT TABLE_NAME'))

    def test_main_rejects_partial_database_before_recording_or_applying_migration(self):
        with patch.object(sys, 'argv', ['migration', '--transport', 'docker']), \
                patch.object(migration, 'client', return_value='\n'.join(migration.BASE_TABLES - {'student_attendance'})), \
                patch.object(migration, 'apply_migrations') as apply:
            with self.assertRaisesRegex(ValueError, 'student_attendance'):
                migration.main()
            apply.assert_not_called()

    def cloud_main(self, present):
        calls = []
        def mysql(values, path, sql):
            calls.append(sql)
            if sql.startswith('SELECT TABLE_NAME'):
                return '\n'.join(sorted(present))
            return ''
        return calls, mysql

    def test_cloud_start_private_blocks_new_business_containers_for_partial_schema(self):
        queries, mysql = self.cloud_main(migration.BASE_TABLES - {'intervention_feedback'})
        with patch.object(sys, 'argv', ['cloud-deploy', 'start-private']), \
                patch.object(cloud, 'read_env', return_value={}), patch.object(cloud, 'normalize_env', return_value={}), \
                patch.object(cloud, 'ensure_untracked'), patch.object(cloud, 'validate_env'), \
                patch.object(cloud, 'ensure_capacity'), patch.object(cloud, 'ensure_bind_sources'), \
                patch.object(cloud, 'mysql', side_effect=mysql), patch.object(cloud, 'compose') as compose:
            with self.assertRaisesRegex(ValueError, 'intervention_feedback'):
                cloud.main()
            self.assertEqual(len(queries), 1)
            self.assertEqual([call.args[2:] for call in compose.call_args_list], [
                ('config', '--quiet'), ('stop', 'frontend'),
                ('up', '-d', '--wait', '--wait-timeout', '600', 'mysql', 'redis')
            ])

    def test_cloud_complete_schema_is_checked_before_migrations_then_business_start(self):
        queries, mysql = self.cloud_main(migration.BASE_TABLES)
        with patch.object(sys, 'argv', ['cloud-deploy', 'start-private']), \
                patch.object(cloud, 'read_env', return_value={}), patch.object(cloud, 'normalize_env', return_value={}), \
                patch.object(cloud, 'ensure_untracked'), patch.object(cloud, 'validate_env'), \
                patch.object(cloud, 'ensure_capacity'), patch.object(cloud, 'ensure_bind_sources'), \
                patch.object(cloud, 'mysql', side_effect=mysql), patch.object(cloud, 'compose') as compose, \
                contextlib.redirect_stdout(io.StringIO()):
            cloud.main()
            self.assertTrue(queries[0].startswith('SELECT TABLE_NAME'))
            self.assertTrue(queries[1].startswith('CREATE TABLE IF NOT EXISTS sys_migration_log'))
            self.assertEqual(compose.call_args.args[2:], ('up', '-d', '--wait', '--wait-timeout', '600'))

class LocalStartupGateTest(unittest.TestCase):
    def test_real_up_dispatch_stops_at_each_failed_stage_and_never_claims_ready(self):
        source = (ROOT / 'scripts/local_dev.sh').read_text()
        dispatch = source[source.index('case "$CMD" in'):]
        stages = ['preflight', 'infra_up', 'seed_sql', 'build', 'llm_check', 'java_up', 'front_up']
        for fail in stages + [None]:
            with self.subTest(failed=fail):
                functions = '\n'.join(f'{stage}() {{ printf "%s\\n" {stage}; return {1 if stage == fail else 0}; }}' for stage in stages)
                script = 'set -uo pipefail\nCMD=up\n' + functions + '\nsummary() { echo READY; }\n' + dispatch
                run = subprocess.run(['bash', '-s'], input=script, text=True, capture_output=True)
                expected = stages[:stages.index(fail) + 1] if fail else stages + ['READY']
                self.assertEqual(run.stdout.splitlines(), expected)
                self.assertEqual(run.returncode, 1 if fail else 0)

    def test_real_java_stage_stops_before_gateway_after_business_failure(self):
        source = (ROOT / 'scripts/local_dev.sh').read_text()
        function = source[source.index('java_up() {'):source.index('front_up() {')]
        script = 'CORE_ONLY=true\nCORE_SVCS=(auth-service:8081 mental-service:8085)\nGW_SVCS=(gateway:8080)\nhd(){ :; }\ner(){ :; }\nstart_java(){ echo "$1"; [ "$1" != mental-service ]; }\n' + function + '\njava_up\n'
        run = subprocess.run(['bash', '-s'], input=script, text=True, capture_output=True)
        self.assertEqual(run.stdout.splitlines(), ['auth-service', 'mental-service'])
        self.assertEqual(run.returncode, 1)

if __name__ == '__main__':
    unittest.main(verbosity=2)
