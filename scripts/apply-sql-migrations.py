#!/usr/bin/env python3
"""Apply versioned additive SQL to a selected database; no credentials or rows on stdout."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent
# Existing schema from initialization files 01 through 05. Additive migrations cannot
# safely repair a partially initialized database by replaying its business seeds.
BASE_TABLES = frozenset({
    'sys_user', 'sys_role', 'sys_user_role', 'sys_permission', 'sys_role_permission',
    'teacher_info', 'student_info', 'mental_questionnaire', 'mental_question',
    'mental_response', 'mental_assessment', 'sys_login_log', 'agent_task',
    'intervention_report', 'knowledge_citation', 'agent_export_task', 'audit_log',
    'student_academic_record', 'student_attendance', 'intervention_feedback',
})


def client(transport, target, namespace, database, sql):
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", database):
        raise ValueError("Invalid database name")
    shell = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names "$1"'
    if transport == 'docker':
        command = ['docker', 'exec', '-i', target, 'sh', '-c', shell, 'sh', database]
    else:
        command = ['k3s', 'kubectl', '-n', namespace, 'exec', '-i', target, '--', 'sh', '-c', shell, 'sh', database]
    result = subprocess.run(command, text=True, input=sql, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # SQL diagnostics may include account/user content; preserve only in a private operator log.
        raise RuntimeError('SQL execution failed; inspect the selected database privately before retrying')
    return result.stdout.strip()


def selected_sql(text, database):
    # Client already selects the explicit schema. Allow audited legacy USE directives only.
    text = re.sub(r'(?im)^\s*USE\s+edu_portrait\s*;\s*$', '', text)
    text = re.sub(r'(?im)^\s*CREATE DATABASE IF NOT EXISTS edu_portrait[^;]*;\s*$', '', text)
    if re.search(r'(?im)^\s*(?:USE|CREATE DATABASE)\b', text):
        raise ValueError('Unexpected schema switching directive')
    return text


def validate_base_schema(run):
    """Fail before migration writes when any legacy business table is missing."""
    actual = set(run('SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();').splitlines())
    missing = BASE_TABLES - actual
    if missing:
        # Only static table identifiers are reported, never business records or SQL output.
        raise ValueError('Base schema incomplete; restore missing base tables before applying additive migrations: '
                         + ', '.join(sorted(missing)))


def apply_migrations(run, database):
    run("CREATE TABLE IF NOT EXISTS sys_migration_log (file VARCHAR(160) PRIMARY KEY, checksum CHAR(64) NOT NULL, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")
    files = sorted((ROOT/'sql/migrations').glob('[0-9]*.sql'))
    for path in files:
        if not re.fullmatch(r'[0-9A-Za-z_.-]+', path.name):
            raise ValueError('Unexpected migration filename')
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        previous = run(f"SELECT checksum FROM sys_migration_log WHERE file='{path.name}';")
        if previous:
            if previous != digest:
                raise ValueError(f'Applied migration was modified: {path.name}; create a new migration instead')
            print(f'Already applied: {path.name}')
            continue
        run(selected_sql(path.read_text(), database))
        run(f"INSERT INTO sys_migration_log(file,checksum) VALUES('{path.name}','{digest}');")
        print(f'Applied: {path.name}')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--transport',choices=['docker','k3s'],required=True)
    parser.add_argument('--target')
    parser.add_argument('--namespace',default='educare')
    parser.add_argument('--database',default='edu_portrait')
    parser.add_argument('--initialize',action='store_true',help='Initialize only a completely empty, already-created database')
    args=parser.parse_args()
    target=args.target or ('edu-portrait-mysql' if args.transport=='docker' else 'deployment/mysql')
    run=lambda sql: client(args.transport,target,args.namespace,args.database,sql)
    if args.initialize:
        count=int(run('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();') or 0)
        if count:
            raise ValueError('Initialization refused: selected database is not empty')
        for path in sorted((ROOT/'sql/init').glob('[0-9]*.sql')):
            run(selected_sql(path.read_text(),args.database))
            print(f'Initialized: {path.name}')
    validate_base_schema(run)
    apply_migrations(run,args.database)

if __name__=='__main__':
    main()
