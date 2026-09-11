"""
H-1.3：把 app/services/rag_pipeline.retrieve_from_collection 适配成 MCP tool 调用层。

职责：
- 校验 top_k 合理范围（防 LLM 传 0 / 负数 / 巨大值）
- 统一 logging 前缀（便于 grep MCP 调用）
- 把 pipeline 返回值收敛成 LLM 友好的精简结构
"""
import logging
from typing import Any, Dict

from app.core.config import settings
from app.services.rag_pipeline import retrieve_from_collection

logger = logging.getLogger(__name__)

_MAX_TOP_K = 20  # LLM 偶尔会传过大值，硬上限做防护


def _clamp_top_k(top_k: int) -> int:
    if top_k is None or top_k <= 0:
        return settings.RAG_TOP_K
    return min(top_k, _MAX_TOP_K)


async def search_collection(query: str, collection_key: str, top_k: int) -> Dict[str, Any]:
    """MCP tool 调用入口。

    Args:
        query: LLM 给出的检索语句
        collection_key: case / psychology / policy / success
        top_k: 返回片段数（会被裁剪到 [1, 20]）

    Returns:
        {
            "query": str,
            "collection": str,
            "top_k": int,
            "reranked": bool,
            "fallback": bool,
            "count": int,
            "chunks": [{chunk_id, title, content, score, rerank_score, source_db}, ...]
        }
    """
    top_k = _clamp_top_k(top_k)
    if not query or not query.strip():
        logger.warning("[mcp:rag] 空 query collection=%s", collection_key)
        return {
            "query": query,
            "collection": collection_key,
            "top_k": top_k,
            "reranked": False,
            "fallback": True,
            "count": 0,
            "chunks": [],
            "error": "empty query",
        }

    logger.info("[mcp:rag] search collection=%s top_k=%s query=%s", collection_key, top_k, query[:48])
    pipeline_result = await retrieve_from_collection(
        query=query,
        collection_key=collection_key,
        top_k=top_k,
    )
    chunks = pipeline_result.get("chunks", [])
    return {
        "query": query,
        "collection": collection_key,
        "top_k": top_k,
        "reranked": pipeline_result.get("reranked", False),
        "fallback": pipeline_result.get("fallback", False),
        "count": len(chunks),
        "chunks": chunks,
    }
