"""
H-1.3：FastMCP knowledge-rag server 的 3 个 search tool。

工具命名规约（与 MCP_DESIGN.md §5 一致）：
- search_cases       → 教育介入案例库（settings.MILVUS_COLLECTIONS["case"]）
- search_policies    → 校规/教育政策（settings.MILVUS_COLLECTIONS["policy"]）
- search_psychology  → 心理学/咨询知识（settings.MILVUS_COLLECTIONS["psychology"]）

所有 tool 都是 async（FastMCP 2.x 支持），底层走 rag_pipeline.retrieve_from_collection。
"""
from typing import Any, Dict

from fastmcp import FastMCP

from app.core.config import settings
from app.mcp.rag_adapter import search_collection

mcp = FastMCP(name="knowledge-rag", version="1.0.0")


@mcp.tool(
    name="search_cases",
    description=(
        "检索教育介入案例库（edu_cases collection）。"
        "返回 top_k 条相似案例片段，含 chunk_id / title / content / 召回分数 / 可选 rerank 分数。"
        "用于 Agent Loop 评估学生风险或撰写介入建议时找历史可参考案例。"
    ),
)
async def search_cases(query: str, top_k: int = 5) -> Dict[str, Any]:
    return await search_collection(query=query, collection_key="case", top_k=top_k)


@mcp.tool(
    name="search_policies",
    description=(
        "检索校规/教育政策文档库（edu_policies collection）。"
        "返回 top_k 条政策片段，供 Agent 在生成处理建议时引用具体规章依据，避免凭空主张。"
    ),
)
async def search_policies(query: str, top_k: int = 5) -> Dict[str, Any]:
    return await search_collection(query=query, collection_key="policy", top_k=top_k)


@mcp.tool(
    name="search_psychology",
    description=(
        "检索心理学/咨询知识库（edu_psychology collection）。"
        "返回 top_k 条心理学条目，供 Agent 评估心理风险或给出介入策略时引用专业依据。"
    ),
)
async def search_psychology(query: str, top_k: int = 5) -> Dict[str, Any]:
    return await search_collection(query=query, collection_key="psychology", top_k=top_k)


def get_mcp() -> FastMCP:
    """供 server entrypoint 拿到已注册 tool 的 FastMCP 实例。"""
    return mcp


__all__ = ["mcp", "get_mcp", "search_cases", "search_policies", "search_psychology"]
