#!/usr/bin/env python3
"""Apply a reviewed synthetic dataset on its VM, with backup, isolated rehearsal and rollback.

No network/model API calls and no database values or credentials are printed. Importing
this module performs no subprocesses. The default command validates local inputs only;
--apply is the explicit operational entry point.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import date, datetime, timedelta
from decimal import Decimal
import gzip
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time
import uuid

BUSINESS = (
    "teacher_info", "teacher_portrait_record", "teacher_evaluation", "student_info",
    "student_academic_record", "student_attendance", "student_quality_record",
    "mental_questionnaire", "mental_question", "mental_response", "mental_assessment",
    "agent_task", "intervention_report", "knowledge_citation", "agent_export_task", "intervention_feedback",
)
DELETE_ORDER = (
    "intervention_feedback", "knowledge_citation", "intervention_report", "agent_export_task", "agent_task",
    "mental_assessment", "mental_response", "mental_question", "mental_questionnaire",
    "student_quality_record", "student_academic_record", "student_attendance", "student_info",
    "teacher_evaluation", "teacher_portrait_record", "teacher_info",
)
INSERT_ORDER = (
    "sys_user", "sys_user_role", "teacher_info", "student_info", "teacher_portrait_record", "teacher_evaluation",
    "student_academic_record", "student_attendance", "student_quality_record", "mental_questionnaire",
    "mental_question", "mental_response", "mental_assessment", "agent_task", "intervention_report",
    "knowledge_citation", "agent_export_task", "intervention_feedback",
)
PROTECTED = (
    "sys_user", "sys_user_role", "sys_role", "sys_permission", "sys_role_permission",
    "sys_migration_log", "sys_schema_migration", "sys_login_log", "audit_log", "sys_account_request",
)
KNOWN_TABLES = set(BUSINESS) | set(PROTECTED)
INFRA = {"mysql", "redis", "nacos", "etcd", "minio", "milvus-standalone"}
DISPLAY_COLUMNS = {"nickname", "dept_name", "phone", "email", "bio", "update_time"}
ACTION = "DEMO_DATA_APPLIED"
RESTORE_ACTION = "DEMO_DATA_ROLLED_BACK"
NUMBER_TYPES = {"tinyint", "smallint", "mediumint", "int", "integer", "bigint", "decimal", "numeric", "float", "double"}
PLANNED_DATE_COLUMNS = {
    ("student_info", "expected_graduation"),
    ("mental_questionnaire", "start_time"), ("mental_questionnaire", "end_time"),
}
ACTIVE_AGENT = ("PENDING", "RISK_ANALYZING", "KNOWLEDGE_RETRIEVING", "PLAN_GENERATING", "COMPLIANCE_CHECKING")
EXPECTED_FKS = {
    ("teacher_portrait_record", "teacher_id", "teacher_info", "id"),
    ("teacher_evaluation", "teacher_id", "teacher_info", "id"),
    ("student_quality_record", "student_id", "student_info", "id"),
}


class SafeFailure(RuntimeError):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def fail(code):
    raise SafeFailure(code)


def ident(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", value):
        fail("INVALID_IDENTIFIER")
    return "`" + value + "`"


def json_text(value):
    """JSON with exact Decimal numbers, suitable for JSON columns and canonical receipts."""
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float, Decimal)):
        number = Decimal(str(value))
        if not number.is_finite():
            fail("INVALID_NUMBER")
        return format(number, "f")
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, list):
        return "[" + ",".join(json_text(x) for x in value) + "]"
    if isinstance(value, dict) and all(isinstance(k, str) for k in value):
        return "{" + ",".join(json.dumps(k, ensure_ascii=False) + ":" + json_text(value[k]) for k in sorted(value)) + "}"
    fail("INVALID_VALUE_TYPE")


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float, Decimal)):
        number = Decimal(str(value))
        if not number.is_finite():
            fail("INVALID_NUMBER")
        return format(number, "f")
    if isinstance(value, (dict, list)):
        value = json_text(value)
    if not isinstance(value, str):
        fail("INVALID_VALUE_TYPE")
    return "CONVERT(X'" + value.encode("utf-8").hex() + "' USING utf8mb4)"


def private_json(path, value):
    path = Path(path)
    temporary = path.with_name(path.name + ".tmp-" + uuid.uuid4().hex)
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        stream.write(json_text(value) + "\n")
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"), parse_float=Decimal)


def file_sha(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def datatype(column):
    return column["type"].split("(", 1)[0].split(" ", 1)[0].lower()


def canonical(value):
    if value is None:
        return ["null"]
    if isinstance(value, bool):
        return ["bool", value]
    if isinstance(value, (int, float, Decimal)):
        number = Decimal(str(value))
        if not number.is_finite():
            fail("INVALID_NUMBER")
        return ["number", "0" if number == 0 else format(number.normalize(), "f")]
    if isinstance(value, str):
        return ["string", value]
    if isinstance(value, list):
        return ["list", [canonical(x) for x in value]]
    if isinstance(value, dict):
        return ["object", [[k, canonical(value[k])] for k in sorted(value)]]
    fail("INVALID_VALUE_TYPE")


def normalize_row(row, columns):
    result = {}
    for name, column in columns.items():
        value = row.get(name)
        kind = datatype(column)
        if value is not None and kind == "json" and isinstance(value, str):
            try:
                value = json.loads(value, parse_float=Decimal)
            except Exception:
                fail("INVALID_JSON_COLUMN")
        elif value is not None and kind in NUMBER_TYPES:
            try:
                value = Decimal(str(value))
                if not value.is_finite():
                    fail("INVALID_NUMBER")
            except Exception:
                fail("INVALID_NUMBER")
        elif value is not None and kind in {"datetime", "timestamp"} and isinstance(value, str):
            value = value.replace("T", " ").removesuffix("Z")
            if "." in value:
                value = value.rstrip("0").rstrip(".")
        result[name] = canonical(value)
    return result


def rows_digest(rows, columns):
    normalized = [json_text(normalize_row(row, columns)) for row in rows]
    # Sort complete typed rows; independent of query order and JSON key order.
    digest = hashlib.sha256()
    for item in sorted(normalized):
        encoded = item.encode("utf-8")
        digest.update(str(len(encoded)).encode("ascii") + b":" + encoded)
    return digest.hexdigest()


def columns_from(audit):
    columns = defaultdict(dict)
    for row in audit.get("columns", []):
        if not isinstance(row, dict) or not {"table", "column", "type", "nullable", "extra"} <= row.keys():
            fail("INVALID_SCHEMA_INPUT")
        table, name = row["table"], row["column"]
        ident(table); ident(name)
        if name in columns[table]:
            fail("DUPLICATE_COLUMN_INPUT")
        columns[table][name] = {key: row[key] for key in ("type", "nullable", "extra")}
    if set(columns) != KNOWN_TABLES:
        fail("UNREVIEWED_SCHEMA_TABLES")
    return dict(columns)


def load_inputs(dataset_path, audit_path):
    dataset, audit = load_json(dataset_path), load_json(audit_path)
    columns = columns_from(audit)
    batch = dataset.get("batch_id")
    if not isinstance(batch, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", batch):
        fail("INVALID_BATCH_ID")
    try:
        as_of = date.fromisoformat(dataset["as_of"])
    except Exception:
        fail("INVALID_REFERENCE_DATE")
    cutoff = as_of - timedelta(days=1)
    tables = dataset.get("tables")
    if not isinstance(tables, dict) or set(tables) != set(INSERT_ORDER):
        fail("INVALID_DATASET_TABLES")
    for table, rows in tables.items():
        if not isinstance(rows, list):
            fail("INVALID_DATASET_ROWS")
        seen = set()
        maximum = int(audit.get("max_ids", {}).get(table, 0))
        for row in rows:
            if not isinstance(row, dict) or set(row) != set(columns[table]):
                fail("INCOMPLETE_DATASET_COLUMNS")
            row_id = row.get("id")
            if isinstance(row_id, bool) or not isinstance(row_id, int) or row_id <= maximum or row_id in seen:
                fail("INVALID_NEW_ROW_ID")
            seen.add(row_id)
            for name, column in columns[table].items():
                value = row[name]
                if value is None and str(column["nullable"]).upper() == "NO":
                    fail("NULL_REQUIRED_VALUE")
                if value is not None and datatype(column) in {"date", "datetime", "timestamp"}:
                    if not isinstance(value, str):
                        fail("INVALID_DATE_VALUE")
                    try:
                        day = date.fromisoformat(value[:10])
                    except Exception:
                        fail("INVALID_DATE_VALUE")
                    if day > cutoff and (table, name) not in PLANNED_DATE_COLUMNS:
                        fail("DATA_AFTER_REFERENCE_CUTOFF")
            normalize_row(row, columns[table])
    if len(tables["student_info"]) != 1200 or len(tables["teacher_info"]) != 80:
        fail("INVALID_TARGET_COUNTS")
    if len({row["grade"] for row in tables["student_info"]}) != 4 or len({row["major_name"] for row in tables["student_info"]}) != 8:
        fail("INVALID_GRADE_MAJOR_COUNTS")
    for table in ("agent_task", "intervention_report", "knowledge_citation", "agent_export_task", "intervention_feedback"):
        if tables[table]:
            fail("FABRICATED_EXECUTION_ROWS_REFUSED")
    seed = {int(row["id"]): row for row in audit.get("protected_seed_accounts", [])}
    updates = dataset.get("existing_user_updates", [])
    if not isinstance(updates, list):
        fail("INVALID_EXISTING_USER_UPDATES")
    seen = set()
    for update in updates:
        if not isinstance(update, dict) or not set(update) <= DISPLAY_COLUMNS | {"id"}:
            fail("UNAUTHORIZED_EXISTING_USER_COLUMN")
        row_id = update.get("id")
        if row_id not in seed or seed[row_id].get("username") not in {"teacher", "student"} or row_id in seen:
            fail("UNAUTHORIZED_EXISTING_USER_ID")
        seen.add(row_id)
        # Explicitly supply this ON UPDATE column, so changing display fields never
        # introduces NOW() or accidentally changes an unapproved column.
        if "update_time" not in update or not isinstance(update["update_time"], str):
            fail("EXISTING_USER_UPDATE_TIME_REQUIRED")
        if date.fromisoformat(update["update_time"][:10]) > cutoff:
            fail("DATA_AFTER_REFERENCE_CUTOFF")
    return dataset, audit, columns, file_sha(dataset_path)


def command(arguments, timeout=120, input_bytes=None):
    try:
        result = subprocess.run(arguments, input=input_bytes, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    except Exception:
        fail("SUBPROCESS_UNAVAILABLE_OR_TIMEOUT")
    if result.returncode:
        fail("SUBPROCESS_FAILED")
    return result.stdout


class Mysql:
    def __init__(self, transport, target, database, namespace="educare", secret_file=None):
        ident(database); ident(namespace)
        if not re.fullmatch(r"[A-Za-z0-9_.:/-]{1,100}", target):
            fail("INVALID_MYSQL_TARGET")
        self.transport, self.target, self.database, self.namespace = transport, target, database, namespace
        self.secret_file = secret_file

    def args(self, dump=False, database=True):
        if self.transport == "k3s":
            prefix = ["k3s", "kubectl", "-n", self.namespace, "exec", "-i", self.target, "--"]
            secret = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD"'
        else:
            prefix = ["docker", "exec", "-i", self.target]
            secret = 'MYSQL_PWD="$(cat /run/secrets/demo-restore-root)"'
        executable = ("mysqldump -uroot --single-transaction --quick --routines --triggers --events "
                      "--set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 --databases") if dump else (
                          "mysql -uroot --batch --raw --skip-column-names --unbuffered --binary-mode --default-character-set=utf8mb4")
        script = secret + " exec " + executable + (' "$1"' if database else "")
        return prefix + ["sh", "-c", script, "sh"] + ([self.database] if database else [])

    def session(self, state_dir, label):
        return MysqlSession(self.args(), Path(state_dir) / (label + ".stderr.log"))


class MysqlSession:
    """A persistent, unbuffered mysql CLI session: transaction, lock and verification share one connection."""
    def __init__(self, args, error_path):
        descriptor = os.open(error_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        self.errors = os.fdopen(descriptor, "wb")
        try:
            self.process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.errors,
                                            text=True, encoding="utf-8", bufsize=1)
        except Exception:
            self.errors.close(); fail("MYSQL_SESSION_UNAVAILABLE")
        self.lines = queue.Queue()
        def pump():
            try:
                for line in self.process.stdout:
                    self.lines.put(line.rstrip("\n"))
            finally:
                self.lines.put(None)
        self.reader = threading.Thread(target=pump, daemon=True)
        self.reader.start()

    def execute(self, sql, timeout=180):
        marker = "DEMO_DONE_" + uuid.uuid4().hex
        try:
            self.process.stdin.write(sql.rstrip().rstrip(";") + ";\nSELECT '" + marker + "';\n")
            self.process.stdin.flush()
        except Exception:
            fail("MYSQL_WRITE_FAILED")
        output, deadline = [], time.monotonic() + timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                self.close(); fail("MYSQL_QUERY_TIMEOUT")
            try:
                line = self.lines.get(timeout=min(remaining, 1))
            except queue.Empty:
                if self.process.poll() is not None:
                    fail("MYSQL_QUERY_FAILED")
                continue
            if line is None:
                fail("MYSQL_QUERY_FAILED")
            if line == marker:
                return output
            output.append(line)

    def scalar(self, sql):
        rows = self.execute(sql)
        if len(rows) != 1:
            fail("UNEXPECTED_SCALAR_RESULT")
        return rows[0]

    def json_rows(self, sql):
        rows = self.execute(sql)
        try:
            return [json.loads(bytes.fromhex(row).decode("utf-8"), parse_float=Decimal) for row in rows]
        except Exception:
            fail("INVALID_DATABASE_ENCODING")

    def close(self):
        try:
            self.process.stdin.close()
            self.process.wait(timeout=5)
        except Exception:
            self.process.kill()
            try: self.process.wait(timeout=5)
            except Exception: pass
        self.errors.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


def id_where(ids):
    if not ids:
        return " WHERE 1=0"
    if any(isinstance(x, bool) or not isinstance(x, int) or x <= 0 for x in ids):
        fail("INVALID_CAPTURED_ID")
    return " WHERE id IN (" + ",".join(str(x) for x in ids) + ")"


def fetch_rows(connection, table, columns, ids=None):
    expression = "JSON_OBJECT(" + ",".join(literal(name) + "," + ident(name) for name in sorted(columns)) + ")"
    where = "" if ids is None else id_where(ids)
    return connection.json_rows("SELECT HEX(" + expression + ") FROM " + ident(table) + where)


def metadata(connection):
    return connection.json_rows("SELECT HEX(JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'type',COLUMN_TYPE,"
                                "'nullable',IS_NULLABLE,'extra',EXTRA)) FROM information_schema.COLUMNS "
                                "WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,COLUMN_NAME")


def assert_schema(connection, columns):
    if columns_from({"columns": metadata(connection)}) != columns:
        fail("LIVE_SCHEMA_CHANGED")
    engines = connection.execute("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                                 "AND (TABLE_TYPE<>'BASE TABLE' OR ENGINE<>'InnoDB')")
    if engines:
        fail("UNSUPPORTED_STORAGE_ENGINE_OR_VIEW")
    fks = connection.json_rows("SELECT HEX(JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ref_table',"
                               "REFERENCED_TABLE_NAME,'ref_column',REFERENCED_COLUMN_NAME)) FROM information_schema.KEY_COLUMN_USAGE "
                               "WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL")
    actual = {(x["table"], x["column"], x["ref_table"], x["ref_column"]) for x in fks}
    if actual != EXPECTED_FKS:
        fail("UNREVIEWED_FOREIGN_KEYS")


def assert_idle(connection):
    count = int(connection.scalar("SELECT COUNT(*) FROM agent_task WHERE status IN (" + ",".join(literal(x) for x in ACTIVE_AGENT) + ")"))
    exports = int(connection.scalar("SELECT COUNT(*) FROM agent_export_task WHERE status IN ('PENDING','PROCESSING')"))
    if count or exports:
        fail("ACTIVE_AGENT_OR_EXPORT_TASKS")
    if int(connection.scalar("SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE() AND STATUS='ENABLED'")):
        fail("UNREVIEWED_DATABASE_BACKGROUND_EVENTS")


def snapshot(connection, dataset, audit, columns, sha):
    assert_schema(connection, columns); assert_idle(connection)
    result = {"payload_sha256": sha, "tables": {}, "old_ids": {}, "protected_ids": {}, "expected_protected": {}}
    update_map = {x["id"]: x for x in dataset.get("existing_user_updates", [])}
    for table in sorted(KNOWN_TABLES):
        rows = fetch_rows(connection, table, columns[table])
        ids = [int(row["id"]) for row in rows] if "id" in columns[table] else None
        result["tables"][table] = {"count": len(rows), "sha256": rows_digest(rows, columns[table]), "max_id": max(ids or [0])}
        if table in BUSINESS:
            if len(rows) != int(audit.get("counts", {}).get(table, -1)) or max(ids or [0]) != int(audit.get("max_ids", {}).get(table, 0)):
                fail("BUSINESS_CHANGED_SINCE_REVIEW")
            result["old_ids"][table] = sorted(ids)
        else:
            result["protected_ids"][table] = None if ids is None else sorted(ids)
            expected = []
            for row in rows:
                projected = dict(row)
                if table == "sys_user" and row["id"] in update_map:
                    if row.get("username") not in {"teacher", "student"}:
                        fail("LIVE_DEMO_ACCOUNT_CHANGED")
                    projected.update({k: v for k, v in update_map[row["id"]].items() if k != "id"})
                expected.append(projected)
            result["expected_protected"][table] = {"count": len(expected), "sha256": rows_digest(expected, columns[table])}
        for row in dataset["tables"].get(table, []):
            if row["id"] <= result["tables"][table]["max_id"]:
                fail("NEW_ID_COLLIDES_WITH_LIVE_HISTORY")
    for account in dataset.get("protected_seed_accounts", []):
        actual = fetch_rows(connection, "sys_user", columns["sys_user"], [int(account["id"])])
        if len(actual) != 1 or any(actual[0].get(k) != v for k, v in account.items()):
            fail("PROTECTED_SEED_ACCOUNT_CHANGED")
    for role in dataset.get("role_catalog", []):
        actual = fetch_rows(connection, "sys_role", columns["sys_role"], [int(role["id"])])
        if len(actual) != 1 or any(actual[0].get(k) != v for k, v in role.items()):
            fail("PROTECTED_ROLE_CATALOG_CHANGED")
    result["mysql_version"] = connection.scalar("SELECT VERSION()")
    result["database_bytes"] = int(connection.scalar("SELECT COALESCE(SUM(DATA_LENGTH+INDEX_LENGTH),0) "
                                                     "FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"))
    return result


def verify_snapshot(connection, captured, columns):
    assert_schema(connection, columns)
    for table, before in captured["tables"].items():
        rows = fetch_rows(connection, table, columns[table])
        if len(rows) != before["count"] or rows_digest(rows, columns[table]) != before["sha256"]:
            fail("BACKUP_RESTORE_CONTENT_MISMATCH")


def verify_payload(connection, dataset, captured, columns, include_marker, allow_runtime_history=False):
    assert_schema(connection, columns)
    for table in BUSINESS:
        actual = fetch_rows(connection, table, columns[table])
        wanted = dataset["tables"][table]
        if len(actual) != len(wanted) or rows_digest(actual, columns[table]) != rows_digest(wanted, columns[table]):
            fail("DATASET_READBACK_MISMATCH")
    for table in PROTECTED:
        original = fetch_rows(connection, table, columns[table], captured["protected_ids"][table])
        expected = captured["expected_protected"][table]
        if len(original) != expected["count"] or rows_digest(original, columns[table]) != expected["sha256"]:
            fail("PROTECTED_ORIGINAL_ROWS_CHANGED")
        new = dataset["tables"].get(table, [])
        if new:
            actual_new = fetch_rows(connection, table, columns[table], [x["id"] for x in new])
            if len(actual_new) != len(new) or rows_digest(actual_new, columns[table]) != rows_digest(new, columns[table]):
                fail("NEW_IDENTITY_READBACK_MISMATCH")
        expected_count = captured["tables"][table]["count"] + len(new) + (1 if include_marker and table == "audit_log" else 0)
        actual_count = int(connection.scalar("SELECT COUNT(*) FROM " + ident(table)))
        # A second run may follow genuine logins/operations. Original history
        # remains immutable and its hash is still verified above; additions are
        # allowed only for append-only history, never business or identity rows.
        if (actual_count < expected_count if allow_runtime_history and table in {"sys_login_log", "audit_log"}
                else actual_count != expected_count):
            fail("UNEXPECTED_PROTECTED_TABLE_ADDITIONS")
    relations = [
        ("student_info", "user_id", "sys_user", "id"), ("teacher_info", "user_id", "sys_user", "id"),
        ("student_academic_record", "student_id", "student_info", "id"),
        ("student_attendance", "student_id", "student_info", "id"),
        ("student_quality_record", "student_id", "student_info", "id"),
        ("teacher_portrait_record", "teacher_id", "teacher_info", "id"),
        ("teacher_evaluation", "teacher_id", "teacher_info", "id"),
        ("mental_question", "questionnaire_id", "mental_questionnaire", "id"),
        ("mental_response", "questionnaire_id", "mental_questionnaire", "id"),
        ("mental_response", "student_id", "student_info", "id"),
        ("mental_assessment", "student_id", "student_info", "id"),
        ("mental_assessment", "questionnaire_id", "mental_questionnaire", "id"),
        ("mental_assessment", "response_id", "mental_response", "id"),
        ("sys_user_role", "user_id", "sys_user", "id"), ("sys_user_role", "role_id", "sys_role", "id"),
    ]
    for child, column, parent, key in relations:
        bad = int(connection.scalar("SELECT COUNT(*) FROM " + ident(child) + " c LEFT JOIN " + ident(parent)
                                    + " p ON c." + ident(column) + "=p." + ident(key) + " WHERE c." + ident(column)
                                    + " IS NOT NULL AND p." + ident(key) + " IS NULL"))
        if bad:
            fail("LOGICAL_ORPHAN_DETECTED")
    for table, account_type in (("student_info", 2), ("teacher_info", 1)):
        if int(connection.scalar("SELECT COUNT(*) FROM " + ident(table) + " p JOIN sys_user u ON p.user_id=u.id "
                                 "WHERE u.user_type<>" + str(account_type) + " OR u.deleted<>0")):
            fail("INVALID_PORTRAIT_ACCOUNT_TYPE")
        if int(connection.scalar("SELECT COUNT(*) FROM (SELECT user_id FROM " + ident(table)
                                 + " GROUP BY user_id HAVING COUNT(*)>1) x")):
            fail("SHARED_PORTRAIT_ACCOUNT")
    if int(connection.scalar("SELECT COUNT(*) FROM mental_assessment a JOIN mental_response r ON a.response_id=r.id "
                             "WHERE a.student_id<>r.student_id OR a.questionnaire_id<>r.questionnaire_id")):
        fail("ASSESSMENT_RESPONSE_OWNER_MISMATCH")
    for start in range(0, len(dataset["tables"]["sys_user"]), 300):
        user_ids = [str(row["id"]) for row in dataset["tables"]["sys_user"][start:start + 300]]
        bad = connection.scalar("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON ur.user_id=u.id "
                                "JOIN sys_role r ON ur.role_id=r.id WHERE u.id IN (" + ",".join(user_ids) + ") AND "
                                "(r.deleted<>0 OR r.status<>1 OR (u.user_type=1 AND r.code<>'teacher') OR "
                                "(u.user_type=2 AND r.code<>'student') OR u.user_type NOT IN (1,2))")
        if int(bad):
            fail("NEW_IDENTITY_ROLE_ESCALATION")


def read_marker(connection, batch):
    rows = connection.json_rows("SELECT HEX(JSON_OBJECT('id',id,'detail',detail)) FROM audit_log WHERE action="
                                + literal(ACTION) + " AND target_type='demo_data' AND target_id=" + literal(batch))
    if len(rows) > 1:
        fail("DUPLICATE_BATCH_MARKER")
    if not rows:
        return None
    try:
        detail = rows[0]["detail"]
        return json.loads(detail, parse_float=Decimal) if isinstance(detail, str) else detail
    except Exception:
        fail("INVALID_BATCH_MARKER")


def apply_transaction(connection, dataset, captured, columns, sha):
    locked = False
    try:
        if connection.scalar("SELECT GET_LOCK('educare_demo_data_apply',10)") != "1":
            fail("APPLICATION_LOCK_UNAVAILABLE")
        locked = True
        connection.execute("SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';"
                           " SET SESSION FOREIGN_KEY_CHECKS=1; SET SESSION innodb_lock_wait_timeout=15; START TRANSACTION")
        marker = read_marker(connection, dataset["batch_id"])
        if marker:
            fail("BATCH_ALREADY_PRESENT_DURING_TRANSACTION")
        connection.execute("SELECT id FROM sys_role WHERE code='admin' AND deleted=0 FOR UPDATE")
        for table in DELETE_ORDER:
            ids = captured["old_ids"][table]
            for start in range(0, len(ids), 300):
                connection.execute("DELETE FROM " + ident(table) + id_where(ids[start:start + 300]))
        for table in INSERT_ORDER:
            rows = dataset["tables"][table]
            names = sorted(columns[table])
            prefix = "INSERT INTO " + ident(table) + " (" + ",".join(ident(x) for x in names) + ") VALUES "
            for start in range(0, len(rows), 100):
                values = ["(" + ",".join(literal(row[x]) for x in names) + ")" for row in rows[start:start + 100]]
                connection.execute(prefix + ",".join(values))
        for update in dataset.get("existing_user_updates", []):
            assignments = [ident(k) + "=" + literal(v) for k, v in sorted(update.items()) if k != "id"]
            if assignments:
                connection.execute("UPDATE sys_user SET " + ",".join(assignments) + " WHERE id=" + str(update["id"]))
        verify_payload(connection, dataset, captured, columns, False)
        detail = {"batch_id": dataset["batch_id"], "payload_sha256": sha, "version": dataset.get("version"),
                  "as_of": dataset["as_of"], "table_counts": {k: len(v) for k, v in dataset["tables"].items()},
                  "before_protected": captured["expected_protected"], "protected_ids": captured["protected_ids"]}
        if len(json_text(detail).encode("utf-8")) > 60000:
            fail("BATCH_MARKER_TOO_LARGE")
        connection.execute("INSERT INTO audit_log(task_id,actor_role,actor_id,action,target_type,target_id,detail,audit_passed,created_at) VALUES "
                           "(NULL,'system',NULL," + literal(ACTION) + ",'demo_data'," + literal(dataset["batch_id"])
                           + "," + literal(json_text(detail)) + ",1,NOW())")
        verify_payload(connection, dataset, captured, columns, True)
        connection.execute("COMMIT")
    except Exception:
        try: connection.execute("ROLLBACK")
        except Exception: pass
        raise
    finally:
        if locked:
            try: connection.execute("SELECT RELEASE_LOCK('educare_demo_data_apply')")
            except Exception: pass


def stream_process(arguments, error_path, producer, timeout=600):
    descriptor = os.open(error_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "wb") as errors:
        process = subprocess.Popen(arguments, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=errors)
        timer = threading.Timer(timeout, process.kill); timer.daemon = True; timer.start()
        try:
            producer(process.stdin)
            process.stdin.close()
            process.wait(timeout=timeout)
            if process.returncode:
                fail("MYSQL_IMPORT_FAILED")
        except SafeFailure:
            raise
        except Exception:
            fail("MYSQL_IMPORT_FAILED")
        finally:
            timer.cancel()
            if process.poll() is None:
                process.kill(); process.wait(timeout=10)


def restore_dump(mysql, dump, state_dir, label):
    def producer(stream):
        with gzip.open(dump, "rb") as source:
            shutil.copyfileobj(source, stream, 1024 * 1024)
    # Complete --databases dump is restored without selecting/replacing its schema name.
    stream_process(mysql.args(database=False), Path(state_dir) / (label + ".stderr.log"), producer)


def backup(mysql, state_dir, captured):
    state = Path(state_dir); dump = state / "database-before.sql.gz"
    receipt = state / "backup.json"
    if dump.exists() or receipt.exists():
        if not dump.exists() or not receipt.exists():
            fail("INCOMPLETE_EXISTING_BACKUP")
        recorded = load_json(receipt)
        if recorded.get("sha256") != file_sha(dump) or recorded.get("payload_sha256") != captured["payload_sha256"]:
            fail("EXISTING_BACKUP_CHANGED")
        return dump
    if shutil.disk_usage(state).free < max(1024 ** 3, captured["database_bytes"] * 4):
        fail("INSUFFICIENT_BACKUP_RESTORE_DISK")
    descriptor = os.open(state / "backup.stderr.log", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    temporary = state / "database-before.sql.gz.part"
    with os.fdopen(descriptor, "wb") as errors:
        process = subprocess.Popen(mysql.args(dump=True), stdout=subprocess.PIPE, stderr=errors)
        timer = threading.Timer(600, process.kill); timer.daemon = True; timer.start()
        try:
            descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "wb") as output, gzip.GzipFile(fileobj=output, mode="wb", mtime=0) as zipped:
                shutil.copyfileobj(process.stdout, zipped, 1024 * 1024)
            process.wait(timeout=600)
            if process.returncode:
                fail("MYSQL_BACKUP_FAILED")
            with gzip.open(temporary, "rb") as verify:
                total = sum(len(block) for block in iter(lambda: verify.read(1024 * 1024), b""))
            if total < 100:
                fail("EMPTY_MYSQL_BACKUP")
            os.replace(temporary, dump)
            private_json(receipt, {"sha256": file_sha(dump), "uncompressed_bytes": total,
                                   "payload_sha256": captured["payload_sha256"]})
        finally:
            timer.cancel()
            if process.poll() is None:
                process.kill(); process.wait(timeout=10)
            if temporary.exists(): temporary.unlink()
    return dump


def kubectl(namespace, *args, data=None, timeout=180):
    output = command(["k3s", "kubectl", "-n", namespace, *args], timeout, data)
    try:
        return json.loads(output) if output.strip().startswith((b"{", b"[")) else output
    except Exception:
        fail("INVALID_KUBERNETES_RESULT")


class Maintenance:
    def __init__(self, namespace, state_dir, sha):
        self.namespace, self.state_dir, self.sha = namespace, Path(state_dir), sha
        self.record = None
        self.paused = False

    def capture(self):
        path = self.state_dir / "maintenance.json"
        if path.exists():
            self.record = load_json(path)
            if self.record.get("payload_sha256") != self.sha:
                fail("MAINTENANCE_STATE_BATCH_MISMATCH")
            return
        if kubectl(self.namespace, "get", "cronjobs", "-o", "json").get("items") or kubectl(self.namespace, "get", "jobs", "-o", "json").get("items"):
            fail("UNREVIEWED_BACKGROUND_JOBS")
        if kubectl(self.namespace, "get", "statefulsets", "-o", "json").get("items"):
            fail("UNREVIEWED_STATEFUL_WORKLOADS")
        deployments = kubectl(self.namespace, "get", "deployments", "-o", "json")["items"]
        applications = {}
        for obj in deployments:
            name = obj["metadata"]["name"]
            if name not in INFRA:
                applications[name] = {"replicas": int(obj["spec"].get("replicas", 1)),
                                      "selector": obj["spec"]["selector"]["matchLabels"]}
        if not {"gateway", "agent-service", "auth-service", "frontend"} <= set(applications):
            fail("INCOMPLETE_APPLICATION_WORKLOADS")
        hpas = []
        for obj in kubectl(self.namespace, "get", "hpa", "-o", "json")["items"]:
            if obj["spec"]["scaleTargetRef"]["name"] not in applications:
                fail("UNREVIEWED_HPA_TARGET")
            clean = {"apiVersion": obj["apiVersion"], "kind": obj["kind"], "spec": obj["spec"],
                     "metadata": {"name": obj["metadata"]["name"], "namespace": self.namespace}}
            hpas.append(clean)
        self.record = {"payload_sha256": self.sha, "applications": applications, "hpas": hpas}
        private_json(path, self.record)

    def pause(self):
        self.capture()
        self.paused = True  # A partial pause also requires a restore/maintenance branch.
        for hpa in self.record["hpas"]:
            kubectl(self.namespace, "delete", "hpa", hpa["metadata"]["name"], "--ignore-not-found=true")
        names = sorted(self.record["applications"], key=lambda name: (0 if name in {"gateway", "agent-service"} else 1, name))
        for name in names:
            kubectl(self.namespace, "scale", "deployment/" + name, "--replicas=0")
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            pods = kubectl(self.namespace, "get", "pods", "-o", "json")["items"]
            if not any(all(pod["metadata"].get("labels", {}).get(k) == v for k, v in spec["selector"].items())
                       for spec in self.record["applications"].values() for pod in pods):
                return
            time.sleep(2)
        fail("APPLICATION_PODS_DID_NOT_EXIT")

    def hold_public(self):
        for name in ("gateway", "frontend"):
            if name in self.record["applications"]:
                try: kubectl(self.namespace, "scale", "deployment/" + name, "--replicas=0")
                except Exception: pass

    def resume(self):
        names = sorted(self.record["applications"], key=lambda name: (2 if name in {"gateway", "frontend"} else 1 if name == "agent-service" else 0, name))
        try:
            # Start independent providers together, then orchestration, then public entry points.
            # This preserves dependency gates without serializing every JVM cold start.
            groups = ([n for n in names if n not in {"agent-service", "gateway", "frontend"}],
                      [n for n in names if n == "agent-service"],
                      [n for n in names if n in {"gateway", "frontend"}])
            for group in groups:
                for name in group:
                    replicas = self.record["applications"][name]["replicas"]
                    kubectl(self.namespace, "scale", "deployment/" + name, "--replicas=" + str(replicas))
                for name in group:
                    if self.record["applications"][name]["replicas"]:
                        kubectl(self.namespace, "rollout", "status", "deployment/" + name, "--timeout=240s", timeout=260)
            for hpa in self.record["hpas"]:
                kubectl(self.namespace, "apply", "-f", "-", data=json_text(hpa).encode("utf-8"))
            self.paused = False
        except Exception:
            self.hold_public(); fail("APPLICATION_RESTORE_NOT_READY")


class RestoreSandbox:
    def __init__(self, state_dir):
        self.state = Path(state_dir)
        self.directory = self.state / ("restore-" + uuid.uuid4().hex)
        self.name = "educare-demo-restore-" + uuid.uuid4().hex[:16]
        self.created = False

    def __enter__(self):
        try:
            command(["docker", "image", "inspect", "mysql:8.0", "--format", "{{.Id}}"])
        except SafeFailure:
            # Import only the already-cached K3s image. Never pull an image or use
            # the network to obtain a potentially different MySQL version.
            image_tar = self.state / ("mysql-image-" + uuid.uuid4().hex + ".tar")
            descriptor = os.open(image_tar, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            os.close(descriptor)
            try:
                command(["k3s", "ctr", "images", "export", str(image_tar), "docker.io/library/mysql:8.0"], timeout=300)
                command(["docker", "load", "-i", str(image_tar)], timeout=300)
                command(["docker", "image", "inspect", "mysql:8.0", "--format", "{{.Id}}"])
            finally:
                image_tar.unlink(missing_ok=True)
        self.directory.mkdir(mode=0o700)
        data = self.directory / "data"; data.mkdir(mode=0o700)
        password = self.directory / "root-password"
        descriptor = os.open(password, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w") as stream: stream.write(secrets.token_urlsafe(48))
        try:
            # Name ownership is established before dispatch, so an ambiguous
            # docker-run completion still removes only this unique container.
            self.created = True
            command(["docker", "run", "-d", "--pull", "never", "--name", self.name, "--network", "none", "--memory", "2g", "--cpus", "1",
                     "-v", str(data) + ":/var/lib/mysql:Z",
                     "-v", str(password) + ":/run/secrets/demo-restore-root:ro,Z",
                     "-e", "MYSQL_ROOT_PASSWORD_FILE=/run/secrets/demo-restore-root", "mysql:8.0", "--event-scheduler=OFF"])
            mysql = Mysql("docker", self.name, "edu_portrait")
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                try:
                    output = command(mysql.args(database=False), timeout=10, input_bytes=b"SELECT 1;\n")
                    if output.strip() == b"1": return mysql
                except SafeFailure: pass
                time.sleep(2)
            fail("RESTORE_INSTANCE_NOT_READY")
        except Exception:
            self.cleanup(); raise

    def cleanup(self):
        if self.created:
            try: command(["docker", "rm", "-f", self.name])
            except Exception: fail("RESTORE_INSTANCE_CLEANUP_FAILED")
        if self.directory.exists():
            shutil.rmtree(self.directory)

    def __exit__(self, *_):
        self.cleanup()


def rehearsal(dump, state_dir, dataset, captured, columns, sha, database):
    with RestoreSandbox(state_dir) as clone:
        clone.database = database
        restore_dump(clone, dump, state_dir, "rehearsal-restore")
        with clone.session(state_dir, "rehearsal-before") as connection:
            if connection.scalar("SELECT VERSION()") != captured["mysql_version"]:
                fail("RESTORE_MYSQL_VERSION_MISMATCH")
            verify_snapshot(connection, captured, columns)
            apply_transaction(connection, dataset, captured, columns, sha)
            verify_payload(connection, dataset, captured, columns, True)
            marker = read_marker(connection, dataset["batch_id"])
            if not marker or marker.get("payload_sha256") != sha:
                fail("REHEARSAL_BATCH_MARKER_MISMATCH")
        private_json(Path(state_dir) / "rehearsal.json", {"payload_sha256": sha, "backup_restore_verified": True,
                                                        "rehearsal_transaction_verified": True, "model_calls": 0})


def already_applied(mysql, dataset, columns, sha, state_dir):
    with mysql.session(state_dir, "marker-check") as connection:
        marker = read_marker(connection, dataset["batch_id"])
        if not marker:
            return False
        if marker.get("payload_sha256") != sha:
            fail("BATCH_PAYLOAD_CHANGED")
        path = Path(state_dir) / "before.json"
        if not path.exists():
            fail("APPLIED_BATCH_PRIVATE_RECEIPT_REQUIRED")
        captured = load_json(path)
        if captured.get("payload_sha256") != sha:
            fail("APPLIED_BATCH_PRIVATE_RECEIPT_MISMATCH")
        verify_payload(connection, dataset, captured, columns, True, allow_runtime_history=True)
        return True


def operate(args, dataset, audit, columns, sha):
    state = Path(args.state_dir)
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    if state.is_symlink() or state.stat().st_uid != os.getuid():
        fail("UNSAFE_PRIVATE_STATE_DIRECTORY")
    os.chmod(state, 0o700)
    mysql = Mysql("k3s", args.mysql_target, args.database, args.namespace)
    lock_path = state / "operator.lock"
    descriptor = os.open(lock_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream: stream.write(str(os.getpid()))
    maintenance = Maintenance(args.namespace, state, sha)
    captured = None; dump = None; committed = False; verified = False
    try:
        if already_applied(mysql, dataset, columns, sha, state):
            receipt = load_json(state / "receipt.json") if (state / "receipt.json").exists() else {}
            if receipt.get("status") != "COMPLETE" and (state / "maintenance.json").exists():
                maintenance.capture(); maintenance.resume()
            return {"status": "ALREADY_APPLIED", "batch_id": dataset["batch_id"], "payload_sha256": sha, "model_calls": 0}
        with mysql.session(state, "pre-maintenance") as connection: assert_idle(connection)
        maintenance.pause()
        with mysql.session(state, "capture-before") as connection:
            captured = snapshot(connection, dataset, audit, columns, sha)
            before_path = state / "before.json"
            if before_path.exists():
                previous = load_json(before_path)
                if previous.get("payload_sha256") != sha or previous["tables"] != captured["tables"]:
                    fail("PREVIOUS_PRIVATE_BASELINE_MISMATCH")
            else:
                private_json(before_path, captured)
        dump = backup(mysql, state, captured)
        rehearsal(dump, state, dataset, captured, columns, sha, args.database)
        with mysql.session(state, "production-apply") as connection:
            verify_snapshot(connection, captured, columns)
            apply_transaction(connection, dataset, captured, columns, sha)
            committed = True
            verify_payload(connection, dataset, captured, columns, True)
            verified = True
        private_json(state / "receipt.json", {"status": "COMMITTED_VERIFIED", "batch_id": dataset["batch_id"],
                                             "payload_sha256": sha, "model_calls": 0, "backup_sha256": file_sha(dump)})
        maintenance.resume()
        private_json(state / "receipt.json", {"status": "COMPLETE", "batch_id": dataset["batch_id"], "payload_sha256": sha,
                                             "model_calls": 0, "backup_sha256": file_sha(dump), "applications_restored": True})
        return {"status": "COMPLETE", "batch_id": dataset["batch_id"], "payload_sha256": sha,
                "table_counts": {table: len(rows) for table, rows in dataset["tables"].items()}, "model_calls": 0}
    except Exception as error:
        code = error.code if isinstance(error, SafeFailure) else "UNEXPECTED_OPERATION_FAILURE"
        restored = False
        if maintenance.paused and captured and not verified:
            try:
                with mysql.session(state, "failure-assess") as connection:
                    marker = read_marker(connection, dataset["batch_id"])
                    committed = bool(marker and marker.get("payload_sha256") == sha)
                    if not committed:
                        verify_snapshot(connection, captured, columns)
                        restored = True
                if committed and dump:
                    maintenance.pause()
                    restore_dump(mysql, dump, state, "production-rollback")
                    with mysql.session(state, "rollback-verify") as connection: verify_snapshot(connection, captured, columns)
                    restored = True
                if restored:
                    maintenance.resume()
            except Exception:
                maintenance.hold_public(); restored = False
        elif maintenance.paused and not captured:
            # No database writes have started yet; restoring replicas is safe.
            try: maintenance.resume(); restored = True
            except Exception: maintenance.hold_public()
        elif maintenance.paused:
            # Valid committed data, but workloads not Ready: keep public entry points closed.
            maintenance.hold_public()
        private_json(state / "failure.json", {"code": code, "batch_id": dataset["batch_id"], "payload_sha256": sha,
                                              "committed": committed, "data_verified": verified,
                                              "original_state_restored": restored, "maintenance_held": maintenance.paused})
        fail(code)
    finally:
        lock_path.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--audit-before", required=True)
    parser.add_argument("--state-dir", required=True)
    parser.add_argument("--database", default="edu_portrait")
    parser.add_argument("--namespace", default="educare")
    parser.add_argument("--mysql-target", default="deployment/mysql")
    parser.add_argument("--validator", default=str(Path(__file__).resolve().parent.parent / "validate_demo_data.py"),
                        help="Independent local validator; defaults to scripts/validate_demo_data.py and runs before every operation")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    try:
        dataset, audit, columns, sha = load_inputs(args.dataset, args.audit_before)
        validator = Path(args.validator).resolve()
        own = Path(__file__).resolve()
        allowed = validator.parent == own.parent or validator == own.parent.parent / "validate_demo_data.py"
        if not allowed or validator == own or not validator.is_file():
            fail("UNREVIEWED_VALIDATOR_PATH")
        command([sys.executable, str(validator), "--dataset", str(Path(args.dataset).resolve()),
                 "--audit-before", str(Path(args.audit_before).resolve())], timeout=180)
        result = operate(args, dataset, audit, columns, sha) if args.apply else {
            "status": "OFFLINE_PLAN_VALIDATED", "batch_id": dataset["batch_id"], "payload_sha256": sha,
            "table_counts": {table: len(rows) for table, rows in dataset["tables"].items()}, "production_calls": 0, "model_calls": 0}
        print(json_text(result))
        return 0
    except Exception as error:
        print(json_text({"status": "FAILED", "code": error.code if isinstance(error, SafeFailure) else "INVALID_INPUT_OR_OPERATION_FAILURE"}))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
