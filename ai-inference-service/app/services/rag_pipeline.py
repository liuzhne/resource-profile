"""
H-1.3：单库 RAG 检索管线，给 MCP knowledge-rag tool 与未来的 ad-hoc 调用复用。

与 app/api/rag.py 的差异：
- rag.py：多路 query × 多集合聚合 + chunk_id 去重 + 全局 rerank（service-level / multi-source）
- rag_pipeline.py：单 query × 单集合召回 + 可选 rerank（MCP tool 粒度的最小单元）

两者底层都走 embedding_client + milvus_client + reranker_client + redis_client，但语义不同：
- 上层 rag.py 不调本文件（保留既有路由代码不动，避免 H-1.3 引入回归）
- MCP rag_adapter 调本文件
"""
import hashlib
import json
import logging
from typing import Any, Dict, List, Optional

from app.core.config import settings
from app.services.embedding_client import get_embedding_client
from app.services.milvus_client import search as milvus_search
from app.services.redis_client import cache_get, cache_setex
from app.services.reranker_client import get_reranker_client

logger = logging.getLogger(__name__)


def _pipeline_cache_key(collection_key: str, query: str, top_k: int) -> str:
    h = hashlib.sha256()
    h.update(collection_key.encode("utf-8"))
    h.update(b"|")
    h.update(query.strip().lower().encode("utf-8"))
    h.update(b"|")
    h.update(str(top_k).encode())
    return "edu:rag:pipe:" + h.hexdigest()[:32]


async def retrieve_from_collection(
    query: str,
    collection_key: str,
    top_k: int = 5,
    enable_rerank: Optional[bool] = None,
) -> Dict[str, Any]:
    """单集合向量检索 + 可选 Cross-Encoder 精排。

    Args:
        query: 检索 query（自然语言）
        collection_key: settings.MILVUS_COLLECTIONS 的 key（case / psychology / policy / success）
        top_k: 返回片段数
        enable_rerank: None → 跟随 settings.RERANKER_ENABLED；True/False 强制开关

    Returns:
        {
            "collection": <原始 collection_key>,
            "milvus_collection": <实际 milvus 集合名>,
            "query": <query>,
            "top_k": <top_k>,
            "reranked": <bool>,
            "fallback": <bool 表示是否走了空召回降级>,
            "chunks": [{chunk_id, title, content, score, rerank_score, source_db}, ...]
        }
    """
    collection_name = settings.MILVUS_COLLECTIONS.get(collection_key)
    if not collection_name:
        logger.warning("rag_pipeline: 未知 collection_key=%s", collection_key)
        return {
            "collection": collection_key,
            "milvus_collection": None,
            "query": query,
            "top_k": top_k,
            "reranked": False,
            "fallback": True,
            "chunks": [],
            "error": f"unknown collection_key: {collection_key}",
        }

    cache_key = _pipeline_cache_key(collection_key, query, top_k)
    cached = await cache_get(cache_key)
    if cached:
        try:
            payload = json.loads(cached)
            logger.info("rag_pipeline X-Cache: HIT key=%s collection=%s", cache_key, collection_key)
            return payload
        except Exception as exc:
            logger.warning("rag_pipeline 缓存反序列化失败 key=%s: %s", cache_key, exc)

    recall = max(top_k * settings.RAG_RECALL_EXPAND, top_k)
    embed_client = get_embedding_client()
    vector = await embed_client.embed(query)

    hits = milvus_search(
        collection_name=collection_name,
        query_vector=vector,
        top_k=recall,
        output_fields=["chunk_id", "title", "content", "source"],
    )

    if not hits:
        logger.info(
            "rag_pipeline 空召回 collection=%s query=%s（集合未初始化或匹配为零）",
            collection_name,
            query[:32],
        )
        return {
            "collection": collection_key,
            "milvus_collection": collection_name,
            "query": query,
            "top_k": top_k,
            "reranked": False,
            "fallback": True,
            "chunks": [],
        }

    reranked = False
    do_rerank = settings.RERANKER_ENABLED if enable_rerank is None else enable_rerank
    if do_rerank and len(hits) > 1:
        documents = [(h.get("content") or h.get("title") or "") for h in hits]
        rerank_results = await get_reranker_client().rerank(
            query=query,
            documents=documents,
            top_n=top_k,
        )
        if rerank_results and any(r.get("relevance_score", 0) > 0 for r in rerank_results):
            ordered: List[Dict[str, Any]] = []
            for r in rerank_results:
                idx = r.get("index")
                if idx is None or idx >= len(hits):
                    continue
                row = dict(hits[idx])
                row["rerank_score"] = float(r.get("relevance_score", 0.0))
                ordered.append(row)
            hits = ordered
            reranked = True
        else:
            logger.warning("rag_pipeline reranker 返回零分，回退 milvus 排序")

    final = hits[:top_k]
    chunks = [
        {
            "chunk_id": str(h.get("chunk_id") or "unknown"),
            "source_db": collection_key,
            "title": h.get("title"),
            "content": h.get("content"),
            "score": float(h.get("score") or 0.0),
            "rerank_score": h.get("rerank_score"),
        }
        for h in final
    ]
    payload = {
        "collection": collection_key,
        "milvus_collection": collection_name,
        "query": query,
        "top_k": top_k,
        "reranked": reranked,
        "fallback": False,
        "chunks": chunks,
    }
    await cache_setex(cache_key, settings.RAG_CACHE_TTL, json.dumps(payload, ensure_ascii=False))
    logger.info(
        "rag_pipeline X-Cache: MISS key=%s collection=%s top_k=%s reranked=%s",
        cache_key,
        collection_key,
        top_k,
        reranked,
    )
    return payload
