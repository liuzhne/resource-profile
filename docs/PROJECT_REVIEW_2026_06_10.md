# 项目检视报告：完整问题清单与修改计划

> **版本**：v2（2026-06-10，整合「安全/实现缺口」与「过度设计评估」两轮检视）
> **检视范围**：gateway / auth / 业务服务（student·mental·teacher·data·user）/ agent-service / mcp-student-data / ai-inference-service / frontend / docker / sql
> **方法**：静态走查（鉴权链路、跨服务调用、敏感数据流、配置默认值、组件启用度、测试分布、仓库卫生），关键结论以 `文件:符号` 坐实
> **如何使用本文件**：§2 总览表一眼看全；§3/§4 是分类问题清单（A=安全正确性，B=过度设计）；§5 是按执行阶段编排的修改计划，每个任务回链到问题编号。完成一项就把对应 `☐` 勾成 `☑` 并追加完成日期。

---

## 0. 总体诊断

这个项目工程化很用心（执行计划、灰度、prompt 缓存、字段权限脚手架、eval gate、agent 17 个测试类），但有两个相互关联的系统性问题：

1. **地基与展示层倒挂**：AI 工程成熟度（MCP / 多层记忆 / 混合检索 / 模型路由 / 灰度 / Langfuse）远超业务地基成熟度。而地基是**裸奔的鉴权、8/9 模块零测试、可预测的默认账号**。精力流向了展示性的 AI 层，最该硬的地基却是空的。

2. **「做完了但默认不开」反模式**：大量组件被构建、测试、写进执行计划、配了灰度，却在默认运行态里**关闭或未接入**。系统按默认配置跑起来，真正用到的只有「一次 LLM 调用 + 可选 RAG」。功能的*完成度*与*启用度*严重背离——这正是过度设计最清晰的指纹。

| 组件 | 默认状态 |
|------|---------|
| AgentLoop（ReAct）+ 百分比灰度 | ❌ `EDUCARE_AGENT_LOOP_ENABLED=false` |
| 双 ChatClient + ModelRouter（云端） | ❌ `EDUCARE_CLOUD_ENABLED=false` |
| 四层记忆系统 + memory MCP(8096) | ❌ 未接入 MCP client，`memory.enabled=false` |
| Hybrid Retrieval（BM25+RRF） | ❌ `RAG_HYBRID_ENABLED=false` |
| 字段权限（@SensitiveField） | ❌ `educare.field-permission.enabled=false` |
| Skills 热更新 | ⚠️ 开关 true，但只在（默认关的）AgentLoop 路径注入 |

> **结论**：是的，存在明显过度设计。修复方向不是"再加组件"，而是**先补地基（A 类）+ 砍掉/降级展示性半成品（B 类）**，让系统的实际启用面收敛到真实业务需求。

---

## 1. 图例

| 标记 | 含义 |
|------|------|
| **P0** | 安全红线，直接导致敏感数据泄露/越权，最优先 |
| **P1** | 健壮性/合规/规范缺口 |
| **P2** | 工程质量与文档卫生，成本低应顺手清 |
| **过度** | 过度设计/不必要复杂度，瘦身收益高 |
| ☐ / ☑ | 待修复 / 已完成 |

---

## 2. 问题总览表

### A 类 — 安全与正确性缺口

| 编号 | 问题 | 级别 | 修复阶段 |
|------|------|------|---------|
| A1 | 网关与业务服务实际无 JWT 准入鉴权（整链路裸奔） | P0 | 阶段一 |
| A2 | 水平越权（IDOR）——身份取自请求参数无校验 | P0 | 阶段一 |
| A3 | MCP server 与 dry-run 端点无鉴权且对外可达 | P0 | 阶段一 |
| A4 | Python 服务 CORS 为非法危险组合（`*`+credentials） | P1 | 阶段三 |
| A5 | 密钥/凭证硬编码 + 默认账号可预测 | P1 | 阶段三 |
| A6 | JWT 登出无法真正撤销（Redis 白名单未被消费） | P1 | 阶段一 |
| A7 | 异常处理不规范，GlobalExceptionHandler 仅 1/9 模块 | P1 | 阶段三 |
| A8 | 测试覆盖失衡，安全关键代码零覆盖 | P1 | 阶段三 |
| A9 | 编译产物（.pyc）入库，.gitignore 缺关键项 | P2 | 阶段四 |
| A10 | 仓库根存在误入的大目录 `师生画像系统/` | P2 | 阶段四 |
| A11 | 文档与代码漂移（Spring AI 版本/模型名/鉴权描述） | P2 | 阶段四 |
| A12 | `getMentalIndicators` 取"最近一次"未真正排序 | P2 | 阶段四 |
| A13 | 前端 HTTP 401 不触发自动登出 | P2 | 阶段四 |

### B 类 — 过度设计与不必要的复杂度

| 编号 | 组件 | 现状 | 修复阶段 |
|------|------|------|---------|
| B1 | 四层记忆系统 + memory MCP(8096) | 悬空，未接入 | 阶段二 |
| B2 | 三个独立部署的 MCP server | 对内部唯一消费者属协议错配 | 阶段二 |
| B3 | 双 ChatClient + ModelRouter（云端路由） | 云端默认关，只用本地 | 阶段二 |
| B4 | AgentLoop 百分比灰度切流 | 单机 demo 套用生产流量治理 | 阶段二 |
| B5 | Hybrid Retrieval（BM25+RRF） | 默认关，小知识库收益低 | 阶段二 |
| B6 | 重型向量栈（Milvus 全家桶 + Reranker） | 4 容器+独立端口，对小库过重 | 阶段二（评估） |
| B7 | Skills + SkillLoader 热更新 | 只在默认关的 AgentLoop 路径生效 | 阶段二 |
| B8 | 「做完默认全关」反模式（横切） | 半成品长期留主干 | 贯穿全程 |

---

## 3. A 类 — 安全与正确性缺口（清单）

### A1 网关与业务服务实际无 JWT 准入鉴权  `P0`  ☑
- **完成于 2026-06-19**：gateway `JwtAuthGlobalFilter`（签名+过期，`/auth/`+`/actuator/` 放行，`/_internal/`→403，token 双轨 header/`?token`），`JwtAuthGlobalFilterTest` 10 例。
- **位置**：`backend/gateway/`（仅空的 `GatewayApplication.java`，`application.yml` 只有路由+CORS，无 `GlobalFilter`）；唯一 Spring Security 在 `auth-service/.../SecurityConfig.java`，只能保护它自己的 8081；`student/mental/teacher/data/user` 均不引用 `JwtUtil`。
- **现象/影响**：业务请求 `浏览器→gateway(8080)→业务服务` 不经过 auth-service，全链路不校验 token。任何人不带 token 即可读取学生心理/学业等敏感数据；前端 token/登出逻辑全部失效。
- **建议**：gateway 加全局 JWT 校验 `GlobalFilter`（签名+过期+Redis 白名单），`/auth/**`、健康检查放行；或在 `common` 提供 `OncePerRequestFilter` 由各服务启用。

### A2 水平越权（IDOR）  `P0`  ☑
- **完成于 2026-06-19**：统一经 `common/AccessGuard.allowSelfRoleOrInternal`（本人/教职工角色/内网匿名）。已覆盖 student（`StudentController`/`StudentAcademicController`/`StudentAttendanceController`）、mental（`StudentMentalController` 4 端点）、agent（`ExportController`/`AgentTaskController`/`InterventionFeedbackController`）、data（`DashboardController`）；各模块补越权单测。
- **位置**：`mental-service/.../StudentMentalController.java`（`myHistory/myDetail/myQuestionnaires(@RequestParam Long userId)`）；`student-service/.../StudentController.java`（`getById(@PathVariable Long id)`）。
- **现象/影响**：请求参数 `userId/id` 直接当身份，无"登录用户==被查对象"校验。学生 A 改 `userId` 即可拉 B 的数据。涉未成年人敏感数据，合规红线。
- **建议**：身份一律从已验证 token 的 subject/claims 取；跨人查询走显式角色校验 + 字段权限。

### A3 MCP server 与 dry-run 端点无鉴权  `P0`  ☑ 2026-06-20
- **完成**：① dry-run 端点经网关 `/_internal/`→403 挡在公网外，8087/8094-96 端口收紧 `127.0.0.1`；② 内部预共享 token 全链闭合（`educare.mcp.token`/`EDUCARE_MCP_TOKEN`，gated，默认空=仅网络隔离）：agent client customizer 附 `X-MCP-Token` + 8094(student-data) `McpTokenFilter` + 8095(knowledge-rag) `McpTokenMiddleware`（纯 ASGI，保流式）；三容器 compose 直通同一 env；③ **8087 自身鉴权** `AgentSelfAuthFilter`（闭合 AccessGuard 内网信任在直连 8087 下的 tokenless 缺口）。单测：8094 filter 编译验证、8095 middleware 5 例、8087 filter 7 例。**仅活模型 e2e 待用户侧起栈验证**（fastmcp 不在 .venv）。
- **位置**：`mcp-student-data/.../StudentDataTools.java`（8094，4 个读敏感数据 tool，端口无鉴权，Feign 调下游不带 token）；`agent-service/.../AgentLoopDryRunController.java`（`/agent/api/v1/_internal/loop/dry-run`，自述"不挂 Security/JWT"，挂在 gateway `/agent/**` 对外可达）。
- **建议**：MCP 端口加内部预共享 token / 网络隔离；删除 dry-run 端点或挂 admin。（注：B2 若把 MCP 收回内联，A3 的 MCP 部分自然消除。）

### A4 Python 服务 CORS 非法组合  `P1`  ☑ 2026-06-20（白名单 + 关 credentials）
- **位置**：`ai-inference-service/app/main.py`（`allow_origins=["*"]` + `allow_credentials=True`）。
- **建议**：收敛为白名单；无凭证需求则 `allow_credentials=False`。

### A5 密钥硬编码 + 默认账号可预测  `P1`  ☑ 2026-06-20（JWT 密钥 fail-fast；infra/种子账号见 §5.3 T14 备注）
- **位置**：`docker/docker-compose.yml`（MySQL `root`/`edu123456`、Nacos token 明文、MinIO `minioadmin`、Langfuse `dev-only-replace-in-prod-…`）；`sql/init/01_init.sql`（admin/teacher/student 同一 bcrypt hash，密码=用户名）。
- **建议**：敏感值改环境变量/Nacos 加密配置且无默认（缺失即 fail-fast）；首启强制改密或随机化默认口令。

### A6 JWT 登出无法真正撤销  `P1`  ☑
- **完成于 2026-06-19**：gateway 加 `spring-boot-starter-data-redis-reactive`，`JwtAuthGlobalFilter` 签名校验后再比对 Redis 会话白名单 `token:{userId}`（值相等才放行）—— 登出删 key/改密重登覆盖 key 后旧 token 立即 401。可经 `educare.gateway.auth.check-session` 关闭（本地无 Redis 联调）。Redis 异常 fail-closed 503。
- **位置**：`auth-service/.../AuthServiceImpl.java`（登录写 Redis `token:{userId}`，logout 删 key），但无任何校验端读该白名单。
- **建议**：并入 A1 的准入 filter——校验 Redis 白名单存在且匹配才放行，让登出/改密即时失效。

### A7 异常不规范，GlobalExceptionHandler 仅 1/9 模块  `P1`  ☑ 2026-06-20（下沉 common 自动装配 + BusinessException）
- **位置**：`GlobalExceptionHandler` 仅 auth-service；`AuthServiceImpl` 全程裸 `RuntimeException`，用户名/密码错误返回 500（应 400/401）。
- **建议**：`GlobalExceptionHandler` 下沉 `common` 统一装配；业务用带错误码的自定义异常。

### A8 测试覆盖失衡  `P1`  ☑ 2026-06-20（安全关键单测补齐：gateway 10/AccessGuard 12/各 controller/FieldPermission 5 + CI 测试门禁 backend-ci；覆盖率%门待核心单测补齐后引入）
- **现状**：agent-service 17 个测试类，其余 8 模块均 0；`JwtUtil`/`FieldPermissionAdvice`/`AuthServiceImpl` 零覆盖。
- **建议**：优先为鉴权/越权/字段权限补纯 Mockito 单测；CI 加最低覆盖门槛。

### A9 编译产物入库 + .gitignore 缺项  `P2`  ☑ 2026-06-20（清 14 个 .pyc + 补 Python .gitignore）
- **现状**：`git ls-files` 跟踪 14 个 `*.pyc`；`.gitignore` 缺 `__pycache__/`、`*.pyc`、`.DS_Store`。
- **建议**：补 `.gitignore` 并 `git rm -r --cached` 清除已跟踪产物。

### A10 误入的大目录  `P2`  ☑ 2026-06-19（.gitignore 已排除 师生画像系统/、知识库数据/；git 未跟踪）
- **位置**：仓库根 `师生画像系统/`（`.docx`、知识库数据、`.DS_Store`）。
- **建议**：移出仓库或归入 `docs/` 并纳入忽略/LFS。

### A11 文档与代码漂移  `P2`  ☑ 2026-06-20（CLAUDE.md/rules：Spring AI 1.1.6、模型名、MCP 端口、鉴权描述）
- **现状**：`CLAUDE.md` 写 Spring AI `1.0.0-M6`（实际 `pom.xml` 1.1.6 GA）；模型名 `qwen2.5-32b` vs `config.py` 默认 `qwen2.5-14b`；CLAUDE.md 鉴权描述错误（见 A1）。
- **建议**：以代码为准刷新 CLAUDE.md。

### A12 `getMentalIndicators` 未真正排序  `P2`  ☑ 2026-06-20（按 assessTime/createTime 倒序 max 取首条）
- **位置**：`mcp-student-data/.../StudentDataTools.java`（注释称倒序取最近，实际 `list.get(0)`）。
- **建议**：显式按 `assessTime/createTime` 倒序取首条，或下游约定有序返回。（B2 内联后随之处理。）

### A13 前端 HTTP 401 不触发登出  `P2`  ☑ 2026-06-20（error 拦截器 HTTP 401→logout）
- **位置**：`frontend/src/utils/request.js`（只认 body `res.code===401`，HTTP 401 走 error 分支只弹消息）。
- **建议**：error 拦截器中 `error.response?.status===401 → userStore.logout()`。

---

## 4. B 类 — 过度设计与不必要的复杂度（清单）

> 每条给出**现状（启用度）/ 为何过度 / 处置建议（删·降级·保留条件）/ 影响面**。影响面默认按"删除"评估。

### B1 四层记忆系统 + memory MCP(8096)  `过度`  ☑ 删除于 2026-06-19
- **现状**：Working/Episodic/Semantic/Procedural over Redis + 独立 Python MCP server(8096) + Java `MemoryGateway` + 13 个单测 + `MEMORY_DESIGN.md`。但 agent-service 的 MCP client **只接了 student-data + knowledge-rag，memory-server 未接入**，`memory.enabled=false`。
- **为何过度**：直接照搬 MemGPT 认知架构概念，投入完整却零产出；对"学生风险画像"真实需要的记忆可能就是"上次分析结论"一行。
- **处置**：**删除**（或保留单 key 存"上次结论"的极简版）。
- **影响面**：删 `app/services/memory_store.py`、`app/mcp/memory_*.py`、`MemoryGateway.java`、`memory-mcp` 容器、相关测试与设计文档。**默认路径未接入 → 删除零影响**。

### B2 三个独立部署的 MCP server  `过度`  ✗ 不采纳（简历取向：保留 student-data/knowledge-rag 2 个 MCP 作亮点；memory MCP 已随 B1 删）
- **现状**：student-data(8094, Java 独立 Spring Boot) / knowledge-rag(8095, Python) / memory(8096)。工具的唯一消费者是自己的 agent-service。
- **为何过度**：MCP 的价值在于让*外部、跨供应商*客户端复用工具。这里把 student-data 拆成独立服务再 Feign 回调业务服务，比起 agent-service 内一个 `@Tool` 直连下游，凭空多一个微服务+一层网络跳转+一层协议序列化。
- **处置**：**收回内联**——student-data 4 个 tool 改为 agent-service 内 `@Tool`（直接 Feign 调 student/mental）；knowledge-rag 改为 agent 直调 ai-inference 的 RAG 端点。
- **影响面**：删 `mcp-student-data` 模块 + 容器 + compose 段；agent 改用本地 `ToolCallback`。**保留条件**：确有外部 MCP 客户端（如 Claude Desktop）要消费这些工具，才保留独立 server。同时消解 A3 的 MCP 鉴权问题。

### B3 双 ChatClient + ModelRouter（云端路由）  `过度`  ☑ 删除于 2026-06-19
- **现状**：本地 `@Primary` + `cloudChatClient` + `ModelRouter`（敏感→本地、方案/审核→云端、未就绪回落）+ 审计 logger + 计数器。`EDUCARE_CLOUD_ENABLED=false`，api-key 空 → 一律回落本地。
- **为何过度**：99% 情况只有一个本地模型，却维护整套路由/审计/fail-safe，为"未来可能的多模型"预留抽象，YAGNI。
- **处置**：**删除** router 层，`RiskAnalyzeService` 直连本地 ChatClient。
- **影响面**：删 `router/ModelRouter`、云端 ChatClient bean、`application.yml` 云端配置段及相关测试。**保留条件**：真接入云端模型时再加。

### B4 AgentLoop 百分比灰度切流  `过度`  ☑ 降级为单布尔于 2026-06-19（AgentLoop 设默认路径）
- **现状**：按 taskId 确定性分桶的 `canary-percent`(0→10→50→100) + `AgentLoopCanaryGate`(@RefreshScope) + Nacos `agent-canary.yml` + `agent_loop_canary.sh` + 回滚。
- **为何过度**：百分比灰度是生产流量治理，用在单机、单用户手动触发、本地 LLM 的功能上，与场景不匹配；脚本至今"待用户实跑"。
- **处置**：**降级**为单一布尔开关（AgentLoop 本身按 §5 决策保留与否）。
- **影响面**：删灰度门控类 + 脚本 + Nacos canary 配置；保留 `EDUCARE_AGENT_LOOP_ENABLED` 单开关。

### B5 Hybrid Retrieval（BM25+RRF）  `过度`  ☑ 删除于 2026-06-19（RAG 回纯 dense）
- **现状**：dense 候选池上自实现 BM25Okapi + 中英 tokenizer + RRF 融合 + `eval/hybrid_eval.py`。`RAG_HYBRID_ENABLED=false`。
- **为何过度**：知识库规模（案例/政策）很小，dense 检索已够；混合检索是"RAG 进阶展示"。
- **处置**：**删除**或移至 experimental 分支。
- **影响面**：删 `app/services/hybrid_retrieval.py` + eval + 开关，RAG 回纯 dense。**保留条件**：知识库上千条且检索质量实测不足时再引入。

### B6 重型向量检索栈（Milvus 全家桶 + Reranker）  `过度`  ✗ 保留（简历取向：RAG 真灌库亮点依赖 Milvus）
- **现状**：Milvus + etcd + minio + attu（4 容器）+ BGE-reranker 独立 llama.cpp 端口(8093)。
- **为何过度**：小知识库下 Milvus 的运维成本（etcd/minio 依赖）与收益不成比例；reranker 重排边际收益低。
- **处置**：**评估降级**——用 pgvector（复用现有库）替换 Milvus 全家桶；reranker 视知识库规模决定去留。
- **影响面**：较大（需迁移向量存储），列为**评估项**而非立即删；先量化知识库真实规模再决策。

### B7 Skills + SkillLoader 热更新  `过度`  ☑ 简化为 classpath 静态于 2026-06-19
- **现状**：4 个技能 markdown + 外部目录按 mtime 热更新 + classpath 兜底，只在 AgentLoop（默认关）路径注入 system prompt。
- **处置**：**简化**为 classpath 静态加载（去掉外部目录热更新），或随 B4/AgentLoop 决策一并处理。
- **影响面**：小，删 `SkillLoader` 的目录监听逻辑。

### B8 「做完默认全关」反模式（横切）  `过度`  ☑ 2026-06-19（记忆/路由/灰度/hybrid 已删；AgentLoop 默认开，收敛默认启用面）
- **现状**：A/B 表中多达 6 处 feature flag 默认关或未接入，半成品长期留主干。
- **处置**：对每个 flag 二选一——**「启用并端到端跑通」或「删除」**，不留长期默认关的半成品。这是贯穿 §5 全程的纪律，而非单点修改。

---

## 5. 修改计划（Roadmap）

### 5.0 前置：目标定位决定瘦身力度

- **若目标是落地使用** → 按下方四阶段全做，B 类大幅瘦身。
- **若目标是简历/答辩/学习** → A 类（尤其 P0 安全）仍必须做（否则现场被问倒）；B 类改为**挑 1–2 个真正端到端跑通**（如 RAG 真灌库、AgentLoop 设为默认路径），其余仍按 B8 删除——展示"跑通的少数"胜过"默认全关的全部"。

> **阶段排序理由**：P0 安全是出血点，必须最先止血（阶段一）；但**在补测/规范前先瘦身**（阶段二），避免给即将删除的代码做安全加固和补单测——先缩小工作面，再对保留下来的代码做规范化（阶段三），最后清卫生（阶段四）。

### 5.1 阶段一 — 安全地基止血（P0，最高优先）

| 任务 | 对应 | 关键动作 | 验收 | 状态 |
|------|------|---------|------|------|
| T1 | A1·A6 | gateway 全局 JWT `GlobalFilter`（签名+过期+Redis 白名单），放行 `/auth/**`+健康检查 | 无 token 调 `/student/**` 返回 401；登出后旧 token 失效 | ☑ 2026-06-19 |
| T2 | A2 | controller 身份改取自 token claims，请求参数 userId 不可信；跨人查询加角色校验 | 学生越权查他人返回 403 | ☑ 2026-06-19 |
| T3 | A3 | MCP 端口加内部 token / 网络隔离；删除或给 dry-run 挂 admin | 外部直连 8094/dry-run 被拒 | ☑ 2026-06-20（端口隔离 + 全链 MCP token 8094/8095/client + 8087 自鉴权） |
| T4 | A11 | 同步更正 CLAUDE.md 鉴权描述 | 文档与代码一致 | ☑ 2026-06-19 |

### 5.2 阶段二 — 瘦身（B 类删除/降级，缩小后续工作面）

| 任务 | 对应 | 关键动作 | 影响面 | 状态 |
|------|------|---------|--------|------|
| T5 | B1 | 删除四层记忆系统 + memory MCP | 默认未接入，零影响 | ☑ 2026-06-19 |
| T6 | B2·A3 | ~~student-data MCP 收回 agent `@Tool`~~ | — | ✗ 不采纳（简历取向：保留 2 个 MCP server 作亮点）；A3 改加内部 token：8094+client ☑，8095 待运行验证 |
| T7 | B3 | 删除 ModelRouter/cloudChatClient，直连本地 | 删 router 包+配置+测试 | ☑ 2026-06-19 |
| T8 | B4 | AgentLoop 灰度降级为单布尔开关 + 设默认路径 | 删灰度门控+脚本+Nacos canary | ☑ 2026-06-19 |
| T9 | B5·B7 | 删除 Hybrid Retrieval；Skills 简化为 classpath 静态 | RAG 回纯 dense | ☑ 2026-06-19 |
| T10 | B6 | 评估 pgvector 替换 Milvus 全家桶 | 评估项 | ✗ 保留 Milvus（简历取向：RAG 真灌库亮点依赖它） |
| T11 | B8 | 逐一决策剩余 flag："跑通"或"删除" | 主干无默认关半成品 | ☑ 2026-06-19（记忆/路由/灰度/hybrid 删；AgentLoop 默认开） |

### 5.3 阶段三 — 规范与健壮性（P1，只对保留代码做）

| 任务 | 对应 | 关键动作 | 状态 |
|------|------|---------|------|
| T12 | A7 | `GlobalExceptionHandler` 下沉 common；业务用带错误码自定义异常 | ☑ 2026-06-20 |
| T13 | A4 | Python CORS 收敛白名单 / 关 credentials | ☑ 2026-06-20 |
| T14 | A5 | 密钥改无默认环境变量（缺失 fail-fast） | ☑ 2026-06-20（JWT 密钥 fail-fast；infra/默认账号 see 备注） |
| T15 | A8 | 为鉴权/越权/字段权限补纯 Mockito 单测；CI 加门槛 | ☑ 2026-06-20（单测补齐 + CI 测试门禁 backend-ci；覆盖率%门待核心单测补齐后引入） |
| T16 | （字段权限） | `educare.field-permission.enabled` 默认开 | ☑ 2026-06-20（默认开 + 内网放行安全前提；运行态矩阵待 e2e） |

> T14 备注：JWT 密钥（鉴权基石）已 fail-fast 无默认。MySQL/MinIO/Nacos 默认口令 + SQL 种子默认账号
> （admin/teacher/student 密码=用户名）属本地 demo 便利项，端口已绑 127.0.0.1；生产前需改，暂留文档。

### 5.4 阶段四 — 工程卫生（P2）

| 任务 | 对应 | 关键动作 | 状态 |
|------|------|---------|------|
| T17 | A9 | 补 `.gitignore` + `git rm -r --cached` 清 `.pyc`/`__pycache__` | ☑ 2026-06-20 |
| T18 | A10 | 移出 `师生画像系统/` 大目录 | ☑ 2026-06-19（Phase 1 .gitignore 已排除，git 未跟踪，零文件） |
| T19 | A11 | 刷新 CLAUDE.md 版本号/模型名（鉴权部分已在 T4 处理） | ☑ 2026-06-20 |
| T20 | A12·A13 | `getMentalIndicators` 显式排序；前端 error 拦截器处理 HTTP 401 | ☑ 2026-06-20 |

---

## 6. 附录：关键复核命令

```bash
# A1：确认网关/业务服务无 JWT 准入（应只命中 auth-service 与 common 的 RoleContextFilter）
grep -rln -iE 'OncePerRequestFilter|SecurityFilterChain|GlobalFilter|@EnableWebSecurity' backend
grep -rln 'JwtUtil|parseToken|validateToken' backend/{student,mental,teacher,data,user}-service

# B1：确认 memory MCP 未接入 agent（agent 只接 student-data + knowledge-rag）
grep -nA6 'mcp:' backend/agent-service/src/main/resources/application.yml | grep -iE 'student-data|knowledge-rag|memory'

# B8：盘点默认关闭的 feature flag
grep -rn -iE 'enabled' backend/agent-service/src/main/resources/application.yml | grep -iE 'loop|cloud|memory|field'
grep -n -iE 'HYBRID_ENABLED|AGENT_LOOP_ENABLED|CLOUD_ENABLED' ai-inference-service/app/core/config.py docker/docker-compose.yml

# A9：被 git 跟踪的编译产物
git ls-files | grep -E '__pycache__|\.pyc$'

# A8：各模块测试类数量
for m in backend/*/; do echo "$m: $(find $m -path '*src/test*' -name '*Test.java' | wc -l)"; done
```
