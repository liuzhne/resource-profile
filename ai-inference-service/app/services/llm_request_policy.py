"""Bounded, async chat admission; Java and Python share a Redis provider cooldown."""
from __future__ import annotations

import asyncio
import json
import math
import time
from email.utils import parsedate_to_datetime

from app.core.config import settings
from app.services.redis_client import get_redis

COOLDOWN_KEY = "edu:llm:provider-cooldown"
CLAIM = ("local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000); "
         "local at=tonumber(redis.call('GET',KEYS[1]) or '0'); "
         "if at>now then return at-now end; "
         "local delay=tonumber(ARGV[1]); if delay>0 then redis.call('PSETEX',KEYS[1],delay,now+delay) end; return 0")
DEFER = ("local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000); "
         "local at=math.max(tonumber(redis.call('GET',KEYS[1]) or '0'),now+tonumber(ARGV[1])); "
         "if at>now then redis.call('PSETEX',KEYS[1],at-now,at) end; return 0")
SAFE_CODES = {"json_validate_failed", "rate_limit_exceeded", "invalid_request_error", "context_length_exceeded", "invalid_api_key"}


class LlmProviderError(RuntimeError):
    """Never includes provider messages/failed_generation, prompts, output, URLs or credentials."""


def retry_after_seconds(header, now=None, fallback=1.0):
    if not header:
        return fallback
    try:
        seconds = float(header)
        if math.isfinite(seconds) and seconds >= 0:
            return seconds
    except ValueError:
        pass
    try:
        return max(0.0, parsedate_to_datetime(header).timestamp() - (time.time() if now is None else now))
    except (ValueError, TypeError, OverflowError):
        return fallback


class LlmRequestPolicy:
    def __init__(self):
        self._active = asyncio.Lock()
        self._pending = 0
        self._next_at = 0.0

    async def request(self, client, url, payload, headers):
        if not settings.LLM_REQUEST_POLICY_ENABLED:
            response = await client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            return response.json()
        if not 1 <= settings.LLM_RETRY_MAX_ATTEMPTS <= 3 or not 0 <= settings.LLM_REQUEST_QUEUE_CAPACITY <= 20:
            raise LlmProviderError("Invalid bounded LLM request policy")
        if not 1 <= settings.LLM_REQUEST_MAX_WAIT_MS <= 300000 or not 0 <= settings.LLM_MIN_REQUEST_INTERVAL_MS <= 120000:
            raise LlmProviderError("Invalid bounded LLM wait policy")
        if (not 0 <= settings.LLM_RETRY_MAX_DELAY_MS <= 180000
                or not 1 <= settings.LLM_MAX_OUTPUT_TOKENS <= 8192
                or not 1 <= settings.LLM_MAX_INPUT_CHARACTERS <= 64000):
            raise LlmProviderError("Invalid bounded LLM token/retry policy")
        if (not 1 <= payload.get("max_tokens", 0) <= settings.LLM_MAX_OUTPUT_TOKENS
                or len(json.dumps(payload.get("messages", []), ensure_ascii=False)) > settings.LLM_MAX_INPUT_CHARACTERS):
            raise LlmProviderError("LLM request exceeds configured input/output budget")
        if self._pending >= settings.LLM_REQUEST_QUEUE_CAPACITY + 1:
            raise LlmProviderError("LLM request queue is full")
        self._pending += 1
        try:
            return await asyncio.wait_for(self._execute(client, url, payload, headers),
                                          settings.LLM_REQUEST_MAX_WAIT_MS / 1000)
        except asyncio.TimeoutError:
            raise LlmProviderError("LLM request deadline exceeded") from None
        finally:
            self._pending -= 1

    async def _shared_slot(self):
        if not settings.LLM_SHARED_COOLDOWN_ENABLED:
            return
        redis = await get_redis()
        if redis is None:
            raise LlmProviderError("Shared LLM cooldown Redis is unavailable")
        try:
            while True:
                delay = await redis.eval(CLAIM, 1, COOLDOWN_KEY, settings.LLM_MIN_REQUEST_INTERVAL_MS)
                if delay == 0:
                    return
                await asyncio.sleep(delay / 1000)
        except LlmProviderError:
            raise
        except Exception:
            raise LlmProviderError("Shared LLM cooldown is unavailable") from None

    async def _defer(self, delay):
        if not settings.LLM_SHARED_COOLDOWN_ENABLED or delay <= 0:
            return
        try:
            redis = await get_redis()
            if redis is None:
                raise LlmProviderError("Shared LLM cooldown Redis is unavailable")
            await redis.eval(DEFER, 1, COOLDOWN_KEY, math.ceil(delay * 1000))
        except Exception:
            raise LlmProviderError("Shared LLM cooldown is unavailable") from None

    async def _execute(self, client, url, payload, headers):
        async with self._active:
            for attempt in range(settings.LLM_RETRY_MAX_ATTEMPTS):
                await asyncio.sleep(max(0, self._next_at - time.monotonic()))
                await self._shared_slot()
                self._next_at = time.monotonic() + settings.LLM_MIN_REQUEST_INTERVAL_MS / 1000
                response = await client.post(url, json=payload, headers=headers)
                if response.is_success:
                    return response.json()
                code = "provider_error"
                try:
                    found = response.json().get("error", {}).get("code")
                    if found in SAFE_CODES:
                        code = found
                except (ValueError, TypeError, AttributeError):
                    pass
                if response.status_code != 429:
                    raise LlmProviderError(f"LLM HTTP {response.status_code}; code={code}")
                delay = retry_after_seconds(response.headers.get("Retry-After"), fallback=2.0 ** attempt)
                await self._defer(delay)
                if attempt + 1 == settings.LLM_RETRY_MAX_ATTEMPTS:
                    raise LlmProviderError(f"LLM HTTP 429; code={code}")
                if delay * 1000 > settings.LLM_RETRY_MAX_DELAY_MS:
                    raise LlmProviderError("LLM Retry-After exceeds request budget")
                await response.aclose()
                self._next_at = max(self._next_at, time.monotonic() + delay)
        raise LlmProviderError("LLM retry budget exhausted")


policy = LlmRequestPolicy()
