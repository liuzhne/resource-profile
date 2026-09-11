# MCP 选型决策（H-1.1）

> **目标**：定 Phase H 瘦身版（仅 student-data + knowledge-rag 两个 MCP server + 主 Agent Loop）的 SDK 版本与传输模式，作为 H-1.2 / H-1.3 / H-2 落地的地基。
>
> **依据**：IMPROVEMENT §0 拍板 "Spring AI MCP 主力 + Python FastMCP 补 RAG/Memory"。

---

## 1. 拍板结论

| 项 | 选定 | 理由 |
|----|------|------|
| Spring AI 版本 | **升到 `1.1.6` GA**（H-1.1.6 拍板，2026-05-20） | 1.0.x 仅支持 SSE/STDIO，无 Streamable HTTP；1.1.0 GA 起原生支持 streamable transport（spec 2025-03-26）。1.0→1.1 主线 API 完全兼容，零代码改动 |
| Java MCP server SDK | `spring-ai-starter-mcp-server-webmvc`（1.1.x artifactId 不变） | `@Tool` + `@ToolParam` 注解暴露方法，需用 `MethodToolCallbackProvider` 手动登记 |
| Python MCP server SDK | **FastMCP 2.x**（`fastmcp>=2.3,<3.0`） | 原生支持 `mcp.run(transport="http", ...)`；装饰器 API；与 LangChain/Milvus 集成自然 |
| 传输模式 | **统一 Streamable HTTP**（单端点 `/mcp`） | MCP spec 2025-03-26 推荐传输；SSE 已 deprecated；单端点同时承担 client→server POST + server→client GET/SSE 流式响应，ops 比双端点简单；跨语言/跨进程统一 |
| MCP client 集成 | `spring-ai-starter-mcp-client` + `spring.ai.mcp.client.streamable-http.*` 子配置（1.1.x） | 在 agent-service 配 server URL 列表，自动注册为 ChatClient 可用 tool（H-2 时再接入） |
| Agent Loop 选型 | Spring AI **`ChatClient` + `ToolCallback`**（M6 已有的 fluent API 在 GA 保留） | 无需引第二个 Agent 框架；Tool 调用循环由 Spring AI 内部处理 |

---

## 2. Spring AI 升级影响面（M6 → GA）

**已知不变**：
- `ChatClient.Builder` / `ChatClient.prompt().system().user().call().content()` 在 GA 里保留，签名兼容
- `OpenAiChatModel` / `OpenAiChatOptions` 保留
- `OpenAiApi` 构造签名 GA 有少量变化但 4-arg `(baseUrl, apiKey, RestClient.Builder, WebClient.Builder)` 形式仍可用

**已知会变**：
- 包路径 `org.springframework.ai.chat.client` 等没动
- 一些 internal class 重命名（`StructuredOutputConverter` 等），但我们没用到
- 自动配置类名 `OpenAiAutoConfiguration` GA 起改 `OpenAiChatAutoConfiguration` —— 影响 `AgentServiceApplication.java:11` 的 exclude 行，**升级时同步修**

**回归点**（升级 PR 必须 smoke 通过）：
1. `mvn -pl agent-service compile`
2. `RiskAnalyzeService.analyzeRisk` 端到端：发一次 `/agent/api/v1/task/trigger/{id}` 看 LLM 实际调用是否 200
3. `LlamaCppCachePromptInterceptor` + `LlmMetricsInterceptor` 仍被挂上（看 `/actuator/prometheus` 的 `educare_llm_*` 指标增长）
4. Langfuse trace 仍被推（看 Langfuse UI）

---

## 3. 传输模式：为什么选 Streamable HTTP

**MCP spec 演进**：
- 早期 spec 用 SSE 双端点（`/sse` 连接 + `/mcp/message` POST），但 spec 2025-03-26 已将 SSE 标记 **deprecated**
- 取而代之的 **Streamable HTTP**：单端点 `/mcp` 同时承担 client→server POST 与 server→client GET/SSE 流式响应
- Spring AI 1.1.0 GA（2025-11-15）原生支持，FastMCP 2.x 原生支持

**Streamable HTTP 相对 SSE 的优势**：
- **运维简化**：单端点替代双端点，反代/网关只需挂一条路由规则
- **协议一致**：spec 推荐路线，未来 SDK / inspector / cloud 生态都按此演进
- **能力对等**：仍走 HTTP，跨进程/跨容器/跨主机/跨语言完整支持
- **健康检查/重连/鉴权**：仍可走 HTTP 现成机制

**为什么不混用 stdio**：
- agent-service ↔ Python rag-server 必走网络，stdio 不可用
- Java student-data 也走 HTTP（端口 8094）与 Python rag-server（8095）形成对称结构
- docker-compose 里两个 server 都是普通 service，运维统一

**唯一退路**：Phase H 末期若性能不达标（每 tool call > 100ms），可把 student-data 改 stdio，但当前不预设。

---

## 4. 端口分配 + 配置草案

| 服务 | 端口 | 路径 |
|------|------|------|
| agent-service（MCP client） | 8087（已有） | — |
| student-data MCP server | **8094** | Streamable HTTP，单端点 `/mcp` |
| knowledge-rag MCP server | **8095** | Streamable HTTP，单端点 `/mcp` |

mcp-student-data `application.yml` 实际配置（H-1.1.6 落地）：
```yaml
spring:
  ai:
    mcp:
      server:
        name: student-data
        version: 1.0.0
        type: SYNC
        protocol: STREAMABLE
        streamable-http:
          mcp-endpoint: /mcp
```

agent-service `application.yml` 草案（H-2 接入时填）：
```yaml
spring:
  ai:
    mcp:
      client:
        streamable-http:
          connections:
            student-data:
              url: ${MCP_STUDENT_DATA_URL:http://localhost:8094}
              endpoint: /mcp
            knowledge-rag:
              url: ${MCP_KNOWLEDGE_RAG_URL:http://localhost:8095}
              endpoint: /mcp
```

---

## 5. 工具命名约定

| MCP Server | Tool name | Java/Python 实现 |
|-----------|-----------|------------------|
| student-data | `get_student_profile(student_id)` | Java |
| student-data | `get_academic_history(student_id)` | Java |
| student-data | `get_mental_indicators(student_id)` | Java |
| student-data | `get_attendance(student_id)` | Java |
| knowledge-rag | `search_cases(query, top_k)` | Python |
| knowledge-rag | `search_policies(query, top_k)` | Python |
| knowledge-rag | `search_psychology(query, top_k)` | Python |

**约定**：
- snake_case 命名（与 OpenAI tool calling 规约一致；Spring AI MCP 自动转）
- 所有参数显式 typed（避免 LLM 传错）
- 返回值始终是 JSON-serializable 的 dict / list（LLM 端解析稳）

---

## 6. H-1 子步分配（落进 EXECUTION_PLAN）

- **H-1.1（本步）** — 选型决策（本文件）
- **H-1.2** — student-data MCP server（Java，新模块 `backend/mcp-student-data/` 或在 agent-service 内嵌）
- **H-1.3** — knowledge-rag MCP server（Python，`ai-inference-service/mcp/rag/` 子包）
- **H-1.4** — `mcp-smoke-test.sh`：从 `mcp-inspector` CLI 或 Claude Desktop 直连两 server 跑 happy path

---

## 7. 风险与折中

| 风险 | 处理 |
|------|------|
| Spring AI GA 与 M6 不兼容引发 ChatClient regression | 升级单独 PR，附 smoke checklist 见 §2；通过后再开 H-1.2 |
| Spring AI MCP starter 在 GA 才稳，可能仍有 1.0.x patch 漂移 | 锁定具体小版本而非 `1.0.+`；每次升级跑 eval（G-6） |
| Python FastMCP 与 langchain-openai 0.0.8 同 venv 依赖冲突 | requirements.txt 升前先 `pip-compile` 解依赖；冲突大时把 rag-server 拆独立 venv（独立容器即可） |
| MCP 协议本身仍在演进（spec 版本） | 锁定 spec version（在客户端配置声明），server SDK 升级时跑 H-1.4 smoke |
| 端口 8094/8095 与未来其他服务冲突 | 加进 `docs/educare/deploy.md` 端口表（H-1.2 落地时同步） |

---

## 8. 不在本文件范围（明确推后）

- **MCP 鉴权**：v1 spec 鉴权为可选，本期 server 仅监听 docker 内网，不开 auth；公网暴露时再加 OAuth/PAT
- **Tool sandboxing**：所有 tool 当前都是只读查询，不需要审批；写入型 tool（如 intervention-server 的 `assign_to_counselor`）留 H 后续阶段
- **Memory MCP server**：原 H-5 任务，瘦身后 "视进度"，本文件不涉及

---

## 9. 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-05-14 | 初版 | H-1.1 选型决策 |
| 2026-05-19 | 校准 1.0.0 GA 事实：starter artifactId 改 `spring-ai-starter-mcp-server-webmvc` / `spring-ai-starter-mcp-client-webmvc`；明确 `@Tool` + `@ToolParam` 与 `MethodToolCallbackProvider` 手动登记模式；SSE 端点澄清为 `/sse`（连接）+ `/mcp/message`（消息） | H-1.2 落地实测过程中订正 |
| 2026-05-20 | **H-1.1.6 升级 + 协议切换**：Spring AI 1.0.0 → 1.1.6 GA；MCP transport 全栈从 SSE 切到 **Streamable HTTP**（单端点 `/mcp`）；§1 拍板表（Spring AI 版本 / Python SDK 改 `fastmcp>=2.3` / 传输模式）、§3 整段（删"为什么不混用 SSE/stdio"换"为什么选 Streamable HTTP"）、§4 端点表与 yml 草案三处同步更新 | MCP spec 2025-03-26 已将 SSE 标记 deprecated，Streamable HTTP 为推荐传输。1.0→1.1 实测主线 API 完全兼容（`@Tool`/`@ToolParam`/`MethodToolCallbackProvider`/`OpenAiApi.builder()`/`OpenAiChatModel.builder()`/`OpenAiChatAutoConfiguration` 全保留），代码零改动；MCP server 仅 application.yml 三行配置切换。Streamable HTTP 实际 property key 为 `spring.ai.mcp.server.protocol=STREAMABLE` + `spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp`（通过 `spring-ai-autoconfigure-mcp-server-common-1.1.6.jar` 的 `spring-configuration-metadata.json` 核实） |
| 2026-05-20 | **H-1.3 knowledge-rag MCP server 落地**：抽 `ai-inference-service/app/services/rag_pipeline.py` 单库检索管线；新增 `app/mcp/` 模块（`tools.py` 3 个 `@mcp.tool` + `rag_adapter.py` 入参清洗 + `knowledge_rag_server.py` 启动入口）；`requirements.txt` 加 `fastmcp>=2.3,<3.0`；`app/core/config.py` 加 `MCP_KNOWLEDGE_RAG_PORT=8095` + `MCP_KNOWLEDGE_RAG_PATH=/mcp` | 与 H-1.2 student-data（Java/8094）对称：独立进程 + Streamable HTTP 单端点；embedding/milvus/reranker/redis client 全复用，既有 `/api/v1/rag/retrieve` 路由不动（其多源聚合语义与 MCP 单源 tool 不重合，无强抽公共层必要）。`top_k` 上限硬裁剪到 20 防 LLM 传超大值 |
