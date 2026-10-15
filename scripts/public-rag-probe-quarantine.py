#!/usr/bin/env python3
"""Two-phase isolation of the three fixed 2026-10-07 synthetic RAG probes.

Run inside the configured ai-inference-service runtime. No embedding or LLM
requests occur. ``backup`` only reads Milvus; copy its private file to durable
VM storage and verify the SHA256 before ``apply``. Never print backup contents.
"""
import argparse
import datetime
import hashlib
import json
import logging
import math
import numbers
import os
from pathlib import Path
import stat
import sys
import uuid

SOURCE = "synthetic-acceptance-20261007"
FORMAT = "resource-profile-rag-probe-backup-v1"
FIELDS = ("id", "chunk_id", "title", "content", "source", "embedding")
MAX_BACKUP_BYTES = 1024 * 1024
FIXTURES = {
    "case": {
        "title": "部署验收合成案例：考试复习支持",
        "content": "此资料为部署验收合成案例，不对应真实学生。示例学生甲在考试前需要改善复习安排。支持步骤包括：先列出三个薄弱知识点，再制定每天二十分钟复习计划，最后由示例同伴乙每周核对学习进度。所有人物与情节均为虚构。",
    },
    "policy": {
        "title": "部署验收合成政策：学习支持申请",
        "content": "此资料为部署验收合成政策，不是真实学校规章。示例学校的学习支持申请流程为：提交一份合成申请表，预约示例辅导员面谈，再由示例教学办公室确认支持时段。不得将此虚构流程当作真实政策。",
    },
    "psychology": {
        "title": "部署验收合成知识：考前放松练习",
        "content": "此资料为部署验收合成心理知识，不是诊断或真实个案。虚构练习依次进行缓慢呼吸、描述当下感受和安排短暂休息。示例情境仅用于验证中文知识检索，不含真实心理测评或个人信息。",
    },
}


class GuardError(Exception):
    """A fixed error code, never a provider exception/body or stored row."""


def require(condition, code):
    if not condition:
        raise GuardError(code)


def doc_id(key):
    require(key in FIXTURES, "unknown_fixture")
    return f"accept_{key}_20261007"


def chunk_id(key):
    return doc_id(key) + "_0000"


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def fixture_digest():
    return hashlib.sha256(canonical({"source": SOURCE, "fixtures": FIXTURES})).hexdigest()


def normalize_sdk_row(row):
    """Milvus may return NumPy INT64/FLOAT32 scalars inside ordinary lists.

    Convert those actual stored values to JSON primitives; do not recompute an
    embedding. Reject strings/booleans/non-finite or nested vector values.
    """
    require(isinstance(row, dict) and set(row) == set(FIELDS), "row_schema_mismatch")
    require(isinstance(row["id"], numbers.Integral) and not isinstance(row["id"], bool), "primary_key_invalid")
    vector = row["embedding"]
    require(isinstance(vector, (list, tuple))
            and all(isinstance(value, numbers.Real) and not isinstance(value, bool)
                    and math.isfinite(float(value)) for value in vector), "vector_invalid")
    return {**row, "id": int(row["id"]), "embedding": [float(value) for value in vector]}


def validate_row(key, row, dimension):
    require(isinstance(row, dict) and set(row) == set(FIELDS), "row_schema_mismatch")
    require(isinstance(row["id"], int) and not isinstance(row["id"], bool) and row["id"] >= 0,
            "primary_key_invalid")
    require(row["chunk_id"] == chunk_id(key) and row["source"] == SOURCE
            and row["title"] == FIXTURES[key]["title"] and row["content"] == FIXTURES[key]["content"],
            "fixed_fixture_identity_mismatch")
    vector = row["embedding"]
    require(isinstance(vector, list) and len(vector) == dimension
            and all(isinstance(v, (float, int)) and not isinstance(v, bool) and math.isfinite(v) for v in vector)
            and any(v != 0 for v in vector), "vector_invalid")


def same_semantic_row(left, right):
    return all(left[field] == right[field] for field in FIELDS if field != "id")


def validate_backup(value):
    require(isinstance(value, dict) and value.get("format") == FORMAT
            and value.get("fixture_sha256") == fixture_digest(), "backup_format_or_fixture_mismatch")
    require(value.get("embedding_dim") == 1024, "backup_dimensions_mismatch")
    require(isinstance(value.get("collections"), dict) and set(value["collections"]) == set(FIXTURES)
            and isinstance(value.get("rows"), dict) and set(value["rows"]) == set(FIXTURES), "backup_collections_mismatch")
    names = list(value["collections"].values())
    require(all(isinstance(v, str) and v for v in names) and len(set(names)) == 3, "collection_mapping_invalid")
    for key, row in value["rows"].items():
        validate_row(key, row, value["embedding_dim"])


def _private_file(path):
    path = Path(path)
    require(path.is_absolute(), "absolute_backup_path_required")
    # O_NOFOLLOW protects the file itself. Reject symlink directories too.
    require(not any(parent.is_symlink() for parent in (path, *path.parents)), "backup_symlink_forbidden")
    return path


def write_backup(path, value):
    validate_backup(value)
    path = _private_file(path)
    path.parent.mkdir(mode=0o700, exist_ok=True)
    require(stat.S_IMODE(path.parent.stat().st_mode) & 0o077 == 0, "backup_directory_not_private")
    encoded = canonical(value)
    require(len(encoded) <= MAX_BACKUP_BYTES, "backup_too_large")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "wb") as stream:
        stream.write(encoded)
        stream.flush()
        os.fsync(stream.fileno())
    directory = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)
    digest = hashlib.sha256(encoded).hexdigest()
    require(read_backup(path, digest) == value, "backup_readback_mismatch")
    return digest


def read_backup(path, expected_sha256):
    require(isinstance(expected_sha256, str) and len(expected_sha256) == 64
            and all(c in "0123456789abcdef" for c in expected_sha256), "expected_sha256_required")
    path = _private_file(path)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, "rb") as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600
                and info.st_uid == os.geteuid(), "backup_file_not_private")
        require(0 < info.st_size <= MAX_BACKUP_BYTES, "backup_size_invalid")
        encoded = stream.read(MAX_BACKUP_BYTES + 1)
    require(hashlib.sha256(encoded).hexdigest() == expected_sha256, "backup_sha256_mismatch")
    value = json.loads(encoded)
    validate_backup(value)
    return value


def snapshot(runtime):
    require(runtime.dimension == 1024, "runtime_dimensions_mismatch")
    rows = {}
    for key in FIXTURES:
        found = [normalize_sdk_row(row) for row in runtime.query(key)]
        require(len(found) == 1, "expected_one_fixed_probe_per_collection")
        validate_row(key, found[0], runtime.dimension)
        rows[key] = found[0]
    return {"format": FORMAT, "fixture_sha256": fixture_digest(),
            "created_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "collections": runtime.collection_names, "embedding_dim": runtime.dimension,
            "auto_primary_key": True, "rows": rows}


def mutate(runtime, backup, operation):
    require(operation in ("apply", "restore"), "unknown_operation")
    validate_backup(backup)
    require(runtime.dimension == backup["embedding_dim"]
            and runtime.collection_names == backup["collections"], "runtime_backup_mapping_mismatch")
    # Validate all three before any mutation. Exact IDs only; unknown rows and
    # similarly prefixed documents are never selected by this tool.
    for key, expected in backup["rows"].items():
        current = runtime.query(key)
        require(len(current) <= 1, "duplicate_fixed_probe")
        if current:
            validate_row(key, current[0], runtime.dimension)
            require(current[0] == expected if operation == "apply"
                    else same_semantic_row(current[0], expected), "probe_changed_since_backup")
    runtime.lock()
    changed = {}
    try:
        for key, expected in backup["rows"].items():
            current = runtime.query(key)
            require(len(current) <= 1, "duplicate_fixed_probe")
            if current:
                validate_row(key, current[0], runtime.dimension)
                require(current[0] == expected if operation == "apply"
                        else same_semantic_row(current[0], expected), "probe_changed_since_backup")
            runtime.rotate_revision(key)  # fail closed if Redis is unavailable
            changed[key] = 0
            if operation == "apply" and current:
                runtime.delete_exact(key, expected)
                changed[key] = 1
            elif operation == "restore" and not current:
                runtime.restore_exact(key, expected)
                changed[key] = 1
            after = runtime.query(key)
            require(not after if operation == "apply" else len(after) == 1
                    and same_semantic_row(after[0], expected), "mutation_verification_failed")
            runtime.clear_document_hashes(key)
            runtime.rotate_revision(key)  # invalidate any concurrent refill
    finally:
        runtime.unlock()
    return {"operation": operation, "verified": True, "changed_rows": sum(changed.values()),
            "collections": changed, "revision_updated": True,
            "auto_primary_key_changes_on_restore": operation == "restore"}


class Runtime:
    def __init__(self):
        # Imports happen only for explicit live subcommands, so offline tests
        # require neither pymilvus, Redis nor the inference stack.
        from app.core.config import settings
        from pymilvus import Collection, connections, DataType
        self.settings = settings
        self.dimension = settings.EMBEDDING_DIM
        self.collection_names = {key: settings.MILVUS_COLLECTIONS[key] for key in FIXTURES}
        self.alias = "probe-quarantine-" + uuid.uuid4().hex
        self.connections = connections
        connections.connect(alias=self.alias, host=settings.MILVUS_HOST, port=settings.MILVUS_PORT, timeout=10)
        self.collections = {}
        expected_types = dict(zip(FIELDS, (DataType.INT64, DataType.VARCHAR, DataType.VARCHAR,
                                           DataType.VARCHAR, DataType.VARCHAR, DataType.FLOAT_VECTOR)))
        for key, name in self.collection_names.items():
            collection = Collection(name, using=self.alias)
            fields = {field.name: field for field in collection.schema.fields}
            require(set(fields) == set(FIELDS) and all(fields[k].dtype == v for k, v in expected_types.items()),
                    "live_collection_schema_mismatch")
            require(fields["id"].is_primary and fields["id"].auto_id
                    and int(fields["embedding"].params["dim"]) == self.dimension, "live_collection_schema_mismatch")
            collection.load(timeout=60)
            self.collections[key] = collection
        self.redis = None
        self.lock_value = None

    def query(self, key):
        # Read every exact identity, then validate source/content in Python.
        # A conflicting same-ID row must stop the run, not be overlooked.
        expression = f"chunk_id == {json.dumps(chunk_id(key))}"
        return [normalize_sdk_row(row) for row in self.collections[key].query(
            expr=expression, output_fields=list(FIELDS), limit=2, consistency_level="Strong", timeout=60)]

    def lock(self):
        from redis import Redis
        self.redis = Redis(host=self.settings.REDIS_HOST, port=self.settings.REDIS_PORT,
                           password=self.settings.REDIS_PASSWORD or None, db=self.settings.REDIS_DB,
                           decode_responses=True, socket_connect_timeout=3, socket_timeout=3)
        self.redis.ping()
        self.lock_value = uuid.uuid4().hex
        require(self.redis.set("edu:rag:quarantine:20261007:lock", self.lock_value, nx=True, ex=600),
                "quarantine_already_running")

    def rotate_revision(self, key):
        # Verify ownership and renew the maintenance lock with every cache
        # boundary. A lost/expired lock prevents the next mutation.
        require(self.redis.eval(
            "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end "
            "redis.call('setex', KEYS[2], ARGV[2], ARGV[3]); "
            "redis.call('expire', KEYS[1], 600); return 1",
            2, "edu:rag:quarantine:20261007:lock", f"edu:rag:revision:{key}",
            self.lock_value, max(self.settings.RAG_CACHE_TTL + 60, self.settings.UPSERT_HASH_TTL),
            uuid.uuid4().hex) == 1, "revision_update_failed")

    def clear_document_hashes(self, key):
        # Exact old/current idempotency entries, never FLUSHDB or response scans.
        self.redis.delete(f"edu:rag:upsert:hash:{key}:{doc_id(key)}",
                          f"edu:rag:upsert:hash:v2:{key}:{doc_id(key)}")

    def delete_exact(self, key, row):
        expression = (f'id in [{row["id"]}] and source == {json.dumps(SOURCE)} '
                      f'and chunk_id == {json.dumps(chunk_id(key))}')
        self.collections[key].delete(expression, timeout=60)
        self.collections[key].flush(timeout=60)

    def restore_exact(self, key, row):
        fields = [field.name for field in self.collections[key].schema.fields if not field.is_primary]
        self.collections[key].insert([[row[field]] for field in fields], timeout=60)
        self.collections[key].flush(timeout=60)

    def unlock(self):
        if self.redis and self.lock_value:
            self.redis.eval("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                            1, "edu:rag:quarantine:20261007:lock", self.lock_value)

    def close(self):
        self.connections.disconnect(self.alias)


def main():
    os.umask(0o077)
    logging.disable(logging.CRITICAL)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("check", "backup", "apply", "restore"))
    parser.add_argument("--file", type=Path)
    parser.add_argument("--expected-sha256")
    args = parser.parse_args()
    runtime = None
    try:
        if args.operation == "check":
            print(json.dumps({"operation": "check", "fixed_probe_count": 3,
                              "fixture_sha256": fixture_digest(), "network": False}))
            return 0
        require(args.file is not None, "backup_file_required")
        backup = None if args.operation == "backup" else read_backup(args.file, args.expected_sha256)
        runtime = Runtime()
        if args.operation == "backup":
            digest = write_backup(args.file, snapshot(runtime))
            result = {"operation": "backup", "backup_sha256": digest, "rows": 3,
                      "embedding_dimensions": runtime.dimension, "mutation": False}
        else:
            result = mutate(runtime, backup, args.operation)
            result["backup_sha256"] = args.expected_sha256
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except Exception as error:
        # Never expose SDK error text, vector, raw row, host or credentials.
        print(json.dumps({"operation": args.operation, "verified": False,
                          "error_code": str(error) if isinstance(error, GuardError) else "runtime_operation_failed",
                          "partial_mutation_possible": args.operation in ("apply", "restore")}))
        return 1
    finally:
        if runtime:
            try:
                runtime.close()
            except Exception:
                pass


if __name__ == "__main__":
    sys.exit(main())
