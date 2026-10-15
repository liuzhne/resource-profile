"""
RAG 检索路由：/api/v1/rag/retrieve

流程：
  1. 多路 query 各自向量化 → Milvus 多集合召回 top_k * RAG_RECALL_EXPAND 候选
  2. chunk_id 去重，保留最高 milvus 分数
  3. 若 RERANKER_ENABLED：用首路 query 调 Cross-Encoder 精排，返回 top_k
  4. 否则按 milvus 分数降序截断 top_k

Milvus 空召回时明确返回空片段，不用虚构案例或政策替代知识。

D 阶段新增：相同请求的 1 小时 Redis 缓存。命中时日志输出 X-Cache: HIT 便于排查。
"""
import hashlib
import json
import logging
from typing import Any, Dict, List, Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.core.config import settings
from app.services.embedding_client import get_embedding_client
from app.services.milvus_client import search as milvus_search
from app.services.reranker_client import get_reranker_client
from app.services.redis_client import cache_get, cache_setex
from app.services.knowledge_provenance import corpus_revision, provenance

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/rag", tags=["rag"])


def _rag_cache_key(top_k: int, sources: List[str], queries: List[str], risk_type: Optional[str], revision: str = "") -> str:
    h = hashlib.sha256()
    h.update(str(top_k).encode())
    h.update(b"|")
    h.update(",".join(sorted(sources)).encode())
    h.update(b"|")
    h.update("\x1f".join(q.strip().lower() for q in queries).encode("utf-8"))
    h.update(b"|")
    h.update((risk_type or "").encode("utf-8"))
    h.update(b"|")
    h.update(revision.encode("utf-8"))
    # v2 excludes cached v1 responses which had already discarded provenance.
    return "edu:rag:v2:" + h.hexdigest()[:32]


class RetrieveRequest(BaseModel):
    risk_type: Optional[str] = None
    queries: List[str] = Field(..., min_length=1, description="多路查询语句，建议 2-3 路")
    top_k: Optional[int] = None
    sources: Optional[List[str]] = Field(
        default=None,
        description="限定知识库 case/psychology/policy/success；默认全部",
    )


class KnowledgeChunk(BaseModel):
    chunk_id: str
    source_db: str
    source: Optional[str] = None
    doc_id: Optional[str] = None
    source_url: Optional[str] = None
    title: Optional[str] = None
    content: Optional[str] = None
    score: float = 0.0
    rerank_score: Optional[float] = None


class RetrieveResponse(BaseModel):
    chunks: List[KnowledgeChunk]
    fallback: bool = Field(default=False, description="true 表示空召回，未提供知识依据")
    reranked: bool = Field(default=False, description="是否经过 Cross-Encoder 精排")


@router.post("/retrieve", response_model=RetrieveResponse)
async def retrieve_knowledge(req: RetrieveRequest) -> RetrieveResponse:
    top_k = req.top_k or settings.RAG_TOP_K
    sources = req.sources or list(settings.MILVUS_COLLECTIONS.keys())
    recall_per_route = top_k * settings.RAG_RECALL_EXPAND

    cache_key = _rag_cache_key(top_k, sources, req.queries, req.risk_type,
                             await corpus_revision(sources))
    cached = await cache_get(cache_key)
    if cached:
        try:
            payload = json.loads(cached)
            logger.info("X-Cache: HIT key=%s query_count=%d", cache_key, len(req.queries))
            return RetrieveResponse(**payload)
        except Exception as exc:
            logger.warning("RAG 缓存反序列化失败 key=%s type=%s", cache_key, type(exc).__name__)

    embed_client = get_embedding_client()
    all_hits: List[Dict[str, Any]] = []

    for query in req.queries:
        vector = await embed_client.embed(query)
        for source_key in sources:
            collection_name = settings.MILVUS_COLLECTIONS.get(source_key)
            if not collection_name:
                continue
            hits = milvus_search(
                collection_name=collection_name,
                query_vector=vector,
                top_k=recall_per_route,
                output_fields=["chunk_id", "title", "content", "source"],
            )
            for h in hits:
                h["source_db"] = source_key
                all_hits.append(h)

    if not all_hits:
        logger.info("Milvus 无召回结果，返回空知识片段")
        return RetrieveResponse(chunks=[], fallback=True)

    # chunk_id 去重，保留最高 milvus 分数
    by_id: Dict[str, Dict[str, Any]] = {}
    for h in all_hits:
        cid = str(h.get("chunk_id") or h.get("source") or id(h))
        if cid not in by_id or h.get("score", 0) > by_id[cid].get("score", 0):
            by_id[cid] = h

    candidates = sorted(by_id.values(), key=lambda x: x.get("score", 0), reverse=True)
    candidates = candidates[: top_k * settings.RAG_RECALL_EXPAND]

    reranked = False
    if settings.RERANKER_ENABLED and len(candidates) > 1:
        primary_query = req.queries[0]
        documents = [
            (c.get("content") or c.get("title") or "") for c in candidates
        ]
        rerank_results = await get_reranker_client().rerank(
            query=primary_query,
            documents=documents,
            top_n=top_k,
        )
        if rerank_results and any(r.get("relevance_score", 0) > 0 for r in rerank_results):
            ordered: List[Dict[str, Any]] = []
            for r in rerank_results:
                idx = r.get("index")
                if idx is None or idx >= len(candidates):
                    continue
                c = dict(candidates[idx])
                c["rerank_score"] = float(r.get("relevance_score", 0.0))
                ordered.append(c)
            candidates = ordered
            reranked = True
        else:
            logger.warning("Reranker 返回零分，回退至 milvus 排序")

    final = candidates[:top_k]
    chunks = [
        KnowledgeChunk(
            chunk_id=str(h.get("chunk_id") or "unknown"),
            source_db=str(h.get("source_db") or "unknown"),
            **provenance(h),
            title=h.get("title"),
            content=h.get("content"),
            score=float(h.get("score") or 0.0),
            rerank_score=h.get("rerank_score"),
        )
        for h in final
    ]
    response = RetrieveResponse(chunks=chunks, fallback=False, reranked=reranked)
    await cache_setex(cache_key, settings.RAG_CACHE_TTL, response.model_dump_json())
    logger.info("X-Cache: MISS key=%s 写回缓存 ttl=%ss", cache_key, settings.RAG_CACHE_TTL)
    return response
