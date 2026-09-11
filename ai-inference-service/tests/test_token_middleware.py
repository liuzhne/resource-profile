"""McpTokenMiddleware 纯 ASGI 单测（无 fastmcp/starlette 依赖，纯逻辑）。"""
import unittest

from app.mcp.token_middleware import McpTokenMiddleware


class _DownstreamApp:
    """记录是否被调用的假下游 ASGI app。"""
    def __init__(self):
        self.called = False

    async def __call__(self, scope, receive, send):
        self.called = True
        await send({"type": "http.response.start", "status": 200, "headers": []})
        await send({"type": "http.response.body", "body": b"ok"})


def _http_scope(token_header=None):
    headers = []
    if token_header is not None:
        headers.append((b"x-mcp-token", token_header.encode()))
    return {"type": "http", "headers": headers}


async def _noop_receive():
    return {"type": "http.request"}


class TokenMiddlewareTest(unittest.IsolatedAsyncioTestCase):

    async def _run(self, middleware, scope):
        sent = []
        async def send(msg):
            sent.append(msg)
        await middleware(scope, _noop_receive, send)
        return sent

    async def test_token_set_missing_header_rejected_401(self):
        down = _DownstreamApp()
        mw = McpTokenMiddleware(down, token="secret")
        sent = await self._run(mw, _http_scope(None))
        self.assertFalse(down.called)
        self.assertEqual(sent[0]["status"], 401)

    async def test_token_set_wrong_header_rejected_401(self):
        down = _DownstreamApp()
        mw = McpTokenMiddleware(down, token="secret")
        sent = await self._run(mw, _http_scope("nope"))
        self.assertFalse(down.called)
        self.assertEqual(sent[0]["status"], 401)

    async def test_token_set_correct_header_passes(self):
        down = _DownstreamApp()
        mw = McpTokenMiddleware(down, token="secret")
        await self._run(mw, _http_scope("secret"))
        self.assertTrue(down.called)

    async def test_empty_token_passes_through(self):
        down = _DownstreamApp()
        mw = McpTokenMiddleware(down, token="")
        await self._run(mw, _http_scope(None))
        self.assertTrue(down.called)

    async def test_non_http_scope_passes_even_with_token(self):
        # lifespan 等非 http scope 必须透传，否则 MCP session manager 起不来
        down = _DownstreamApp()
        mw = McpTokenMiddleware(down, token="secret")
        await self._run(mw, {"type": "lifespan"})
        self.assertTrue(down.called)


if __name__ == "__main__":
    unittest.main()
