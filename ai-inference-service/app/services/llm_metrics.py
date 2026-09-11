"""
G-1.5：LLM 调用指标（进程内内存累加，无锁安全）。

llama.cpp 在 OpenAI-compatible chat/completions 响应里附加 root.timings：
  {"prompt_n": N_总prompt_token,
   "cached_n": N_命中cache的token,  # 越大越好；首次调用 ≈ 0
   "prompt_ms": prefill_耗时_毫秒,
   "predicted_n": 解码出的_token, ...}

如果 cached_n / prompt_n 接近 1，说明 slot prefix cache 命中良好。

Phase G-3 会把这些字段接到 Micrometer Prometheus；当前先用 /diagnostics
端点裸读。
"""
from __future__ import annotations

import logging
import threading
from typing import Any, Dict

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_metrics: Dict[str, Any] = {
    "total_calls": 0,
    "total_prompt_tokens": 0,
    "total_cached_tokens": 0,
    "total_completion_tokens": 0,
    "total_prefill_ms": 0.0,
    "by_route": {},  # route -> { calls, prompt_tokens, cached_tokens }
}


def record_llm_response(route: str, response_json: Dict[str, Any]) -> None:
    """从 llama.cpp 原始 JSON 响应抽 timings + usage 入 metric。

    被 agent.py 的 _call_llm_json 在每次成功调用后调用。
    """
    timings = response_json.get("timings") or {}
    usage = response_json.get("usage") or {}

    prompt_n = int(timings.get("prompt_n") or usage.get("prompt_tokens") or 0)
    cached_n = int(timings.get("cached_n") or 0)
    predicted_n = int(timings.get("predicted_n") or usage.get("completion_tokens") or 0)
    prompt_ms = float(timings.get("prompt_ms") or 0.0)

    with _lock:
        _metrics["total_calls"] += 1
        _metrics["total_prompt_tokens"] += prompt_n
        _metrics["total_cached_tokens"] += cached_n
        _metrics["total_completion_tokens"] += predicted_n
        _metrics["total_prefill_ms"] += prompt_ms

        route_metrics = _metrics["by_route"].setdefault(
            route, {"calls": 0, "prompt_tokens": 0, "cached_tokens": 0}
        )
        route_metrics["calls"] += 1
        route_metrics["prompt_tokens"] += prompt_n
        route_metrics["cached_tokens"] += cached_n

    hit_rate = (cached_n / prompt_n) if prompt_n else 0.0
    logger.info(
        "llm.metric route=%s prompt_tokens=%d cached_tokens=%d hit_rate=%.1f%% prefill_ms=%.1f",
        route, prompt_n, cached_n, hit_rate * 100, prompt_ms,
    )


def snapshot() -> Dict[str, Any]:
    with _lock:
        total_prompt = _metrics["total_prompt_tokens"]
        total_cached = _metrics["total_cached_tokens"]
        return {
            "total_calls": _metrics["total_calls"],
            "total_prompt_tokens": total_prompt,
            "total_cached_tokens": total_cached,
            "total_completion_tokens": _metrics["total_completion_tokens"],
            "total_prefill_ms": _metrics["total_prefill_ms"],
            "cache_hit_rate": (total_cached / total_prompt) if total_prompt else 0.0,
            "by_route": {k: dict(v) for k, v in _metrics["by_route"].items()},
        }
