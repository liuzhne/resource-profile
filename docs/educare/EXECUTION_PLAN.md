# EduCare 改进落实计划（Execution Playbook）

> **作用**：将 [`IMPROVEMENT_2026_MAY.md`](./IMPROVEMENT_2026_MAY.md) 的 Phase G/H/I 拆为可逐步执行的原子任务清单。**每次开工前先读本文件 → 找到第一个未完成项 → 执行 → 回写本文件勾选与备注**。
>
> **设计源**：`IMPROVEMENT_2026_MAY.md`（v1.1，2026-05-12 拍板）
> **创建日期**：2026-05-13
> **最近更新**：2026-08-27（**R-1~R-4 完结**：JDK 17 Enforcer fail-fast；补 user/teacher 授权并将两控制器与 MCP token filter 纳入 ≥80% JaCoCo 门；Python 新增 RAG/upsert/MCP ASGI 集成测试；Redis/MCP 凭据改为生产硬门且 Redis requirepass/消费者接线闭环。全后端 177 例、Python 25 例通过；下一步 R-5 真 AI/可观测验收）
> **历史最近更新（进度审计）**：2026-08-26（**进度审计（纯文档回写，零代码变更）**：对照本文件 §1-§11 与仓库实际产物逐项复核。确认完结面：Phase G/H/I/J 全部 `[x]` + 四阶段审查路线图（PROJECT_REVIEW_2026_06_10 §5.1-5.4）+ 安全增量（A3 MCP token 全链 client+8094+8095 / 8087 自鉴权 / 字段权限 list-page 漏脱敏修复 / backend-ci 测试门禁）+ 上线前工程化；产物在位核验通过：`.github/workflows/{backend-ci,eval-gate}.yml`、`docker/docker-compose.{prod,monitoring}.yml`、`scripts/preflight-prod.sh`、`backend/mcp-student-data` 与 `ai-inference-service/app/mcp/` 双端 server、`eval/run_eval.py`。剩余项收敛为三类——① 用户侧 e2e 实跑验证（GPU/Docker 环境）、② CI 覆盖率 % 门（唯一非环境依赖项）、③ Phase I 储备项 I-2/I-4/I-6 不排期）
> **历史最近更新（上线前工程化）**：2026-06-27（**上线前工程化（非 Phase 任务，按上线清单逐项）**：① ESLint 链路(0/0)；② 生产密钥 fail-fast（compose 密钥参数化 + `docker/.env.example` + `scripts/preflight-prod.sh` + `sql/prod/` 改密 + `.gitignore` 护密钥/证书/备份）；③ auth-service 9 登录链单测(100% 覆盖) + jacoco 定向覆盖率门(`AuthServiceImpl`≥80%)接 CI；④ 生产部署形态（12 主机端口收敛 `127.0.0.1` + nginx TLS/SPA/SSE + `Dockerfile.frontend` + `docker-compose.prod.yml` + `docs/DEPLOY.md`）；⑤ 可观测（gateway actuator + `docker-compose.monitoring.yml` Prometheus/Grafana + 看板 + 4 告警规则）；⑥ 备份/压测（`backup-mysql.sh`/`restore-mysql.sh` + k6 `load-test.js`）；⑦ 补 `questionnaire.vue` 查看按钮。dev 默认不变、prod 经 `docker/.env` 覆盖 + preflight fail-fast。全后端 `mvn clean test` 11 模块绿 + 前端 build/lint(0/0) 绿；Docker daemon 未起→镜像 build/`nginx -t`/全栈 smoke + GPU 实跑(Task #7)留用户侧。详见 §6）
> **历史最近更新（孤儿清理）**：2026-06-24（**孤儿代码清理（工程卫生，非 Phase 任务）**：两轮清理——① 96 游离空目录；② Python 死代码 `rag_service.py`/`embedding_service.py`（被 `rag_pipeline`/`embedding_client` 取代，0 引用）；③ 前端 4 孤儿文件 + 2 冗余依赖（`vue-echarts`/`js-cookie`）；④ **删 `AgentLoopDryRunController`**（兑现 H-2.3"切流后视情况删除"）；⑤ 过期文档 `GATEWAY_ISSUE_SUMMARY.md` + `AGENTS.md` 同步 CLAUDE.md。工具：knip/vulture/类级引用扫描 + 人工复核（Java 框架类 / Python FastAPI 端点判定非孤儿予以保留）。验证：前端 build 绿 + 后端 BUILD SUCCESS（17 filter 测试）+ Python 19 例。详见 §6）
> **历史最近更新（P1 收尾）**：2026-06-20（**P1 收尾项全做完 + 一处真实 bug 修复**：① **8087 自鉴权** `AgentSelfAuthFilter`（闭合 AccessGuard 内网信任在直连 8087 下的 tokenless 缺口，豁免 actuator/_internal，7 单测）；② **8095 knowledge-rag 服务端 token** `McpTokenMiddleware`（纯 ASGI 保流式，5 单测；临装 fastmcp 2.14 验证 `http_app(middleware=)` 真挂栈后卸载）—— 至此 A3 全链 token 闭合（client+8094+8095）；③ **字段权限对 list/page 漏脱敏 bug** —— `FieldPermissionAdviceWalkTest` 端到端单测抓到 `walk()` 先判 isLeaf 致容器(java.*)被当叶子跳过，列表响应从不脱敏；修复容器递归提到 isLeaf 前；④ e2e runbook 触发链补登录取 token（鉴权 ON 下可跑）。6 个独立 commit，`mvn clean install` 11 模块全绿 + Python 19 例全过。剩余仅用户侧 e2e 起栈验证 + CI 覆盖率%门）
> **历史最近更新（阶段三/四）**：2026-06-20（对应 `docs/PROJECT_REVIEW_2026_06_10.md` §5.3/§5.4：**§5.3**：T12 GlobalExceptionHandler 下沉 common 自动装配 + BusinessException（A7）；T13 Python CORS 白名单+关 credentials（A4）；T14 JWT 密钥 fail-fast 无默认（A5，infra/种子账号留文档）；T16 字段权限默认开 + 内网放行安全前提（端用户按角色脱敏、内网 Feign 放行不破坏 AI 取数）+ canSee 单测；T15 单测已补、CI 覆盖门槛未加（◐）。**§5.4**：T17 清 14 个 .pyc + 补 .gitignore；T19 CLAUDE.md/rules 版本模型名漂移；T20 getMentalIndicators 显式排序 + 前端 HTTP 401 自动登出；T18 PII 目录已排除。8 个独立 commit，`mvn clean install` 11 模块全绿 + Python 14 例全过。**剩余仅用户侧 e2e 运行验证 + 2 个 P1 收尾（8087 自鉴权、CI 覆盖门槛）**）
> **历史最近更新（阶段二瘦身）**：2026-06-19（对应 `docs/PROJECT_REVIEW_2026_06_10.md` §5.2，目标定位＝简历/答辩、亮点链＝Agent 风险画像全链：**删/降**：B5 Hybrid Retrieval（RAG 回纯 dense）、B3 ModelRouter+cloudChatClient（直连本地）、B1 四层记忆+memory MCP(8096)、B4 灰度百分比→单布尔、B7 SkillLoader→classpath 静态；**接线亮点**：AgentLoop 设默认主路径（`educare.agent.loop.enabled` 默认 true）、保留 2 个 MCP server（8094/8095）作展示、A3 余项加 MCP 内部预共享 token（`educare.mcp.token` gated：client customizer + 8094 filter，8095 Python 侧待运行验证）；**不采纳**：B2 MCP 收回内联（保留作亮点）、B6 Milvus→pgvector（RAG 灌库依赖）。6 个独立 commit，`mvn clean install` 11 模块全绿 + Python 14 例全过）
> **历史最近更新（阶段一安全止血）**：2026-06-19（对应 `docs/PROJECT_REVIEW_2026_06_10.md` §5.1 T1-T4：① 余下 IDOR 闭环 —— agent（`ExportController`/`AgentTaskController`/`InterventionFeedbackController`）+ data（`DashboardController`）统一经 `common/AccessGuard.allowSelfRoleOrInternal(STAFF_VIEW)` 限教职工或内网，堵住「猜 jobId 下载他人报告 / 学生拉全校看板」；② **A6 登出撤销** —— gateway 加 reactive Redis，`JwtAuthGlobalFilter` 签名校验后再比对会话白名单 `token:{userId}`（值相等才放行），登出/改密后旧 token 立即 401（开关 `educare.gateway.auth.check-session` 默认开，Redis 异常 fail-closed 503）；③ 补单测 —— gateway 10 例（+3 A6 会话）/ data `DashboardControllerTest` 3 / agent 三控制器 3+4+3，`mvn clean install` 全绿。**A3 余项**（MCP 8094/8095 应用层 token）移交 Phase 2 T6。修复中发现并改正一处 reactive bug：`deny()` 返回 `Mono<Void>` 会被 `switchIfEmpty` 误判空→二次 deny，改 `defaultIfEmpty("")`）
> **历史最近更新（安全增量第一批）**：2026-06-15（① 网关 P0 鉴权门 `JwtAuthGlobalFilter`（默认开 `educare.gateway.auth.enabled`，非公开路由校验 JWT→401，`/_internal/`→403，token 双轨 header/`?token`）补「无 token 直达业务服务」缺口；② mental-service `StudentMentalController` 修横向越权 IDOR（4 端点断言 `userId`==token subject 否则 403）；③ docker-compose 把 8087/8094-96 收紧到 `127.0.0.1` 仅本机；④ 订正 CLAUDE.md 鉴权描述。gateway 7 例 + mental 7 例单测全绿。⚠ 注：Phase J 已于 2026-06-06 全完结（见 §10），此前 §1 指针/本行滞后，现一并回写）
> **历史最近更新（I-5）**：2026-06-05（I-5 干预反馈闭环完成：`sql/init/05_intervention_feedback.sql` + agent-service `InterventionFeedback` entity/mapper/dto/service/controller（POST 提交 + GET 月报 JSON + GET report.csv 导出，手动校验 score 1-5/outcome 枚举/task 存在）+ 前端 `ReportDetail.vue` "干预效果跟进"评分卡片（el-rate + 结果下拉 + 文字反馈）+ `api/agent.js` 3 个 API；`InterventionFeedbackServiceTest` 7 case，agent-service 共 32 全绿，Vue SFC 编译通过。**Phase G/H/I（I-2 之前）规划项全部完结**，剩余仅储备项 I-2/I-3/I-4/I-6 + 用户侧实跑 smoke）
> **历史最近更新（I-1）**：2026-06-05（I-1 Hybrid Retrieval 完成：决策"进程内 BM25 + RRF"（不加 ES、不改 Milvus schema，`HYBRID_RETRIEVAL_DESIGN.md`）；`hybrid_retrieval.py` 纯函数（中英 tokenizer + 自实现 BM25Okapi + rrf_fuse + fuse_hits）；`rag_pipeline` 加 `enable_hybrid` + `RAG_HYBRID_ENABLED` 灰度，knowledge-rag 三 tool 自动透传；`eval/hybrid_eval.py` + `hybrid_queries.jsonl` 用 top-3 命中率对齐验收（不引 RAGAS 重依赖）；`tests/test_hybrid_retrieval.py` 9 case，Python 共 33 全过。Phase I 仅剩 I-5，指针指向 I-5.1）
> **历史最近更新（H-6）**：2026-06-05（`.github/workflows/eval-gate.yml` 两段式 eval gate —— validate-dataset（无条件 stdlib 体检 50 例）+ eval-threshold（`EVAL_LLM_ENABLED=true` 才跑，`--threshold 0.85` + 可选 baseline 回退检测）；`run_eval.py` 加 `validate_cases()` + `--validate-only/--threshold/--baseline/--baseline-tolerance`；`eval/README §5` 重写。**Phase H 全部完结（H-1~H-6）**，进入 Phase I，指针指向 I-1 Hybrid Retrieval）
> **历史最近更新（H-5）**：2026-06-05（自实现轻量记忆层（决策放弃 Mem0/Letta SDK，`MEMORY_DESIGN.md` §1）；`memory_store.py` 四层 Redis（Working/Episodic/Semantic/Procedural）+ `app/mcp/memory_{adapter,tools,server}.py` memory-server MCP（FastMCP，端口 8096，Streamable HTTP `/mcp`，3 工具 recall/save/summarize）；`redis_client` 改延迟 import；`tests/test_memory_store.py` 13 case，Python 共 25 全过。不强接 AgentLoop（接线留灰度）。H-1~H-5 完结，指针推进至 H-6 eval gate CI）
> **历史最近更新（H-4）**：2026-06-05（双 ChatClient（本地 `@Primary` + `cloudChatClient`）+ `router/ModelRouter`（敏感/原始画像→本地、方案/审核→云端、云端未就绪 fail-safe 回落本地）+ 审计 logger `MODEL_ROUTER_AUDIT` + `educare.model.routed{tier,stage}` 计数器；`SpringAiConfig` 本地保留 cache_prompt+metrics 拦截器、云端只挂 metrics；`RiskAnalyzeService` 改经 router 强制本地；`application.yml` 加 `educare.model.{local.base-url,cloud.*}`（云端 key 走 env/Nacos 加密）；`ModelRouterTest` 7 case，agent-service 共 25 全绿。H-1~H-4 完结，指针推进至 H-5 记忆层）
> **历史最近更新（H-3）**：2026-06-05（4 个技能 markdown（`agent-service/src/main/resources/skills/`，classpath 内置）+ `SkillLoader`（外部目录 `educare.agent.skills.dir` 按 mtime 热更新 / classpath 兜底）+ `educare.agent.skills.active` 逗号有序"按需选择" + `AgentTaskServiceImpl.runAgentLoopForTask` 注入 `composeActiveSkillsPrompt()` 到 AgentLoop system prompt（空串时不破坏 G-1 cache 字节稳定）；`SkillLoaderTest` 7 case，agent-service 共 18 case 全绿。H-1/H-2/H-3 全部完结，指针推进至 §3 H-4.1 ModelRouter）

> **历史最近更新（H-2.4）**：2026-06-05（布尔升级为按 taskId 分桶的百分比灰度 + `AgentLoopCanaryGate` + `agent_loop_canary.sh`）
> **历史最近更新（H-2.3）**：2026-05-22（H-2.3 完成：`application.yml` 加 `educare.agent.loop.enabled`（默认 false，env `EDUCARE_AGENT_LOOP_ENABLED`）；新增 `classpath:prompts/agent-loop.system.md` AgentLoop system prompt（字节稳定，命中 G-1 cache，规约 `final_answer` schema = `{risk_analysis, intervention_plan}`）；`AgentTaskServiceImpl` 注入 `AgentLoop` + `ObjectProvider<ToolCallbackProvider>` + `@Value` 开关 + `@PostConstruct` 加载 prompt；`doExecute` 入口加 feature flag 分支：开关开 → `doExecuteAgentLoop`（AgentLoop 一次性出风险+方案，状态机仍流转 PENDING→RISK_ANALYZING→KNOWLEDGE_RETRIEVING→PLAN_GENERATING→COMPLIANCE_CHECKING→COMPLETED/REJECTED，低/无风险短路保留，P4 合规审核仍走旧 audit），开关关 → `doExecuteLegacy` 旧 4 阶段；`AgentLoopParsed` 内部 record 解析 final_answer 拆 risk/plan/level 写库，解析失败 FAILED；`mvn -pl agent-service compile` 通过，`AgentLoopTest` 4 case 全绿。指针推进至 H-2.4 灰度切流脚本 + eval 回归）

---

## 0. 使用约定（Update Protocol）

每次执行流程：

1. **读** 本文件，跳到 §1 "下一步指针"，确认要做什么
2. **执行** 该原子任务（可能跨多个文件 / 多次 tool 调用）
3. **回写** 本文件：
   - 把该任务前的 `[ ]` 改为 `[x]`
   - 在该任务下方追加一行 `- 完成于 YYYY-MM-DD：<一句话备注，含关键 commit 或文件路径>`
   - 更新 §1 的 "下一步指针" 为新的第一个未完成项
   - 顶部 "最近更新" 改为今日日期 + 一句变更摘要
4. **不要** 在本文件里写实现细节、代码片段或长篇分析 —— 那些放进 commit message 或 PR 描述
5. 重大决策变更（如某子任务被拆分 / 合并 / 砍掉）→ 在文件底部 §6 "变更记录" 追加一行说明，再改任务列表

任务粒度原则：每条 `[ ]` 应是 **0.5-2 天可完成 + 单次 PR 可合并** 的尺度。如果发现某条远超此粒度，先拆分再开工。

---

## 1. 下一步指针（Next Action）

**当前阶段**（2026-08-27）：Phase G/H/I/J 的保留代码已落地，当前进入 §12 **Release Readiness**。目标是让瘦身后的真实交付面可上线：AgentLoop 默认主路径 + student-data/knowledge-rag 两个 MCP + dense RAG + 干预闭环 + 安全/运维基线。历史上已删除的 memory-server、Hybrid Retrieval、ModelRouter、百分比灰度不属于上线范围，不恢复、不作为环境阻塞。
**下一步**：执行 **R-5.1 Langfuse 真 trace 验收**；随后按 R-5.2~R-5.4 完成 Qwen 14B、BGE embedding/dense baseline 与真模型质量门。R-1~R-4 已闭环；U-8 已完成范围裁决，不再重建已删除能力。
- **I-5 完结**：`intervention_feedback` 表 + agent-service 反馈提交/月报/CSV 接口 + `ReportDetail.vue` 评分组件
- **I-1 完结**：进程内 BM25 + RRF（不加 ES/不改 Milvus schema）`hybrid_retrieval.py` + `RAG_HYBRID_ENABLED` 灰度 + `eval/hybrid_eval.py` top-3 命中率评测 + `HYBRID_RETRIEVAL_DESIGN.md`
- **待实跑（用户侧）**：① 灰度切流 `agent_loop_canary.sh`（需 MCP server + llama.cpp + Nacos）② memory-server 8096 mcp-inspector 验 3 tool ③ hybrid `RAG_HYBRID_ENABLED=true` 跑 `eval/hybrid_eval.py`（需 Milvus 已灌库）④ 加载 `05_intervention_feedback.sql` 后验证反馈提交/CSV ⑤ G-2.3/G-3.4 既有待实跑项
- **H-6 完结**：`.github/workflows/eval-gate.yml` 两段式（validate-dataset 无条件 + eval-threshold 条件）；`run_eval.py` 加 `--validate-only/--threshold/--baseline`
- **H-5 完结**：自实现轻量记忆层（`memory_store` 四层 Redis）+ memory-server MCP（8096，3 工具）+ `MEMORY_DESIGN.md`；不强接 AgentLoop，接线留灰度
- **H-4 完结**：双 ChatClient（本地 `@Primary` + `cloudChatClient`）+ `ModelRouter`（敏感→本地、方案/审核→云端、未就绪 fail-safe 回落）+ 审计 logger `MODEL_ROUTER_AUDIT` + `educare.model.routed` 计数器；`RiskAnalyzeService` 经 router 取本地
- **H-3 完结**：4 个技能 markdown（classpath `skills/`）+ `SkillLoader`（外部目录 mtime 热更新 / classpath 兜底）+ `educare.agent.skills.active` 按需选择 + 注入 AgentLoop system prompt
- **H-2.4 完结**：灰度切流从布尔升级为**按 taskId 确定性分桶的 `canary-percent`（0-100）** —— `AgentLoopCanaryGate`（`@RefreshScope`，Nacos `agent-canary.yml` 热生效）+ `scripts/agent_loop_canary.sh`（逐档 0→10→50→100，触发真实任务 + 读 `educare_agent_loop_routed_total` 路由增量 + FAILED 即回滚）。⚠ 原计划"跑 run_eval.py 对比 baseline"作废：`run_eval.py` 打 Python 8090 不经 Java flag，无法验证 AgentLoop 路径
- **H-2.3 完结**：`AgentTaskServiceImpl.doExecute` 入口挂切流分支 —— 命中 AgentLoop 路径即一次性出 risk+plan（含 MCP 7 工具 + final_answer 双 JSON 解析 + 状态机完整流转 + P4 合规审核保留）；未命中走 legacy 4 阶段。`classpath:prompts/agent-loop.system.md` 字节稳定 prompt
- ~~**遗留待办**：`/agent/api/v1/_internal/loop/dry-run` 零 auth~~ **已闭（2026-06-15）**：网关 `JwtAuthGlobalFilter` 对所有 `/_internal/` 路径一律 403，公网网关不再暴露内网手测端点。**端点本身已于 2026-06-24 删除**（AgentLoop 为默认路径，手测脚手架使命结束；见 §6）
- **H-1 子阶段全部完结**：H-1.1（选型）+ H-1.1.5（Spring AI 1.0.0 GA）+ H-1.2（student-data MCP server，8094）+ H-1.1.6（Spring AI 1.1.6 GA + MCP transport 全栈 Streamable HTTP）+ H-1.3（FastMCP knowledge-rag MCP server，8095）+ H-1.4（`mcp_smoke_test.sh` 一键回归脚本）均已合入
- **H-2.1/H-2.2 完结**：`com.edu.agent.core.AgentLoop` ReAct JSON 协议；接 Langfuse 顶层 trace + MCP `ToolCallbackProvider` 7 工具自动注入；`AgentLoopTest` 4 case 全绿
- **H-1.2 / H-1.3 用户侧 smoke 一键化**：两个 server 起好后执行 `bash scripts/mcp_smoke_test.sh`（依赖 bash≥4 / curl / jq）即可完成 7 个 tool 的 happy path 回归，取代手动 mcp-inspector 流程
- **Phase G 全部完成**（代码侧）；只剩用户侧 `FIELD_PERMISSION_VERIFY.md`（G-2.3）实跑回写
- **smoke 剩余 3 项待用户实跑**：本地 llama.cpp 起后跑一次 `/agent/api/v1/task/trigger/{id}`，验证 `LlamaCppCachePromptInterceptor` + `LlmMetricsInterceptor` + Langfuse trace 推送（见 `MCP_DESIGN.md §2` 项 2-4）；H-2.3 切流后还需对 `EDUCARE_AGENT_LOOP_ENABLED=true` 跑一次 e2e（要求两个 MCP server + 本地 llama.cpp 全部在线）

---

## 2. Phase G —— 快速赢 + 基础设施（第 1-2 周）

### G-1 Prompt Caching（llama.cpp slot cache 路线）

- [x] **G-1.1** 盘点 4 个 LLM 调用点
  - 完成于 2026-05-12：见 IMPROVEMENT 文档；4 点为 Java `RiskAnalyzeService.java:73-77` + Python `agent.py` 的 risk/plan/audit 三处
- [x] **G-1.2** 锁定 llama.cpp slot cache 路线（不走 Spring AI 1.1+ cache_control）
  - 完成于 2026-05-12：决策记录在 IMPROVEMENT §0
- [x] **G-1.3** 抽离 prompts 到独立资源文件（保证字节稳定）
  - 完成于 2026-05-12：Python 侧已落到 `ai-inference-service/app/prompts/{risk,plan,audit}.system.md`；Java 侧 `RiskAnalyzeService.java:24-28` 已附 byte-stability 注释
- [x] **G-1.4** 透传 `cache_prompt:true` 给 llama.cpp（双侧）
  - 完成于 2026-05-13：
    - Python：新增 `chat_completion_raw` (httpx) 走 `/v1/chat/completions`，body 注入 `cache_prompt:true`；`agent.py:_call_llm_json` 改用此函数（`llm_client.py` + `agent.py`）
    - Java：新增 `LlamaCppCachePromptInterceptor`（拦截 `/chat/completions` 路径，向 JSON body 注入 `cache_prompt:true`），通过自定义 `RestClient.Builder` 挂到 `OpenAiApi` bean（`SpringAiConfig.java` + `LlamaCppCachePromptInterceptor.java`）
    - 副带修复：`AgentTaskServiceImpl.java:311/336` 预先存在的 Long→String 编译错（mirror line 272 的 `String.valueOf` 模式），否则 mvn compile 阻塞
- [x] **G-1.5** 拦截 LLM 响应，抓 `timings.cached_n / prompt_n`，本地累加 metrics
  - Python ✅ 完成于 2026-05-13：与 G-1.4 耦合实现 —— `chat_completion_raw` 拿到原始 JSON 后自动调 `record_llm_response(route, data)`，按 risk/plan/audit 三路分别统计
  - Java ✅ 完成于 2026-05-13：新增 `LlmMetricsInterceptor`（`ClientHttpRequestInterceptor`），response 侧拿 buffered body，抓 `timings.{prompt_n, cached_n, predicted_n, prompt_ms}` 喂 5 个 Micrometer meter（`educare.llm.{prompt_tokens, cached_tokens, completion_tokens, calls}` counter + `educare.llm.prefill` timer）；通过 `SpringAiConfig.openAiApi(LlmMetricsInterceptor)` 注入；`cache_hit_rate` 不写 gauge，留 Prom 端 PromQL 算
- [x] **G-1.6** 暴露 `/api/v1/diagnostics/llm-metrics` 端点 + 验收脚本
  - Python ✅ 完成于 2026-05-13：新增 `app/api/diagnostics.py` 注册到 `main.py`；脚本 `scripts/verify_prompt_cache.sh`（连发两次同 payload，看增量 `cached_tokens / prompt_tokens ≥ THRESHOLD`，默认 0.8）；手册 `docs/educare/PROMPT_CACHE_VERIFY.md`
  - Java ✅ 完成于 2026-05-13：新增 `DiagnosticsController` 路径 `/agent/api/v1/diagnostics/llm-metrics`，从 `MeterRegistry` 读 6 个数（calls / prompt / cached / completion / hit_rate / prefill 累计）；Prom 全量 exposition 仍走 `/actuator/prometheus`

### G-2 后端字段级权限

- [x] **G-2.1** 设计字段权限模型（角色 → 字段白名单），输出 `docs/educare/FIELD_PERMISSION.md`
  - 完成于 2026-05-13：覆盖 6 个角色 × 4 档分级（PUBLIC/MEDIUM/HIGH/EXTREME）矩阵 + 4 个 entity 字段总表；落地方案选 `@SensitiveField` 注解 + `ResponseBodyAdvice`；拆出 G-2.2 五个子步 a-e；行级权限与 audit_log 明确推迟 Phase I-4
- [x] **G-2.2** 按 `FIELD_PERMISSION.md §6` 落地实施
  - [x] **G-2.2-a** `common/.../security/`：`SensitiveField` 注解 + `Sensitivity` 枚举 + `FieldPermissionAdvice`（`@RestControllerAdvice` 实现 `ResponseBodyAdvice`）+ 反射缓存
    - 完成于 2026-05-13：4 个新文件（Sensitivity / SensitiveField / RequestContext / FieldPermissionAdvice）；advice 用 `@ConditionalOnProperty(educare.field-permission.enabled=true)` 默认关闭，避免链路未补齐时误伤；矩阵实现见 `FieldPermissionAdvice.canSee`，与 `FIELD_PERMISSION.md §4` 列级部分一致；`mvn -pl common -am compile` 通过
  - [x] **G-2.2-b** JWT 加 `roles: List<String>` claim：`auth-service/.../AuthServiceImpl.java` 登录时查 `sys_role` 写入；`common/.../JwtUtil.java` 加 `parseRoles()`
    - 完成于 2026-05-13：抽 `buildClaims(User)` 复用于 login + refreshToken（防刷新后角色丢失）；`JwtUtil.parseRoles()` 缺失/异常一律返回空 `Set`（调用方按无角色处理）；`mvn -pl auth-service,common -am compile` 通过
  - [x] **G-2.2-c** `RoleContextFilter`（`common` 模块，`OncePerRequestFilter`），把 JWT roles 解到 `RequestContext`
    - 完成于 2026-05-13：`Bearer` 头解析 → `JwtUtil.parseRoles` → `RequestContext.setRoles`，`finally` 清 ThreadLocal 防线程复用串角色；非 Bearer / 空 / 非法 token 一律不灌（advice 走"无角色"降级）；不做 auth 决策（拦截由 auth-service 链路负责）；类未加 `@Component`，由 G-2.2-e 用 `FilterRegistrationBean` 注册
  - [x] **G-2.2-d** 给 4 个 entity（Student / Teacher / MentalAssessment / User）按 `FIELD_PERMISSION.md §3` 加 `@SensitiveField` 注解；User.password 用 `@JsonIgnore` 永不返回
    - 完成于 2026-05-13：Student（birthDate=HIGH, gpa/credits=MEDIUM）；Teacher（birthDate=HIGH, education=MEDIUM）；MentalAssessment（score/result/suggestion=EXTREME, level=MEDIUM）；User × 2（auth-service + user-service 各一份，password 加 `@JsonIgnore`，email/phone=HIGH）；4/5 模块 `mvn compile` 通过，mental-service 因预先存在 `Question` 实体字段缺失导致编译断（与本任务无关，见 §8）；`gender` 等未列出字段不标，默认 PUBLIC，linter 上线后再补
  - [x] **G-2.2-e** `common` 模块加 `@AutoConfiguration`，所有 service 自动启用 advice 与 filter
    - 完成于 2026-05-13：新增 `FieldPermissionAutoConfiguration`（`@AutoConfiguration` + `@ConditionalOnProperty(educare.field-permission.enabled=true)`）；3 个 `@Bean`：advice / filter / `FilterRegistrationBean<RoleContextFilter>`（url `/*`，order `LOWEST_PRECEDENCE - 100`，让 auth filter 先跑）；登记到 `META-INF/spring/...AutoConfiguration.imports` 第 4 行；`mvn -pl common,auth-service,student-service,teacher-service,user-service -am compile` 通过
- [x] **G-2.3** 前端去除 "假脱敏"（信任后端返回），手动测试 admin / teacher / counselor / academic_advisor / student 五角色对同一 studentId 详情的字段集 diff，与 `FIELD_PERMISSION.md §4` 矩阵对齐
  - 代码部分完成于 2026-05-13：`frontend/src/directives/permission.js` JSDoc 改写为 "UX-only，非安全防线"（G-2.2 后后端 advice 是权威）；新增 `docs/educare/FIELD_PERMISSION_VERIFY.md`（启用配置、5 角色测试账号、3 接口期望矩阵、一键 curl+jq 脚本、5 类故障排查表、通过标准 checklist）
  - 手测部分 ⏳ 待用户运行 `FIELD_PERMISSION_VERIFY.md §4` 脚本；通过后回写勾选 + 顶部加"验收通过"
  - 完成于 2026-08-26：U-6 五角色字段矩阵、gateway_verify、登出吊销与 IDOR 活体断言全部通过，见 §11 U-6。

### G-3 启用 E-1 定时扫描 + Prometheus 监控

- [x] **G-3.1** 在 `agent-service/pom.xml` 加 `micrometer-registry-prometheus` 依赖 + `actuator` 暴露 `/actuator/prometheus`
  - 完成于 2026-05-13：pom 加 `spring-boot-starter-actuator` + `io.micrometer:micrometer-registry-prometheus`；`application.yml` 加 `management.endpoints.web.exposure.include: health,info,prometheus` + `management.metrics.tags.application=${spring.application.name}`；jar 已解析（micrometer 1.12.5 / actuator 3.2.5）；`mvn -pl agent-service -am compile` 通过
- [x] **G-3.2** 给 `DailyScanScheduler` 加 Micrometer counter / timer（trigger 总数、失败数、单次耗时）
  - 完成于 2026-05-13：注入 `MeterRegistry`，`@PostConstruct` 预创建 3 个 meter（`educare.daily_scan.triggered` / `educare.daily_scan.failed` 两个 Counter + `educare.daily_scan.duration` 一个 Timer）；scanAll 循环里同步 `counter.increment()`，finally 块用 `scanTimer.record(elapsedMs, MILLISECONDS)`；指标名走点号分隔，Prometheus exposition 时自动转下划线带 `_total/_seconds` 后缀；`mvn -pl agent-service -am compile` 通过
- [x] **G-3.3** 把 G-1.5 的 LLM metrics 也接 Micrometer（cache_hit_rate gauge、tokens_total counter）
  - 完成于 2026-05-13：随 G-1.5 Java 一同实现 —— `LlmMetricsInterceptor` 已是 Micrometer 端，所有 LLM 调用经 Spring AI 时自动累加；Python 侧已有 `llm_metrics.snapshot()` 透出，未来 G-5 Langfuse 接入后这层指标合并到 trace 维度
- [x] **G-3.4** Nacos 里把 `educare.schedule.enabled` 在测试环境置 true，本地拉一次跑通
  - 验收手册完成于 2026-05-13：`docs/educare/SCHEDULE_METRICS_VERIFY.md` —— 6 节启动/配置/触发/验证流程 + 6 类故障排查表 + 后续 Prometheus/Grafana 接入建议；推荐用 `EDUCARE_SCHEDULE_CRON="0 */1 * * * ?"` 把 cron 改为每分钟避免等到 02:00
  - 实跑验证 ⏳ 待用户在本地启 mysql/redis/nacos + agent-service 后执行手册

### G-4 增量知识导入 API

- [x] **G-4.1** 设计 `/api/v1/rag/upsert`（Python 侧）的 schema：`{collection, doc_id, text, metadata, chunk_strategy}`
  - 完成于 2026-05-13：`docs/educare/RAG_UPSERT_DESIGN.md` 10 节 —— Pydantic 请求/响应 schema、Milvus 字段映射决策（不改 schema，用 `chunk_id={doc_id}_{idx:04d}` 命名约定承担分组）、3 种 chunk 策略（none/fixed_size/sentence）、delete-then-insert 幂等流程（解释为何不用 pymilvus.upsert）、`X-Admin-Token` 鉴权 + doc_id+text-hash 短路、字段名/路径/状态码约定、已知限制（metadata 只 title/source 入库）
- [x] **G-4.2** 实现：BGE 嵌入 → Milvus upsert（已有 collection 走 update，新 doc 走 insert）
  - 完成于 2026-05-13：3 个新/扩文件 ——
    1. `app/services/text_splitter.py`（none/fixed_size/sentence 三策略 + `MAX_CHUNKS=200` 上限）
    2. `app/services/milvus_client.py` 扩 `delete_by_doc_id(collection, doc_id)` 走 `chunk_id like "{doc_id}_%"` + `insert_chunks(collection, rows)` 批量插入并 flush
    3. `app/api/rag_upsert.py` 路由 `POST /api/v1/rag/upsert`：validate → split → 顺序 embed N 次（带 24h emb 缓存）→ delete-then-insert → 返回 UpsertResponse 含 chunks_written / deleted_first / embedding_ms / milvus_ms；422/503 错误码按设计文档 §8
  - `main.py` 注册 router；`ast.parse + 3 个 splitter 行为断言` 全过；鉴权 + 幂等短路留 G-4.3
- [x] **G-4.3** 加 admin token 鉴权 + 幂等键（doc_id 去重）
  - 完成于 2026-05-13：
    - `app/core/config.py` 加 `ADMIN_TOKEN`（env `EDUCARE_ADMIN_TOKEN`，默认空）+ `UPSERT_HASH_TTL`（默认 7d）
    - `rag_upsert.py` `_check_admin_token` 走 `hmac.compare_digest` 防时序攻击；ADMIN_TOKEN 未配置时 503（fail-closed），不匹配 401
    - 幂等：`sha256(text)[:32]` 存 Redis key `edu:rag:upsert:hash:{collection}:{doc_id}`；命中直接 `UpsertResponse(skipped=true, ...=0)`；未命中走完流程后 `cache_setex` 写入
    - `tests/test_text_splitter.py` 14 个 `unittest` 用例覆盖 4 类（none/fixed_size/sentence/unknown）+ 常量；`python -m unittest tests.test_text_splitter` 全过
    - **G-4 段全部完成**

### G-5 Langfuse 接入

- [x] **G-5.1** Docker compose 加 Langfuse self-hosted（postgres + langfuse-server），或确认走云端实例（决策点）
  - 完成于 2026-05-14：决策选 self-hosted（已有 compose 编排 + traces 数据非敏感无需云端隔离 + 免 API key/付费层）；用 v2（2 容器）而非 v3（5 容器，clickhouse + redis + worker）；compose 加 `langfuse-postgres`（pg15-alpine）+ `langfuse-server`（langfuse/langfuse:2，3000 → 宿主 3001 避免与 attu 冲突）+ `langfuse_pg_data` 卷；用 `profiles: [langfuse]` 默认不启（resume 场景不浪费），按需 `docker-compose --profile langfuse up -d`；env 占位用 `${LANGFUSE_DB_PASSWORD:-langfuse123}` 等默认，生产必须改
- [x] **G-5.2** Python 侧：`requirements.txt` 加 `langfuse`，在 `_call_llm_json` 包一层 `@observe`
  - 完成于 2026-05-14：`requirements.txt` 加 `langfuse>=2.36,<3.0`（v2 SDK 对应 v2 server）；`config.py` 加 `LANGFUSE_PUBLIC_KEY/SECRET_KEY/HOST` 三个 env；`llm_client.py` 用 try-import + no-op fallback 降级（SDK 缺失或 keys 为空时透明无影响）；`chat_completion_raw` 加 `@observe(as_type="generation")` 装饰器，调 `langfuse_context.update_current_observation` 两次：调用前打 `name=llm.{route}`/model/input/metadata，调用后透 llama.cpp `timings.{prompt_n,predicted_n,cached_n,prompt_ms}` 到 `usage` + `metadata`；`ast.parse + import` 在无 langfuse 包环境下通过
- [x] **G-5.3** Java 侧：`pom.xml` 加 `langfuse-java`（若无则用 OTel exporter），在 ChatClient 拦截器埋 trace
  - 完成于 2026-05-14：决策 **不引入 unofficial Java SDK 也不走 OTel**（Langfuse v2 不支持 OTel），直接 HTTP POST `/api/public/ingestion`：
    - `application.yml` 加 `langfuse.{public-key,secret-key,host}` env passthrough
    - 新 `LangfuseClient.java`：`@PostConstruct` 检 keys 三者全配才 `enabled`，否则全部 trace 调用静默 no-op；`traceGeneration(...)` 走 `@Async("agentExecutor")`（复用 G 已有的执行器）批 POST 一条 `trace-create` + 一条 `generation-create`，HTTP Basic 鉴权 `public:secret`
    - 扩 `LlmMetricsInterceptor`：从 request body 抽 `model + messages` + 从 response 抽 `output + timings`，调 `langfuseClient.traceGeneration` 异步上报；任何解析异常仅 debug 日志
    - `mvn -pl agent-service -am compile -q` 通过
- [x] **G-5.4** 前端 "管理员追踪" 页 iframe 嵌 Langfuse（路由在 `frontend/src/router/index.js`）
  - 完成于 2026-05-14：新增 `frontend/src/views/admin/trace.vue`（满高 iframe + 刷新/新窗口动作 + 未配置 URL 时友好 empty 占位）；router `/admin/trace` 加在 `Admin` 子节点（继承父 `roles: ['admin']`，title 'LLM 追踪'，icon 'Connection'）；`frontend/.env.example` 落 `VITE_LANGFUSE_URL=http://localhost:3001` 模板；iframe `sandbox="allow-same-origin allow-scripts allow-forms allow-popups"` 允许 Langfuse SPA 必需特性

### G-6 Braintrust eval 集启动构建（先 50 例）

- [x] **G-6.1** 决策：Braintrust SaaS vs 自建 promptfoo（决策点）
  - 完成于 2026-05-14：选 **promptfoo**。三条理由：(a) resume 项目无付费 tier 预算；(b) YAML 配置 + JSONL 数据集随代码 git 版本化，对齐 "Eval 驱动迭代" 原则；(c) Langfuse（G-5）已覆盖**在线 trace**，离线 eval 走轻量 CLI 即可，不需要再付费拿一个 SaaS dashboard。运行方式留 G-6.4 README 详写
- [x] **G-6.2** 在 `eval/` 目录建 `risk_assessment.jsonl` 50 例（input: 学生画像快照；expected: 风险等级 + 理由要点）
  - 完成于 2026-05-14：`eval/risk_assessment.jsonl` 50 条；每条含 `id` / `description` / `input.student_profile`（覆盖 GPA / failedCourses / attendanceRate / mentalHealthLevel / familyEconomicLevel / counselorNotes 等 7-10 个字段）/ `expected.{risk_level, primary_type_hints, key_phrases}`；分布：等级 none=8 / low=12 / medium=17 / high=13；类型覆盖 学业 18 / 心理 29 / 经济 8 / 社交 7（部分多打标签）；边缘 2 例（RA-049 数据缺失、RA-050 高 GPA + 重度量表矛盾信号）；JSON 行解析全过
- [x] **G-6.3** 写一个 `eval/run_eval.py` 跑现有 risk endpoint 并打分（faithfulness / 等级一致率）
  - 完成于 2026-05-14：异步 `httpx + asyncio.Semaphore` 并发跑（默认 4），三指标：等级一致率（exact）/ 等级加权分（相邻 0.5）/ 关键短语命中率（substring 大小写不敏感）；输出 JSON 全量 + 可选 MD 报告 + 控制台进度；混淆矩阵 + 失败用例列表；CI 退出码（exact ≥ 0.6 → 0，否则 1）；httpx 走 lazy import，纯函数（`_level_score / _phrase_hits / _flatten_response`）在无 httpx 环境也能验证；用例：`python eval/run_eval.py --base-url http://localhost:8090 --md eval/run_results.md`
- [x] **G-6.4** README 说明运行方式；CI 接入留到 H-6
  - 完成于 2026-05-14：`eval/README.md` 8 节 —— 快速开始（一行命令）、文件清单、JSONL schema（每字段语义）、4 个指标定义 + 计算公式、CI 接入草案（GitHub Actions YAML + 阈值演进路径 0.6 起步）、增量维护规则（等级分布约定、改 ground truth 的 PR 要求）、5 类已知限制（覆盖度/RAGAS 替代/三 endpoint 单点/多标签未启/温度未锁）、与 Langfuse 在线 trace 的分工说明；**Phase G 代码部分全部完成**

**Phase G 验收总标准**：
- Langfuse 看到完整 trace（含 tool 调用、token 数、cache 状态）
- `/agent/api/v1/diagnostics/llm-metrics` 返回 cache_hit_rate ≥ 50%（同学生重复触发场景下 ≥ 80%）
- E-1 调度器在测试环境每日跑通，Prometheus 抓到指标
- 字段权限通过角色矩阵手测
- Eval 集 ≥ 50 例可本地跑通

---

## 3. Phase H —— MCP 化 + Agent Loop 重构（第 3-6 周，瘦身版）

> 瘦身决策（IMPROVEMENT §0）：仅 student-data + knowledge-rag 2 个 MCP Server + 主 Agent Loop + 4 个 Skill + Model Router（本地/云端双路由）。H-5/H-6 视进度。

### H-1 MCP Servers（瘦身：仅 2 个）

- [x] **H-1.1** 选型确认：Spring AI MCP starter 版本与 stdio/SSE 传输模式
  - 完成于 2026-05-14：`docs/educare/MCP_DESIGN.md` 9 节决策 ——
    - Spring AI **升 1.0.0-M6 → 1.0.0 GA**（M6 无 MCP starter；GA 起有 `spring-ai-mcp-{server,client}-spring-boot-starter`）；升级 smoke checklist 4 项落在文档 §2
    - 传输模式 **统一 SSE / streamable HTTP**，拒绝混用 stdio：跨语言 + docker-compose 友好；唯一退路是性能不达标时改 stdio
    - SDK：Java 用 spring-ai 官方 starter，Python 用 `fastmcp>=0.4`
    - 端口规划：student-data=8094 / knowledge-rag=8095；agent-service application.yml 配置草案见 §4
    - 7 个 tool 命名约定（snake_case + typed args + JSON-serializable return）见 §5
    - 4 类风险与折中（API 漂移 / SDK patch / Python 依赖冲突 / spec 版本演进）见 §7
    - 明确推后：MCP 鉴权 / sandboxing / memory-server（瘦身后视进度）
- [x] **H-1.1.5** Spring AI `1.0.0-M6 → 1.0.0` GA 升级（单独 PR，H-1.2 前置）
  - 完成于 2026-05-19：5 处改动 ——
    1. `backend/pom.xml`：`spring-ai.version` 改 `1.0.0`；移除 `spring-milestones` 仓库声明（GA 已上 Maven Central）
    2. `backend/agent-service/pom.xml`：starter artifactId 改 `spring-ai-starter-model-openai`（GA 命名规约 `spring-ai-starter-model-{model}`），删去显式 `spring-ai-core`（starter 已传递依赖）
    3. `AgentServiceApplication.java`：exclude 改 `org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration`（GA 把 auto-config 按 model/vector/mcp 拆包）
    4. `SpringAiConfig.java`：`new OpenAiApi(...)` → `OpenAiApi.builder().baseUrl(...).apiKey(...).restClientBuilder(...).webClientBuilder(...).build()`；`new OpenAiChatModel(...)` → `OpenAiChatModel.builder().openAiApi(...).defaultOptions(...).build()`；两个拦截器（`LlamaCppCachePromptInterceptor` + `LlmMetricsInterceptor`）通过 `restClientBuilder` 仍挂在 RestClient 链上
    5. `RiskAnalyzeService.java` 无需改：`chatClient.prompt().system().user().call().content()` fluent API 在 GA 保留
  - Smoke 项 1：`mvn -pl agent-service -am clean compile` 40 文件全过（M6→GA 后第一次 clean 编译，依赖能解析）
  - Smoke 项 2-4 留用户实跑：`/agent/api/v1/task/trigger/{id}` 一次 → 看 `/actuator/prometheus` 的 `educare_llm_*` 仍增长 + Langfuse UI 出现 trace
- [x] **H-1.2** `student-data-server`（Java）—— tools: `get_student_profile / get_academic_history / get_mental_indicators / get_attendance`
  - 完成于 2026-05-19：起独立微服务 `backend/mcp-student-data`（端口 8094），不内嵌 agent-service。模块入口 `McpStudentDataApplication.java` 通过 `MethodToolCallbackProvider` 显式登记 `StudentDataTools` 的 4 个 `@Tool`（1.0.x starter 不自动扫描，必须手动登记）。
  - **DDL 增量**：`sql/init/04_student_extras.sql` 新建 `student_academic_record` + `student_attendance`，含种子数据 student_id=1 共 3 门课 + 4 天考勤。
  - **student-service 域扩展**：`AcademicRecord`/`Attendance` entity + mapper + service + 两个 controller，端点 `GET /student/{id}/academic` / `/student/{id}/attendance` / `/student/{id}/attendance/summary`。不动既有 `StudentController.java`。
  - **mcp-student-data 模块**：pom 引 `spring-ai-starter-mcp-server-webmvc` + Nacos + Feign。`StudentDataTools` 通过 Feign 调 student-service + mental-service；`get_mental_indicators` 先 Feign 取 `Student.userId` 再调 `/mental/student/assessments?userId=`，保持 mental-service 改动 0。
  - **MCP_DESIGN.md 校准**：starter artifactId 改 `spring-ai-starter-mcp-server-webmvc`（GA 重命名）；1.0.x 用 `@Tool`+`@ToolParam` 且需 `MethodToolCallbackProvider` 手动登记；SSE 端点 `/sse`（连接）+ `/mcp/message`（消息）。
  - Smoke 待用户实跑：加载 04_student_extras.sql → `mvn -pl mcp-student-data spring-boot:run` → mcp-inspector 选 **Streamable HTTP** `http://localhost:8094/mcp`（H-1.1.6 后） → 4 个 tool 各调一次。
- [x] **H-1.1.6** Spring AI `1.0.0 → 1.1.6` GA 升级 + MCP transport 全栈切 Streamable HTTP（H-1.3 前置）
  - 完成于 2026-05-20：4 处改动 ——
    1. `backend/pom.xml`：`spring-ai.version` 改 `1.1.6`；MCP Java SDK 随之升到 0.18.2（依赖树验证）。1.0→1.1 核心 API（`@Tool`/`@ToolParam`/`MethodToolCallbackProvider`/`OpenAiApi.builder()`/`OpenAiChatModel.builder()`/`OpenAiChatAutoConfiguration`）保持 source-compatible，agent-service 40 文件 + mcp-student-data 8 文件 `mvn clean compile` 全过，零代码改动
    2. `mcp-student-data/application.yml`：删除 `stdio` / `sse-endpoint` / `sse-message-endpoint`；新增 `spring.ai.mcp.server.protocol=STREAMABLE` + `spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp`（实际 property key 通过 spring-ai-autoconfigure-mcp-server-common 1.1.6 的 spring-configuration-metadata.json 核实）
    3. `mcp-student-data` Javadoc 注释：`McpStudentDataApplication.java` + `StudentDataTools.java` 顶注 Spring AI 版本与传输协议表述更新
    4. `MCP_DESIGN.md §1/§3/§4/§9` + `deploy.md` 端口表协议列同步：拍板表 transport `SSE/streamable HTTP` → `Streamable HTTP`；§3 整段重写为"为什么选 Streamable HTTP"；§4 端点表 `/sse + /mcp/message` → 单端点 `/mcp`
  - Smoke 项 1：`mvn -pl mcp-student-data,agent-service -am clean compile` 全过
  - Smoke 项 2-4 留用户实跑：`mvn -pl mcp-student-data spring-boot:run` 后日志确认 `protocol=STREAMABLE` + mcp-inspector 选 Streamable HTTP → `http://localhost:8094/mcp` → 4 tool 可见
- [x] **H-1.3** `knowledge-rag-server`（Python FastMCP **2.x**，**Streamable HTTP**，端口 8095）—— tools: `search_cases / search_policies / search_psychology`
  - 完成于 2026-05-20：4 处改动 ——
    1. `ai-inference-service/app/services/rag_pipeline.py` 新文件：单 query × 单集合检索（embed → milvus_search → 可选 rerank → Redis 缓存），与 `app/api/rag.py` 的多源聚合管线职责分离；缓存 key `edu:rag:pipe:<sha256>`，TTL 沿用 `settings.RAG_CACHE_TTL`
    2. `ai-inference-service/app/mcp/` 新模块：`__init__.py` + `rag_adapter.py`（`search_collection` 入口，`top_k` 裁剪到 [1, 20]，空 query 早退） + `tools.py`（3 个 FastMCP `@mcp.tool` async：`search_cases`/`search_policies`/`search_psychology`，分别打到 `case`/`policy`/`psychology` collection） + `knowledge_rag_server.py`（独立进程 main，`mcp.run(transport="http", host, port, path)`）
    3. `requirements.txt` 加 `fastmcp>=2.3,<3.0`（原生 Streamable HTTP，spec 2025-03-26）
    4. `app/core/config.py` 加 `MCP_KNOWLEDGE_RAG_PORT=8095` + `MCP_KNOWLEDGE_RAG_PATH=/mcp`
  - **复用而非重写**：embedding_client / milvus_client / reranker_client / redis_client 全部沿用；`rag.py` `/api/v1/rag/retrieve` 路由零改动（其多源聚合语义与 MCP 单源 tool 不重合，不强抽公共层）
  - **独立进程**：不合入 FastAPI 主进程（8090），8095 独立 Uvicorn，资源/重启/调试独立；docker-compose 编排留 H-1.4 smoke 时再加
  - Smoke 项 1：`python3 -c "ast.parse(...)"` 5 文件语法全过
  - Smoke 项 2-4 留用户实跑：`pip install -r requirements.txt` → `python -m app.mcp.knowledge_rag_server` → mcp-inspector Streamable HTTP `http://localhost:8095/mcp` 验 3 tool 各调一次
- [x] **H-1.4** 写一个 `mcp-smoke-test` 脚本，从 Claude Desktop / agent-service 直连两个 server 跑 happy path
  - 完成于 2026-05-21：`scripts/mcp_smoke_test.sh`（纯 `curl + jq`，无 npm 依赖）一键回归两端 MCP server 的 Streamable HTTP 握手 → `notifications/initialized` → `tools/list` 校验 → 7 个 tool（`get_student_profile / get_academic_history / get_mental_indicators / get_attendance` + `search_cases / search_policies / search_psychology`）各调一次 happy path
  - 实现要点：`declare -A SESSION_ID` 同时管理两端 `Mcp-Session-Id` 自动接力；脚本起手 `BASH_VERSINFO` 检测（mac 默认 3.2 给提示 + exit 1）；`Accept: application/json, text/event-stream` 双声明；single failures 记录到 `FAIL` 不中断，末尾按 exit code 汇总
  - 降级语义：`tools/call` 返回 `content` 空 → ⚠（不 fail，对应下游 student-service / Milvus 没起的情形）；返回 `error` 字段 → ✗（exit 1）
  - 用法：`bash scripts/mcp_smoke_test.sh`，或自定义 `STUDENT_ID=<n> STUDENT_DATA_URL=... KNOWLEDGE_RAG_URL=...`
  - 取代手动 `npx @modelcontextprotocol/inspector` 点击 7 个 tool 的 H-1.2/H-1.3 smoke 流程，给后续 Spring AI / FastMCP / Milvus 升级提供回归 baseline

### H-2 主 Agent Loop（替换 `AgentTaskServiceImpl.doExecute`）

- [x] **H-2.1** 新建 `agent-service/.../core/AgentLoop.java`，实现 think→tool→observe 循环（参考 Claude Agent SDK）
  - 完成于 2026-05-21：新建包 `com.edu.agent.core`，5 个 Java 源（`AgentLoop` `@Component` 主循环 + `AgentLoopRequest`/`AgentLoopResult`/`AgentTrace` record + `AgentLoopStatus` enum）；选 **ReAct JSON 协议**（每轮 LLM 输出 `{"thought":..., "action":{...}}` 或 `{"thought":..., "final_answer":...}`），不走 Spring AI 1.1.6 `ChatClient.tools(...)` 的隐式内部循环 —— 让 thought / action / observation 三类事件可见、可单测、可后续接 Langfuse trace
  - 默认 `max_iterations=5`、`CONSECUTIVE_PARSE_ERROR_LIMIT=2`、`TOOL_RESULT_MAX_LEN=4096`、tool 调用失败一次重试，再失败立即 `TOOL_ERROR` 终止；4 种终止态枚举 `COMPLETED / MAX_ITERATIONS / TOOL_ERROR / PARSE_ERROR`
  - 工具列表类型用 `List<ToolCallback>`（`org.springframework.ai.tool.ToolCallback`），H-2.2 接 MCP 时 `ToolCallbackProvider` 直接喂入，AgentLoop 零改动
  - `AgentLoopTest.java` 3 case 全绿（`mvn -pl agent-service test -Dtest=AgentLoopTest`）：纯 Mockito `RETURNS_DEEP_STUBS` + JUnit 5，无 `@SpringBootTest`；覆盖 first-turn final / tool→final / max_iterations 触发；agent-service `pom.xml` 增加 `spring-boot-starter-test` scope=test
  - 不动 `AgentTaskServiceImpl.doExecute` 旧 4 阶段（feature flag 留 H-2.3）；不引 `spring-ai-starter-mcp-client-webmvc` 依赖（H-2.2 时一并加）；不接 Langfuse（H-2.2 一并接）
- [x] **H-2.2** 接入 G-1 的 prompt caching + G-5 的 Langfuse
  - 完成于 2026-05-21：agent-service `pom.xml` 加 `spring-ai-starter-mcp-client`（**非** `-webmvc` —— 1.1.6 BOM 仅有 `spring-ai-starter-mcp-client`（默认 JDK HttpClient transport）与 `-webflux`，文档草案误写；`mvn dependency:tree` 实测校准）；`application.yml` 加 `spring.ai.mcp.client.streamable-http.connections.{student-data,knowledge-rag}`（env var 默认 `localhost:8094`/`8095`，端点 `/mcp`），启动 Spring AI 1.1.6 auto-config 产出合并 `ToolCallbackProvider` Bean（7 个工具）
  - `AgentLoop` 构造改签注入 `LangfuseClient`，所有 5 个终止 return 路径汇集 `private finishRun(req, result, runStart)` 上报顶层 `traceGeneration("agent.loop", ...)`，metadata 含 `task_tag`/`iterations`/`status`/`traces_summary`（截断 2048）；每轮 LLM call 仍由 `LlmMetricsInterceptor:113-123` 各自上报独立 `llm.chat` generation trace（两类并列、不嵌套，嵌套留 H-2.4 eval 调优时扩展 LangfuseClient + ThreadLocal context）
  - 新增 admin 端点 `/agent/api/v1/_internal/loop/dry-run` POST（`AgentLoopDryRunController`），注入 `ToolCallbackProvider` + `AgentLoop`，接受 `{userPrompt, systemPrompt?, maxIterations?, taskTag?}`，返回完整 `AgentLoopResult`（含 traces）；不挂 Sentinel/Security/JWT，gateway `/agent/**` 路由自动暴露，H-2.3 切流后视情况删除或挂 admin role
  - G-1 prompt caching **零接线** —— `SpringAiConfig.java:46-58` 拦截器链已挂 `LlamaCppCachePromptInterceptor` 在 `OpenAiApi` 的 `RestClient.Builder` 层，`AgentLoop` 用同一 `ChatClient` @Bean 自动继承 `cache_prompt=true` 与 Micrometer prompt/cached/predicted/prefill_ms 指标
  - `AgentLoopTest` 旧 3 case 改签兼容（`setUp` 多 mock 1 个 `LangfuseClient`，构造 `new AgentLoop(chatClient, langfuseClient)`），新增 `shouldFireLangfuseTraceOnRunComplete` 用 `verify(langfuseClient, atLeastOnce()).traceGeneration(eq("agent.loop"), ...)` 验证 trace 至少调用 1 次；`mvn -pl agent-service test -Dtest=AgentLoopTest` 4 case 全绿
  - 不动 `AgentTaskServiceImpl.doExecute` 旧 4 阶段（feature flag 留 H-2.3）；不接 Langfuse 嵌套 trace（留 H-2.4）；dry-run 端点零 auth 仅供本地 curl
- [x] **H-2.3** Feature flag：`educare.agent.loop.enabled`，默认 false；旧 4 阶段保留作 fallback
  - 完成于 2026-05-22：4 处改动 ——
    1. `agent-service/application.yml` 加 `educare.agent.loop.enabled`（env `EDUCARE_AGENT_LOOP_ENABLED`，默认 false）
    2. 新增 `agent-service/src/main/resources/prompts/agent-loop.system.md` —— AgentLoop system prompt，规约 `final_answer` 为合法 JSON 字符串 `{risk_analysis: {...}, intervention_plan: {...}}`，字节稳定以命中 G-1 prompt cache；列明 7 个 MCP 工具的语义指导（数据先取后判 + 必要时检索知识 + 轮次预算 + 安全声明）
    3. `AgentTaskServiceImpl` 注入 `AgentLoop` + `ObjectProvider<ToolCallbackProvider>` + `@Value` 开关 + `@PostConstruct` 加载 prompt；`doExecute` 入口加 if/else 分支：开 → `doExecuteAgentLoop(taskId)`；关 → `doExecuteLegacy(taskId)`（原方法重命名）
    4. 新方法 `doExecuteAgentLoop`：状态机仍流转 PENDING→RISK_ANALYZING→KNOWLEDGE_RETRIEVING→PLAN_GENERATING→COMPLIANCE_CHECKING→COMPLETED/REJECTED（中间 KNOWLEDGE_RETRIEVING/PLAN_GENERATING 仅打卡，AgentLoop 内部已通过 MCP knowledge-rag 完成 RAG）；`AgentLoopParsed` 内部 record 拆 `risk_analysis` + `intervention_plan` 两份 JSON 落 `riskAnalysisResult` + `interventionPlan` + `riskLevel`；解析失败或 status≠COMPLETED → FAILED；低/无风险保留短路；P4 合规审核仍走旧 `executeComplianceAudit`
  - `mvn -pl agent-service -am compile` 通过；`AgentLoopTest` 4 case 全绿（H-2.3 改动未触及 AgentLoop 公共接口，旧测试零回归）
  - 不删 `/agent/api/v1/_internal/loop/dry-run`（仍可独立 curl 验证 ToolCallbackProvider + AgentLoop 链路），H-2.4 灰度切流通过后再视情况删除或挂 admin role
  - **更新 2026-06-24**：该 dry-run 端点（`AgentLoopDryRunController`）已删除 —— 切流早完成、AgentLoop 为默认路径，手测脚手架使命结束；网关对 `/_internal/` 一律 403 的拦截仍由 `JwtAuthGlobalFilterTest`/`AgentSelfAuthFilterTest` 以该路径作样例覆盖（孤儿清理，见 §6）
- [x] **H-2.4** 灰度切流脚本（10% → 50% → 100%），观察 eval 集回归
  - 完成于 2026-06-05：把布尔开关升级为**按 taskId 确定性分桶的百分比灰度** ——
    1. 新增 `agent-service/.../core/AgentLoopCanaryGate.java`（`@Component @RefreshScope`）：`shouldUseAgentLoop(taskId)` 用 Fibonacci-hash 把 taskId 打散到 100 桶，`bucket < canary-percent` 时走 AgentLoop；对 percent **单调**（放量不把已命中任务退回 legacy）、**确定性**（同 taskId 稳定）；注入可选 `MeterRegistry` 埋 `educare.agent.loop.routed{path=loop|legacy}` 计数器（null-safe，单测 `new` 构造不受影响）
    2. `AgentTaskServiceImpl`：删 `@Value boolean agentLoopEnabled`，改注入 `AgentLoopCanaryGate`；`doExecute` 分支由 `canaryGate.shouldUseAgentLoop(taskId)` 决策；`@PostConstruct` 日志透出 enabled + percent
    3. `application.yml`：加 `educare.agent.loop.canary-percent`（env `EDUCARE_AGENT_LOOP_CANARY_PERCENT`，默认 100）+ 新增 `optional:nacos:agent-canary.yml` import（切流脚本只写这一个 dataId，不碰 agent-service.yml 主配置；@RefreshScope 热生效）
    4. `scripts/agent_loop_canary.sh`（纯 curl+jq）：Nacos 登录 → 推 baseline(enabled=false) → 逐档 10/50/100 推 `agent-canary.yml` → 等热刷新 → 对一组学生触发真实任务轮询到终态 + 从 `/actuator/prometheus` 读路由计数增量核对分流比例 → 门控（出现 FAILED 立即回滚 enabled=false 退出 1）→ 逐档手动确认（`AUTO=1` 自动；`KEEP_ON_FINISH=1` 跑完保留 100%）
    5. 新增 `AgentLoopCanaryGateTest`（7 case：enabled 总闸 / 0%/100% 边界 / clamp / 确定性 / 放量单调 / 分布 ≈percent / bucket 范围）；`mvn -pl agent-service test -Dtest=AgentLoopCanaryGateTest,AgentLoopTest` 11 case 全绿，全量 compile 通过
  - 实跑待用户：两个 MCP server + 本地 llama.cpp + Nacos 全在线后 `STUDENT_IDS="1 2 ..." bash scripts/agent_loop_canary.sh`；`run_eval.py`（打 Python 8090）不经 Java flag，故 eval 回归改为切流脚本驱动 agent-service 真实流水线 + 路由计数观测（见 §6 变更记录）

### H-3 Skill markdown 文件（4 个）

- [x] **H-3.1** `risk-assessment.md`
- [x] **H-3.2** `psychological-screening.md`
- [x] **H-3.3** `intervention-design.md`
- [x] **H-3.4** `compliance-audit.md`
- [x] **H-3.5** Skill 加载器：按需读 markdown 注入 system prompt，热更新支持
  - 完成于 2026-06-05：5 个文件一次落地 ——
    1. 4 个技能 markdown 放在 `agent-service/src/main/resources/skills/`（**改放 classpath** 而非计划的 `agent-service/skills/`，保证打包进 jar 始终可用；外部目录覆盖见下）。每个含 frontmatter（name/description/when_to_use）+ 正文（数据采集顺序、判定标准、责任人分工、红线）
    2. `skill/SkillDefinition.java`（record：name/description/whenToUse/body + `toPromptBlock()`）
    3. `skill/SkillLoader.java`（`@Component`）：双来源 —— 外部目录 `educare.agent.skills.dir`（配置且存在则优先，按 **mtime 热更新**，编辑 md 不重启）> classpath `skills/`（兜底，加载一次缓存）；`getSkill/listActive/composeActiveSkillsPrompt`；frontmatter 解析为静态纯函数便于单测
    4. `application.yml` 加 `educare.agent.skills.{enabled,dir,active}`（active 默认 4 个技能、逗号有序、env 可覆盖 → "按需选择"）
    5. `AgentTaskServiceImpl.runAgentLoopForTask` 注入 `skillLoader.composeActiveSkillsPrompt()` 到 AgentLoop system prompt（关闭/无技能时空串，prompt 字节稳定仍命中 G-1 cache）
  - `SkillLoaderTest` 7 case（frontmatter 解析/无 frontmatter/classpath 加载/未知技能/compose 含 4 技能/disabled 空/activeNames trim）；`mvn -pl agent-service test` 共 18 case（含 AgentLoop 系列）全绿，全量 compile 通过

### H-4 Model Router（本地 14B + 云端 API 双路由）

- [x] **H-4.1** 宿主侧起本地 14B 实例（端口 8092），云端 API key 走 Nacos 加密配置
  - 完成于 2026-06-05（代码侧）：`application.yml` 加 `educare.model.local.base-url`（默认复用 `spring.ai.openai.base-url`，要独立 14B 实例改指 8092 即可）+ `educare.model.cloud.{enabled,base-url,api-key,model}`；云端 key 走 env `EDUCARE_CLOUD_LLM_API_KEY` / Nacos `agent-service.yml`（加密配置），默认空。`SpringAiConfig` 本地 `baseUrl` @Value 链式取 `educare.model.local.base-url`。实跑（宿主起 14B + 配云端 key）留用户
- [x] **H-4.2** 新增 `agent-service/.../router/ModelRouter.java`：敏感/原始画像 → 本地 14B；方案/审核 → 云端
  - 完成于 2026-06-05：`router/ModelTier`（LOCAL/CLOUD）+ `router/ModelRouter`（`@Component`）。`SpringAiConfig` 改造为**双 ChatClient**：本地 `chatClient` 标 `@Primary`（挂 cache_prompt + metrics 拦截器），新增 `cloudChatClient`（只挂 metrics，不注 llama.cpp 私有 cache_prompt）。`ModelRouter.decide(stage, sensitiveRawData)`：敏感原始数据强制 LOCAL；plan/intervention/audit/compliance/review 阶段倾向 CLOUD；其余 LOCAL。`client/route` 在云端未就绪（enabled=false 或 key 空）时 **fail-safe 回落本地**。`RiskAnalyzeService` 改注入 `ModelRouter` 经 `route(-1,"risk",true)` 取本地 client（原始画像不出本地）。AgentLoop 仍用 `@Primary` 本地（整段处理敏感数据，本地是正解）；云端 tier 为已脱敏推理类调用预留
- [x] **H-4.3** 加路由审计日志（每次决策记录 task_id + 选用模型 + 数据敏感级）
  - 完成于 2026-06-05：`ModelRouter.audit` 走独立 logger `MODEL_ROUTER_AUDIT`（可单配 logback appender）打 `task_id/stage/sensitive/decided/actual`（含 cloud→local 回落标注）+ Micrometer 计数器 `educare.model.routed{tier,stage}`（从 `/actuator/prometheus` 可观测）。`ModelRouterTest` 7 case（敏感强制本地 / plan·audit 倾向云端 / 未知阶段本地 / cloudReady 门控 / 未就绪回落 / 就绪用云端 / 计数器按实际 tier 累加）；agent-service 共 25 case 全绿

### H-5 Mem0 集成 + memory-server（视进度）

> ⚠ **后记（2026-08-26 审计补注）**：H-5 三项曾于 2026-06-05 落地；**2026-06-19 阶段二瘦身按 review B1/T5 整体删除**（commit 75fc3f4，理由：四层记忆照搬 MemGPT 投入完整却零产出、agent 从未接入 memory-server）。Python memory_store/mcp/memory_* 与 Java MemoryGateway、compose memory-mcp(8096)、MEMORY_DESIGN.md 均已移除。下方勾选保留作历史记录，当前代码无记忆子系统。

- [x] **H-5.1** 评估 Mem0 vs Letta 当前稳定性，做选型决策
  - 完成于 2026-06-05：`docs/educare/MEMORY_DESIGN.md` §1 拍板 **自实现轻量记忆层**（不引 Mem0/Letta SDK）。理由：Mem0 SDK 演进快、破坏性变更多且其向量/LLM 抽象与本项目 Milvus+llama.cpp 重叠；Letta 太重且自带 agent 框架与 H-2 AgentLoop 冲突。接口（recall/save/summarize）比实现重要，先用 Redis 跑通闭环，未来换 Mem0 只替换 `memory_store` 实现层、MCP 工具签名不变
- [x] **H-5.2** 实现记忆分层（Working / Episodic / Semantic / Procedural）
  - 完成于 2026-06-05：`app/services/memory_store.py` 四层（Working=Redis string 1h / Episodic=Redis list LPUSH+LTRIM 有界 90d / Semantic=Redis string(JSON) 180d / Procedural=Semantic 摘要内 strategies 字段）；config 加 `MEMORY_{WORKING,EPISODE,SEMANTIC}_TTL` + `MEMORY_EPISODE_MAX`；纯函数（key 构造 / build_episode / filter_episodes）抽出便于单测；Redis 不可用全降级。附带把 `redis_client` 的 `from redis.asyncio import Redis` 改延迟 import（纯逻辑单测无需装 redis 包）
- [x] **H-5.3** memory-server MCP（Python）暴露 `recall_student_history / save_episode / summarize_long_term`
  - 完成于 2026-06-05：`app/mcp/memory_adapter.py`（业务+校验+LLM 蒸馏，LLM 不可用降级规则摘要）+ `app/mcp/memory_tools.py`（3 个 FastMCP `@mcp.tool`）+ `app/mcp/memory_server.py`（独立进程，端口 8096，Streamable HTTP `/mcp`，与 knowledge-rag(8095) 对称）；config 加 `MCP_MEMORY_PORT/PATH`。`tests/test_memory_store.py` 13 case（纯函数 + 校验 + redis-down 降级 + 规则摘要）；`python -m unittest tests.test_memory_store tests.test_text_splitter` 25 case 全过。**不强接 AgentLoop**（避免每任务依赖 memory-server 在线），接线留灰度，方案见 MEMORY_DESIGN §4。实跑（起 8096 + mcp-inspector 验 3 tool）留用户

### H-6 Braintrust eval gate 接入 CI（视进度）

- [x] **H-6.1** GitHub Actions / 内部 CI workflow：PR 触发 eval 跑全集
  - 完成于 2026-06-05：`.github/workflows/eval-gate.yml` 两段式 —— `validate-dataset`（无条件，`run_eval.py --validate-only` 纯 stdlib 体检 50 例：id 唯一/input 非空/risk_level 合法，秒级零依赖）+ `eval-threshold`（条件：仓库变量 `EVAL_LLM_ENABLED=true` 才跑，需可达推理端点 `EVAL_LLM_BASE_URL` secret）。PR paths 命中 `eval/` `ai-inference-service/` `agent-service/` 触发；未配端点的 fork 只跑第 1 段不误红。`run_eval.py` 加 `--validate-only`/`--threshold`/`--baseline`/`--baseline-tolerance` 四参 + `validate_cases()` 体检函数
- [x] **H-6.2** 阈值：faithfulness ≥ baseline、等级一致率 ≥ 0.85，不达标阻塞合并
  - 完成于 2026-06-05：workflow eval-threshold 段 `--threshold 0.85` 卡等级一致率；`--baseline eval/baseline.json`（存在时）经 `_check_baseline` 检查相比 baseline 回退超 `--baseline-tolerance`（默认 0.05）即 exit 1。退出码 1 阻塞合并。`eval/README.md §5` 重写为已落地的两段式 + 启用步骤（仓库 Variables/Secrets/baseline）+ 阈值 0.6→0.85 演进路径。faithfulness 以 `phrase_hit_rate` 为代理报告（硬门是 level_accuracy，与 G-6.3 一致）

**Phase H 验收总标准**：
- 旧 `AgentTaskServiceImpl.doExecute()` 4 阶段 if-else 删除（fallback 保留通过 feature flag）
- 新增一个 Agent 仅需写 Skill + 注册 MCP tool，零 Java 改动
- eval 集回归通过率 ≥ baseline
- ModelRouter 决策可在 Langfuse 看到

---

## 4. Phase I —— 高级能力 + 业务闭环（第 7-9 周，最小版）

> 最小决策（IMPROVEMENT §0）：仅 I-1 Hybrid Retrieval + I-5 干预反馈闭环。I-2/I-3/I-4/I-6 列入储备。

### I-1 Hybrid Retrieval（向量 + BM25）

> ⚠ **后记（2026-08-26 审计补注）**：I-1 四项曾于 2026-06-05 全部落地（commit 01d788e）；**2026-06-19 阶段二瘦身按 review B5/T9 整体删除**（commit fb4ec14，理由：知识库规模小 dense 已够、`RAG_HYBRID_ENABLED` 默认关长期未启用、简历取向删半成品），RAG 回纯 dense。下方勾选保留作历史记录，当前代码已无 hybrid 路径；`eval/hybrid_eval.py`/`hybrid_queries.jsonl`/`HYBRID_RETRIEVAL_DESIGN.md` 一并移除。

- [x] **I-1.1** Docker compose 加 Elasticsearch 8.x（或评估 Milvus 2.4 内置 BM25 替代，决策点）
  - 完成于 2026-06-05：`docs/educare/HYBRID_RETRIEVAL_DESIGN.md` §1 拍板 **进程内 BM25 + RRF（不加 ES，不改 Milvus schema）**。理由：Milvus 原生 BM25 函数要 2.5+（现 2.4.1 仅 sparse 向量、要改 schema 重灌）；ES 是重容器违背"复用既有栈"；I-1 验收是专有名词 top-3 命中，dense 放大候选池 + BM25 池内重排正好命中。升级路径（Milvus 2.5 原生 BM25 / ES）接口不变
- [x] **I-1.2** `ai-inference-service/app/services/hybrid_retrieval.py`：dense + BM25 并行召回 + RRF 融合
  - 完成于 2026-06-05：纯函数模块 —— `tokenize`（中英混排：ASCII alnum + CJK 单字 + bigram）+ `BM25`（自实现 Okapi，无 rank_bm25 依赖）+ `rrf_fuse`（k=60）+ `fuse_hits`（dense 序 × BM25 序 RRF 融合，回填 hybrid_rank）。`tests/test_hybrid_retrieval.py` 9 case 全过
- [x] **I-1.3** `knowledge-rag-server` 接入 hybrid，灰度对比纯 dense
  - 完成于 2026-06-05：`rag_pipeline.retrieve_from_collection` 加 `enable_hybrid` 参数 + 配置 `RAG_HYBRID_ENABLED`（默认 false，行为不变）；dense 召回后 `fuse_hits` 重排（reranker 开启时被 cross-encoder 覆盖，关闭时 hybrid 决定序）；payload 加 `hybrid` 字段，缓存 key 区分 dense/hybrid。knowledge-rag 三个 MCP search tool 经 rag_pipeline 自动透传，零改动
- [x] **I-1.4** RAGAS 离线评测：context_precision / answer_relevancy 必须 ≥ 纯 dense baseline
  - 完成于 2026-06-05：以 **top-3 命中率**直接对齐 I-1 验收（≥ 纯 dense + 15%），不引 RAGAS 重依赖（理由见设计 §4）。`eval/hybrid_eval.py`（同 query 跑 dense/hybrid 两遍比 top-k 命中 + 提升）+ `eval/hybrid_queries.jsonl`（6 条专有名词 query：高数 II / SCL-90 / PHQ-9 / 助学金 / 勤工助学 / GAD-7）。`hit_at_k` 纯函数 + 数据集校验通过；实跑需 Milvus 在线 + collection 已灌数据，留用户

### I-5 干预反馈闭环

- [x] **I-5.1** DB 表 `intervention_feedback`（task_id / counselor_id / score 1-5 / outcome / created_at）
  - 完成于 2026-06-05：`sql/init/05_intervention_feedback.sql`（task_id/student_id/counselor_id/score/outcome/comment/created_at/updated_at/deleted + 3 索引），与 agent_task 同库
- [x] **I-5.2** 后端 `POST /agent/api/v1/intervention/feedback`
  - 完成于 2026-06-05：agent-service 加 `InterventionFeedback` entity + mapper + `FeedbackRequest` dto + `InterventionFeedbackService(Impl)` + `InterventionFeedbackController`。POST 提交（手动校验 score 1-5 / outcome 枚举 / task 存在，studentId 从 task 回填，counselor_id 取 JWT subject）；另含 GET `/feedback/report?month=yyyy-MM`（JSON 明细）。`InterventionFeedbackServiceTest` 7 case（校验 + happy + CSV 转义）全绿
- [x] **I-5.3** 前端：干预方案页加 "1 个月后跟进" 评分组件
  - 完成于 2026-06-05：`ReportDetail.vue` 加"干预效果跟进"卡片（COMPLETED/REJECTED 时显示）—— `el-rate` 1-5 分 + 结果下拉（improved/unchanged/worsened/escalated）+ 文字反馈，提交走 `submitInterventionFeedback`；`api/agent.js` 加 3 个 API（submit/report/csv）。`@vue/compiler-sfc` 解析 + script 编译通过
- [x] **I-5.4** 月度报表：导出反馈数据 CSV
  - 完成于 2026-06-05：GET `/agent/api/v1/intervention/feedback/report.csv?month=yyyy-MM` 导出（UTF-8 BOM 防 Excel 乱码 + CSV 字段转义），`exportCsv` service 实现；前端 `downloadFeedbackCsv` API 备用。**注**：原计划"回流给 H-5 procedural memory"调整为先交付 CSV 月报（H-5 memory-server 未强接 AgentLoop，回流留后续灰度接线时一并做，见 §6）

### 储备项（不在本季度排期）

- [ ] **I-2** GraphRAG / Neo4j —— 等 I-1 RAG 评测结果再决定
- [ ] **I-3** 3 个 Subagent（班主任 / 心理咨询师 / 学业导师）
- [ ] **I-4** 合规框架（policy 文档 + audit_log + tool guard）
- [ ] **I-6** 学生时间线页面

**Phase I 最小验收**：
- Hybrid 在专有名词 query（如 "高数 II"、"SCL-90"）召回 top-3 命中率 ≥ 纯 dense + 15%
- 干预反馈闭环：教师可提交评分，后台可导出月度 CSV

---

## 5. 跨阶段依赖图

```
G-1 (caching) ──► G-1.5 metrics ──► G-3.3 Prometheus ──► H-2 接入 trace
G-3.1 (Micrometer 依赖)            ──► G-3.2 (Scheduler 指标)
G-5 (Langfuse) ──► H-2.2 (Loop 接 trace) ──► H-6 (eval gate)
G-6 (eval 50 例) ──► H-6 (CI gate) ──► I-1.4 (Hybrid 评测复用框架)
H-1 (MCP Server) ──► H-2 (Loop 用 MCP tool)
H-4 (ModelRouter) ──► H-2 (Loop 选模型)
```

阻塞性强依赖：**G-1 → G-3 → H-2** 是关键路径，先把 G-1 全部跑通再启动 H 阶段任何子项。

---

## 6. 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-05-13 | 初版创建，根据现状盘点回填 G-1.1/G-1.2/G-1.3 完成态 | IMPROVEMENT v1.1 拍板后启动执行 |
| 2026-05-13 | G-1.5 标 `[~]` 半完成：Python 部分随 G-1.4 一同实现（`chat_completion_raw` 自动调 `record_llm_response`），Java 部分单列保留 | LangChain `ChatOpenAI.ainvoke` 隐藏原始响应；为拿 `timings` 必须 raw httpx，与 G-1.4 强耦合 |
| 2026-05-13 | G-1.6 标 `[~]` 半完成：Python diagnostics 端点 + 脚本 + 手册落地；Java 端点延后到 G-3.1 引入 Micrometer 时一起做 | 单独为 Java 起一个 controller 太碎，与 Phase G-3 Prometheus 路径合流更经济 |
| 2026-05-13 | G-2.2 拆为 a/b/c/d/e 五个子步 | 设计落实后发现 JWT 改造（roles claim）+ 注解定义 + advice 实现 + entity 标注 + 自动配置 各自独立可合并，单 PR 粒度更易 review |
| 2026-05-14 | G-5.1 选 Langfuse self-hosted v2 + `profiles: [langfuse]` 默认不启 | 已有 compose 编排可复用；v2 两容器够用，v3 的 clickhouse+worker 五容器对 resume 项目超量；profiles 让 default `up` 仍精简 |
| 2026-05-14 | G-6.1 选 promptfoo 而非 Braintrust SaaS | 无付费 tier；YAML+JSONL 随 git 版本化；Langfuse 已覆盖在线 trace，离线 eval 用轻量 CLI 互补 |
| 2026-05-19 | H-1.1 拆出 H-1.1.5：Spring AI 升级单独 PR | M6→GA 改动面（starter 改名 + auto-config 拆包 + OpenAiApi/Model builder 化）远超 MCP_DESIGN §2 预估的"4-arg constructor 仍可用"，单 PR 隔离回归风险 |
| 2026-05-19 | H-1.2 决定起独立微服务 `backend/mcp-student-data` 而非内嵌 agent-service；同步补 `student_academic_record` / `student_attendance` 两张表 DDL；并订正 MCP_DESIGN.md 中 starter artifactId / 注解 / SSE 端点三处事实错误 | 单一职责 + 端口独立（8094）便于 mcp-inspector 直连 smoke；agent-service 重启不影响 MCP server；与 H-1.3 Python 端口 8095 形成对称结构。MCP_DESIGN 原写法（`spring-ai-mcp-server-spring-boot-starter` + `@Tool` 自动扫描 + `/sse` 路径）在 1.0.0 GA 已重命名/拆分，落地实测后才发现并校准 |
| 2026-05-20 | 在 H-1.2 与 H-1.3 之间插入 H-1.1.6：Spring AI 1.0.0 → 1.1.6 GA + MCP 传输全栈切到 Streamable HTTP | MCP spec 2025-03-26 已将 SSE 标记 deprecated，Streamable HTTP 为推荐传输；Spring AI 1.0.x 仅支持 SSE/STDIO，必须升 1.1.x 才能切。提早切的好处：H-1.3 直接落 HTTP transport，未来 H-2 client 一次到位，避免后续二次返工。1.0→1.1 实测核心 API 完全兼容（`@Tool`/`@ToolParam`/`MethodToolCallbackProvider`/`OpenAiApi.builder()` 等保留），零代码改动；MCP server 仅 application.yml 三行配置切换 |
| 2026-05-20 | H-1.3 落地：抽 `rag_pipeline.py` 单库检索管线（与既有多源聚合 `rag.py` 职责分离）；新增 `app/mcp/` FastMCP 2.x 模块（3 个 `@mcp.tool` + adapter + server entry），跑独立进程端口 8095 / Streamable HTTP / 单端点 `/mcp` | 与 H-1.2 student-data MCP server（8094）形成对称结构；不合入 FastAPI 主进程（8090）以保资源/重启/调试独立；rag.py 路由零改动，只新增依赖（fastmcp）与新模块，最小风险面 |
| 2026-05-21 | H-1.4 落地 `scripts/mcp_smoke_test.sh`：纯 `curl + jq` 一键回归两个 MCP server 的 `initialize → notifications/initialized → tools/list → 7×tools/call`，session id 自动接力；H-1 子阶段全部完结，指针推进至 H-2.1 AgentLoop | 取代手动 `mcp-inspector` 点点点的 H-1.2/H-1.3 smoke 流程；脚本即基线，后续 Spring AI / FastMCP / Milvus 升级跑一次即可回归。无 npm 依赖（保留 macOS 默认 toolchain），但需要 bash≥4（`declare -A` 双 session id 接力），脚本头自检 + 提示 |
| 2026-05-21 | H-2.1 落地：新建 `com.edu.agent.core.AgentLoop` + 4 个 record/enum + 3-case 单元测试，think→tool→observe 循环走 ReAct JSON 协议 | 选 ReAct 而非 Spring AI 1.1.6 native tool calling 的理由：1.1.6 `ChatClient.tools(...)` 默认内部隐式循环，thought/action/observation 三类事件个体不可见，无法 trace、无法 inject early-stop、无法限 max_iterations；`internalToolExecutionEnabled=false` 路径未实测稳定。ReAct JSON 把控制流封装在 AgentLoop 内部，外部只需 `ToolCallback` 列表，H-2.2 接 MCP 时调用方零改动；后续模型升 32B+ 想换 native tool calling 也只改 AgentLoop 内部。本步不动旧 4 阶段，feature flag 留 H-2.3 |
| 2026-05-21 | H-2.2 落地：agent-service 接 MCP client 1.1.6（`spring-ai-starter-mcp-client` + `streamable-http.connections.{student-data,knowledge-rag}`，启动产出合并 `ToolCallbackProvider` 7 个工具）+ `AgentLoop` 接 Langfuse 顶层 `agent.loop` trace（`finishRun()` 收口 5 个 return 路径）+ 新增 `/agent/api/v1/_internal/loop/dry-run` admin 手测端点；不切旧 4 阶段、不接嵌套 trace、不加 feature flag、dry-run 端点零 auth | 把 H-1 落地的 7 个 MCP tool 通过 1.1.6 `ToolCallbackProvider` auto-config 红利接入 `AgentLoop`，最小代码。artifactId 实测校准（**非** plan 草案的 `-webmvc`，1.1.6 BOM 只有 `spring-ai-starter-mcp-client`（默认 JDK HttpClient）与 `-webflux`）。trace 颗粒选顶层一次而非嵌套是有意收敛侵入面：嵌套要改 `LangfuseClient` + `LlmMetricsInterceptor` + 引入 ThreadLocal context 3 处，回归面碰已 GA 的 G-5.3 路径，嵌套优化留 H-2.4 eval 调优时再做。G-1 prompt caching 通过 `SpringAiConfig` RestClient 拦截器链零接线继承，AgentLoop 用同一 `ChatClient` @Bean 自动得到 `cache_prompt=true` 与 cache_hit_rate 指标 |
| 2026-06-05 | I-5 落地：`intervention_feedback` 表 + agent-service 提交/月报/CSV 接口 + 前端评分卡片；I-5.4 由"回流 procedural memory"调整为"CSV 月报" | 反馈表与 agent_task 同库（agent-service 自治），校验手动做（该模块未引 validation starter，避免为一处加全局依赖）。I-5.4 原计划回流 H-5 procedural memory，但 H-5 memory-server 本轮决定不强接 AgentLoop（独立进程、接线留灰度），故先交付 CSV 月报满足"后台可导出"验收，回流留 memory-server 接线时一并做。CSV 加 UTF-8 BOM 解决 Excel 中文乱码 |
| 2026-06-05 | I-1 落地：决策"进程内 BM25 + RRF over dense 池"（弃 ES、弃 Milvus schema 改造）；`hybrid_retrieval.py` 纯函数 + `rag_pipeline` 灰度接入 + top-3 命中率评测代替 RAGAS | ES 是重容器、Milvus 原生 BM25 要 2.5+（现 2.4.1 要改 schema 重灌），均违背"复用既有栈"。I-1 验收点是专有名词 top-3 命中，dense 放大候选池 + 池内 BM25 重排即可命中，零基建。RAGAS 需额外 LLM 评审 + 重依赖，对该具体验收点过度工程，用 top-3 命中率直接对齐（≥ dense +15%）。升级到 Milvus 2.5 原生 BM25/ES 时 `fuse_hits` 调用点不变 |
| 2026-06-05 | H-6 落地：`.github/workflows/eval-gate.yml` 两段式 eval gate；`run_eval.py` 参数化阈值 + validate-only + baseline 对比 | CI 无真实 LLM 会让全集 eval 全走 fallback（medium）误红，故拆两段：数据集体检无条件硬门（纯 stdlib、确定性），真实跑分门用仓库变量 `EVAL_LLM_ENABLED` 开关 + secret 端点，未配置环境只跑体检不阻塞。阈值 0.85 经 CLI 参数化（默认仍 0.6，演进可调），faithfulness 用 phrase_hit_rate 代理（与 G-6.3 既有指标一致，不引 RAGAS 重依赖） |
| 2026-06-05 | H-5 落地：自实现轻量记忆层（放弃 Mem0/Letta），`memory_store` 四层 Redis + memory-server MCP（8096，3 工具）+ `MEMORY_DESIGN.md`；不强接 AgentLoop | Mem0/Letta SDK 不稳定且与既有 Milvus+llama.cpp 栈重叠（与 G-5/G-6/H-1.3"复用既有栈"决策一致）。接口比实现重要：先 Redis 跑通 recall/save/summarize 闭环，未来换 Mem0 只替换 memory_store 实现层。Semantic 层留 Milvus 向量化升级路径。不强接 AgentLoop 是为避免每个任务都硬依赖 memory-server 在线（与 H-1.3 knowledge-rag 独立进程同理），接线走灰度。附带把 redis_client 的 redis import 改延迟，让纯逻辑单测无需安装 redis 包 |
| 2026-06-05 | H-3 落地：4 个技能 markdown 改放 `src/main/resources/skills/`（classpath）而非计划写的 `agent-service/skills/`；`SkillLoader` 双来源（外部目录 mtime 热更新 / classpath 兜底）；按 `educare.agent.skills.active` 选择注入 | classpath 放置保证打包进 jar 始终可用，外部目录仅作热更新/覆盖；"按需选择"用配置化 active 列表实现，单一任务类型下默认注入全部 4 技能 |
| 2026-06-05 | H-4 落地：双 ChatClient（本地 `@Primary` + `cloudChatClient`）+ `ModelRouter`（敏感→本地、方案/审核→云端、未就绪 fail-safe 回落）+ 审计 logger + 计数器；`RiskAnalyzeService` 经 router 取本地；AgentLoop 仍用本地 `@Primary` | 双 ChatClient 比"单 client 调用点切 base-url"干净：本地保留 llama.cpp 私有 `cache_prompt` 拦截器、云端不注（标准 OpenAI 端点不识别）。本地标 `@Primary` 让既有裸 `ChatClient` 注入零改动。AgentLoop 不接 router：它整段处理原始敏感画像，本地是正确 tier，强行按阶段切云端会让原始数据出本地，违背 H-4.2 敏感→本地原则；云端 tier 留给未来"已脱敏方案精修"类调用。云端默认关闭 + key 空即回落，保证未配置环境零行为变化 |
| 2026-06-05 | H-2.4 落地：布尔 `enabled` 升级为 `enabled + canary-percent` 百分比灰度；新增 `@RefreshScope AgentLoopCanaryGate`（按 taskId 分桶 + 路由计数器）+ `agent-canary.yml` 专用 Nacos dataId + `scripts/agent_loop_canary.sh`；放弃原计划的 run_eval.py baseline 对比 | 任务标题 "10%→50%→100%" 布尔无法表达，单实例百分比灰度必须按请求/任务确定性分桶。计划 §1 原设想 "(b) 无需再改 service + 跑 run_eval.py 对比" 有两个事实错误：(1) `run_eval.py` 打的是 Python ai-inference `/api/v1/agent/risk`（8090），完全不经过 Java agent-service 的 feature flag，翻 flag 前后跑 run_eval.py 结果不变，无法验证 AgentLoop 路径；(2) 布尔 `@Value` 非 `@RefreshScope`，Nacos 推送不热生效。改法：路由决策抽到独立 `@RefreshScope` 小 bean（重型 `AgentTaskServiceImpl` 不变 refresh-scope），Nacos 热生效；分桶哈希对 percent 单调（放量不回退已命中任务）；加 `educare.agent.loop.routed{path}` 计数器让脚本能从 Prometheus 实测分流比例；eval 回归改由切流脚本驱动 agent-service 真实流水线（trigger→poll→门控 FAILED）而非离线 run_eval.py |
| 2026-05-22 | H-2.3 落地：`AgentTaskServiceImpl.doExecute` 入口加 `educare.agent.loop.enabled` 分支 —— 开 → `doExecuteAgentLoop`（AgentLoop 一次性出 risk+plan、状态机完整流转、P4 合规审核保留）；关 → `doExecuteLegacy`（原 4 阶段）；新增 `prompts/agent-loop.system.md` 字节稳定 prompt，规约 final_answer 双 JSON schema | 切流方式选"入口分支 + 状态机完整流转"而非"AgentLoop 完全替换状态机"：前端 PollingTask + SSE 已基于 5 个中间态做 UI 渲染，完全替换会回归前端展示；同时 P4 合规审核（教育合规相关）暂不进 AgentLoop —— audit 端点已有 Python prompt + Langfuse trace + RAG 引用规范，AgentLoop 把它当工具调反而绕远。AgentLoop final_answer 用"双 JSON 拼装"而非"单 JSON 扁平"：(a) 兼容旧 `riskAnalysisResult` / `interventionPlan` 两个 DB 字段，前端展示零改；(b) 让 LLM 自由组织字段顺序时不混淆两个语义域。`ObjectProvider<ToolCallbackProvider>` 注入而非直接 `ToolCallbackProvider`：MCP starter 启动期 fail-fast 已由 application.yml 强制，但单测/未来内嵌运行时仍想要 graceful degrade 到零工具运行 |
| 2026-06-15 | 安全增量（非 Phase G–J 计划内，应急修复）：网关 P0 鉴权门 `JwtAuthGlobalFilter`（默认开）+ mental `StudentMentalController` 修横向越权 IDOR（`userId`==subject 否则 403）+ docker-compose 把 8087/8094-96 收紧到 `127.0.0.1` + 订正 CLAUDE.md 鉴权描述 | 原"鉴权由 auth-service Spring Security 强制"描述与代码不符：网关 7 条路由仅 `StripPrefix`、业务服务无 security，任意人不带 token 即可经网关直读心理数据，G-2 字段权限因无准入门形同虚设。gateway/mental 各 7 例单测全绿；仅 gateway 叶子模块 + mental 单控制器，零跨模块回归 |
| 2026-06-15 | 安全增量（续，IDOR）：抽 `common/AccessGuard`（解析 token→userId/角色，`allowSelfRoleOrInternal`「本人/教职工/内网匿名」判定）+ 注册自动装配；mental `StudentMentalController` 改用之并**修回归**；student-service `StudentController`/`Academic`/`Attendance` 同口径闭 IDOR（读端点本人或教职工、写/列限 admin/teacher，academic/attendance 反查 studentId→userId） | 内部 Feign（mcp→student/mental，绕网关、端口未公网发布）无 token 透传，上一行 mental self-only 会 403 掉匿名取数打断 AI 画像 → 改"内网可信 + 网关为边界"模型：无 token 视为可信内部、带 token 强制属主/角色。AccessGuard 12 + student 10 + mental 7 例单测全绿 |
| 2026-06-24 | 孤儿代码清理（工程卫生，非 Phase G–J 任务）：删 `AgentLoopDryRunController`(dry-run 端点) + 前端 4 孤儿文件(`api/user.js`/`store/index.js`/`mental/{fill,student-list}.vue`) + 2 冗余依赖(`vue-echarts`/`js-cookie`) + Python 死代码(`rag_service.py`/`embedding_service.py`) + 96 游离空目录 + 过期文档 `GATEWAY_ISSUE_SUMMARY.md`；`AGENTS.md` 同步 CLAUDE.md | 工具(knip/vulture/类级引用扫描)+人工复核区分真孤儿 vs 框架管理类(Java `@Configuration`/`@RestController`/`@Mapper`、Python FastAPI 端点经 legacy fallback 实调，均非孤儿保留)；16 unused export 按 API 完整性保留。dry-run 删除兑现 H-2.3"切流后视情况删除"。验证：前端 build 绿 + 后端 BUILD SUCCESS(17 filter 测试) + Python 19 例 |
| 2026-06-27 | 上线前工程化（非 Phase G–J 任务，按上线清单逐项）：① **ESLint 链路**（`.eslintrc.cjs`+TS parser+prettier+auto-import globals，`npm run lint` 0/0）；② **生产密钥 fail-fast**（compose 5 处写死密钥 MySQL/Nacos/MinIO 参数化为 `${VAR:-devdefault}` + 容器 cred 透传 + `docker/.env.example` + `scripts/preflight-prod.sh`(三场景测过) + `sql/prod/01_rotate_default_passwords.sql` + `.gitignore` 护 `.env`/证书/备份）；③ **安全单测 + 覆盖率门**（auth-service `AuthServiceImplTest` 9 例覆盖登录链 100%：用户不存在/密码错同一 401 防枚举、禁用 403、写 Redis 白名单、claims 带 roles、logout 撤销；jacoco 父 pom 定向 check `AuthServiceImpl`≥80% + CI 归档）；④ **生产部署形态**（12 主机端口收敛 `127.0.0.1` + `docker/nginx/edu-portrait.conf`(TLS/SPA/SSE) + `Dockerfile.frontend` + `docker-compose.prod.yml` + `docs/DEPLOY.md`）；⑤ **可观测**（gateway 加 actuator/micrometer + `docker-compose.monitoring.yml`(Prometheus+Grafana) + 看板 JSON + 4 告警规则）；⑥ **备份/压测**（`backup-mysql.sh`/`restore-mysql.sh` + k6 `load-test.js`，互补既有 `bench_agent.sh`）；⑦ 补 `questionnaire.vue` 漏接的查看按钮 | 对齐上线清单（阻塞=生产密钥、质量门=测试+CI、生产化=nginx/监控/备份）。改动保持 dev 默认不变：compose `${VAR:-default}` + 端口 `127.0.0.1` 对 localhost 透明，prod 经 `docker/.env` 覆盖 + preflight fail-fast。覆盖率门采"按类 includes 定向"而非全局百分比（历史业务模块覆盖稀疏会误红，随补测往 includes 追加类逐步收紧）。gateway actuator 经 `PUBLIC_PREFIXES` 已放行 `/actuator/` 且不在 `/api` 下故不经 nginx 对外。验证：全后端 `mvn clean test` 11 模块绿 + jacoco 门过 + 前端 build/lint(0/0) 绿 + 三套 compose config 解析通过 + preflight 三场景。**Docker daemon 未起**→镜像 build/`nginx -t`/全栈 smoke + **GPU 全链路实跑(Task #7)留用户侧**；网关复验已脚本化 `scripts/gateway_verify.sh`（6 项断言：准入 401/有效 200/无效 401/`_internal` 403/登出吊销）+ `E2E_RUNBOOK §6.5`（字段脱敏/IDOR 列手动复验），runbook 另见 `DEPLOY.md` 四 + `smoke_test_agent.sh`/`local_real_run.sh` |
| 2026-08-26 | 进度审计回写（纯文档，零代码变更）：确认 Phase G/H/I/J + 安全增量 + 上线前工程化全部完结、关键 CI/部署/MCP 产物在位；剩余项收敛为「用户侧 e2e 实跑（GPU/Docker）+ CI 覆盖率 % 门（唯一非环境项）+ I 储备项 I-2/I-4/I-6」 | 会话级全面盘点后按 §0 协议同步状态 |

---

## 7. 关联文档

- 设计源：`docs/educare/IMPROVEMENT_2026_MAY.md`
- 现状架构：`docs/educare/architecture.md`
- 部署：`docs/educare/deploy.md`
- 验收：`docs/educare/acceptance.md`
- 待创建：`docs/educare/COMPLIANCE.md`（Phase I-4 储备项）
- 已产出：`PROMPT_CACHE_VERIFY.md`（G-1.6）、`FIELD_PERMISSION.md`（G-2.1）、`FIELD_PERMISSION_VERIFY.md`（G-2.3）、`SCHEDULE_METRICS_VERIFY.md`（G-3.4）、`RAG_UPSERT_DESIGN.md`（G-4.1）、`eval/README.md`（G-6.4）、`MCP_DESIGN.md`（H-1.1）

---

## 8. 已知阻塞（Known Blockers）

> 本节登记 **跨任务、阻塞下游验证、但本次执行不修** 的预先存在问题。每条都有"何时解锁"标注。

- ~~**B-1（mental-service 编译断）**~~ **已解决 2026-06-05**：`Question` 补 `scoringRules/scaleMin/scaleMax/scaleLabels` 4 字段（列在 `01_init.sql:163-166` 本就存在，纯 Java 漏映射）+ `QuestionService` 补 `deleteByQuestionnaireId / saveBatch(Long, List)` 并在 `QuestionServiceImpl` 实现。`mvn clean install` 全 11 模块 BUILD SUCCESS。无需数据库迁移（schema 已含这些列）。

---

## 9. 可运行性硬化（2026-06-05，"5 件事"）

针对"是否可运行、合格 Agent"的评估，依次执行 5 项：

1. **修 B-1 → 全量构建绿** ✅ 见 §8。`mvn clean install` 11 模块全过。
2. **端到端真跑** ✅ **已实际执行（桩 LLM 版）2026-06-05**：用 brew 本地 redis+mysql + `scripts/mock_llm_server.py`（桩 LLM 返回 ReAct final_answer）真实启动 agent-service（`EDUCARE_AGENT_LOOP_ENABLED=true`），`curl -XPOST /trigger/1` → **status=COMPLETED**，`risk_analysis_result`/`intervention_plan` 真写入 MySQL，日志 `RISK_ANALYZING -> COMPLETED` + `AgentLoop 路径风险等级 LOW，直接完成`，桩 LLM 经 ChatClient/拦截器链被真实 HTTP 调到。脚本 `scripts/local_real_run.sh`，证据见 `E2E_RUNBOOK.md` 附录。**真实 14B 模型 + MCP 工具 + Langfuse** 那层仍需 GPU/Docker 机器按 Runbook §1-§6 执行。途中修真实 bug：`AgentLoopDryRunController` 硬依赖 `ToolCallbackProvider` → 改 `ObjectProvider`。
3. **冒烟集成测试** ✅ `AgentLoopE2ECodeTest`：用真实 `AgentLoop` + 真实 `agent-loop.system.md` + 真实 `SkillLoader` + 脚本化假 LLM/工具，跑通 `think→调 get_student_profile→final_answer→解析 risk/plan/level` 全链路（除"活模型生成"外的全部代码路径）；`parseAgentLoopFinalAnswer` 改包级 static 可测。agent-service 共 34 case 全绿。
4. **docker-compose 编排 agent 全栈** ✅ `docker/Dockerfile.springboot`（按 MODULE 参数化）+ compose 加 `agent-service(8087)` `mcp-student-data(8094)` `knowledge-rag-mcp(8095)` `memory-mcp(8096)` 四服务，带 healthcheck + `depends_on: service_healthy`（agent-service 等两个 MCP 探活后启动）；`docker compose config` 校验通过。
5. **agent vs legacy eval 对比** ✅ **已实际执行（活对比）2026-06-05**：桩 LLM 升级双模（按 system prompt 含 `final_answer` 分流 ReAct/risk JSON），同栈下以 `enabled=false/true` 重启 agent-service 各触发真实任务，实测 **legacy student=11 → COMPLETED|LOW**、**agent student=12 → COMPLETED|LOW**，两路均活跑通且终态一致 → agent 不比 legacy 差。边界：桩对两路均返回 low（风险阶段短路），证明的是"两条代码路径均能活跑且一致"，**非**判别性准确率对比（后者需真实 14B + legacy 完整 plan/audit 链路 = Python+Milvus，Milvus 需 Docker）；判别性全量对比留 `eval/agent_vs_legacy.sh` 在 GPU/Docker 机器执行。

> 结论：**5 件全部实际执行**。第 1/3/4 硬证据完成；第 2 项用桩 LLM 在**活服务**上真跑通过（ReAct 循环跑完 + final_answer 解析落库 MySQL + COMPLETED）；第 5 项**两条路径活对比一致**。仅"真实 14B 判别质量 + 经真实 MCP 工具取数 + Langfuse trace + legacy 深链路 plan/audit"受 GPU/Docker（Milvus）物理限制，留具备条件的机器按 Runbook 执行。可运行性已从"完全缺失证据"跃升到"活服务跑通 + 双路径活对比 + 一键可复现"。
> **注（沙箱测试边界）**：第 2/5 项早期用 brew 本地 redis+mysql(9.6) + 桩 LLM 取证；**生产/部署方案不变**，仍走 `docker-compose.yml`。
> **第 2 件升级（2026-06-06，真实 Docker 栈）**：Docker daemon 起来后改用真实编排跑：
> - ✅ **真实 Docker infra 全起**：`mysql:8.0`（schema 自动加载、native_password）+ `redis:7-alpine` + `nacos`（推了 common.yml/jwt）+ `milvus`（healthy）+ etcd/minio —— "起齐 MySQL/Redis/Nacos/Milvus" 字面达成
> - ✅ **真实服务链**：`student-service`（/student/1→200）+ `mcp-student-data`（8094，真 MCP server）+ `agent-service`（真 Nacos+mysql:8.0+真 MCP client）全部真实启动
> - ✅ **真实 MCP 集成验证**：mcp-student-data 日志确认 agent-service 的 `spring-ai-mcp-client`（student-data/knowledge-rag）对真 MCP server 做了真实 `initialize` 握手 over Streamable HTTP；AgentLoop **真实调用了 `get_student_profile` 工具**（经真 MCP client→Streamable HTTP→真 server，工具调用确实发生）
> - ⚠ 残留：① 双连接指同一 server 时工具名冲突被去重，需第 2 个工具名不同的 MCP server（knowledge-rag/memory）；② 这俩 Python MCP server 构建受 `requirements.txt` 依赖死锁阻塞 —— **已修**（httpx/uvicorn/python-dotenv 放宽 pin，resolver 通过；本沙箱 pymilvus 编译 + 镜像源 421 使完整构建过慢未在会话内跑完）；③ llama.cpp+14B 物理不可得（无 GPU），仍用桩 LLM
> **第 2 件再升级（完整真实 MCP 工具调用闭环）2026-06-06**：定位到之前"未知工具"的真因 —— 桩调 `get_student_profile`(蛇形) 与 @Tool 实际名 `getStudentProfile`(驼峰) 不符（非 MCP 问题；Spring AI 多连接会自动给重复工具加 `alt_N_` 前缀，不排除）。修正后实测 task 3410：
> ```
> [AgentLoop][task-3410] iter=1 tool=getStudentProfile → 166 bytes   ← 真 MCP client→Streamable HTTP→真 mcp-student-data，@Tool 执行返回
> [AgentLoop][task-3410] iter=2 COMPLETED finalAnswer=...            ← 多轮 ReAct 循环真实走完
> DB(真 Docker mysql:8.0): id=3410 status=COMPLETED risk_level=LOW risk_len=237 plan_len=219  ← final_answer 解析落库
> ```
> **至此第 2 件除"真 14B"(桩替代，无 GPU)与"Langfuse trace"(未配 key)外全部跑通**：真 Docker infra(mysql:8.0/redis/nacos/milvus) + 真 mcp-student-data(MCP server) + agent-service，**ReAct 多轮循环跑完 + 真实 MCP 工具调用(getStudentProfile 经 Streamable HTTP) + final_answer 解析落库 COMPLETED**。knowledge-rag/memory 两个 server 未起不影响该闭环验证（仅多两组工具）。

> 结论：除"真 14B 模型"（无 GPU 物理不可能）外，**MySQL/Redis/Nacos/Milvus + 真 MCP server + agent 真实 MCP 工具调用 + 多轮 ReAct + final_answer 落库 全部以真实 Docker 部署跑通闭环**，远超 brew 替代；并顺手修了阻塞 Python MCP 镜像构建的真实依赖 bug。

---

## 10. Phase J —— Agent Harness 补全（计划：`~/.claude/plans/whimsical-twirling-coral.md`）

> 目标：把能活跑的 ReAct AgentLoop 升级为现代 Agent Harness —— 补齐"会在真实多轮/多工具/敏感数据负载下出问题"的核心原语。范围全量 J-1+J-2+J-3，工具协议双轨（ReAct 默认 + native 选项）。

### J-1 关键：让 loop 在真实负载下不崩
- [x] **J-1.1 上下文/历史压缩**
  - 完成于 2026-06-06：新 `core/HistoryCompactor`（纯函数）—— 最近 `KEEP_RECENT_TURNS=3` 轮 observation 原文（截断 2048），更早轮压成单行（observation 截断 256），总长被窗口上界框住不随轮数线性爆；`AgentLoop.composeUserPromptWithHistory` 委托之。`HistoryCompactorTest` 6 case
- [x] **J-1.2 工具守卫层**
  - 完成于 2026-06-06：`core/ToolGuard` 接口 + `GuardDecision` + `DefaultToolGuard`（总开关 + 工具白名单 + 参数体量上限 + 敏感工具按角色门控，async 无上下文默认放行；DENY 计数 `educare.agent.tool_guard.denied{tool}`）；`AgentLoop` `@Autowired(required=false) ToolGuard`，`invokeTool` 前 check，DENY → `TOOL_DENIED` observation 喂回 LLM 循环继续不崩（null guard=全放行，旧测试零改动）；`application.yml` 加 `educare.agent.tool-guard.*`。`DefaultToolGuardTest` 7 + `AgentLoopGuardTest` 1
- [x] **J-1.3 输出验证 + 自纠错**
  - 完成于 2026-06-06：`core/FinalAnswerValidator` 函数式接口；`AgentLoop.run(req, validator)` 重载（`run(req)` 委托，零 churn）—— final_answer 不合格且有修复预算（`MAX_FINAL_ANSWER_REPAIRS=2`）→ `FINAL_ANSWER_INVALID` observation 触发修复轮，预算耗尽接受当前答案；`AgentTaskServiceImpl` 传 `validateFinalAnswer`（复用 `parseAgentLoopFinalAnswer` 校验双 JSON）。`AgentLoopValidationTest` 3 case
  - **J-1 关口**：`mvn clean install` 11 模块 SUCCESS，agent-service **51 测试**全绿（J-1 新增 17），Python 33 全过

### J-2 能力对齐
> ⚠ **后记（2026-08-26 审计补注）**：J-2.2 的 MemoryGateway 随 H-5 记忆子系统一并被 B1/T5 删除（commit 75fc3f4）；J-2.1 子代理与 J-2.3/J-2.4 仍在代码中。
- [x] **J-2.1 子代理编排（agent-as-tool，= I-3）**
  - 完成于 2026-06-06：`SubAgent` + `SubAgentToolCallback`（受限 AgentLoop 包成工具，独立 context + 工具子集，无递归）+ `SubAgentRegistry`（班主任/心理咨询师/学业导师，技能正文做 prompt，默认关 `educare.agent.subagents.enabled`）；`AgentTaskServiceImpl` 主工具 = MCP + 子代理工具。`SubAgentToolCallbackTest` 5 case
- [x] **J-2.2 记忆接线（接 H-5 memory-server）**
  - 完成于 2026-06-06：`MemoryGateway`（从 MCP provider 按名解析 recall/save，默认关 + 降级）；loop 前 recall 注入背景、研判落库后 save_episode；memory MCP 连接 opt-in。`MemoryGatewayTest` 6 case
- [x] **J-2.3 规划/Todo 显式步**
  - 完成于 2026-06-06：ReAct 协议加可选 `plan` 字段，`parseLlmJson` 抽取、run() 捕获首个 plan 记日志（随 rawLlmOutput 进 trace）。`AgentLoopPlanTest` 4 case
- [x] **J-2.4 并行工具执行**
  - 完成于 2026-06-06：`action` 支持数组 → `parallelCalls`；`executeParallel`（有 `agentExecutor` 则 CompletableFuture 并发、无则顺序；逐个守卫+重试，单个失败/被拒不影响其它，合并 Observation）。`AgentLoopParallelTest` 4 case
  - **J-2 关口**：`mvn clean install` SUCCESS，agent-service **70 测试**全绿（J-2 新增 19），Python 全过

### J-3 生产化 / DX
- [x] **J-3.1 Hooks 生命周期**
  - 完成于 2026-06-06：`AgentLoopHook` 接口（onStart/onIteration/onFinish）；AgentLoop `@Autowired(required=false) List<AgentLoopHook>`，9 处 traces.add 统一走 `recordTrace` 触发 onIteration，钩子异常被吞不影响主循环。`AgentLoopHookTest` 2 case
- [x] **J-3.2 过程流式（接既有 SSE）**
  - 完成于 2026-06-06：`StreamingHook` 每轮发 `WarningPublisher.publishProgress`（新频道 `edu:agent:progress`），默认关 `educare.agent.streaming.enabled`
- [x] **J-3.3 检查点 / 续跑**
  - 完成于 2026-06-06：`CheckpointHook` 每轮轨迹 RPUSH Redis `edu:agent:loop:ckpt:<taskTag>`（onStart 清旧 + TTL 回收 + onFinish 记终态），零 schema 改动；完整读回续跑留增量。默认关
  - J-3.2/3.3 共 `StreamingAndCheckpointHookTest` 4 case
- [x] **J-3.4 工具协议双轨（ReAct + native 选项 flag）**
  - 完成于 2026-06-06：`educare.agent.loop.protocol=react|native`；native 走 `chatClient...toolCallbacks(tools).call()` 原生 tool-calling（单条 trace），react 默认；两协议共享 ToolGuard/记忆/子代理/hooks。`AgentLoopNativeTest` 2 case
  - **J-3 关口**：`mvn clean install` SUCCESS，agent-service **78 测试**全绿（Phase J 累计新增 45），Python 全过
  - **Phase J 活跑验证**：全部 J-1/J-2/J-3 bean 接入后 agent-service 真实启动（3s，health UP，无 DI 冲突）+ 桩 LLM 触发任务 → COMPLETED，risk/plan 落库 MySQL，AgentLoop 正常、无 hook 异常

**Phase J 验收**：✅ Harness 补全全部完成（上下文压缩 / 工具守卫 / 输出验证自纠错 / 子代理 / 记忆接线 / 规划步 / 并行工具 / hooks / 流式 / 检查点 / 协议双轨）。从"能活跑的 ReAct loop"升级为"现代 Agent Harness"，所有原语默认安全（守卫开、其余高级特性默认关，opt-in），既有链路零回归。

---

## 11. 上线解阻清单（2026-08-26 固化）

> 由 2026-08-26 进度审计提炼：代码侧已收尾，卡点集中在运行态验证。本节按「可执行性」排序逐项解阻，每项完成后按 §0 协议回写。环境前提类（GPU）无法在本机闭环的明确标注"用户侧"，不虚勾。

- [x] **U-1 Docker daemon 起动**（解锁 U-2/U-3 的前置）
  - 完成于 2026-08-26：`open -a Docker` 启动 Docker Desktop，daemon v29.6.2 就绪
- [x] **U-2 生产形态实例化验证**：`docker compose -f docker-compose.prod.yml config` 解析 + 前端镜像 build（`Dockerfile.frontend`）+ `nginx -t`（`docker/nginx/edu-portrait.conf`）+ `scripts/preflight-prod.sh` 真实场景复跑
  - 完成于 2026-08-26（Docker daemon v29.6.2）：① preflight 三场景实测——缺 `.env` exit 1 / dev 默认值 exit 1 / `openssl rand -hex|base64` 强随机生成 `docker/.env`（gitignored）后「体检全部通过」exit 0；② compose 校验须用叠加形式 `-f docker-compose.yml -f docker-compose.prod.yml`（单跑 prod 报 edu-network 未定义，属预期用法），config 解析 OK；③ `nginx -t` 首跑因 upstream `gateway` 与证书缺失失败，加 `--add-host gateway:127.0.0.1` + 一次性自签证书挂载后 syntax ok / test successful；④ 前端镜像 `edu-portrait-frontend:audit` 构建成功（node:20-alpine build + nginx:1.27-alpine 运行时）。**遗留真实发现**：prod 形态此前从未实例化验证过，本次为首次全链通过
- [x] **U-3 桩 LLM 全栈起栈冒烟**（当前 HEAD 复验 §9 结论）：infra（mysql/redis/nacos/milvus）+ agent-service/mcp-student-data + `scripts/mock_llm_server.py` → `bash scripts/mcp_smoke_test.sh` + `/agent/api/v1/task/trigger/{id}` e2e 到 COMPLETED
  - 完成于 2026-08-26：栈形态＝Docker infra（mysql:8.0 全新卷自动加载 sql/init 5 脚本 21 表 / redis / nacos(healthy) / milvus+etcd+minio）+ 宿主 jar 六服务（student 8084 / mental 8085 / gateway 8080 / agent 8087 / mcp-student-data 8094 / knowledge-rag 8095，JDK17+Maven 经 brew 现装）+ 桩 LLM :8091（chat + 新增 /v1/embeddings）。**结果**：① `mcp_smoke_test.sh` **7/7 工具全绿**（student-data 四工具回真 MySQL 数据；knowledge-rag 三工具空库 fallback 正常）；② 网关触发 task=1 → `[AgentLoop] iter=1 tool=getStudentProfile → 224 bytes`（真 MCP Streamable HTTP 取数）→ iter=2 final_answer → **COMPLETED / risk_level=LOW / risk 313B + plan 271B 落库**；③ 鉴权链活体验证：无 token 401、手铸 HS256 token + Redis 会话白名单 `token:{userId}` 后 200。**途中修的真实问题（3 个 commit 内）**：a) `01_init.sql:150` mental_questionnaire 表缺逗号 + update_time 列重复定义——全新库初始化必断的真 schema bug；b) `mcp_smoke_test.sh` 两处滞后：未剥 SSE 信封致 jq 必败 + student-data 工具名蛇形≠实际驼峰 @Tool 名（§9 已知问题的残留）；c) compose nacos 服务补 `platform: linux/amd64`（该 tag 无 arm64 清单）。**环境经验**：BuildKit 内 Maven 走外网仅 B 级速率（宿主同源 MB 级），Java 服务改宿主构建运行；服务经 Nacos 注册 LAN IP 会被本机防火墙超时，需 `SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1`
- [x] **U-4 RAG 灌库机械链路（瘦身后为 dense）**：Milvus 起后 `/api/v1/rag/upsert` 灌库并验证 dense retrieve；真 embedding 语义质量移交 R-5.3
  - 机械链路完成于 2026-08-26：`init_milvus.py` 建 4 集合（dim=1024）→ 桩 LLM 新增 `/v1/embeddings`（确定性向量，dim 对齐）→ G-4 upsert API 三库灌入种子语料（chunks_written=1×3，幂等短路/删除重插路径首次真跑）→ `/api/v1/rag/retrieve` dense 检索命中。**两项转用户侧/作废**：① hybrid eval **随 B5 删除而作废**（见 §4 I-1 后记）；② "真实语料 + 真 BGE 向量"的语义检索质量验证需 GPU/embedding 服务（桩向量仅证明机械链路，无语义判别力）
- [x] **U-5 CI 覆盖率 % 门推进**（唯一非环境依赖项）：为业务模块核心类补单测 + 扩 jacoco includes 定向门，保持 backend-ci 绿
  - 完成于 2026-08-26：新增 `common` 两测试类——`JwtUtilTest` 8 例（A5 密钥 fail-fast 空/短、签发解析往返、roles claim 缺失/非集合降级、过期抛 ExpiredJwtException、签名篡改拒、异密钥拒；注：纯单测不触发 `@PostConstruct`，须显式 `init()`）+ `PromptSanitizerTest` 8 例（控制字符清洗但保留 \n\t、行首角色前缀剥离含中文「系统：」且非行首不误伤、截断标记、递归清洗仅触字符串叶、wrap 标签消毒）；jacoco includes 追加 `com.edu.common.util.{JwtUtil,PromptSanitizer}` ≥80% LINE。验证：`mvn -pl common test` 40 例全绿 + jacoco check 过 + **全后端 `mvn clean test` 11 模块 BUILD SUCCESS** + Python unittest 19 例全过
- [x] **U-6 五角色字段权限活体复核**（栈起后）：跑 `FIELD_PERMISSION_VERIFY.md §4` curl+jq 一键脚本 + `scripts/gateway_verify.sh`
  - 完成于 2026-08-26（9 服务活栈：auth/user/teacher/student/mental/gateway/agent/mcp-student-data/knowledge-rag）：① 造数：counselor/academic_advisor 两角色 + test_* 五账号（htpasswd 生成 bcrypt）经真 `/auth/login` 取 token；② **字段矩阵与 FIELD_PERMISSION.md §4 完全一致**——/student/1 birthDate(HIGH)：admin✓ counselor✓ acadv✗ teacher✗ student✗；gpa(MEDIUM)：student✗ 其余✓；/user/1 email/phone(HIGH) 同模式 + password 字段对全角色缺失（@JsonIgnore）✓；③ `gateway_verify.sh` **6/6 全过**（公开路径放行/准入 401/无效 token 401/_internal 403/**登出吊销 A6 活体验证**）；④ IDOR 活体断言：test_student(8) 读 student 1(属3) → 业务码 403「无权访问该学生档案」，acadvs(6) 查 mental userId=3 → 403「无权访问他人心理测评数据」（注：业务层拒绝走 Result.error(403)+HTTP200 信封，验脚本须比对 body.code 而非 HTTP status）。**附带发现**：种子账号默认口令与 AGENTS.md「password 等于 username」描述不符（admin/admin 登录 401），文档需订正或轮换种子哈希
- [ ] **U-7 真 14B GPU 全链路 + Langfuse trace**（**用户侧**，无 GPU 物理限制）：llama.cpp(8091) + Runbook §1-§6 + G-1 cache 命中验收
  - 执行计划（2026-08-26 固化，拆两个半场）：① **U-7a Langfuse 半场（无 GPU 可做）**：`--profile langfuse` 起 postgres+langfuse-server → 控制台/API 建 project 取 key → agent-service/ai-inference 注入 LANGFUSE_* 重启 → 触发任务 → Langfuse API 查到 trace 即闭环；② **U-7b 真 14B 半场**：本机 48GB M4 Pro 具备 Metal 跑 Q4_14B 条件——brew install llama.cpp → hf-mirror 拉 qwen2.5-14b Q4_K_M（~9GB，网络允许则后台下）→ 起 :8091 → 切真模型全栈 e2e + `verify_prompt_cache.sh` 验 G-1 命中率 ≥80%；下载不可行则此半场明确留用户侧并注明带宽卡点
- [x] **U-8 范围裁决：不恢复 memory-server / 百分比灰度**
  - 完成于 2026-08-27：两项分别已由 review B1/B4 决策删除或降级，且当前交付目标是瘦身后的单布尔 AgentLoop 主路径；重新实现属于扩 scope，不是上线验证。保留真实主路径/legacy fallback 对比到 R-5.4。
- [x] **U-9 种子账号口令核实与修正**：实测种子哈希真实明文 → 二选一（订正文档 OR 轮换种子哈希对齐「password=username」约定）→ 同步运行库与 AGENTS.md/deploy 文档 → gateway_verify.sh 默认凭据复验通过
  - 完成于 2026-08-26（U-8 前置先行）：① python bcrypt 对旧种子哈希 `$2a$10$N.zmd...` 跑 12 个常见候选（password/admin/123456/edu123456 等）**全部不中**——判定为教程复制来的来源不明孤儿凭据；② 决策走「轮换种子哈希对齐文档既有约定」（E2E_RUNBOOK/DEPLOY/AGENTS.md 本就写 password=username，改文档反而三处撒谎）：`01_init.sql` 三账号各生成独立 bcrypt（不再共用一哈希，顺带消除同盐表象）+ 运行库 UPDATE 同步；③ 复验：admin/admin 经网关登录 200 + `gateway_verify.sh` **默认凭据** 6/6 全过。prod 侧既有 `sql/prod/01_rotate_default_passwords.sql` 兜底不受影响

---

## 12. Release Readiness —— 上线原子任务（2026-08-27）

> **上线完成定义**：R-1~R-6 全部完成；默认 ReAct 路径与可选 native 路径安全语义一致；生产依赖无 high/critical 已知漏洞；CI 覆盖后端/Python/前端/eval；真模型、真 embedding、Langfuse 与生产 compose 均有当前运行证据。储备项 I-2/I-4/I-6 不属于本轮上线范围。

### R-1 MCP 工具契约闭环（首要阻塞）

- [x] **R-1.1** 统一 Java MCP 工具名：显式声明为跨语言一致的 snake_case，保证 Agent prompt、Skills、SubAgent 白名单、ToolGuard 与 ToolDefinition 同名
  - 完成于 2026-08-27：`StudentDataTools` 4 个 `@Tool` 显式声明 snake_case，同步 smoke/mock 调用契约。
- [x] **R-1.2** 补真实 ToolDefinition 名称回归：覆盖 4 个 Java + 3 个 Python 工具、SubAgent 子集过滤、心理工具敏感守卫
  - 完成于 2026-08-27：新增 `StudentDataToolsContractTest`、`SubAgentRegistryContractTest`、`test_mcp_tool_contract.py`，契约与既有 `ToolGuardTest` 共同覆盖。
- [x] **R-1.3** 跑 agent-service/mcp-student-data 定向测试 + 全后端测试
  - 完成于 2026-08-27：JDK 17 下 `mvn -B -ntp clean test` 11 模块 160 例全绿；Python unittest 20 例全绿。

### R-2 native 协议安全语义闭环

- [x] **R-2.1** native 调用前包装/过滤 ToolCallback，确保每次工具调用经过 ToolGuard
  - 完成于 2026-08-27：`AgentLoop.guardNativeTools` 包装 native callbacks，拒绝调用返回 `TOOL_DENIED` 且不触发下游工具。
- [x] **R-2.2** native 最终输出 validator 失败时不得返回 COMPLETED；定义失败/修复语义并补测试
  - 完成于 2026-08-27：新增 `AgentLoopStatus.VALIDATION_ERROR`，validator 失败保留 trace 并按失败态返回。
- [x] **R-2.3** 证明 react/native 两路在守卫、输出校验、hooks 的可观察语义一致
  - 完成于 2026-08-27：`AgentLoopNativeTest` 扩至 6 例，覆盖允许/拒绝工具、输出校验和 start/iteration/finish hooks；全量回归通过。

### R-3 前端供应链与 CI

- [x] **R-3.1** 升级 axios/echarts 及传递依赖，`npm audit --omit=dev` 达到 high=0、critical=0
  - 完成于 2026-08-27：锁定 axios 1.20.0、ECharts 6.1.0、PostCSS 8.5.26、nanoid 3.3.18，并升级 Vite 8.2.2；完整 `npm audit` 0 漏洞。
- [x] **R-3.2** 新增 frontend-ci：npm ci + lint（无写模式）+ build + audit high 门
  - 完成于 2026-08-27：新增 `.github/workflows/frontend-ci.yml` 与 `lint:check`，干净安装、只读 lint、生产构建、audit 门均本地通过。
- [x] **R-3.3** 处理 >500KB 主 chunk（拆包或记录可接受预算并设显式门）
  - 完成于 2026-08-27：Vite 函数式 manualChunks 将主入口拆至 64.0 KiB；`check-bundle-size.mjs` 固化入口 500 KiB/单 chunk 1200 KiB/总 JS 2500 KiB 预算并接 CI。

### R-4 测试、工具链与生产安全门

- [x] **R-4.1** 增加 Java 17 Maven Enforcer/Toolchains fail-fast，避免默认 JDK 26 + Lombok 静默失效
  - 完成于 2026-08-27：父 POM `maven-enforcer-plugin` 强制 `[17,18)`；JDK 26 validate 按明确提示失败，JDK 17 全模块放行。
- [x] **R-4.2** 为 user-service、teacher-service、mcp-student-data 补核心安全/控制器测试；扩 JaCoCo 定向门
  - 完成于 2026-08-27：补 user/teacher 端点授权与 13 例控制器测试、MCP token filter 4 例；三类纳入定向行覆盖率 ≥80% 门。
- [x] **R-4.3** 为 Python FastAPI RAG/upsert/MCP 增加无外部服务集成测试
  - 完成于 2026-08-27：ASGI transport + stub 覆盖 RAG 降级、upsert 鉴权/写入、MCP adapter 限流与响应整形；Python 共 25 例，CI 安装最小测试依赖。
- [x] **R-4.4** 生产 preflight 把 EDUCARE_MCP_TOKEN/REDIS_PASSWORD 从建议项升级为硬门
  - 完成于 2026-08-27：两凭据均要求非占位且 ≥32 字符；compose 启用 Redis requirepass 并向 gateway/Agent/Python RAG 全消费者传递，新增 preflight 正反例 CI 回归。

### R-5 真实 AI 与可观测验收

- [ ] **R-5.1** Langfuse self-hosted 起栈、建 project、注入 key，触发任务并从 API/UI 查到 agent.loop + llm.chat trace
- [ ] **R-5.2** 真 Qwen 14B 启动并跑完整 AgentLoop；真实工具名调用成功、final_answer 合法落库
- [ ] **R-5.3** 真 BGE embedding 灌入真实语料，跑检索质量集并固化 dense baseline
- [ ] **R-5.4** 真模型下 AgentLoop/legacy 对比 + prompt cache ≥80% + 50 例风险 eval ≥0.85

### R-6 上线总验收

- [ ] **R-6.1** CI 全门复验：backend/Python/frontend/eval 全绿，无 high/critical 生产依赖漏洞
- [ ] **R-6.2** 生产 compose + TLS/nginx + gateway auth + 字段权限 + IDOR + MCP token + 备份恢复 + 监控告警全栈验收
- [ ] **R-6.3** 更新 DEPLOY/E2E runbook 与本文件证据，形成上线签字清单；仅在全部证据齐全后声明可上线
