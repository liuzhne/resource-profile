#!/usr/bin/env python3
"""Generate a private, validated demonstration payload without contacting any service.

Usage: python3 scripts/generate_demo_data.py --audit-before FILE --password-hashes FILE
       --output-dir PRIVATE_DIR --as-of 2026-10-09
The password file contains fresh bcrypt hashes only, never plaintext passwords.
"""
import argparse
import collections
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import random
import re

from demo_data.portraits import build_portraits, validate_portraits, TABLES as PORTRAIT_TABLES
from demo_data.mental import build_mental, validate_mental, TABLES as MENTAL_TABLES

if not __debug__:
    raise RuntimeError("Run the demonstration validator without Python optimization")

BATCH_ID = "DEMO-DATA-20261009"
BUSINESS_TABLES = (
    "teacher_info", "teacher_portrait_record", "teacher_evaluation", "student_info",
    "student_academic_record", "student_attendance", "student_quality_record",
    "mental_questionnaire", "mental_question", "mental_response", "mental_assessment",
    "agent_task", "intervention_report", "knowledge_citation", "agent_export_task",
    "intervention_feedback",
)


def allocate(next_ids, table):
    result = next_ids[table]
    next_ids[table] += 1
    return result


def validate_schema(tables, audit):
    """Check the actual deployed columns, ranges, JSON and primary/natural keys."""
    by_table = collections.defaultdict(dict)
    for c in audit["columns"]:
        by_table[c["table"]][c["column"]] = c
    for table, rows in tables.items():
        assert table in (*BUSINESS_TABLES, "sys_user", "sys_user_role"), table
        columns = by_table[table]
        ids = set()
        for row in rows:
            assert row.keys() <= columns.keys(), (table, row.keys() - columns.keys())
            assert isinstance(row["id"], int) and row["id"] > audit["max_ids"].get(table, 0)
            assert row["id"] not in ids, (table, "duplicate id")
            ids.add(row["id"])
            for key, value in row.items():
                col = columns[key]
                assert value is not None or col["nullable"] == "YES", (table, key, "null")
                if value is None:
                    continue
                typ = col["type"]
                size = re.fullmatch(r"(?:var)?char\((\d+)\)", typ)
                if size:
                    assert len(str(value)) <= int(size[1]), (table, key, "length")
                if typ == "json":
                    json.loads(value) if isinstance(value, str) else json.dumps(value, allow_nan=False)
                if typ.startswith(("bigint", "int", "tinyint")):
                    assert isinstance(value, int) and not isinstance(value, bool), (table, key)
                    limit = 127 if typ.startswith("tinyint") else 2147483647 if typ.startswith("int") else 9223372036854775807
                    assert -limit-1 <= value <= limit, (table, key, "integer range")
                if typ.startswith("decimal"):
                    precision, scale = map(int, re.search(r"\((\d+),(\d+)\)", typ).groups())
                    assert abs(float(value)) < 10 ** (precision-scale), (table, key, "decimal range")
                if typ == "datetime":
                    dt.datetime.fromisoformat(value)
                if typ == "date":
                    dt.date.fromisoformat(value)
    for table, key in (("sys_user", "username"), ("teacher_info", "employee_id"), ("student_info", "student_id")):
        values = [r[key] for r in tables[table]]
        assert len(values) == len(set(values)), (table, key)


def validate_dataset(data, audit):
    tables = data["tables"]
    as_of = dt.date.fromisoformat(data["as_of"])
    validate_schema(tables, audit)
    validate_portraits({t: tables[t] for t in PORTRAIT_TABLES}, as_of)
    validate_mental({t: tables[t] for t in MENTAL_TABLES}, tables["student_info"], as_of)
    assert len(tables["student_info"]) == 1200 and len(tables["teacher_info"]) == 80
    assert len(tables["sys_user"]) == 1278
    protected = {u["id"]: u for u in audit["protected_seed_accounts"]}
    users = {u["id"]: u for u in tables["sys_user"]} | protected
    updates = data.get("existing_user_updates", [])
    expected_updated_ids = {u["id"] for u in protected.values() if u["username"] in ("teacher", "student")}
    assert {u["id"] for u in updates} == expected_updated_ids and len(updates) == 2
    update_columns = {"id", "nickname", "dept_name", "phone", "email", "bio", "update_time"}
    for u in updates:
        assert set(u) == update_columns and u["phone"] is None and u["email"].endswith(".example")
        users[u["id"]] = users[u["id"]] | u
    profiles = tables["student_info"] + tables["teacher_info"]
    assert len({r["user_id"] for r in profiles}) == 1280
    roles = {r["code"]: r["id"] for r in audit["role_catalog"] if r["status"] == 1 and r["deleted"] == 0}
    grants = {r["user_id"]: r["role_id"] for r in tables["sys_user_role"]}
    new_user_ids = {u["id"] for u in tables["sys_user"]}
    assert len(tables["sys_user_role"]) == len(new_user_ids) == len(grants) == 1278
    assert set(grants) == new_user_ids, "role grants must belong only to new demo accounts"
    for table, kind, role in (("student_info", 2, "student"), ("teacher_info", 1, "teacher")):
        for p in tables[table]:
            u = users[p["user_id"]]
            assert u["user_type"] == kind and u["status"] == 1 and u["deleted"] == 0
            assert u["nickname"] == p["name"] and u["dept_name"] == p["dept_name"]
            if u["id"] not in protected:
                assert grants[u["id"]] == roles[role]
                assert u["phone"] is None and u["email"].endswith(".example")
                assert re.fullmatch(r"\$2[aby]\$10\$[./A-Za-z0-9]{53}", u["password"])
    # Model, tool, audit, export and login history must never be invented as seed data.
    assert all(not tables.get(t) for t in ("agent_task", "intervention_report", "knowledge_citation", "agent_export_task", "intervention_feedback"))
    assert not any(t in tables for t in ("sys_login_log", "audit_log"))
    # Event timestamps are earlier than the reference day. Planned windows/graduation may be future.
    cutoff = dt.datetime.combine(as_of, dt.time.min)
    for table, rows in tables.items():
        for row in rows:
            for key in ("create_time", "update_time", "submit_time", "assessment_date", "warning_updated_at", "created_at", "updated_at", "completed_at"):
                value = row.get(key)
                if value:
                    assert dt.datetime.fromisoformat(value) < cutoff, (table, key, "future event")
    return {"schema": "passed", "relations": "passed", "identity": "passed", "event_dates": "passed", "no_invented_model_history": "passed"}


def generate(audit, password_hashes, as_of, seed=20261009):
    assert len(password_hashes) == 1278
    for value in password_hashes:
        assert re.fullmatch(r"\$2[aby]\$10\$[./A-Za-z0-9]{53}", value), "invalid bcrypt hash"
    next_ids = {t: audit["max_ids"].get(t, 0) + 1 for t in (*BUSINESS_TABLES, "sys_user", "sys_user_role")}
    existing = {u["username"]: u for u in audit["protected_seed_accounts"]}
    assert set(existing) == {"admin", "teacher", "student"}
    assert all(u["status"] == 1 and u["deleted"] == 0 for u in existing.values())
    students = [existing["student"]["id"]] + [allocate(next_ids, "sys_user") for _ in range(1199)]
    teachers = [existing["teacher"]["id"]] + [allocate(next_ids, "sys_user") for _ in range(79)]
    tables, portrait_summary = build_portraits(as_of=as_of, rng=random.Random(seed), next_ids=next_ids,
                                            student_user_ids=students, teacher_user_ids=teachers)
    mental, mental_summary = build_mental(tables["student_info"], as_of=as_of, rng=random.Random(seed+1),
                                         next_ids=next_ids, actor_id=existing["admin"]["id"])
    assert not (tables.keys() & mental.keys()), "duplicate module tables"
    tables.update(mental)
    for rows in tables.values():
        for row in rows:
            for key, value in row.items():
                if isinstance(value, bool):
                    row[key] = int(value)
    # No synthetic execution states: current schema has no separate offline-report source type.
    for name in BUSINESS_TABLES:
        tables.setdefault(name, [])
    for name in ("agent_task", "intervention_report", "knowledge_citation", "agent_export_task", "intervention_feedback"):
        tables[name] = []
    roles = {r["code"]: r["id"] for r in audit["role_catalog"] if r["status"] == 1 and r["deleted"] == 0}
    users, grants = [], []
    hashes = iter(password_hashes)
    for table, kind, role, natural_key in (("student_info", 2, "student", "student_id"), ("teacher_info", 1, "teacher", "employee_id")):
        for p in tables[table]:
            if p["user_id"] in (existing["student"]["id"], existing["teacher"]["id"]):
                continue
            username = "demo_" + p[natural_key]
            users.append({"id": p["user_id"], "username": username, "password": next(hashes),
                          "nickname": p["name"], "avatar": None, "email": username + "@campus.example",
                          "phone": None, "user_type": kind, "status": 1, "deleted": 0,
                          "dept_name": p["dept_name"], "bio": "本科院校演示场景的合成账号，无真实联系方式。",
                          "create_time": p["create_time"], "update_time": p["update_time"]})
            grants.append({"id": allocate(next_ids, "sys_user_role"), "user_id": p["user_id"],
                           "role_id": roles[role], "create_time": p["create_time"]})
    tables["sys_user"], tables["sys_user_role"] = users, grants
    display_updates = []
    for table in ("student_info", "teacher_info"):
        p = tables[table][0]
        assert p["user_id"] in (existing["student"]["id"], existing["teacher"]["id"])
        display_updates.append({"id": p["user_id"], "nickname": p["name"], "dept_name": p["dept_name"],
                                "phone": None, "email": "demo." + str(p["user_id"]) + "@campus.example",
                                "bio": "本科院校演示场景的合成资料，无真实联系方式。",
                                "update_time": f"{as_of-dt.timedelta(days=1)} 23:59:59"})
    result = {"batch_id": BATCH_ID, "version": 1, "seed": seed, "as_of": as_of.isoformat(),
              "timezone": "Asia/Shanghai", "tables": tables,
              "existing_user_updates": display_updates,
              "protected_seed_accounts": audit["protected_seed_accounts"], "role_catalog": audit["role_catalog"],
              "summary": {"synthetic": True, "ratios_are_simulation_assumptions": True,
                          "portrait": portrait_summary, "mental": mental_summary,
                          "identity": {"existing_account_credentials_preserved": 3, "demo_account_display_updates": 2, "new_accounts": 1278,
                                       "new_password_plaintexts_saved": False, "new_passwords_shared": False},
                          "model_history": "No synthetic Agent/feedback/export/trace/audit execution history is inserted."}}
    result["validation"] = validate_dataset(result, audit)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit-before", type=Path, required=True)
    parser.add_argument("--password-hashes", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--as-of", type=dt.date.fromisoformat, required=True)
    parser.add_argument("--seed", type=int, default=20261009)
    args = parser.parse_args()
    os.umask(0o077)
    audit = json.loads(args.audit_before.read_text())
    result = generate(audit, json.loads(args.password_hashes.read_text()), args.as_of, args.seed)
    args.output_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    payload = json.dumps(result, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode()
    (args.output_dir / "dataset.json").write_bytes(payload)
    safe = {k: result[k] for k in ("batch_id", "version", "seed", "as_of", "timezone", "summary", "validation")}
    safe["summary"] = json.loads(json.dumps(result["summary"]))
    for key in ("student_ids", "teacher_ids", "student_account_cohorts", "current_registration"):
        safe["summary"]["portrait"].pop(key, None)
    safe["counts"] = {t: len(rows) for t, rows in result["tables"].items()}
    safe["payload_sha256"] = hashlib.sha256(payload).hexdigest()
    (args.output_dir / "generation-report.json").write_text(json.dumps(safe, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"batch_id": BATCH_ID, "counts": safe["counts"], "validation": safe["validation"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
