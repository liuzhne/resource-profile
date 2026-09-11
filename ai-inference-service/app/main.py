from contextlib import asynccontextmanager

from fastapi import FastAPI
from starlette.middleware.cors import CORSMiddleware

from app.api import agent, diagnostics, health, llm, rag, rag_upsert
from app.core.config import settings
from app.core.lifespan import chain_lifespans

@asynccontextmanager
async def lifespan(app: FastAPI):
    print(f"   {settings.APP_NAME} v{settings.APP_VERSION} 已启动")
    print(f"   LLM Base URL: {settings.LLM_BASE_URL}")
    print(f"   Milvus Host:  {settings.MILVUS_HOST}:{settings.MILVUS_PORT}")

    yield

    print(f"  {settings.APP_NAME} 正在关闭...")

def create_app() -> FastAPI:
    # FastMCP's StreamableHTTPSessionManager is initialized by the lifespan
    # exposed from http_app(). Build it before the parent FastAPI application so
    # both lifespans can be registered together.
    mcp_app = None
    app_lifespan = lifespan
    try:
        from starlette.middleware import Middleware
        from app.mcp.tools import get_mcp
        from app.mcp.token_middleware import McpTokenMiddleware

        mcp = get_mcp()
        mcp_app = mcp.http_app(
            path="/",
            transport="http",
            middleware=[Middleware(McpTokenMiddleware, token=settings.MCP_TOKEN)],
        )
        app_lifespan = chain_lifespans(lifespan, mcp_app.lifespan)
    except Exception as e:
        print(f"Warning: Failed to prepare MCP endpoint: {e}")

    app = FastAPI(
        title=settings.APP_NAME,
        version=settings.APP_VERSION,
        description="EduCare AI 推理服务 - llama.cpp & milvus",
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=app_lifespan,
    )

    # 跨域（A4）：收敛为显式白名单 + 关 credentials（消除 *+credentials 非法组合）。
    cors_origins = [o.strip() for o in settings.CORS_ALLOW_ORIGINS.split(",") if o.strip()]
    app.add_middleware(
        CORSMiddleware,
        allow_origins=cors_origins,
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # 注册路由
    app.include_router(health.router)
    app.include_router(llm.router)
    app.include_router(agent.router)
    app.include_router(rag.router)
    app.include_router(diagnostics.router)
    # 注册 MCP Streamable HTTP 协议端点 (/mcp)
    if mcp_app is not None:
        app.mount("/mcp", mcp_app)

    return app

app = create_app()
