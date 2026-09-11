"""
A3 纵深防御：knowledge-rag MCP server(8095) 内部预共享 token 校验。

纯 ASGI 中间件（不依赖 fastmcp）：token 非空时，校验请求头 X-MCP-Token，不匹配即 401；
token 为空（默认）则透明放行，仅靠 127.0.0.1 网络隔离。

为何用纯 ASGI 而非 Starlette BaseHTTPMiddleware：MCP Streamable HTTP 是流式响应，
BaseHTTPMiddleware 会缓冲 body 干扰流式；纯 ASGI 直接 await 下游、零缓冲，保流式语义。
与 agent-service McpClientConfig 附的 X-MCP-Token 头配对（设同一 EDUCARE_MCP_TOKEN 即启用）。
"""
from typing import Any, Awaitable, Callable

HEADER_NAME = b"x-mcp-token"


class McpTokenMiddleware:
    def __init__(self, app: Callable, token: str = "") -> None:
        self.app = app
        self.token = (token or "").strip()

    async def __call__(self, scope: dict, receive: Callable[[], Awaitable[Any]],
                       send: Callable[[dict], Awaitable[None]]) -> None:
        if self.token and scope.get("type") == "http":
            headers = dict(scope.get("headers") or [])
            presented = headers.get(HEADER_NAME, b"")
            if isinstance(presented, bytes):
                presented = presented.decode("latin-1")
            if presented != self.token:
                await send({
                    "type": "http.response.start",
                    "status": 401,
                    "headers": [(b"content-type", b"application/json")],
                })
                await send({
                    "type": "http.response.body",
                    "body": b'{"code":401,"message":"invalid or missing X-MCP-Token"}',
                })
                return
        await self.app(scope, receive, send)
