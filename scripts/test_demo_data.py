#!/usr/bin/env python3
"""Independent corruption checks against a generated private demo payload."""
import argparse
import copy
import datetime
import hashlib
import json
from pathlib import Path
import sys
import unittest

from generate_demo_data import generate, validate_dataset


class DemoIntegrityTests(unittest.TestCase):
    dataset = None
    audit = None

    def reject(self, table, index, **changes):
        candidate = dict(self.dataset)
        candidate["tables"] = dict(self.dataset["tables"])
        candidate["tables"][table] = list(self.dataset["tables"][table])
        candidate["tables"][table][index] = self.dataset["tables"][table][index] | changes
        with self.assertRaises((AssertionError, ValueError)):
            validate_dataset(candidate, self.audit)

    def test_full_dataset(self):
        self.assertEqual(validate_dataset(self.dataset, self.audit)["relations"], "passed")

    def test_same_seed_and_inputs_are_reproducible(self):
        hashes = [u["password"] for u in self.dataset["tables"]["sys_user"]]
        rebuilt = generate(self.audit, hashes, datetime.date.fromisoformat(self.dataset["as_of"]), self.dataset["seed"])
        def digest(value):
            normalized = json.loads(json.dumps(value, ensure_ascii=False, allow_nan=False))
            return hashlib.sha256(json.dumps(normalized, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        self.assertEqual(digest(rebuilt), digest(self.dataset), "same inputs must produce the same payload")

    def test_unknown_database_column_rejected(self):
        self.reject("student_info", 0, nonexistent_column="value")

    def test_reused_old_id_rejected(self):
        self.reject("student_info", 0, id=self.audit["max_ids"]["student_info"])

    def test_shared_account_rejected(self):
        self.reject("student_info", 1, user_id=self.dataset["tables"]["student_info"][0]["user_id"])

    def test_gpa_must_match_weighted_records(self):
        self.reject("student_info", 0, gpa=0)

    def test_score_range_rejected(self):
        self.reject("student_academic_record", 0, score=101)

    def test_unearned_credits_rejected(self):
        self.reject("student_info", 0, credits=160)

    def test_first_year_has_no_final_grades(self):
        index = next(i for i, s in enumerate(self.dataset["tables"]["student_info"]) if s["grade"] == "2026")
        self.reject("student_info", index, gpa=3.5, credits=20)

    def test_attendance_on_national_holiday_rejected(self):
        self.reject("student_attendance", 0, attend_date="2026-10-01")

    def test_assessment_score_must_match_answers(self):
        row = self.dataset["tables"]["mental_assessment"][0]
        self.reject("mental_assessment", 0, score=row["score"]-1)

    def test_assessment_response_link_required(self):
        self.reject("mental_assessment", 0, response_id=None)

    def test_passwords_cannot_be_shared_default(self):
        self.reject("sys_user", 0, password="student")

    def test_extra_existing_admin_role_cannot_be_smuggled(self):
        candidate = dict(self.dataset)
        candidate["tables"] = dict(self.dataset["tables"])
        candidate["tables"]["sys_user_role"] = list(self.dataset["tables"]["sys_user_role"]) + [{
            "id": 999999, "user_id": 1, "role_id": 1, "create_time": "2026-10-08 10:00:00"}]
        with self.assertRaises((AssertionError, ValueError)):
            validate_dataset(candidate, self.audit)

    def test_future_event_rejected(self):
        self.reject("student_info", 0, create_time="2027-01-01 10:00:00")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--dataset", type=Path, required=True)
    p.add_argument("--audit-before", type=Path, required=True)
    args = p.parse_args()
    DemoIntegrityTests.dataset = json.loads(args.dataset.read_text())
    DemoIntegrityTests.audit = json.loads(args.audit_before.read_text())
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(DemoIntegrityTests))
    sys.exit(not result.wasSuccessful())


if __name__ == "__main__":
    main()
