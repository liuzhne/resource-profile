import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from fractions import Fraction

spec = importlib.util.spec_from_file_location("quarantine", Path(__file__).with_name("public-rag-probe-quarantine.py"))
quarantine = importlib.util.module_from_spec(spec)
spec.loader.exec_module(quarantine)


class MemoryRuntime:
    dimension = 1024
    collection_names = {"case": "edu_cases", "policy": "edu_policies", "psychology": "edu_psychology"}

    def __init__(self):
        self.rows = {}
        self.events = []
        self.redis_available = True
        self.fail_revision_number = None
        self.revisions = 0
        for index, key in enumerate(quarantine.FIXTURES):
            fixed = {"id": 100 + index, "chunk_id": quarantine.chunk_id(key),
                     "source": quarantine.SOURCE, "embedding": [0.125] * self.dimension,
                     **quarantine.FIXTURES[key]}
            # Same document-like prefix, explicit unknown row: must survive.
            adjacent = {**fixed, "id": 900 + index, "chunk_id": quarantine.doc_id(key) + "_other_0000",
                        "source": "unknown-publisher", "content": "unrelated private data"}
            self.rows[key] = [fixed, adjacent]

    def query(self, key):
        return copy.deepcopy([row for row in self.rows[key] if row["chunk_id"] == quarantine.chunk_id(key)])

    def lock(self):
        if not self.redis_available:
            raise RuntimeError("sensitive Redis exception")
        self.events.append("lock")

    def rotate_revision(self, key):
        self.revisions += 1
        if self.revisions == self.fail_revision_number:
            raise RuntimeError("sensitive Redis exception")
        self.events.append(("revision", key))

    def clear_document_hashes(self, key):
        self.events.append(("clear_hashes", key))

    def delete_exact(self, key, expected):
        self.events.append(("delete", key))
        self.rows[key] = [row for row in self.rows[key] if row["id"] != expected["id"]]

    def restore_exact(self, key, expected):
        self.events.append(("restore", key))
        self.rows[key].append({**copy.deepcopy(expected), "id": 2000 + len(self.events)})

    def unlock(self):
        self.events.append("unlock")


class QuarantineTest(unittest.TestCase):
    def test_fixed_probe_identity_is_exact_and_not_source_or_prefix_only(self):
        runtime = MemoryRuntime()
        backup = quarantine.snapshot(runtime)
        self.assertEqual(len(backup["rows"]), 3)
        for key, row in backup["rows"].items():
            self.assertEqual(row["chunk_id"], quarantine.doc_id(key) + "_0000")
            self.assertEqual(row["source"], "synthetic-acceptance-20261007")
            self.assertEqual(len(row["embedding"]), 1024)
        for field in ("source", "chunk_id", "title", "content"):
            broken = copy.deepcopy(backup["rows"]["case"])
            broken[field] += "altered"
            with self.assertRaises(quarantine.GuardError):
                quarantine.validate_row("case", broken, 1024)

    def test_private_full_vector_backup_checksum_and_exclusive_write(self):
        backup = quarantine.snapshot(MemoryRuntime())
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder).resolve() / "private.json"
            digest = quarantine.write_backup(path, backup)
            self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)
            self.assertEqual(quarantine.read_backup(path, digest), backup)
            self.assertEqual(len(json.loads(path.read_text())["rows"]["policy"]["embedding"]), 1024)
            with self.assertRaises(FileExistsError):
                quarantine.write_backup(path, backup)
            path.write_bytes(path.read_bytes() + b" ")
            with self.assertRaises(quarantine.GuardError):
                quarantine.read_backup(path, digest)

    def test_refuses_symlink_and_public_backup_file(self):
        backup = quarantine.snapshot(MemoryRuntime())
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder).resolve() / "private.json"
            digest = quarantine.write_backup(path, backup)
            link = Path(folder).resolve() / "symlink.json"
            link.symlink_to(path)
            with self.assertRaises(quarantine.GuardError):
                quarantine.read_backup(link, digest)
            os.chmod(path, 0o644)
            with self.assertRaises(quarantine.GuardError):
                quarantine.read_backup(path, digest)

    def test_apply_preserves_unknown_rows_invalidates_before_after_and_restores_vectors(self):
        runtime = MemoryRuntime()
        backup = quarantine.snapshot(runtime)
        result = quarantine.mutate(runtime, backup, "apply")
        self.assertEqual(result["changed_rows"], 3)
        self.assertTrue(result["revision_updated"])
        self.assertNotIn("embedding", json.dumps(result))
        for key in quarantine.FIXTURES:
            self.assertEqual([row["source"] for row in runtime.rows[key]], ["unknown-publisher"])
            deletion = runtime.events.index(("delete", key))
            self.assertEqual(runtime.events[deletion - 1], ("revision", key))
            self.assertEqual(runtime.events[deletion + 2], ("revision", key))
        self.assertEqual(quarantine.mutate(runtime, backup, "apply")["changed_rows"], 0)
        restored = quarantine.mutate(runtime, backup, "restore")
        self.assertEqual(restored["changed_rows"], 3)
        for key in quarantine.FIXTURES:
            row = runtime.query(key)[0]
            self.assertNotEqual(row["id"], backup["rows"][key]["id"])
            self.assertTrue(quarantine.same_semantic_row(row, backup["rows"][key]))
        self.assertEqual(quarantine.mutate(runtime, backup, "restore")["changed_rows"], 0)

    def test_all_three_revalidated_before_mutation_and_duplicate_conflict_rejected(self):
        for mutation in ("title", "source", "duplicate"):
            runtime = MemoryRuntime()
            backup = quarantine.snapshot(runtime)
            if mutation == "duplicate":
                runtime.rows["psychology"].append(copy.deepcopy(runtime.rows["psychology"][0]))
            else:
                runtime.rows["psychology"][0][mutation] += "changed"
            with self.assertRaises(quarantine.GuardError):
                quarantine.mutate(runtime, backup, "apply")
            self.assertEqual(runtime.events, [])

    def test_redis_preflight_failure_prevents_deletion(self):
        runtime = MemoryRuntime()
        backup = quarantine.snapshot(runtime)
        runtime.redis_available = False
        with self.assertRaises(RuntimeError):
            quarantine.mutate(runtime, backup, "apply")
        self.assertFalse(any(isinstance(event, tuple) and event[0] == "delete" for event in runtime.events))
        self.assertTrue(all(runtime.query(key) for key in quarantine.FIXTURES))

    def test_revision_failure_after_delete_can_resume_without_losing_backup(self):
        runtime = MemoryRuntime()
        backup = quarantine.snapshot(runtime)
        runtime.fail_revision_number = 2
        with self.assertRaises(RuntimeError):
            quarantine.mutate(runtime, backup, "apply")
        self.assertFalse(runtime.query("case"))
        self.assertTrue(runtime.query("policy"))
        runtime.fail_revision_number = None
        self.assertEqual(quarantine.mutate(runtime, backup, "apply")["changed_rows"], 2)
        self.assertEqual(quarantine.mutate(runtime, backup, "restore")["changed_rows"], 3)

    def test_restore_never_overwrites_modified_or_unknown_same_identity(self):
        runtime = MemoryRuntime()
        backup = quarantine.snapshot(runtime)
        quarantine.mutate(runtime, backup, "apply")
        conflict = copy.deepcopy(backup["rows"]["policy"])
        conflict["source"] = "a-real-source"
        runtime.rows["policy"].append(conflict)
        runtime.events.clear()
        with self.assertRaises(quarantine.GuardError):
            quarantine.mutate(runtime, backup, "restore")
        self.assertEqual(runtime.events, [])

    def test_invalid_backup_dimension_or_vector_rejected_offline(self):
        backup = quarantine.snapshot(MemoryRuntime())
        for field, value in (("embedding", [float("nan")] * 1024), ("embedding", [0.0] * 1024),
                             ("embedding", [True] * 1024), ("id", True)):
            altered = copy.deepcopy(backup)
            altered["rows"]["case"][field] = value
            with self.assertRaises(quarantine.GuardError):
                quarantine.validate_backup(altered)

    def test_sdk_real_scalars_become_json_primitives_without_reembedding(self):
        runtime = MemoryRuntime()
        for key in quarantine.FIXTURES:
            runtime.rows[key][0]["embedding"] = [Fraction(1, 8)] * 1024
        backup = quarantine.snapshot(runtime)
        encoded = quarantine.canonical(backup)
        recovered = json.loads(encoded)
        self.assertTrue(all(type(value) is float and value == 0.125
                            for row in recovered["rows"].values() for value in row["embedding"]))
        row = copy.deepcopy(runtime.rows["case"][0])
        for bad in (["0.125"] * 1024, [True] * 1024, [float("nan")] * 1024):
            row["embedding"] = bad
            with self.assertRaises(quarantine.GuardError):
                quarantine.normalize_sdk_row(row)


if __name__ == "__main__":
    unittest.main()
