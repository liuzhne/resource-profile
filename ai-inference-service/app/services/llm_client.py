"""LLM client wrappers.

Two flavors:
- get_llm_client(): legacy LangChain ChatOpenAI for /api/v1/llm/chat smoke tests.
- chat_completion_raw(): G-1.4 — raw httpx POST to llama.cpp /v1/chat/completions
  with `cache_prompt:true` injected into the body, returning the full JSON so
  G-1.5 can read `timings.cached_n / prompt_n` for slot-cache hit-rate metrics.

Langfuse v4 records model / usage / timings without prompt or response content
by default. LANGFUSE_CAPTURE_CONTENT=true is reserved for synthetic acceptance.
Missing SDK/configuration or telemetry failures leave business calls intact.
"""
from __future__ import annotations

from typing import Any, Dict, List

import httpx
from langchain_openai import ChatOpenAI

from app.core.config import settings
from app.services.llm_metrics import record_llm_response
from app.services.llm_payload import build_chat_payload
from app.services.langfuse_telemetry import telemetry
from app.services.llm_request_policy import policy


def get_llm_client() -> ChatOpenAI:
    return ChatOpenAI(
        model=settings.LLM_MODEL,
        api_key=settings.LLM_API_KEY,
        base_url=settings.LLM_BASE_URL,
        temperature=settings.LLM_TEMPERATURE,
        max_tokens=settings.LLM_MAX_TOKENS,
        timeout=60,
        max_retries=0,
    )


@telemetry.observe_generation
async def chat_completion_raw(
    messages: List[Dict[str, str]],
    route: str,
    *,
    temperature: float | None = None,
    max_tokens: int | None = None,
) -> Dict[str, Any]:
    """POST /v1/chat/completions with cache_prompt=true and record metrics.

    Returns the full llama.cpp JSON response (callers extract `.choices[0].message.content`).
    `route` is a short label used by llm_metrics (e.g. "risk", "plan", "audit").
    """
    payload = build_chat_payload(
        model=settings.LLM_MODEL,
        messages=messages,
        temperature=temperature if temperature is not None else settings.LLM_TEMPERATURE,
        max_tokens=max_tokens if max_tokens is not None else settings.LLM_MAX_TOKENS,
        cache_prompt_enabled=settings.LLM_CACHE_PROMPT_ENABLED,
    )
    if settings.LLM_REQUEST_POLICY_ENABLED:
        payload["response_format"] = {"type": "json_object"}
        if settings.LLM_REASONING_EFFORT:
            payload["reasoning_effort"] = settings.LLM_REASONING_EFFORT
    headers = {"Authorization": f"Bearer {settings.LLM_API_KEY}"}
    base = settings.LLM_BASE_URL.rstrip("/")
    url = f"{base}/chat/completions"

    telemetry.start_call(
        route=route,
        model=settings.LLM_MODEL,
        messages=messages,
        cache_prompt=settings.LLM_CACHE_PROMPT_ENABLED,
    )

    async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT) as client:
        data = await policy.request(client, url, payload, headers)

    record_llm_response(route, data)

    telemetry.record_response(route=route, response=data)

    return data
