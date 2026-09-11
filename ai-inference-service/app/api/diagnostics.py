"""G-1.6：诊断端点，暴露 LLM 调用的 slot-cache 命中率与吞吐指标。

读取的是 `app.services.llm_metrics` 进程内累加的 snapshot；
重启服务会清零。Phase G-3 将把同样的指标接到 Micrometer/Prometheus，届时
这个端点继续保留作为快速人工排查入口。
"""
from typing import Any, Dict

from fastapi import APIRouter

from app.services.llm_metrics import snapshot

router = APIRouter(prefix="/api/v1/diagnostics", tags=["diagnostics"])


@router.get("/llm-metrics")
async def llm_metrics() -> Dict[str, Any]:
    """返回 LLM 调用累计指标。

    关键字段：
    - `cache_hit_rate`: 总命中率 (cached_tokens / prompt_tokens)，0.0–1.0
    - `by_route.<risk|plan|audit>.cached_tokens / prompt_tokens`: 各端点分项
    """
    return snapshot()
