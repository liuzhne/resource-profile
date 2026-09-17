# Resource-Profile 架构与工程决策

> 最近更新：2026-09-16
> 记录范围：当前仍有效的项目级决策。历史阶段细节见 [`docs/educare/EXECUTION_PLAN.md`](./docs/educare/EXECUTION_PLAN.md) §6。

每条记录包含背景、选择、放弃方案和后果。被替换的决策不得直接删除，应改为“已取代”并链接新决策。

## ADR-001：业务微服务使用 Spring Boot + Nacos

- 状态：已采纳
- 背景：用户、教师、学生、心理、统计和 AI 编排有不同数据访问与发布节奏。
- 选择：Java 17、Spring Boot 3.2.5、Spring Cloud 2023.0.1、Nacos 服务发现/可选配置；gateway 按 `/auth`、`/user`、`/teacher`、`/student`、`/mental`、`/data`、`/agent` 路由。
- 原因：保持领域边界，复用 Spring/MyBatis 生态，并允许 Agent 通过 Feign 组合已有服务。
- 放弃方案：单体应用会把心理、用户和 AI 发布周期绑在一起；服务间共享 mapper 会绕过授权和领域所有权。
- 后果：本地完整联调必须启动多个进程并保证 Nacos 可达；跨服务契约需要控制器/Feign/MCP 契约测试。

## ADR-002：Java 负责业务编排，Python 负责模型与向量能力

- 状态：已采纳
- 背景：业务状态机、权限和 MySQL 事务更适合现有 Java 栈；LLM、embedding、Milvus 与 FastMCP 的 Python 生态更成熟。
- 选择：`agent-service` 拥有任务状态与业务编排；`ai-inference-service` 提供风险/方案/审核、RAG、知识 upsert；Python 还以独立进程提供 knowledge-rag MCP。
- 放弃方案：把全部 AI 逻辑移入 Java 会复制 Python 向量生态；把业务状态机移入 Python 会削弱现有鉴权、MyBatis 和服务治理的一致性。
- 后果：必须维护 Java↔Python HTTP 契约；Python 故障需要明确 fallback，不能让合规审核默认放行。

## ADR-003：敏感数据默认使用本地 OpenAI 兼容模型

- 状态：已采纳
- 背景：学生画像包含未成年人身份、学业和心理数据，同时项目需要可控成本与离线演示。
- 选择：生成 LLM、embedding、reranker 运行在宿主 :8091/:8092/:8093；Java Spring AI 与 Python LangChain/httpx 都使用 OpenAI 兼容接口。
- 放弃方案：云端 ModelRouter 已在瘦身中删除；直接绑定某个闭源 SDK 会增加数据外发与供应商锁定。
- 后果：部署方负责模型权重、GPU 和启动脚本；真实模型稳定性必须在 R-5 验证，桩模型只能证明机械链路。

## ADR-004：AgentLoop ReAct 是默认路径，legacy 四阶段作为回退

- 状态：已采纳
- 背景：固定 risk→rag→plan 流水线可控但工具选择僵硬；完全隐式原生 tool-calling 又不利于观察 thought/action/observation 和精确测试。
- 选择：`EDUCARE_AGENT_LOOP_ENABLED=true` 默认走最多 8 轮 ReAct JSON；模型可调用 7 个 MCP 工具并输出双 JSON `final_answer`。中高风险仍复用独立合规审核。`false` 回落 legacy。
- 放弃方案：删除 legacy 会失去回退与对比基线；默认 native tool-calling 会降低细粒度可观测性。native 保留为可选协议，但必须具有同等 ToolGuard、validator 和 hooks 语义。
- 后果：prompt、工具名和 final schema 都是稳定契约；解析失败、工具失败或校验失败不能伪装成 COMPLETED。

## ADR-005：只保留两个 MCP server，并使用 Streamable HTTP

- 状态：已采纳
- 背景：Agent 需要读取业务数据和检索知识，但过多 MCP 服务会提高运维成本。
- 选择：保留 Java `student-data` :8094（4 个只读工具）和 Python `knowledge-rag` :8095（3 个检索工具）；采用 Spring AI 1.1.6/FastMCP 的 Streamable HTTP `/mcp`。
- 放弃方案：旧 SSE transport 已被新协议替换；memory-server 因未接入主链且投入产出低被删除；把 MCP 全收回内联会失去清晰工具边界和独立契约展示。
- 后果：agent-service 启动时会初始化两端工具清单，MCP 不可达可导致启动失败；生产必须配置共享 MCP token 和网络隔离。

## ADR-006：RAG 使用 Milvus dense 检索，可选 reranker

- 状态：已采纳
- 背景：当前知识库规模有限，已有 Milvus 2.4 与 BGE embedding 服务。
- 选择：1024 维 dense recall，候选扩大后可用 BGE reranker 精排；集合为 cases/psychology/policies/success。
- 放弃方案：Hybrid Retrieval/BM25 已删除，因为默认长期未启且在当前规模下过度工程；Elasticsearch 增加重型基础设施；迁移 pgvector 会破坏已有 Milvus 灌库链。
- 后果：专有名词检索能力取决于 embedding/reranker 质量；必须以 R-5.3 的真实语料 baseline 判断是否需要重新引入 lexical 检索。

## ADR-007：MySQL 是业务事实源，Redis 与 Milvus 可重建

- 状态：已采纳
- 背景：任务、反馈和用户数据需要事务与备份；缓存、锁、会话和向量索引具有不同恢复方式。
- 选择：MySQL 保存业务记录；Redis 保存会话、幂等、锁和缓存；Milvus 保存从知识源派生的向量。
- 放弃方案：把任务状态只放 Redis 无法可靠审计；把源文档事实只放 Milvus 难以恢复和版本化。
- 后果：生产必须备份/演练恢复 MySQL；Redis 丢失要求用户重登；Milvus 可通过 upsert 重新构建。

## ADR-008：鉴权采用多层 fail-closed 防线

- 状态：已采纳
- 背景：系统处理学生心理和身份敏感信息，单靠前端菜单或网关网络边界不足以防 IDOR 与直连绕过。
- 选择：gateway JWT+Redis 会话；下游 `AccessGuard` 对象/角色授权；`FieldPermissionAdvice` 字段过滤；agent-service 自鉴权；MCP 共享 token；LLM 前脱敏与 Prompt 清洗。
- 放弃方案：只依赖前端权限属于 UX 控制；只验证 JWT 而不校验 Redis 无法立即吊销；明文信任网关身份头更易伪造。
- 后果：Redis 鉴权故障返回 503 而不是放行；新增接口必须同时补准入、对象授权和字段权限测试。

## ADR-009：使用 llama.cpp `cache_prompt` 与字节稳定 prompt

- 状态：已采纳
- 背景：本地大模型重复 system prompt 成本高，Spring AI 通用 cache metadata 与 llama.cpp 扩展并不等价。
- 选择：Java HTTP interceptor 和 Python raw chat 请求显式注入 `cache_prompt:true`；system prompt 放资源文件并保持字节稳定。
- 放弃方案：依赖不存在/不匹配的 Spring AI `cache_control` 无法保证 llama.cpp slot cache 命中；运行时拼接不稳定前缀会降低命中率。
- 后果：修改固定 prompt 必须重新测缓存；真实模型目标命中率 ≥80%，属于 R-5.4。

## ADR-010：Langfuse 自托管 v2，可选且 fail-soft

- 状态：已采纳
- 背景：需要观察 `agent.loop` 和每次 `llm.chat`，但开发/测试不能硬依赖 trace 平台。
- 选择：compose profile 提供 Langfuse v2 + PostgreSQL；Python 使用 v2 SDK，Java 直接调用 ingestion HTTP；key 为空或上报失败时主流程继续。
- 放弃方案：v3 需要 ClickHouse、Redis、worker，对当前规模过重；非官方 Java SDK增加维护风险；Langfuse v2 不采用 OTel 路线。
- 后果：trace 不是业务事务的一部分；“已接代码”不等于“活 trace 已验收”，R-5.1 完成前必须标待验证。

## ADR-011：CI 采用全测试 + 安全关键类定向覆盖率门

- 状态：已采纳
- 背景：历史模块总体覆盖率不均，立即设置全局 80% 会产生大量与风险不成比例的阻塞。
- 选择：Java 全模块 `clean test`，并对 Auth/JWT/Prompt/用户教师授权/MCP token 等已补测类设置行覆盖率 ≥80%；Python 无外部服务 ASGI/unittest；前端 lint/build/size/audit；eval 数据集无条件校验，真 LLM 阈值按环境开启。
- 放弃方案：只跑受影响模块易漏跨模块安全回归；当前直接设置全局高覆盖率会把补历史测试与功能变更绑死。
- 后果：新增安全关键类应进入 JaCoCo includes；长期仍应逐步提高全局覆盖，而不是把定向门当终点。

## ADR-012：生产只公开 nginx，密钥由 preflight 硬门

- 状态：已采纳
- 背景：gateway、Agent、MCP、数据库和观测面板不应直接暴露公网，开发默认密码不能进入生产。
- 选择：nginx 80/443 做 TLS、SPA 和 `/api` 反代；其余 published ports 绑定 `127.0.0.1`；`docker/.env` 不入库；preflight 拒绝占位/弱 JWT/弱 Redis/MCP 凭据。
- 放弃方案：直接公开 gateway 或 MCP 会扩大绕过面；仅在文档“建议改密”无法形成可执行门禁。
- 后果：证书、密钥和默认账号轮换是发布前置条件；不得用 `docker compose down -v` 做常规回滚。

## ADR-013：上线声明以 R-5/R-6 证据为准

- 状态：已采纳
- 背景：代码测试已覆盖大量机械链路，但真实 Qwen/BGE/Langfuse 与完整生产 compose 尚未全部形成当前证据；基础 compose 还未声明六个普通业务服务；`rag_upsert` 的测试 app 手工注册了 router，而生产 `create_app()` 当前没有注册它。
- 选择：在 R-5/R-6 全部勾选前，只能称“代码侧完成/待环境验收”，不得称“完整生产已上线”。
- 放弃方案：用桩 LLM、单测或部分 compose 健康代替真实模型/全栈签字会掩盖部署缺口。
- 后果：发布负责人必须按 [`RUNBOOK.md`](./RUNBOOK.md) 收集 CI、模型、RAG、trace、安全、备份恢复和监控证据；HTTP upsert 在主应用实际暴露并验收前不得计入完成面。

## ADR-014：修复方案必须同步三份项目文档

- 状态：已采纳
- 背景：历史文档同时存在保留能力与已删除能力的描述，修复若只改代码会继续制造操作和架构认知偏差。
- 选择：只要提出或落地缺陷修复，同一变更必须维护 `ARCHITECTURE.md`、`DECISIONS.md`、`RUNBOOK.md`；即使无架构影响也要留下同标识记录。
- 放弃方案：只在 commit/PR 描述记录无法为后续本地任务提供稳定上下文；只更新“受影响的一份”容易漏掉验证或取舍。
- 后果：三份文档是修复完成定义的一部分。纯格式/拼写机械修改不触发该规则。

## ADR-015：Render 预览部署使用显式服务 URL，持久数据层不伪装成免费 Web Service

- 状态：部分已采纳；数据层方案待部署方确认
- 日期/修复标识：2026-09-01 / RENDER-DEPLOY-20260901
- 背景：Render Blueprint 已关闭 Nacos，但 agent-service 与 mcp-student-data 的 Feign client 仍只配置
  服务名，MCP 服务启动卡在 load-balancer 创建阶段；Agent 又以 20 秒默认窗口初始化两个会冷启动的
  免费 MCP 服务。线上合成登录探测同时证明 `edu-portrait-mysql.onrender.com:3306` 返回 JDBC connect
  timeout，因为 Web Service 的公网入口不是任意 TCP 代理。
- 选择：无注册中心部署为 Feign client 注入显式 HTTPS URL；Agent 保留 MCP 启动 fail-fast，但 Render
  预览环境把请求窗口扩到 120 秒。MySQL 只能选择付费 Private Service + `/var/lib/mysql` 持久盘，
  或部署方提供外部托管 MySQL；Redis 改用 Render Key Value 或外部托管 Redis。未获得计费授权前不
  自动创建付费数据库，也不把当前红灯掩盖成“已上线”。
- 原因：显式 URL 与当前关闭 Nacos 的事实一致，120 秒覆盖 Render 官方说明的 50 秒以上冷启动；
  持久数据层必须使用能接收对应协议且能保存数据的产品形态。
- 放弃方案：关闭 MCP client/延迟到业务请求只会让 Agent 表面启动而运行时缺工具；继续使用
  `*.onrender.com:3306/6379` 会稳定超时；把 MySQL 塞进免费 Web Service 即使偶尔启动也会在重启或
  部署时丢数据；未经确认直接升级计费计划不符合变更授权边界。
- 后果：免费预览的首次请求仍可能较慢；生产发布需要数据库/Redis 连接信息、模型供应商三元组
  `LLM_BASE_URL/LLM_MODEL/LLM_API_KEY` 与 RAG 基础设施。MySQL 镜像若被采用，会按顺序执行
  `sql/init/01`~`05` 全部脚本。
- 证据：Render 失败部署 `dep-daaie3ffdruc73ajt8pg`、`dep-daaiic5g1s2s73d9t920`；2026-09-01
  gateway `/actuator/health` 返回 `UP`，合成不存在账号请求返回 `CannotGetJdbcConnectionException`，
  auth 日志底层为 `SocketTimeoutException: Connect timed out`。本地 JDK 17 定向 Reactor 125 例与
  前端构建通过；修复后的云端复验待完成。

## ADR-016：Render 免费预览采用 Aiven MySQL，并在未配置供应商时关闭 AI 启动依赖

- 状态：已采纳；云端凭据与数据导入待完成
- 日期/修复标识：2026-09-01 / AIVEN-RENDER-20260901
- 背景：部署方已选择 Aiven Free MySQL，并明确暂不配置 LLM 供应商。原 Blueprint 把 MySQL 和
  Redis 作为普通 Free Web Service 暴露，二进制协议不可达；Agent 还会在启动时初始化 MCP，从而让
  尚未配置的 AI 依赖阻塞整套系统。
- 选择：关系库使用外部 Aiven Free MySQL，JDBC 强制 `sslMode=REQUIRED`；七个连接池上限统一为 3、
  最小空闲为 0。Redis 会话改用 Render Free Key Value 的私网连接串。Aiven 凭据仅在 Render 环境组
  手工维护，`render.yaml` 不包含连接秘密。无 LLM 阶段关闭 Spring AI MCP client 和 AgentLoop，
  前端构建时不注册 AI 预警/LLM 追踪路由。
- 原因：Aiven 提供真正的 MySQL 协议、持久存储与 TLS，能被 Render 公网出站连接；小连接池适配免费
  实例连接上限。Render Key Value 与 Spring Data Redis 协议兼容，且其私网连接串可以由 Blueprint
  自动注入。显式关闭 AI 比保留一个必然报错的入口更符合当前可用能力。
- 放弃方案：继续使用 Render Web Service 承载 MySQL/Redis 已被线上 timeout 证伪；Render 付费私有
  MySQL 需要计费授权；TiDB 不是原生 MySQL且需要额外兼容验证；用假 LLM key 或 mock 端点会把演示
  能力误报成真实供应商能力。
- 后果：Aiven Free 单节点没有生产 SLA；Render Free Key Value 重启会清空会话，用户需重新登录。
  AI 页面在重新构建前不可见，直连 Agent API 也不作为当前验收面。恢复 AI 时必须同时配置
  `LLM_BASE_URL/LLM_MODEL/LLM_API_KEY`、可达 MCP/RAG 服务，将两个开关打开并重建前端。
- 证据：[`render.yaml`](./render.yaml)、七个服务的 `application.yml` 与
  [`frontend/src/router/index.js`](./frontend/src/router/index.js)；JDK 17 Reactor 177 例、前端
  `VITE_AI_ENABLED=false` 生产构建和 YAML 语法解析已通过。Aiven 实例创建、SQL `01`~`05` 导入、
  Render Blueprint 同步与线上登录仍待验证。

## ADR-017：Render 健康检查必须复用 FastAPI 已注册路由

- 状态：已采纳
- 日期/修复标识：2026-09-01 / AI-HEALTH-20260901
- 背景：`ai-inference-service` 已在 Render 启动并监听 8090，但 Blueprint 探测
  `/api/v1/health`；生产 app 实际直接注册 `health.router`，公开路径是 `/health`，连续 404 使部署
  长期停留在 Deploying。
- 选择：将 `edu-portrait-ai-inference` 的 `healthCheckPath` 改为 `/health`，不为部署探针额外复制
  一个别名路由。
- 原因：健康检查应以运行应用现有的公开契约为准；单一路径避免文档、测试与部署配置继续漂移。
- 放弃方案：在 FastAPI 增加 `/api/v1/health` 兼容别名会扩大无业务价值的 API 面；继续等待无法让
  固定 404 自愈；关闭健康检查会掩盖进程不可用。
- 后果：无架构和业务行为变化；后续修改 health router 前必须同步 Render 探针与 RUNBOOK 验证命令。
- 证据：Render 部署 `dep-dabdn8jtqb8s73fjjsh0` 日志显示 Uvicorn 启动完成、随后
  `GET /api/v1/health` 持续 404；[`app/api/health.py`](./ai-inference-service/app/api/health.py) 声明
  `prefix="/health"`。修复后云端复验待完成。

## ADR-018：登录客户端不复制服务端密码长度策略

- 状态：已采纳
- 日期/修复标识：2026-09-02 / LOGIN-VALIDATION-20260902
- 背景：线上登录页预填并展示默认账号 `admin/admin`，数据库与 `auth-service` 均接受该账号，但前端
  将密码最小长度硬编码为 6，导致五字符默认密码无法提交，线上浏览器验收被客户端校验阻断。
- 选择：登录表单只校验用户名和密码非空，不在客户端限定已有账号的密码长度；凭据正确性与账号策略
  继续由 `auth-service` 判定。
- 原因：登录是既有凭据的验证入口，不是设置新密码的入口；复制一份不同步的长度策略会拒绝服务端合法
  账号。新密码强度约束应放在注册、重置或修改密码流程，并由服务端作为最终权威。
- 放弃方案：把前端下限从 6 改成 5 仍会形成第二套易漂移策略；把默认管理员密码改成六位以上会影响
  已初始化数据库与验收账号，且不能解决其他历史合法密码被客户端误拒绝的问题。
- 后果：任意非空密码都可提交到认证接口，但错误凭据仍由服务端拒绝；真实使用前仍应轮换默认账号密码。
- 证据：[`frontend/src/views/login/index.vue`](./frontend/src/views/login/index.vue)；线上浏览器修复前显示
  “密码长度不能少于6位”，后端网关验收脚本使用同一默认账号登录成功；2026-09-02 前端 lint 0 error
  （1 条既有 Prettier warning）且生产构建通过。修复后的线上复验待完成。

## ADR-019：Render 免费预览使用 GroqCloud，并显式隔离 llama.cpp 扩展字段

- 状态：已采纳
- 日期/修复标识：2026-09-02 / GROQ-CLOUD-20260902
- 背景：部署方选择 GroqCloud Free 作为在线 LLM。现有 Java interceptor 与 Python raw client 无条件
  注入 llama.cpp 专用 `cache_prompt`，标准 OpenAI 兼容供应商可能拒绝未知字段；Java 与 Python client
  对 base URL 是否包含 `/v1` 的约定也不同。
- 选择：增加 `LLM_CACHE_PROMPT_ENABLED`（默认 true，保留本地 llama.cpp 行为），Render Groq 模式
  设为 false；Java 显式使用 `https://api.groq.com/openai`，Python 使用
  `https://api.groq.com/openai/v1`，两侧使用同一 Groq 模型。API Key 只保存在 Render secret 配置；共享
  环境组作为集中来源，若存在服务级同名变量则必须删除或保持一致。
- 原因：用显式能力开关兼容本地与云端供应商，避免按域名硬编码判断；服务专属 base URL 避免修改两个
  client 的既有路径拼装契约。
- 放弃方案：让 Groq 忽略未知字段缺少契约保证；删除 `cache_prompt` 会损失本地 llama.cpp 已验收能力；
  把 API Key 写入 Blueprint 会泄露持久凭据。
- 后果：Render 恢复 AgentLoop、MCP client 与前端 AI 路由；免费供应商受配额和可用性约束。未部署
  Milvus/BGE 时 knowledge-rag 仍降级为空结果，不能把 LLM 接通等同于完整 RAG 上线。
- 证据：[`render.yaml`](./render.yaml)、[`SpringAiConfig.java`](./backend/agent-service/src/main/java/com/edu/agent/config/SpringAiConfig.java)、
  [`llm_client.py`](./ai-inference-service/app/services/llm_client.py)；2026-09-02 Java/Python 各 2 项兼容
  测试、Python 语法检查与前端生产构建通过，云端验收待完成。

## ADR-020：knowledge-rag MCP 使用独立的规范 endpoint

- 状态：已采纳
- 日期/修复标识：2026-09-02 / MCP-TRAILING-SLASH-20260902
- 背景：Render 首次开启双 MCP client 后，student-data 完成 initialize，但 FastAPI 中挂载的
  knowledge-rag 对 `POST /mcp` 和 `GET /mcp` 返回 307 到 `/mcp/`；Spring AI 的 Streamable HTTP
  transport 未将该重定向当作 MCP 响应，agent-service 因 fail-fast 初始化失败退出。
- 选择：保留每个 MCP server 独立 endpoint；knowledge-rag endpoint 支持环境变量覆盖，并在 Render
  显式设为 `/mcp/`。student-data 继续使用其原生 `/mcp`。
- 原因：直接请求服务端规范路径可保留初始化 fail-fast、共享 token 和工具发现语义，也兼容本地独立
  knowledge-rag server 的历史路径。
- 放弃方案：让客户端跟随 POST 重定向受 Java HTTP/MCP transport 行为限制；关闭 MCP 虽可启动但会
  违背开启 AgentLoop 工具链的目标；统一修改两个服务路由会扩大兼容性影响。
- 后果：线上双 MCP 初始化不再依赖重定向；新增的 endpoint 环境变量必须与实际 ASGI mount 路径一致。
- 证据：Render 日志中 `POST /mcp`、`GET /mcp` 均为 307，agent 随后在
  `McpSyncClient.initialize` 退出；修复后云端复验待完成。

## ADR-021：父 FastAPI 显式托管 FastMCP lifespan

- 状态：已采纳
- 日期/修复标识：2026-09-03 / MCP-LIFESPAN-20260903
- 背景：规范 endpoint 生效后，Render 日志显示请求进入 FastMCP，但
  `StreamableHTTPSessionManager task group was not initialized`；父 FastAPI 创建时没有注册自身已有的
  lifespan，也没有注册 `mcp_app.lifespan`。
- 选择：在创建父应用前构造 MCP ASGI 子应用，通过通用组合器依次进入 ai-inference 与 FastMCP
  lifespan，再把组合结果传给 `FastAPI(lifespan=...)`。
- 原因：这是 FastMCP ASGI 挂载的生命周期契约；组合器保留现有启动/关闭逻辑，并保证逆序释放资源。
- 放弃方案：在请求时惰性调用 SessionManager 私有 API 会绕开框架契约；把 MCP 改回独立 Render 服务
  会增加免费实例和冷启动面；关闭 MCP 会破坏 AgentLoop 工具链目标。
- 后果：父进程启动期间会同步初始化 MCP task group；若 MCP 构造失败，HTTP 推理服务仍按既有降级逻辑
  启动，但不会挂载损坏的 endpoint。
- 证据：Render 线上堆栈明确指向缺失 `mcp_app.lifespan`；新增生命周期顺序单元测试；修复部署后 Agent
  日志已显示 student-data 与 knowledge-rag 均完成 initialize/list tools，服务成功 Live。

## ADR-022：Render 采用 Groq 组织允许的模型并显式处理服务级覆盖

- 状态：已采纳
- 日期/修复标识：2026-09-03 / GROQ-RUNTIME-CONFIG-20260903
- 背景：新 Groq Key 经官方 `/openai/v1/models` 验证有效，但 Render 在线调用先返回 401，随后在修正
  Key 后返回 `model_permission_blocked_org`。检查发现 Render 服务级 `LLM_API_KEY` 占位值覆盖了共享环境组，
  且组织 Allowed Models 当前只允许 `openai/gpt-oss-120b`。
- 选择：在 agent-service 与 ai-inference-service 的服务级环境变量中保存同一 Groq Key；Blueprint 两侧
  模型统一改为 `openai/gpt-oss-120b`。共享环境组继续保存 Key 作为集中来源，但发布验收必须检查服务级
  同名变量是否覆盖。
- 原因：使用账户已经允许且官方请求返回 200 的模型可直接恢复功能；同步 Blueprint 可避免后续同步把
  Dashboard 热修复恢复为被禁用模型。
- 放弃方案：扩大 Groq 组织的 Allowed Models 会改变账户级权限且不是必要条件；仅修改共享环境组无法
  覆盖既有服务级同名变量；继续使用 qwen 会稳定返回 403。
- 后果：LLM 可用性受 `openai/gpt-oss-120b` 免费配额与 Groq 组织权限约束；若将来调整 Allowed Models，
  Java/Python 两侧必须同步改模型。密钥不得写入仓库、日志或前端变量。
- 证据：Groq `/openai/v1/models` 与 `openai/gpt-oss-120b` 最小 completion 均返回 200；Render 线上
  Python chat 返回 `GROQ_OK`，Agent 日志确认真实 LLM、AgentLoop 与双 MCP 初始化完成。完整业务任务受
  Aiven DNS 故障阻塞，不能据此宣称数据库链路已验收。

## ADR-023：先核验 Aiven 当前端点，不猜测替换失效主机名

- 状态：已采纳
- 日期/修复标识：2026-09-04 / AIVEN-DNS-20260904
- 背景：Agent 已成功启动且 liveness/readiness 均为 UP，但聚合健康检查返回 503；Render 日志中的
  MySQL health probe 以 `UnknownHostException` 失败，当前配置的 Aiven 主机名无法解析。
- 选择：登录既有 Aiven 项目，从 MySQL 服务的 Connection Information 读取当前 host、port、user 与
  TLS 要求；与 Render secret 逐项比对，仅更新不一致值，然后重新部署数据库消费者。
- 原因：Aiven 端点可能因服务删除、重建或停用而变化；以控制台当前连接信息为唯一事实来源，避免把
  猜测地址写入 Render 或仓库。
- 放弃方案：根据旧命名规则猜测 DNS 会扩大故障；临时关闭数据库健康组件会掩盖所有 CRUD/任务落库
  实际不可用；未经授权重建数据库可能丢失数据或改变费用。
- 后果：修复需要已登录的 Aiven 会话；若原服务已不存在，必须先确认数据保留与免费计划后再决定是否
  新建实例。任何数据库凭据仍只保存到 Render secret，不进入仓库或日志。
- 证据：线上 `/actuator/health` 返回 DOWN/503，而 `/actuator/health/liveness` 和
  `/actuator/health/readiness` 均返回 UP/200；同一时段日志明确记录 Aiven host 的 DNS 解析失败。恢复原
  Free-1-1gb 服务后 DNS 重新发布，Agent 聚合 health 已恢复 UP/200，无需改动连接凭据。

## ADR-024：Groq 免费层 429 采用定向有界退避

- 状态：已采纳
- 日期/修复标识：2026-09-04 / GROQ-429-RETRY-20260904
- 背景：真实 AgentLoop 第一轮已成功调用 MCP 工具，第二轮携带 observation 请求 Groq 时触发组织级
  8,000 TPM 限制；Groq 返回 429 并明确建议约 14 秒后重试。Spring AI 默认把 4xx 作为不可恢复错误，
  任务因此直接进入 FAILED。
- 选择：使用 Spring AI 1.1.6 原生 `spring.ai.retry` 配置，仅将 429 列入可重试 HTTP 码；起始退避
  15 秒、最大 30 秒、最多 3 次。自定义 `OpenAiApi` 注入自动配置的 `ResponseErrorHandler`，自定义
  `OpenAiChatModel` 注入自动配置的 `RetryTemplate`；401/403 等错误仍保持不可重试。
- 原因：等待服务端已给出的配额窗口可保留完整 prompt、工具 observation 与 final_answer 输出预算；
  使用框架重试层避免在 AgentLoop 中复制 HTTP 供应商逻辑。
- 放弃方案：把输出预算压到不足以稳定生成双 JSON 会降低质量；升级付费计划不符合当前免费部署目标；
  将全部 4xx 设为可重试会掩盖密钥或模型权限错误。
- 后果：命中 TPM 限制时单任务可能额外增加 15–45 秒延迟；超过 3 次仍明确失败。Render/Gateway 超时
  必须容纳该上限，且日志应保留 429 与重试次数用于验收。
- 证据：线上任务 2/4 在 MCP 工具调用后返回 Groq 429，响应给出 TPM limit=8000、建议等待约 10–14 秒；
  任务 4 仍为 `NonTransientAiException`，反证原自定义 Bean 绕过自动配置。Spring AI 1.1.6 字节码确认
  error handler 先按 `on-http-codes` 把 429 转为 `TransientAiException`，chat model 再由 RetryTemplate 执行
  退避。配置级测试固定两个依赖必须注入；修复后线上复验待完成。

## ADR-025：Groq ReAct 使用 JSON Object Mode

- 状态：已采纳
- 日期/修复标识：2026-09-04 / GROQ-JSON-MODE-20260904
- 背景：`e736507` 上线后已确认 429 会进入 Spring AI RetryTemplate；在无并发的任务 7 中，Groq 请求
  成功但 GPT-OSS 连续两轮未产生可解析的 ReAct JSON，任务以 `PARSE_ERROR` 结束。自由文本提示不足以
  保证协议语法，同时 2048 最大输出与默认 reasoning 会增加 8,000 TPM 免费额度压力。
- 选择：仅在 Render Blueprint 为 agent-service 设置 `response_format=json_object`、reasoning effort `low`
  和 1200 最大输出 tokens；自定义 `SpringAiConfig` 显式把这两个 Spring AI 选项传给 chat model。
- 原因：Groq 官方声明 GPT-OSS 120B 支持 JSON Object Mode，且项目提示已明确要求 JSON；使用供应商约束
  解码比继续扩展宽松字符串解析更可靠，降低 reasoning/output 预算也为多轮工具调用保留 TPM 空间。
- 放弃方案：记录或持久化完整原始模型输出会扩大敏感数据面；切换已下线的 Llama 模型不可行；在解析器
  猜测并修补任意非 JSON 文本无法提供稳定协议保证；升级付费层不符合当前部署目标。
- 后果：Render 上的每轮输出必须是 JSON 对象，复杂最终方案受 1200-token 上限约束；本地端点默认保持
  TEXT 兼容。若最终方案被截断，应先压缩 prompt/schema，再审慎调高预算并重新核算 TPM。
- 证据：线上任务 7 单线程执行、无 429，却连续两轮 parse error；Groq 官方 API 文档与 GPT-OSS 模型页
  明确列出 JSON Object Mode。配置级测试覆盖 response format 与 reasoning effort；线上复验待部署后执行。

## ADR-026：main 即生产，Render 在 CI 通过后按服务增量部署

- 状态：已采纳
- 日期/修复标识：2026-09-15 / RENDER-CD-20260915
- 背景：Render 服务未声明 `branch`（隐式跟随仓库默认分支 `main`），并使用已废弃的 `autoDeploy: true`
  （每个提交立即部署）。CI 只在 `pull_request` 上运行，main 的 ruleset 只要求走 PR、不要求检查通过，
  因此 CI 红的 PR 合入后照样上线，被部署的 main 提交本身从未验证。10 个 Docker 服务均无 `buildFilter`，
  任何提交（含仅改文档）都会全部重建；Hobby 工作区每月 500 分钟构建额度耗尽后 Render 停止构建，届时
  合入 main 也不再部署。
- 选择：`render.yaml` 为每个服务显式声明 `branch: main` 与 `autoDeployTrigger: checksPass`；10 个 Docker
  服务按 Maven 反应堆依赖声明 `buildFilter`（自身模块 + `backend/common/**` + 父 `backend/pom.xml` +
  自身 Dockerfile；ai-inference 为 `ai-inference-service/**`）；`backend-ci`/`frontend-ci` 增加 main 的
  push 触发，路径覆盖全部 buildFilter 与 `render.yaml`。
- 原因：以「被部署的那个提交」的 CI 结果门控上线，而非 PR 头提交；全部落在仓库配置内，可审阅、可回滚，
  不引入新密钥。buildFilter 让构建额度只花在确实变化的服务上。
- 放弃方案：① 保持 `commit` 触发、在 ruleset 加必需状态检查——路径过滤未触发的 workflow 会让 PR 永远
  等待必需检查，且仍不验证合并后的提交；② 关闭自动部署、由 GitHub Actions 调用 11 个 Deploy Hook——需
  维护 11 个秘密 URL 并自行实现按路径部署，重复 Render 已有能力；③ 不设 buildFilter——每次提交 10 次
  Maven 全量 Docker 构建，额度数周内即可耗尽。
- 后果：上线延迟增加一次 CI 时长（后端全量单测数分钟）。Render 在提交上检测不到 check 时不部署，
  buildFilter 与 CI push 路径必须同步维护；`npm audit` 遇新公布的 high/critical 漏洞会阻断该提交涉及的
  全部部署，需修依赖或经 Dashboard Manual Deploy。改 `common` 或父 POM 仍会重建 9 个 Java 服务。
  Blueprint 首次同步本变更时各服务可能各重建一次。GitHub 在每个提交上为 `coderabbitai` 自动建空 check
  suite（queued、0 runs）；Render 文档未说明是否计入，推断不计入（Dokploy #5284 实测 Render 自身的 App
  也会产生同类空 suite），首次合入须确认，若被阻塞按 RUNBOOK 关闭该 App 的自动建 suite。
- 证据：[`render.yaml`](./render.yaml)、[`backend-ci.yml`](./.github/workflows/backend-ci.yml)、
  [`frontend-ci.yml`](./.github/workflows/frontend-ci.yml)。线上前端 `index.html` 的 `last-modified` 为
  2026-09-13 08:49 UTC（#11 合入 main 后约 38 分钟），bundle 含 #11 的重试文案、不含未合入的 #13，佐证
  当前按 main 提交即部署；main 头提交 `bcb01c4` 的 check-runs 为空。本地 Schema 校验与路径覆盖自检见
  RUNBOOK 同名条目；Blueprint 同步与首次门控部署待验证。

## 新决策模板

```markdown
## ADR-NNN：标题

- 状态：提议 / 已采纳 / 已取代 / 已废弃
- 日期/修复标识：YYYY-MM-DD / FIX-NNN
- 背景：发生了什么，约束是什么。
- 选择：最终方案。
- 原因：为何适合当前项目。
- 放弃方案：至少列出评估过的替代方案和放弃原因。
- 后果：收益、代价、兼容性、迁移/回滚约束。
- 证据：代码、测试、运行记录链接；未执行则标“待验证”。
```

## 维护记录

| 日期/标识 | 变更 | 决策影响 |
|---|---|---|
| 2026-09-01 / DOC-BASELINE | 从当前实现与执行计划整理有效决策，并建立修复文档规则 | 新增 ADR-001~014；未改变运行时代码 |
| 2026-09-01 / RENDER-DEPLOY-20260901 | 新增 ADR-015，明确 Render 预览调用链与数据层授权边界 | 禁止把免费 Web Service MySQL/Redis 误报为可用生产数据层 |
| 2026-09-01 / AIVEN-RENDER-20260901 | 新增 ADR-016，拍板 Aiven MySQL、Render Key Value 与无 LLM 降级模式 | 取代 ADR-015 中待确认的数据层分支 |
| 2026-09-01 / AI-HEALTH-20260901 | 新增 ADR-017，校准 ai-inference 的 Render 健康检查路径 | 不增加兼容别名，以实际 FastAPI 路由作为唯一探针契约 |
| 2026-09-02 / LOGIN-VALIDATION-20260902 | 新增 ADR-018，移除登录页与服务端不一致的密码长度门槛 | 客户端只做非空校验，认证策略由 `auth-service` 统一裁决 |
| 2026-09-02 / GROQ-CLOUD-20260902 | 新增 ADR-019，选择 GroqCloud 并隔离 llama.cpp 专用字段 | Render 恢复外部 LLM；API Key 不进入仓库，RAG 降级边界保持显式 |
| 2026-09-02 / MCP-TRAILING-SLASH-20260902 | 新增 ADR-020，按服务配置 MCP 规范 endpoint | 保留 fail-fast 与双 MCP 工具链，消除 FastAPI 307 握手失败 |
| 2026-09-03 / MCP-LIFESPAN-20260903 | 新增 ADR-021，父 FastAPI 显式托管 FastMCP lifespan | 保留单进程挂载方案并初始化 Streamable HTTP SessionManager |
| 2026-09-03 / GROQ-RUNTIME-CONFIG-20260903 | 新增 ADR-022，修正服务级凭据覆盖并采用组织允许模型 | 不扩大 Groq 组织权限；保持 Java/Python 模型一致并固化 Blueprint |
| 2026-09-04 / AIVEN-DNS-20260904 | 新增 ADR-023，以 Aiven 当前连接信息修复失效 DNS | 不猜测端点、不绕过健康检查，数据库凭据继续仅存 Render secret |
| 2026-09-04 / GROQ-429-RETRY-20260904 | 新增 ADR-024，对 Groq TPM 429 使用有界退避 | 保留完整 AgentLoop 质量；401/403 继续 fail-fast |
| 2026-09-04 / GROQ-JSON-MODE-20260904 | 新增 ADR-025，Render GPT-OSS 改用 JSON Object Mode 与低推理预算 | 保留 ReAct 主路径；本地 OpenAI 兼容端点维持 TEXT 默认值 |
| 2026-09-15 / RENDER-CD-20260915 | 新增 ADR-026，main 即生产、CI 通过后按服务增量部署 | 部署以被部署提交的 CI 为准；buildFilter 与 CI push 路径须同步维护 |

## ADR-027：生产诊断与待实施修复（PROD-AUDIT-20260916，2026-09-16）

背景：[生产验收报告](./docs/production-tests/2026-09-16/REPORT.md)实测 Agent 列表429/SSE握手超时，日志复现 MCP 初始化429 → `mcpSyncClients`失败 → Agent退出；user首次429，唤醒后列表6.647秒→0.764秒，池初始化约2.98秒。user健康路由不存在但HTTP200；多个前端原型未接入已有后台，Langfuse缺配置。

选择（尚未落地）：先取得具体 MCP connection/platform 状态，恢复依赖并隔离依赖未就绪与业务服务启动；保留鉴权与MCP契约。补真实健康语义并评估 readiness 中的必要初始化；接入真实用户/学业API，修订误导提示；恢复trace并降低/脱敏网关请求头日志；单独核对发布配置漂移。代码证据为 [MCP启动配置](./backend/agent-service/src/main/resources/application.yml)、[UserController](./backend/user-service/src/main/java/com/edu/user/controller/UserController.java)、[学生学业控制器](./backend/student-service/src/main/java/com/edu/student/controller/StudentAcademicController.java) 和报告中的前端链接。

放弃作为当前解释/修复：仅增加前端重试或MCP超时不能处理立即返回的429；绕过鉴权、删MCP或伪造健康会破坏既有交付边界；无SQL耗时/模型调用证据，不归因于慢SQL、连接配额或Groq限流。

代价与约束：依赖恢复可能涉及套餐或部署时间；初始化前移延后readiness；启动隔离需要明确依赖错误语义；Agent定时任务不可机械套lazy初始化。所有方案待实施、修复后待验证，本轮不改变R-5/R-6状态，不宣称生产可上线。

## PERF-500MS-20260916：消除请求线程重任务与首次初始化（2026-09-16）

- 背景：生产诊断发现冷请求 Hikari 初始化约2.98s、Agent MCP握手429导致启动失败；代码审查确认 AI/PDF 同对象 @Async 调用实际同步执行，CallerRunsPolicy 会将满队列重任务交给请求线程。持久连接热态 auth/userInfo 已约400ms，原逐次 curl 测量包含新建连接成本。
- 选择：显式后台提交并拒绝过载；MCP依赖启动解耦、后台按30s重连，完整工具集才可使用；生产 eager Bean/Servlet 初始化及 DB SELECT 1 预热、连接池保留2条空闲连接/keepalive；shared Actuator 让健康探针实际存在；统计合并数据库往返、可索引时间谓词；Server-Timing 区分应用与网关；关闭生产SQL stdout/gateway DEBUG并开启响应压缩。实现见 ARCHITECTURE 的同名记录及性能报告。
- 原因：优先消除已证实的阻塞并保留响应/权限契约，不能用超时、快速错误或缓存他人敏感记录冒充性能达标。
- 放弃：削弱密码哈希/JWT/Redis/角色检查；将免费实例地址改成不能接收私网请求的内部主机名；给所有请求设置500ms强制中断；未经预算确认升级付费；把内部 LLM/RAG 结果改成异步ID而破坏 Feign/MCP 契约；未经慢查询证据在生产自动DDL加索引。
- 代价：eager 初始化增加冷启动阶段工作；空闲数据库连接占配额（7个领域服务每个最少2条、最多3条）；MCP后台重连仅在Agent进程存活时进行，未就绪任务仍明确失败；有界队列满载返回业务503。模型/PDF提交时间与后台完成时间分开度量，SSE衡量握手/首帧，不衡量长连接总时长。500ms是需要指定地区、热态和负载的验收目标，不是已证实的任意请求硬保证。
- 验证：完整 Maven 回归/安全覆盖率、新增异步提交及MCP失败恢复、数据库预热失败测试通过；Python28项、前端构建/体积门及既有61条逻辑断言通过，ESLint零错误/一个既有格式警告。Render Blueprint验证通过。生产部署与500ms结果以 PERFORMANCE.md 的实测记录为准。

PERF-500MS-20260916 补充（2026-09-16）：问卷Excel逐题 INSERT 为N次数据库往返，选择每100题参数化批量写入，在同一事务内同步题目数，并手动保留自定义SQL不执行的auto-fill。放弃本次将导入改为异步ID，因为前端目前依赖同步完成后读取题目，直接切换会破坏契约；分批约束单条SQL的参数/报文大小。实际MyBatis/H2验证JSON、中文/引号、可空值、默认删除值和整体回滚通过；205题回归确认100/100/5三次写入。无生产问卷写入验收，不声称任意文件上传/解析500ms。

PERF-500MS-20260916 启动回归修正（2026-09-16）：日志证实首轮Agent在toolCallbackResolver创建时因MCP未就绪失败。选择配置条件内动态resolver而不是返回空工具或吞掉503，避免静态空工具快照且保留失败显式语义。新增真实ToolCallingAutoConfiguration启动及刷新工具解析回归通过；默认本地resolver不变。 实现见 [DeferredMcpConfiguration](./backend/agent-service/src/main/java/com/edu/agent/config/DeferredMcpConfiguration.java)。

2026-09-16 / PERF-500MS-20260916：生产 Server-Timing 出现重复 app 指标，原因是领域服务扫描 common 的 @RestControllerAdvice，同时自动配置再次创建 advice。以 ConditionalOnMissingBean 保证唯一注册，并限制 servlet 条件；启动上下文覆盖扫描/不扫描两种注册路径。此项无架构边界变化，影响 common RequestTimingConfiguration。网关首轮现已 live（09:20:08Z），内存可见样本约233MB，不能据此把此前无报错重启归因为OOM。最终指标必须以全部服务稳定发布后复测为准；回滚恢复原提交和原始 render-performance-plan 配置。

2026-09-17 / PERF-500MS-20260916：生产静态站的外部rewrite未及时转发SSE首帧，Agent和网关直连首帧可用。Warning.vue通过VITE_SSE_BASE_URL直连网关，仍携带Bearer并经过网关会话鉴权；普通API保持原路由。选择绕过静态代理，放弃调长超时及绕过鉴权的Agent直连。render.yaml记录构建变量。验证：前端构建/体积门通过，lint零错误一个既有格式警告；网关CORS预检允许Authorization和站点Origin，直连hello测试通过。前端最终部署待验证；回滚前端提交和该构建变量恢复原路由。完整生产验收记录保留本地，不纳入公开提交；不能据此宣称所有请求满足500ms。

2026-09-17 / PERF-500MS-20260916：热登录仍超过预算；AuthServiceImpl 对成功且服务处理≥500ms的登录仅记录用户查询、密码校验、角色查询、JWT签发和Redis会话五阶段耗时，不记录身份、密码、令牌或SQL。无架构边界变化，不降低bcrypt成本、不缓存密码校验、不取消角色/会话检查；用于区分CPU校验和外部依赖成本，避免猜测原因。复测热登录并读取 Slow login phases 日志，若任一阶段仍超预算，则500ms未通过。此诊断实际生产结果待验证，回滚该提交移除计时日志。

2026-09-17 / PERF-500MS-20260916：分阶段日志证实首次登录还有Redis连接及JWT初始化成本，热密码校验为主要耗时。新增 auth AuthDependencyWarmupConfiguration，在已有预热开关开启时于readiness前PING Redis并初始化内存签名；不写会话、无业务用户/凭证日志，Redis失败不能就绪。无架构影响，auth仍依赖原Redis白名单。放弃密码缓存/降低bcrypt及依赖故障时假健康；验收需验证启动、热登录阶段和401/403。新增预热成功只读与故障阻止就绪测试；生产行为待验证。回滚此auth提交移除额外启动预热，保留强验证与原配置。
