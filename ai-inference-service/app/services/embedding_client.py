"""
Embedding 客户端 — 走 OpenAI 兼容 /v1/embeddings 接口。
默认指向 llama.cpp 或独立的 BGE 嵌入服务，由 EMBEDDING_BASE_URL 切换。
异常时返回零向量降级，保证 RAG 链路不被嵌入失败阻断。

D 阶段新增：Redis 缓存（24h），key = sha256(model + text)，值为 JSON 序列化的向量数组。
"""
import hashlib
import json
import logging
from typing import List

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app.core.config import settings
from app.services.redis_client import cache_get, cache_setex

logger = logging.getLogger(__name__)


def _cache_key(model: str, text: str) -> str:
    h = hashlib.sha256()
    h.update(model.encode("utf-8"))
    h.update(b"\x00")
    h.update(text.encode("utf-8"))
    return "edu:emb:" + h.hexdigest()[:32]


class EmbeddingClient:
    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.EMBEDDING_BASE_URL,
            timeout=30.0,
            headers={"Authorization": f"Bearer {settings.EMBEDDING_API_KEY or settings.LLM_API_KEY}"},
        )

    async def embed(self, text: str) -> List[float]:
        cache_key = _cache_key(settings.EMBEDDING_MODEL, text)

        cached = await cache_get(cache_key)
        if cached:
            try:
                vec = json.loads(cached)
                if isinstance(vec, list) and len(vec) == settings.EMBEDDING_DIM:
                    logger.debug("Embedding 缓存命中 key=%s", cache_key)
                    return vec
            except Exception:
                pass  # 缓存格式异常，走远程

        vec = await self._embed_remote(text)
        if vec and any(v != 0.0 for v in vec):
            await cache_setex(
                cache_key, settings.EMBEDDING_CACHE_TTL, json.dumps(vec)
            )
        return vec

    @retry(stop=stop_after_attempt(2), wait=wait_exponential(min=1, max=4))
    async def _embed_remote(self, text: str) -> List[float]:
        try:
            resp = await self._client.post(
                "/embeddings",
                json={"model": settings.EMBEDDING_MODEL, "input": text},
            )
            resp.raise_for_status()
            data = resp.json()
            return data["data"][0]["embedding"]
        except Exception as exc:
            logger.error("Embedding 调用失败: %s", exc)
            return [0.0] * settings.EMBEDDING_DIM

    async def close(self) -> None:
        await self._client.aclose()


_embedding_client: EmbeddingClient | None = None


def get_embedding_client() -> EmbeddingClient:
    global _embedding_client
    if _embedding_client is None:
        _embedding_client = EmbeddingClient()
    return _embedding_client
