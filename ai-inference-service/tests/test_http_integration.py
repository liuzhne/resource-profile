import sys
import types
import unittest
from unittest.mock import AsyncMock, patch

# 路由模块只需要 milvus_client 的函数边界；CI 不安装/连接 pymilvus，以 stub 保证零外部服务。
pymilvus = types.ModuleType("pymilvus")
pymilvus.Collection = object
pymilvus.connections = types.SimpleNamespace()
pymilvus.utility = types.SimpleNamespace()
sys.modules.setdefault("pymilvus", pymilvus)

from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.api import rag, rag_upsert
from app.mcp import rag_adapter


class _EmbeddingClient:
    async def embed(self, _text):
        return [0.1] * rag_upsert.settings.EMBEDDING_DIM


class RagHttpIntegrationTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self):
        app = FastAPI()
        app.include_router(rag.router)
        app.include_router(rag_upsert.router)
        self.client = AsyncClient(transport=ASGITransport(app=app), base_url="http://test")

    async def asyncTearDown(self):
        await self.client.aclose()

    async def test_retrieve_falls_back_without_milvus_hits(self):
        with (
            patch.object(rag, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag, "get_embedding_client", return_value=_EmbeddingClient()),
            patch.object(rag, "milvus_search", return_value=[]),
        ):
            response = await self.client.post(
                "/api/v1/rag/retrieve",
                json={"queries": ["学业风险"], "sources": ["case"], "top_k": 2},
            )

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["fallback"])
        self.assertEqual(len(payload["chunks"]), 2)

    async def test_upsert_rejects_missing_admin_token(self):
        with patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"):
            response = await self.client.post(
                "/api/v1/rag/upsert",
                json={"collection": "case", "doc_id": "case-1", "text": "内容"},
            )

        self.assertEqual(response.status_code, 401)

    async def test_upsert_writes_chunks_without_external_services(self):
        cache_set = AsyncMock(return_value=True)
        with (
            patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"),
            patch.object(rag_upsert, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag_upsert, "cache_setex", cache_set),
            patch.object(rag_upsert, "get_embedding_client", return_value=_EmbeddingClient()),
            patch.object(rag_upsert, "delete_by_doc_id", return_value=2),
            patch.object(rag_upsert, "insert_chunks", return_value=1),
        ):
            response = await self.client.post(
                "/api/v1/rag/upsert",
                headers={"X-Admin-Token": "admin-secret"},
                json={"collection": "case", "doc_id": "case-1", "text": "有效知识内容"},
            )

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["chunks_written"], 1)
        self.assertEqual(payload["chunks_deleted_first"], 2)
        self.assertFalse(payload["skipped"])
        cache_set.assert_awaited_once()


class McpAdapterIntegrationTest(unittest.IsolatedAsyncioTestCase):

    async def test_search_collection_clamps_top_k_and_shapes_response(self):
        pipeline = AsyncMock(return_value={
            "reranked": True,
            "fallback": False,
            "chunks": [{"chunk_id": "c1", "content": "命中"}],
        })
        with patch.object(rag_adapter, "retrieve_from_collection", pipeline):
            result = await rag_adapter.search_collection("风险干预", "case", 99)

        self.assertEqual(result["top_k"], 20)
        self.assertEqual(result["count"], 1)
        self.assertTrue(result["reranked"])
        pipeline.assert_awaited_once_with(query="风险干预", collection_key="case", top_k=20)

    async def test_empty_query_does_not_call_pipeline(self):
        pipeline = AsyncMock()
        with patch.object(rag_adapter, "retrieve_from_collection", pipeline):
            result = await rag_adapter.search_collection("  ", "policy", 5)

        self.assertEqual(result["error"], "empty query")
        pipeline.assert_not_awaited()
