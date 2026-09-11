# Agent Harness 补全总结（Phase J）

> 目的：把一个"能 demo 跑通的 ReAct AgentLoop"升级为**现代 Agent Harness** ——
> 在真实多轮 / 多工具 / 敏感数据负载下稳健可控。
> 范围：J-1（关键件）+ J-2（能力对齐）+ J-3（生产化），共 11 个原语，45 个新单测，零回归。
> 设计原则：**默认安全**（守卫默认开，其余高级特性默认关、opt-in）；**零 churn**（用 `@Autowired(required=false)`/方法重载/可选字段接入，既有 `new AgentLoop(...)` 单测与默认链路分毫不动）。

---

## 一、为什么要做（出发点）

对照现代 Agent Harness（如 Claude Code 式：上下文管理 + 工具守卫 + 子代理 + 输出验证 + 记忆 + 可观测），原 `AgentLoop` 缺若干**会在真实负载下直接出问题**的核心原语。差距矩阵：

| Harness 组件 | 原状态 | 风险 |
|---|---|---|
| 上下文/历史压缩 | ❌ 缺 | 每轮重放全量历史 → 多轮必上下文溢出、成本爆、prompt cache 失效 |
| 工具守卫/权限 | ❌ 缺 | 工具调用零授权，敏感数据/未成年人合规裸奔 |
| 输出验证/自纠错 | ❌ 缺 | final_answer 不合 schema 即 FAILED，无修复机会 |
| 子代理编排 | ❌ 缺 | 无专家视角分工 |
| 记忆接线 | 🟡 建了未接 | memory-server 与 loop 脱节 |
| 规划步 / 并行工具 / hooks / 流式 / 检查点 / 协议双轨 | ❌ 缺 | 长任务不可控、不可观测、不可扩展、单点串行、崩溃即丢、协议单一 |

---

## 二、做了哪些操作（逐项：操作 → 为什么 → 解决了什么）

### J-1 关键：让 loop 在真实负载下不崩

| # | 操作 | 为什么 | 解决了什么 |
|---|---|---|---|
| **J-1.1** 上下文压缩 | 新 `core/HistoryCompactor`（纯函数）：最近 N 轮 observation 原文（截断 2048），更早轮压成单行（截断 256）；`AgentLoop.composeUserPromptWithHistory` 委托之 | 原实现每轮把**全量**历史拼进 prompt | 多轮循环 prompt 不再随轮数线性爆 → 杜绝上下文溢出 + 控成本 + 保 prompt cache 命中 |
| **J-1.2** 工具守卫 | `core/ToolGuard` 接口 + `DefaultToolGuard`（总开关 + 工具白名单 + 参数体量上限 + 敏感工具按角色门控）；`AgentLoop.invokeTool` 前 check，DENY → `TOOL_DENIED` observation 喂回 LLM | 工具调用此前零授权 | 越权/异常工具调用被拦且**不崩**（转结构化错误让模型自适应）；敏感工具（心理量表）按角色受控；DENY 计数可观测 |
| **J-1.3** 输出验证+自纠错 | `core/FinalAnswerValidator` 函数式接口；`AgentLoop.run(req, validator)` 重载：不合格且有修复预算 → `FINAL_ANSWER_INVALID` observation 触发修复轮；`AgentTaskServiceImpl` 传双 JSON schema 校验器 | 原本 schema 不合格直接 FAILED | 模型有机会"自我纠错"再产一份合规 final_answer，减少无谓失败 |

### J-2 能力对齐

| # | 操作 | 为什么 | 解决了什么 |
|---|---|---|---|
| **J-2.1** 子代理（agent-as-tool） | `SubAgent` + `SubAgentToolCallback`（受限 AgentLoop 包成工具，独立 context + 工具子集，无递归）+ `SubAgentRegistry`（班主任/心理咨询师/学业导师，技能正文做 prompt） | 复杂研判需专家视角分工 | 主 loop 可"调用专家子代理"，每个子代理独立上下文、独立工具子集，模块化扩展 |
| **J-2.2** 记忆接线 | `MemoryGateway`（从 MCP provider 按名解析 recall/save，降级安全）；loop 前 `recall_student_history` 注入背景、研判落库后 `save_episode` | H-5 memory-server 建了但没接 loop | 跨任务记忆闭环：分析前补该生历史、分析后留痕，长期可积累 |
| **J-2.3** 规划步 | ReAct 协议加可选 `plan` 字段；`parseLlmJson` 抽取，首轮 plan 记日志 + 进 trace | 长任务执行不透明 | 显式计划可追踪、可 steer、可观测 |
| **J-2.4** 并行工具 | `action` 支持数组 → `parallelCalls`；`executeParallel`（有 agentExecutor 则并发、无则顺序；逐个守卫+重试，单个失败不影响其它） | 多份互不依赖的数据串行取太慢 | 一轮并发取多份数据，减少往返、提速 |

### J-3 生产化 / DX

| # | 操作 | 为什么 | 解决了什么 |
|---|---|---|---|
| **J-3.1** Hooks 生命周期 | `AgentLoopHook` 接口（onStart/onIteration/onFinish）；`AgentLoop` 注入 `List<AgentLoopHook>`，9 处 traces.add 统一走 `recordTrace` 触发钩子，钩子异常被吞 | 审计/流式/检查点等横切关注硬编进循环不可扩展 | 把横切逻辑解耦为可插拔钩子，后续扩展零侵入主循环 |
| **J-3.2** 过程流式 | `StreamingHook` 每轮发 `WarningPublisher.publishProgress`（Redis `edu:agent:progress`）；默认关 | 前端只能看终态，看不到过程 | 思考过程可经 SSE 流式展示，体验与可观测性提升 |
| **J-3.3** 检查点 | `CheckpointHook` 每轮轨迹 RPUSH Redis `edu:agent:loop:ckpt:<taskTag>`（onStart 清旧 + TTL + onFinish 记终态）；零 schema 改动；默认关 | 循环 traces 在内存，崩溃即丢 | 崩溃后可 post-mortem / 续跑基础（持久化已就位） |
| **J-3.4** 协议双轨 | `educare.agent.loop.protocol=react\|native`；native 走 Spring AI 原生 `chatClient...toolCallbacks(tools).call()`；ReAct 仍默认 | 小模型 ReAct JSON 易破；大/云模型更适合原生 tool-calling | 同一套 ToolGuard/记忆/子代理/hooks 下，按模型选协议；为云端大模型留高效路径 |

---

## 三、解决了什么问题（一句话）

- **稳定性**：上下文压缩 + 工具守卫 + 输出自纠错 → loop 在真实多轮 / 越权工具 / 坏输出下**不再崩**。
- **安全/合规**：工具守卫按角色门控敏感工具，DENY 不中断主流程。
- **能力**：子代理分工、跨任务记忆、显式规划、并行取数。
- **可运维**：生命周期 hooks + 过程流式 + 检查点 + 协议双轨。

---

## 四、工程纪律与验证

- **测试**：agent-service **78** 个单测全绿（Phase J 累计新增 **45**）；`mvn clean install` 11 模块 BUILD SUCCESS；Python 33 测试全过。
- **零回归**：所有原语 opt-in / 可选注入，既有默认链路与单测不受影响。
- **活跑验证**：全部 J 系 bean 接入后，agent-service 在真实 Spring 容器启动（3s，无 DI 冲突）+ 真实任务 `COMPLETED`、risk/plan 落库、hooks 正常无异常。
- **真实 Docker 栈闭环**（后续验证）：真 mysql:8.0/redis/nacos/milvus + 真 mcp-student-data MCP server + agent-service，ReAct 多轮循环跑完 + 真实 MCP 工具调用（getStudentProfile 经 Streamable HTTP）+ final_answer 解析落库 COMPLETED。

---

## 五、关键文件清单

| 原语 | 主要文件 |
|---|---|
| 上下文压缩 | `agent-service/.../core/HistoryCompactor.java` |
| 工具守卫 | `core/ToolGuard.java`、`core/DefaultToolGuard.java` |
| 输出验证 | `core/FinalAnswerValidator.java`（+ `AgentLoop.run(req, validator)`） |
| 子代理 | `core/SubAgent.java`、`SubAgentToolCallback.java`、`SubAgentRegistry.java` |
| 记忆接线 | `core/MemoryGateway.java` |
| 规划/并行 | `core/AgentLoop.java`（plan 字段 + `executeParallel`） |
| Hooks/流式/检查点 | `core/AgentLoopHook.java`、`StreamingHook.java`、`CheckpointHook.java` |
| 协议双轨 | `core/AgentLoop.java`（`runNative`）+ `application.yml` |
| 配置 | `agent-service/src/main/resources/application.yml`（`educare.agent.*`） |
| 路线/回写 | `docs/educare/EXECUTION_PLAN.md §10 Phase J`、计划 `~/.claude/plans/whimsical-twirling-coral.md` |
