"""LLM client wrappers.

Two flavors:
- get_llm_client(): legacy LangChain ChatOpenAI for /api/v1/llm/chat smoke tests.
- chat_completion_raw(): G-1.4 — raw httpx POST to llama.cpp /v1/chat/completions
  with `cache_prompt:true` injected into the body, returning the full JSON so
  G-1.5 can read `timings.cached_n / prompt_n` for slot-cache hit-rate metrics.

G-5.2：Langfuse `@observe(as_type="generation")` 包在 chat_completion_raw 上，
trace 自动捕获 input messages / output content / model / usage / metadata；
未配 LANGFUSE_PUBLIC_KEY/SECRET_KEY 时 SDK 自动 no-op。
"""
from __future__ import annotations

from typing import Any, Dict, List

import httpx
from langchain_openai import ChatOpenAI

from app.core.config import settings
from app.services.llm_metrics import record_llm_response
from app.services.llm_payload import build_chat_payload

# Langfuse 装饰器在 SDK 缺失时降级为 no-op，保证主流程可独立运行
try:  # pragma: no cover
    from langfuse.decorators import langfuse_context, observe
    _LANGFUSE_AVAILABLE = True
except Exception:  # pragma: no cover
    _LANGFUSE_AVAILABLE = False

    def observe(*_args, **_kwargs):  # type: ignore
        def decorator(fn):
            return fn
        return decorator

    class _NoopContext:  # type: ignore
        def update_current_observation(self, **_kwargs):
            pass

    langfuse_context = _NoopContext()  # type: ignore


def get_llm_client() -> ChatOpenAI:
    return ChatOpenAI(
        model=settings.LLM_MODEL,
        api_key=settings.LLM_API_KEY,
        base_url=settings.LLM_BASE_URL,
        temperature=settings.LLM_TEMPERATURE,
        max_tokens=settings.LLM_MAX_TOKENS,
        timeout=60,
        max_retries=2,
    )


@observe(as_type="generation")
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
    headers = {"Authorization": f"Bearer {settings.LLM_API_KEY}"}
    base = settings.LLM_BASE_URL.rstrip("/")
    url = f"{base}/chat/completions"

    # 入参打到 trace（output 在 return 时由 @observe 自动捕获）
    langfuse_context.update_current_observation(
        name=f"llm.{route}",
        model=settings.LLM_MODEL,
        input=messages,
        metadata={"route": route, "cache_prompt": settings.LLM_CACHE_PROMPT_ENABLED},
    )

    async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT) as client:
        resp = await client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()

    record_llm_response(route, data)

    # 把 llama.cpp timings 与 usage 透到 trace 的 usage 字段（Langfuse UI 直接显示）
    timings = data.get("timings") or {}
    usage = data.get("usage") or {}
    langfuse_context.update_current_observation(
        usage={
            "input": int(timings.get("prompt_n") or usage.get("prompt_tokens") or 0),
            "output": int(timings.get("predicted_n") or usage.get("completion_tokens") or 0),
            "unit": "TOKENS",
        },
        metadata={
            "route": route,
            "cached_n": int(timings.get("cached_n") or 0),
            "prefill_ms": float(timings.get("prompt_ms") or 0.0),
        },
    )

    return data
