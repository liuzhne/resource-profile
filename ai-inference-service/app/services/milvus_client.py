"""
Milvus 单例客户端 — 提供集合连接 + 向量搜索能力。
集合未初始化或连接失败时，搜索方法返回空列表，链路降级而非阻断。
"""
import logging
from typing import Any, Dict, List, Optional

from pymilvus import Collection, connections, utility

from app.core.config import settings

logger = logging.getLogger(__name__)

_CONN_ALIAS = "default"
_connected = False


def _ensure_connected() -> bool:
    global _connected
    if _connected:
        return True
    try:
        connections.connect(
            alias=_CONN_ALIAS,
            host=settings.MILVUS_HOST,
            port=settings.MILVUS_PORT,
            timeout=5,
        )
        _connected = True
        logger.info("Milvus 已连接 %s:%s", settings.MILVUS_HOST, settings.MILVUS_PORT)
        return True
    except Exception as exc:
        logger.error("Milvus 连接失败: %s", exc)
        return False


def search(
    collection_name: str,
    query_vector: List[float],
    top_k: int = 5,
    expr: Optional[str] = None,
    output_fields: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """向指定集合执行向量搜索；若集合不存在或连接失败，返回空列表。"""
    if not _ensure_connected():
        return []

    try:
        if not utility.has_collection(collection_name, using=_CONN_ALIAS):
            logger.warning("Milvus 集合 %s 不存在，返回空结果", collection_name)
            return []

        collection = Collection(collection_name, using=_CONN_ALIAS)
        collection.load()

        results = collection.search(
            data=[query_vector],
            anns_field="embedding",
            param={"metric_type": "IP", "params": {"nprobe": 16}},
            limit=top_k,
            expr=expr,
            output_fields=output_fields or ["chunk_id", "title", "content", "source"],
        )

        hits: List[Dict[str, Any]] = []
        for hit in results[0]:
            payload = {"score": float(hit.distance)}
            for field in output_fields or ["chunk_id", "title", "content", "source"]:
                try:
                    payload[field] = hit.entity.get(field)
                except Exception:
                    payload[field] = None
            hits.append(payload)
        return hits

    except Exception as exc:
        logger.error("Milvus 搜索失败 collection=%s: %s", collection_name, exc)
        return []


def delete_by_doc_id(collection_name: str, doc_id: str) -> int:
    """G-4.2：按 `chunk_id like "{doc_id}_%"` 删除旧 chunk，返回删除条数。

    Milvus 没有 `LIKE`，但 VARCHAR 字段支持 `like` 表达式（pymilvus 2.4+）。
    集合不存在时返回 0（首次 upsert 走 insert）。
    """
    if not _ensure_connected():
        raise RuntimeError("Milvus 未连接，无法删除")
    if not utility.has_collection(collection_name, using=_CONN_ALIAS):
        return 0
    collection = Collection(collection_name, using=_CONN_ALIAS)
    expr = f'chunk_id like "{doc_id}_%"'
    res = collection.delete(expr)
    deleted = int(getattr(res, "delete_count", 0) or 0)
    collection.flush()
    return deleted


def insert_chunks(
    collection_name: str,
    rows: List[Dict[str, Any]],
) -> int:
    """G-4.2：批量插入 chunk。

    rows 每行 keys：chunk_id / title / content / source / embedding。
    集合不存在则抛错（调用方应保证集合已 init_milvus.py 跑过）。
    """
    if not _ensure_connected():
        raise RuntimeError("Milvus 未连接，无法写入")
    if not utility.has_collection(collection_name, using=_CONN_ALIAS):
        raise RuntimeError(f"Milvus 集合 {collection_name} 不存在，先跑 init_milvus.py")
    if not rows:
        return 0

    collection = Collection(collection_name, using=_CONN_ALIAS)
    data = [
        [r["chunk_id"] for r in rows],
        [r.get("title", "") for r in rows],
        [r.get("content", "") for r in rows],
        [r.get("source", "") for r in rows],
        [r["embedding"] for r in rows],
    ]
    collection.insert(data)
    collection.flush()
    return len(rows)
