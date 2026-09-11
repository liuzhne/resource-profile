"""G-4.2 / G-4.3：RAG 增量知识导入端点。

POST /api/v1/rag/upsert
设计文档：docs/educare/RAG_UPSERT_DESIGN.md

流程：
  1. validate（pydantic + collection 白名单 + chunks 数 ≤ 200）
  2. auth check: X-Admin-Token == settings.ADMIN_TOKEN（fail-closed）
  3. idempotency: sha256(text) 命中 Redis → skipped=true 直返
  4. chunks = split(text, chunk_strategy)
  5. for chunk: embed(chunk)
  6. delete_by_doc_id（幂等替换）
  7. insert_chunks
  8. 写 Redis hash → 返回 UpsertResponse
"""
import hashlib
import hmac
import logging
import time
from typing import Any, Dict, Literal, Optional

from fastapi import APIRouter, Header, HTTPException, status
from pydantic import BaseModel, Field

from app.core.config import settings
from app.services.embedding_client import get_embedding_client
from app.services.milvus_client import delete_by_doc_id, insert_chunks
from app.services.redis_client import cache_get, cache_setex
from app.services.text_splitter import ChunkParams, MAX_CHUNKS, split

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/rag", tags=["rag"])


_COLLECTION_KEYS = tuple(settings.MILVUS_COLLECTIONS.keys())  # ("case","psychology","policy","success")
CollectionKey = Literal["case", "psychology", "policy", "success"]


class ChunkStrategy(BaseModel):
    name: Literal["none", "fixed_size", "sentence"] = "none"
    window: int = 800
    overlap: int = 80
    max_chars: int = 1000


class UpsertRequest(BaseModel):
    collection: CollectionKey
    doc_id: str = Field(..., min_length=1, max_length=48, pattern=r"^[A-Za-z0-9_\-]+$")
    text: str = Field(..., min_length=1)
    metadata: Dict[str, Any] = Field(default_factory=dict)
    chunk_strategy: ChunkStrategy = Field(default_factory=ChunkStrategy)


class UpsertResponse(BaseModel):
    doc_id: str
    collection: str
    chunks_written: int
    chunks_deleted_first: int
    embedding_ms: float
    milvus_ms: float
    skipped: bool = False


def _hash_key(collection: str, doc_id: str) -> str:
    return f"edu:rag:upsert:hash:{collection}:{doc_id}"


def _text_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:32]


def _check_admin_token(provided: Optional[str]) -> None:
    """fail-closed：未配置 ADMIN_TOKEN 也拒，避免误开放。"""
    expected = settings.ADMIN_TOKEN
    if not expected:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="EDUCARE_ADMIN_TOKEN 未配置，端点禁用",
        )
    if not provided or not hmac.compare_digest(provided, expected):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="X-Admin-Token 缺失或不匹配")


@router.post("/upsert", response_model=UpsertResponse)
async def upsert_knowledge(
    req: UpsertRequest,
    x_admin_token: Optional[str] = Header(default=None, alias="X-Admin-Token"),
) -> UpsertResponse:
    _check_admin_token(x_admin_token)

    collection_name = settings.MILVUS_COLLECTIONS.get(req.collection)
    if not collection_name:
        # Literal 已限制，这里只是双保险
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                            detail=f"未知 collection: {req.collection}")

    # 幂等短路：相同 doc_id + 相同 text → 直接 skip
    new_hash = _text_hash(req.text)
    hash_key = _hash_key(req.collection, req.doc_id)
    cached_hash = await cache_get(hash_key)
    if cached_hash == new_hash:
        logger.info("rag.upsert SKIPPED doc_id=%s hash match", req.doc_id)
        return UpsertResponse(
            doc_id=req.doc_id,
            collection=req.collection,
            chunks_written=0,
            chunks_deleted_first=0,
            embedding_ms=0.0,
            milvus_ms=0.0,
            skipped=True,
        )

    params = ChunkParams(
        name=req.chunk_strategy.name,
        window=req.chunk_strategy.window,
        overlap=req.chunk_strategy.overlap,
        max_chars=req.chunk_strategy.max_chars,
    )
    try:
        chunks = split(req.text, params)
    except ValueError as e:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))

    if not chunks:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                            detail="text 切片后为空")
    if len(chunks) > MAX_CHUNKS:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                            detail=f"chunks 数 {len(chunks)} 超过上限 {MAX_CHUNKS}，请用更大的 window/max_chars 或分多次")

    title = str(req.metadata.get("title") or "").strip()
    source = str(req.metadata.get("source_url") or req.metadata.get("source") or "").strip()[:64]

    # 1) 嵌入
    embed_t0 = time.perf_counter()
    embed_client = get_embedding_client()
    vectors = []
    for chunk in chunks:
        vec = await embed_client.embed(chunk)
        if not vec or len(vec) != settings.EMBEDDING_DIM:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE,
                                detail="嵌入服务异常或维度不匹配")
        vectors.append(vec)
    embed_ms = (time.perf_counter() - embed_t0) * 1000

    # 2) 幂等替换 + 写入
    milvus_t0 = time.perf_counter()
    try:
        deleted = delete_by_doc_id(collection_name, req.doc_id)
    except Exception as e:
        logger.warning("delete_by_doc_id 异常（继续 insert）: %s", e)
        deleted = 0

    rows = [
        {
            "chunk_id": f"{req.doc_id}_{idx:04d}",
            "title": (title or f"{req.doc_id} #{idx}")[:255],
            "content": chunk,
            "source": source,
            "embedding": vectors[idx],
        }
        for idx, chunk in enumerate(chunks)
    ]
    try:
        written = insert_chunks(collection_name, rows)
    except Exception as e:
        logger.error("insert_chunks 失败: %s", e)
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail=f"Milvus 写入失败: {e}")
    milvus_ms = (time.perf_counter() - milvus_t0) * 1000

    # 写入 hash，下次同 doc_id+text 直接短路
    await cache_setex(hash_key, settings.UPSERT_HASH_TTL, new_hash)

    logger.info(
        "rag.upsert collection=%s doc_id=%s chunks=%d deleted_first=%d embed_ms=%.0f milvus_ms=%.0f",
        req.collection, req.doc_id, written, deleted, embed_ms, milvus_ms,
    )
    return UpsertResponse(
        doc_id=req.doc_id,
        collection=req.collection,
        chunks_written=written,
        chunks_deleted_first=deleted,
        embedding_ms=round(embed_ms, 1),
        milvus_ms=round(milvus_ms, 1),
    )
