import unittest
from unittest.mock import patch

import httpx

from app.core.config import settings
from app.services.embedding_client import EmbeddingClient


class EmbeddingProviderAuthTest(unittest.IsolatedAsyncioTestCase):
    async def check_authorization(self, embedding_key, expected_key):
        requests = []

        def respond(request):
            requests.append(request)
            return httpx.Response(200, json={"data": [{"embedding": [0.25, 0.75]}]})

        original_client = httpx.AsyncClient

        def make_client(**kwargs):
            return original_client(transport=httpx.MockTransport(respond), **kwargs)

        with patch.multiple(settings, EMBEDDING_API_KEY=embedding_key,
                            LLM_API_KEY="generation-only-test-key",
                            EMBEDDING_BASE_URL="https://embedding.example.test/v1",
                            EMBEDDING_MODEL="embedding-test"):
            with patch("app.services.embedding_client.httpx.AsyncClient", side_effect=make_client):
                client = EmbeddingClient()
            try:
                result = await client._embed_remote("provider authentication test")
            finally:
                await client.close()

        self.assertEqual(result, [0.25, 0.75])
        self.assertEqual(len(requests), 1)
        self.assertEqual(str(requests[0].url), "https://embedding.example.test/v1/embeddings")
        self.assertEqual(requests[0].headers["Authorization"], f"Bearer {expected_key}")

    async def test_embedding_provider_receives_its_own_key(self):
        await self.check_authorization("embedding-only-test-key", "embedding-only-test-key")

    async def test_unconfigured_embedding_key_preserves_shared_provider(self):
        await self.check_authorization("", "generation-only-test-key")


if __name__ == "__main__":
    unittest.main()
