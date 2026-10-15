import asyncio
import time
import unittest
from unittest.mock import AsyncMock, patch

import httpx

from app.core.config import settings
from app.services.llm_request_policy import LlmProviderError, LlmRequestPolicy, retry_after_seconds, COOLDOWN_KEY


class LlmRequestPolicyTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.config = patch.multiple(settings, LLM_REQUEST_POLICY_ENABLED=True,
            LLM_SHARED_COOLDOWN_ENABLED=False, LLM_REQUEST_QUEUE_CAPACITY=0,
            LLM_REQUEST_MAX_WAIT_MS=500, LLM_MIN_REQUEST_INTERVAL_MS=0,
            LLM_RETRY_MAX_ATTEMPTS=3, LLM_RETRY_MAX_DELAY_MS=100,
            LLM_MAX_OUTPUT_TOKENS=2400, LLM_MAX_INPUT_CHARACTERS=18000)
        self.config.start()
        self.addCleanup(self.config.stop)
        self.payload = {"max_tokens": 100, "messages": [{"role": "user", "content": "synthetic"}]}
        self.policy = LlmRequestPolicy()

    async def call(self, responder):
        async with httpx.AsyncClient(transport=httpx.MockTransport(responder)) as client:
            return await self.policy.request(client, "https://provider.example.test/chat/completions", self.payload, {})

    async def test_permanent_400_has_one_attempt_and_no_provider_content(self):
        calls = []
        def respond(request):
            calls.append(request)
            return httpx.Response(400, json={"error": {"code": "json_validate_failed", "message": "PRIVATE", "failed_generation": "PRIVATE"}})
        with self.assertRaises(LlmProviderError) as error:
            await self.call(respond)
        self.assertEqual(len(calls), 1)
        self.assertEqual(str(error.exception), "LLM HTTP 400; code=json_validate_failed")

    async def test_429_honors_retry_after_and_recovers(self):
        at = []
        def respond(request):
            at.append(time.monotonic())
            return httpx.Response(429, headers={"Retry-After": "0.02"}, json={}) if len(at) == 1 else httpx.Response(200, json={"ok": True})
        self.assertEqual(await self.call(respond), {"ok": True})
        self.assertEqual(len(at), 2)
        self.assertGreaterEqual(at[1] - at[0], .018)

    async def test_429_has_finite_three_attempts(self):
        calls = []
        def respond(request):
            calls.append(request)
            return httpx.Response(429, headers={"Retry-After": "0"}, json={"error": {"code": "rate_limit_exceeded"}})
        with self.assertRaisesRegex(LlmProviderError, "HTTP 429"):
            await self.call(respond)
        self.assertEqual(len(calls), 3)

    async def test_long_retry_after_never_retries_early(self):
        calls = []
        def respond(request):
            calls.append(request)
            return httpx.Response(429, headers={"Retry-After": "120"}, json={})
        with self.assertRaisesRegex(LlmProviderError, "Retry-After exceeds"):
            await self.call(respond)
        self.assertEqual(len(calls), 1)

    async def test_full_queue_and_deadline_release_admission(self):
        entered = asyncio.Event()
        async def slow(request):
            entered.set()
            await asyncio.sleep(1)
            return httpx.Response(200, json={})
        with patch.object(settings, "LLM_REQUEST_MAX_WAIT_MS", 30):
            first = asyncio.create_task(self.call(slow))
            await entered.wait()
            with self.assertRaisesRegex(LlmProviderError, "queue is full"):
                await self.call(lambda request: httpx.Response(200, json={}))
            with self.assertRaisesRegex(LlmProviderError, "deadline exceeded"):
                await first
        self.assertEqual(self.policy._pending, 0)
        self.assertEqual(await self.call(lambda request: httpx.Response(200, json={"recovered": True})), {"recovered": True})

    async def test_shared_redis_unavailable_fails_closed_without_http(self):
        responder = AsyncMock()
        with patch.object(settings, "LLM_SHARED_COOLDOWN_ENABLED", True), patch("app.services.llm_request_policy.get_redis", AsyncMock(return_value=None)):
            with self.assertRaisesRegex(LlmProviderError, "Redis is unavailable"):
                await self.call(responder)
        responder.assert_not_awaited()

    async def test_shared_slot_and_final_429_cooldown(self):
        redis = AsyncMock()
        redis.eval.side_effect = [10, 0, 0]
        with patch.object(settings, "LLM_SHARED_COOLDOWN_ENABLED", True), patch.object(settings, "LLM_RETRY_MAX_ATTEMPTS", 1), patch("app.services.llm_request_policy.get_redis", AsyncMock(return_value=redis)):
            with self.assertRaisesRegex(LlmProviderError, "HTTP 429"):
                await self.call(lambda request: httpx.Response(429, headers={"Retry-After": "0.02"}, json={}))
        self.assertEqual(redis.eval.await_count, 3)
        self.assertEqual(redis.eval.await_args_list[-1].args[2:], (COOLDOWN_KEY, 20))

    async def test_input_output_budget_rejects_before_http(self):
        self.payload["max_tokens"] = 2401
        with self.assertRaisesRegex(LlmProviderError, "budget"):
            await self.call(lambda request: self.fail("must not call HTTP"))

    def test_retry_after_fraction_date_and_invalid(self):
        self.assertEqual(retry_after_seconds("1.25"), 1.25)
        self.assertEqual(retry_after_seconds("Thu, 01 Jan 1970 00:00:02 GMT", now=0), 2)
        self.assertEqual(retry_after_seconds("nan", fallback=3), 3)


if __name__ == "__main__":
    unittest.main()
