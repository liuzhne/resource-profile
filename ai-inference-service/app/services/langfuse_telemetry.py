"""Optional Langfuse v4 telemetry; content capture is explicitly opt-in.

The SDK decorator always disables automatic argument/return capture. Only the
fixed fields below reach metadata; SDK and export failures never replace an LLM
result or cause a second provider request after a completed call.
"""
from __future__ import annotations

import logging
import math
from functools import wraps
from typing import Any

from app.core.config import settings

logger = logging.getLogger(__name__)
_ROUTES = frozenset(("risk", "plan", "audit"))


def _sdk_symbols():
    from langfuse import Langfuse, observe

    return Langfuse, observe


def _route(value: str) -> str:
    return value if value in _ROUTES else "other"


class LangfuseTelemetry:
    def __init__(self, config: Any):
        self._client = None
        self._observe = None
        self.capture_content = config.LANGFUSE_CAPTURE_CONTENT is True
        # Do not let SDK defaults select another host/project for partial config.
        if not all(value.strip() for value in (
            config.LANGFUSE_PUBLIC_KEY, config.LANGFUSE_SECRET_KEY, config.LANGFUSE_HOST
        )):
            return
        try:
            client_type, observe = _sdk_symbols()
            self._client = client_type(
                public_key=config.LANGFUSE_PUBLIC_KEY,
                secret_key=config.LANGFUSE_SECRET_KEY,
                base_url=config.LANGFUSE_HOST.rstrip("/"),
                tracing_enabled=True,
            )
            self._observe = observe
        except Exception:
            # Exception messages can contain credentials or request content.
            logger.info("Langfuse telemetry unavailable; business calls remain enabled")

    def observe_generation(self, function):
        if self._client is None or self._observe is None:
            return function

        @wraps(function)
        async def wrapper(*args, **kwargs):
            entered = False
            completed = False
            result = None
            business_error = None

            @wraps(function)
            async def call_business(*call_args, **call_kwargs):
                nonlocal entered, completed, result, business_error
                entered = True
                try:
                    result = await function(*call_args, **call_kwargs)
                    completed = True
                    return result
                except Exception as error:
                    # Prevent @observe from exporting exception text, which can
                    # contain a provider response or sensitive student content.
                    business_error = error
                    self._update(level="ERROR", status_message="llm-call-failed")
                    return None

            try:
                observed = self._observe(
                    as_type="generation", capture_input=False, capture_output=False
                )(call_business)
                await observed(*args, **kwargs)
            except Exception:
                logger.debug("Langfuse observation failed; preserving business outcome")

            if business_error is not None:
                raise business_error
            if completed:
                return result
            # A decorator that failed before invoking the function is a no-op.
            # A completed provider call is never repeated for a telemetry error.
            if not entered:
                return await function(*args, **kwargs)
            return result

        return wrapper

    def _update(self, **fields):
        if self._client is None:
            return
        try:
            self._client.update_current_generation(**fields)
        except Exception:
            logger.debug("Langfuse generation update failed; ignored")

    def start_call(self, *, route, model, messages, cache_prompt):
        fields = {
            "name": f"llm.{_route(route)}",
            "model": model,
            "metadata": {"route": _route(route), "cache_prompt": bool(cache_prompt)},
        }
        if self.capture_content:
            fields["input"] = messages
        self._update(**fields)

    def record_response(self, *, route, response):
        try:
            timings = response.get("timings") or {}
            usage = response.get("usage") or {}
            input_tokens = max(0, int(timings.get("prompt_n") or usage.get("prompt_tokens") or 0))
            output_tokens = max(0, int(timings.get("predicted_n") or usage.get("completion_tokens") or 0))
            cached_tokens = max(0, int(timings.get("cached_n") or 0))
            prefill_ms = float(timings.get("prompt_ms") or 0.0)
            fields = {
                "usage_details": {"input": input_tokens, "output": output_tokens,
                                  "total": input_tokens + output_tokens},
                "metadata": {"route": _route(route), "cached_n": cached_tokens,
                             "prefill_ms": max(0.0, prefill_ms) if math.isfinite(prefill_ms) else 0.0},
            }
            if self.capture_content:
                choices = response.get("choices") or []
                fields["output"] = choices[0].get("message", {}).get("content") if choices else None
            self._update(**fields)
        except Exception:
            logger.debug("Langfuse response metrics unavailable; ignored")

    def shutdown(self):
        if self._client is None:
            return
        try:
            self._client.shutdown()  # SDK flushes its background queue on shutdown.
        except Exception:
            logger.debug("Langfuse shutdown failed; ignored")


telemetry = LangfuseTelemetry(settings)
