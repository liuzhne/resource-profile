"""RAG pipeline 缓存键必须区分是否 rerank（2026-10-13 修复）。"""
import asyncio
import json
from unittest.mock import AsyncMock, MagicMock, patch

from app.core.config import settings
from app.services import rag_pipeline
from app.services.rag_pipeline import _pipeline_cache_key


def test_cache_key_differs_for_rerank_on_off():
    on = _pipeline_cache_key("case", "焦虑", 5, True)
    off = _pipeline_cache_key("case", "焦虑", 5, False)
    assert on != off
    assert on == _pipeline_cache_key("case", "  焦虑 ", 5, True)


def _run(enable_rerank, reranker_enabled, store):
    async def fake_get(key):
        return store.get(key)

    async def fake_setex(key, ttl, value):
        store[key] = value

    embed = MagicMock()
    embed.embed = AsyncMock(return_value=[0.1, 0.2])
    hits = [
        {"chunk_id": "a", "title": "A", "content": "aaa", "score": 0.9},
        {"chunk_id": "b", "title": "B", "content": "bbb", "score": 0.8},
    ]
    reranker = MagicMock()
    reranker.rerank = AsyncMock(return_value=[
        {"index": 1, "relevance_score": 0.95},
        {"index": 0, "relevance_score": 0.5},
    ])
    with patch.object(settings, "RERANKER_ENABLED", reranker_enabled), \
         patch.object(rag_pipeline, "cache_get", side_effect=fake_get), \
         patch.object(rag_pipeline, "cache_setex", side_effect=fake_setex), \
         patch.object(rag_pipeline, "get_embedding_client", return_value=embed), \
         patch.object(rag_pipeline, "milvus_search", return_value=hits) as ms, \
         patch.object(rag_pipeline, "get_reranker_client", return_value=reranker):
        key = next(iter(settings.MILVUS_COLLECTIONS))
        result = asyncio.run(rag_pipeline.retrieve_from_collection("q", key, top_k=2, enable_rerank=enable_rerank))
        return result, ms.call_count


def test_toggling_config_does_not_hit_stale_cache():
    store = {}
    r1, calls1 = _run(None, False, store)
    assert r1["reranked"] is False and calls1 == 1
    r2, calls2 = _run(None, True, store)
    assert calls2 == 1, "开启 rerank 后不应命中未 rerank 的旧缓存"
    assert r2["reranked"] is True
    # 同配置再次请求应命中缓存
    r3, calls3 = _run(None, True, store)
    assert calls3 == 0 and r3["reranked"] is True


def test_per_request_enable_rerank_separates_cache():
    store = {}
    _run(False, True, store)
    r, calls = _run(True, True, store)
    assert calls == 1 and r["reranked"] is True
