"""Provider-neutral OpenAI chat completion payload builder."""
from __future__ import annotations

from typing import Any, Dict, List


def build_chat_payload(
    *,
    model: str,
    messages: List[Dict[str, str]],
    temperature: float,
    max_tokens: int,
    cache_prompt_enabled: bool,
) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    if cache_prompt_enabled:
        payload["cache_prompt"] = True
    return payload
