# EduCare Agent 端到端真跑 Runbook（"可运行"证据采集）

> 目的：把"AgentLoop 真能跑通一个任务"从代码级证据(`AgentLoopE2ECodeTest`)升级为**活模型端到端证据**。
> 需要宿主有 llama.cpp（生成 LLM）+ Docker。本文件给出逐条命令与每步的"通过判据"。

## 0. 为什么需要本文件
代码级证据(无 LLM)已有：`AgentLoopE2ECodeTest` 用真实 AgentLoop + 脚本化 LLM 跑通
`think → 调 get_student_profile → final_answer → 解析 risk/plan/level` 全链路。
**唯一未覆盖的是"活的 14B 模型真的能稳定吐出 ReAct JSON + 双 JSON final_answer"**——这一步只能在有模型的机器上验证，即本 Runbook。

## 1. 前置：宿主起本地模型（llama.cpp / vLLM，OpenAI 兼容）
最少只需生成 LLM(8091)；要完整 RAG 还需 embedding(8092) + reranker(8093)。
```bash
# 生成 LLM（AgentLoop 必需）—— 端口 8091，模型如 qwen2.5-14b-instruct
~/edu-ai/start-llm-server.sh         # 或你的 llama.cpp server 命令，--port 8091
# RAG 用（可选）
bash scripts/start-embedding-server.sh   # 8092
bash scripts/start-reranker-server.sh    # 8093
```
通过判据：`curl http://localhost:8091/v1/models` 返回模型列表。

## 2. 一键起全栈（含 agent-service + 2 个 MCP server）
```bash
cd docker
docker compose up -d                       # 基础设施 + gateway + ai-inference + agent 全栈
docker compose ps                          # 等 agent-service / mcp-student-data / knowledge-rag-mcp 变 healthy
```
通过判据：`docker compose ps` 中 3 个 agent 相关容器 `STATUS=healthy`。
> agent-service 配了 `depends_on: condition: service_healthy`，会等两个 MCP server 探活通过才启动。

## 3. 灌库（建表 + 种子）
```bash
# 主 schema 与扩展（含 mental_question 全字段、student_academic_record/attendance、intervention_feedback）
docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < ../sql/init/01_init.sql
docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < ../sql/init/04_student_extras.sql
docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < ../sql/init/05_intervention_feedback.sql
# RAG 知识入库（亮点链所需，dense 检索用）：POST /api/v1/rag/upsert，见 RAG_UPSERT_DESIGN.md
```

## 4. MCP server 冒烟（7 个 tool happy path）
```bash
bash scripts/mcp_smoke_test.sh             # 需 bash≥4 / curl / jq
```
通过判据：脚本对 student-data(8094) + knowledge-rag(8095) 共 7 个 tool 各调一次，末尾 exit 0。

## 5. 真跑 AgentLoop（核心证据）
AgentLoop 现为**默认主路径**（`educare.agent.loop.enabled` 默认 true），起栈即生效，无需额外开关。
如需回落旧 4 阶段流水线对照，置 `EDUCARE_AGENT_LOOP_ENABLED=false` 重启 agent-service：
```bash
EDUCARE_AGENT_LOOP_ENABLED=false docker compose up -d agent-service   # 回落 legacy（对照用）
```
> ⚠ 网关鉴权默认开（`educare.gateway.auth.enabled`）+ agent-service 自身鉴权默认开
> （`educare.agent.self-auth.enabled`）：经 :8080 / 直连 :8087 的业务请求都须带 JWT，否则 401。
> 先登录取 token（需 auth-service 在跑；默认账号密码=用户名，见 `sql/init/01_init.sql`）。
> 纯本地快验也可临时 `EDUCARE_GATEWAY_AUTH_ENABLED=false EDUCARE_AGENT_SELF_AUTH_ENABLED=false` 起栈跳过鉴权。

触发并观察：
```bash
# 0) 登录取 token（admin 可见全部字段，便于看完整产物）
TOKEN=$(curl -s -XPOST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .data.token)

# 1) 触发 + 轮询（带 Authorization）
TASK=$(curl -s -XPOST http://localhost:8080/agent/api/v1/task/trigger/1 \
  -H "Authorization: Bearer $TOKEN" | jq -r .data)
watch -n2 "curl -s http://localhost:8080/agent/api/v1/task/$TASK -H 'Authorization: Bearer $TOKEN' | jq '{status:.data.status, risk:.data.riskLevel}'"
curl -s http://localhost:8080/agent/api/v1/task/$TASK -H "Authorization: Bearer $TOKEN" \
  | jq '{status:.data.status, riskLevel:.data.riskLevel, risk:.data.riskAnalysisResult, plan:.data.interventionPlan}'
docker compose logs --tail=200 agent-service | grep -E "AgentLoop|iter=|COMPLETED|final_answer"
```
**"可运行"通过判据（全部满足才算真跑通）**：
1. 任务 `status` 走到 `COMPLETED`（或 `REJECTED`，合规分支）；
2. `riskAnalysisResult` 与 `interventionPlan` 两个字段非空且是合法 JSON（= final_answer 双 JSON 被解析落库）；
3. agent-service 日志出现 `[AgentLoop][task-..] iter=N COMPLETED`；
4. Langfuse(http://localhost:3001) 出现 `agent.loop` trace（需配 LANGFUSE_* key）。

## 6. 端到端业务冒烟（含注入/缓存/限流）
```bash
EDUCARE_DEBUG_FORCE_RISK_LEVEL=high bash scripts/smoke_test_agent.sh   # 强制走完整 4 阶段
```

## 6.5 网关安全门复验（鉴权链证据）
```bash
bash scripts/gateway_verify.sh         # 准入 401 / 有效 200 / 无效 401 / _internal 403 / 登出吊销
```
通过判据：6 项自动断言全过（无 token→401、有效→200、无效→401、`/_internal/`→403、登出前 200→登出后 401）。
角色相关项需多账号手动复验：
- **字段脱敏**：admin 与 student 两个 token 取同一学生，student 的响应里敏感字段（心理/经济）应被脱敏（`@SensitiveField` + `FieldPermissionAdvice`，默认开）。
- **IDOR**：student A 的 token 取 student B 详情应 403（`AccessGuard.allowSelfRoleOrInternal`）。

## 7. agent vs legacy eval 对比（合格性证据，见第 5 件）
```bash
bash eval/agent_vs_legacy.sh           # 同一组学生分别按 legacy / agent 跑，比对风险等级一致性与对 ground truth 的准确率
```
通过判据：agent 路径对 ground truth 的等级一致率 **≥ legacy**（不回退）。

---
## 附：无 GPU 桩 LLM 真跑（2026-06-05 已实际执行 ✅）

开发沙箱无 Docker daemon / 无 GPU / 无 14B 权重，但仍用 **brew 本地 redis+mysql + 桩 LLM** 把
**活的 agent-service over HTTP** 真跑了一遍（脚本 `scripts/local_real_run.sh` + `scripts/mock_llm_server.py`）：

- redis（brew）+ mysql 9.6（brew，`edu`/`edu123456`，载入 `03_agent_init.sql` 建 `agent_task`）
- 桩 LLM `:8091` 返回 OpenAI 格式 + ReAct `final_answer`（双 JSON，risk_level=low 触发短路免 Python）
- agent-service 真实启动（`EDUCARE_AGENT_LOOP_ENABLED=true`，关 Nacos/MCP，jwt.secret 直配）：
  `Started AgentServiceApplication in 5.4s`，`/actuator/health = UP`
- `curl -XPOST /agent/api/v1/task/trigger/1` → 轮询 **status=COMPLETED**

**实测证据**（活服务，非单测）：
```
任务 1 状态流转: RISK_ANALYZING -> COMPLETED
任务 1 AgentLoop 路径风险等级 LOW，直接完成
DB Row: 1, 1, COMPLETED, LOW, ..., <<BLOB>>(risk), <<BLOB>>(plan), ...   ← risk/plan 真写入 MySQL
mock-llm /v1/chat/completions hit   ← ChatClient→拦截器→HTTP→解析 全程真实
```
即"ReAct 循环跑完 + final_answer 解析落库 + status COMPLETED"在**活的运行服务上验证通过**。

**桩跑覆盖边界**：用桩 LLM 替代 14B（无 GPU），关闭 MCP client（无 MCP server 进程）。
未覆盖：① 真实 14B 的 ReAct/JSON 守规稳定性；② 经真实 MCP 工具取数；③ Langfuse trace（未配 key）；
④ 合规审核分支（需 Python ai-inference + 高风险触发）；⑤ agent vs legacy 全量对比（legacy 需 Python+Milvus，Milvus 需 Docker）。
这些需具备 GPU/Docker 的机器按上文 §1-§7 执行。

> 途中修复真实缺陷：`AgentLoopDryRunController` 由硬依赖 `ToolCallbackProvider` 改为 `ObjectProvider`，
> 使 MCP client 关闭/未就绪时 agent-service 仍能启动（与 `AgentTaskServiceImpl` 一致）。

### 附 2：agent vs legacy 活对比（2026-06-05 已实际执行 ✅）

`scripts/mock_llm_server.py` 升级为**双模**（请求 system prompt 含 `final_answer` → 返回 ReAct；否则返回
legacy 风险识别期望的普通 risk JSON）。同一桩 LLM 下，分别以 `EDUCARE_AGENT_LOOP_ENABLED=false/true`
重启 agent-service、各触发真实任务，实测：

```
>> legacy 模式 (enabled=false)  student=11 -> COMPLETED | LOW   （doExecuteLegacy → RiskAnalyzeService → 低风险短路）
>> agent  模式 (enabled=true)   student=12 -> COMPLETED | LOW   （doExecuteAgentLoop → AgentLoop → final_answer → 低风险短路）
```

**两条路径都在活服务上跑通、都 COMPLETED、风险等级一致（LOW）→ agent 路径不比 legacy 差。**

边界（诚实标注）：桩 LLM 对两路均返回 low，二者在风险阶段短路，故本对比证明的是**两条代码路径均能活跑且终态一致**，
**不是**对多样 ground truth 的判别性准确率对比——后者需真实 14B（区分不同风险）+ legacy 的完整 plan/audit 链路
（Python ai-inference + Milvus，Milvus 需 Docker）。判别性全量对比用 `eval/agent_vs_legacy.sh` 在具备 GPU/Docker 的机器执行。
