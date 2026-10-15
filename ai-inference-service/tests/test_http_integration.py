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

# Production app construction imports the optional chat SDK. These HTTP tests
# never call it; keep the minimal test environment free of inference packages.
try:
    import langchain_openai
except ModuleNotFoundError as error:
    if error.name != "langchain_openai":
        raise
    langchain_openai = types.ModuleType("langchain_openai")
    langchain_openai.ChatOpenAI = object
    sys.modules["langchain_openai"] = langchain_openai

from httpx import ASGITransport, AsyncClient

from app.api import rag, rag_upsert
from app.main import create_app, lifespan
from app.mcp import rag_adapter
from app.services import knowledge_provenance, rag_pipeline


class _EmbeddingClient:
    async def embed(self, _text):
        return [0.1] * rag_upsert.settings.EMBEDDING_DIM


class RagHttpIntegrationTest(unittest.IsolatedAsyncioTestCase):

    async def test_lifespan_shuts_down_telemetry_even_on_application_error(self):
        with patch("app.main.telemetry.shutdown") as shutdown:
            with self.assertRaisesRegex(RuntimeError, "application failure"):
                async with lifespan(object()):
                    raise RuntimeError("application failure")
            shutdown.assert_called_once_with()

    async def asyncSetUp(self):
        app = create_app()
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
        self.assertEqual(payload["chunks"], [])

    async def test_upsert_rejects_missing_admin_token(self):
        with patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"):
            response = await self.client.post(
                "/api/v1/rag/upsert",
                json={"collection": "case", "doc_id": "case-1", "text": "内容"},
            )

        self.assertEqual(response.status_code, 401)

    async def test_upsert_is_disabled_without_configured_admin_token(self):
        with patch.object(rag_upsert.settings, "ADMIN_TOKEN", ""):
            response = await self.client.post(
                "/api/v1/rag/upsert",
                headers={"X-Admin-Token": "admin-secret"},
                json={"collection": "case", "doc_id": "case-1", "text": "内容"},
            )

        self.assertEqual(response.status_code, 503)

    async def test_upsert_rejects_collection_outside_allowlist(self):
        with patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"):
            response = await self.client.post(
                "/api/v1/rag/upsert",
                headers={"X-Admin-Token": "admin-secret"},
                json={"collection": "unknown", "doc_id": "case-1", "text": "内容"},
            )

        self.assertEqual(response.status_code, 422)

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
        self.assertEqual(cache_set.await_count, 2)

    async def test_retrieve_preserves_publisher_identity_and_url_on_cache_roundtrip(self):
        hit = {"chunk_id": "public-policy_0000", "source": "https://www.csdp.edu.cn/article/4105.html",
               "title": "保密", "content": "公开政策摘要", "score": 0.8}
        cache_set = AsyncMock(return_value=True)
        with (
            patch.object(rag, "cache_get", AsyncMock(return_value=None)),
            patch.object(knowledge_provenance, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag, "cache_setex", cache_set),
            patch.object(rag, "get_embedding_client", return_value=_EmbeddingClient()),
            patch.object(rag, "milvus_search", return_value=[hit]),
            patch.object(rag.settings, "RERANKER_ENABLED", False),
        ):
            response = await self.client.post("/api/v1/rag/retrieve",
                                              json={"queries": ["保密原则"], "sources": ["policy"]})
        chunk = response.json()["chunks"][0]
        self.assertEqual(chunk["source_db"], "policy")
        self.assertEqual(chunk["source"], hit["source"])
        self.assertEqual(chunk["source_url"], hit["source"])
        self.assertEqual(chunk["doc_id"], "public-policy")
        cached_json = cache_set.await_args.args[2]
        with (
            patch.object(rag, "cache_get", AsyncMock(return_value=cached_json)),
            patch.object(knowledge_provenance, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag, "milvus_search") as search,
        ):
            cached = await self.client.post("/api/v1/rag/retrieve",
                                            json={"queries": ["保密原则"], "sources": ["policy"]})
        self.assertEqual(cached.json()["chunks"][0], chunk)
        search.assert_not_called()

    async def test_non_url_legacy_source_is_preserved_without_invented_provenance(self):
        hit = {"chunk_id": "probe-case", "source": "synthetic-probe-20261007", "score": 0.6}
        with (
            patch.object(rag, "cache_get", AsyncMock(return_value=None)),
            patch.object(knowledge_provenance, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag, "cache_setex", AsyncMock()),
            patch.object(rag, "get_embedding_client", return_value=_EmbeddingClient()),
            patch.object(rag, "milvus_search", return_value=[hit]),
        ):
            response = await self.client.post("/api/v1/rag/retrieve",
                                              json={"queries": ["压力"], "sources": ["case"]})
        chunk = response.json()["chunks"][0]
        self.assertEqual(chunk["source"], "synthetic-probe-20261007")
        self.assertIsNone(chunk["doc_id"])
        self.assertIsNone(chunk["source_url"])

    async def test_upsert_rejects_long_source_before_embedding_or_write(self):
        with (
            patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"),
            patch.object(rag_upsert, "get_embedding_client") as embedding,
            patch.object(rag_upsert, "insert_chunks") as insert,
        ):
            response = await self.client.post("/api/v1/rag/upsert", headers={"X-Admin-Token": "admin-secret"},
                                              json={"collection": "policy", "doc_id": "policy-long", "text": "政策",
                                                    "metadata": {"source_url": "https://example.org/" + "a" * 64}})
        self.assertEqual(response.status_code, 422)
        embedding.assert_not_called()
        insert.assert_not_called()

    async def test_upsert_unchanged_document_skips_and_oversized_text_is_not_truncated(self):
        req = rag_upsert.UpsertRequest(collection="policy", doc_id="policy-1", text="完整正文",
                                      metadata={"title": "公开摘要", "source_url": "https://example.org/p"})
        correct_hash = rag_upsert._document_hash(req, *rag_upsert._stored_metadata(req.metadata))
        with (
            patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"),
            patch.object(rag_upsert, "cache_get", AsyncMock(return_value=correct_hash)),
            patch.object(rag_upsert, "get_embedding_client") as embed,
        ):
            response = await self.client.post("/api/v1/rag/upsert", headers={"X-Admin-Token": "admin-secret"},
                                              json=req.model_dump())
        self.assertTrue(response.json()["skipped"])
        embed.assert_not_called()
        with (
            patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"),
            patch.object(rag_upsert, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag_upsert, "get_embedding_client") as embed,
        ):
            response = await self.client.post("/api/v1/rag/upsert", headers={"X-Admin-Token": "admin-secret"},
                                              json={"collection": "policy", "doc_id": "too-large", "text": "a" * 5000})
        self.assertEqual(response.status_code, 422)
        embed.assert_not_called()

    async def test_query_text_is_absent_from_retrieval_logs(self):
        query = "PRIVATE-QUERY-CONTENT-MUST-NOT-BE-LOGGED"
        with (
            patch.object(rag, "cache_get", AsyncMock(return_value=None)),
            patch.object(knowledge_provenance, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag, "get_embedding_client", return_value=_EmbeddingClient()),
            patch.object(rag, "milvus_search", return_value=[]),
            self.assertLogs("app.api.rag", level="INFO") as capture,
        ):
            response = await self.client.post("/api/v1/rag/retrieve", json={"queries": [query], "sources": ["case"]})
        self.assertEqual(response.status_code, 200)
        self.assertNotIn(query, "\n".join(capture.output))

    async def test_upsert_metadata_correction_changes_hash_and_failed_delete_stops_insert(self):
        before = rag_upsert.UpsertRequest(collection="policy", doc_id="policy-1", text="同样内容",
                                         metadata={"source": "old-marker"})
        after = rag_upsert.UpsertRequest(collection="policy", doc_id="policy-1", text="同样内容",
                                        metadata={"source_url": "https://example.org/policy"})
        old_hash = rag_upsert._document_hash(before, *rag_upsert._stored_metadata(before.metadata))
        new_hash = rag_upsert._document_hash(after, *rag_upsert._stored_metadata(after.metadata))
        self.assertNotEqual(old_hash, new_hash)
        with (
            patch.object(rag_upsert.settings, "ADMIN_TOKEN", "admin-secret"),
            patch.object(rag_upsert, "cache_get", AsyncMock(return_value=old_hash)),
            patch.object(rag_upsert, "get_embedding_client", return_value=_EmbeddingClient()),
            patch.object(rag_upsert, "delete_by_doc_id", side_effect=RuntimeError("private failure")),
            patch.object(rag_upsert, "insert_chunks") as insert,
        ):
            response = await self.client.post("/api/v1/rag/upsert", headers={"X-Admin-Token": "admin-secret"},
                                              json=after.model_dump())
        self.assertEqual(response.status_code, 503)
        self.assertNotIn("private failure", response.text)
        insert.assert_not_called()


class McpAdapterIntegrationTest(unittest.IsolatedAsyncioTestCase):

    async def test_real_pipeline_and_adapter_preserve_source_after_reranking(self):
        url = "https://www.who.int/publications/i/item/9789240003927"
        hits = [{"chunk_id": "stress_0000", "source": url, "content": "压力支持", "score": 0.7},
                {"chunk_id": "legacy", "source": "unknown-source", "content": "其他", "score": 0.6}]
        reranker = types.SimpleNamespace(rerank=AsyncMock(return_value=[{"index": 0, "relevance_score": 0.9}]))
        with (
            patch.object(rag_pipeline, "cache_get", AsyncMock(return_value=None)),
            patch.object(knowledge_provenance, "cache_get", AsyncMock(return_value=None)),
            patch.object(rag_pipeline, "cache_setex", AsyncMock()),
            patch.object(rag_pipeline, "get_embedding_client", return_value=_EmbeddingClient()),
            patch.object(rag_pipeline, "milvus_search", return_value=hits),
            patch.object(rag_pipeline, "get_reranker_client", return_value=reranker),
            patch.object(rag_pipeline.settings, "RERANKER_ENABLED", True),
        ):
            result = await rag_adapter.search_collection("学业压力", "psychology", 2)
        self.assertTrue(result["reranked"])
        self.assertEqual(result["chunks"][0]["source_url"], url)
        self.assertEqual(result["chunks"][0]["doc_id"], "stress")
        self.assertEqual(result["chunks"][0]["source"], url)

    def test_provenance_does_not_make_unsafe_urls_clickable_or_invent_ids(self):
        for source in ("javascript:alert(1)", "https://user:secret@example.org/p", "https://example.org/a b"):
            result = knowledge_provenance.provenance({"chunk_id": "legacy-01", "source": source})
            self.assertEqual(result["source"], source)
            self.assertIsNone(result["source_url"])
            self.assertIsNone(result["doc_id"])

    def test_changed_corpus_revision_changes_api_and_mcp_cache_keys(self):
        self.assertNotEqual(rag._rag_cache_key(3, ["policy"], ["保密"], None, "a"),
                            rag._rag_cache_key(3, ["policy"], ["保密"], None, "b"))
        self.assertNotEqual(rag_pipeline._pipeline_cache_key("policy", "保密", 3, "a"),
                            rag_pipeline._pipeline_cache_key("policy", "保密", 3, "b"))

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
