# EduCare Agent 改进方案（2026.05 版）

> **状态**：v1.1 — Section 8 关键问题已拍板（2026-05-12），开始执行 Phase G
> **作者**：基于 SubAgent 架构诊断 + 2026 年 5 月主流范式评测产出
> **关系**：本文档是 G/H/I 三阶段的**设计源 / 决策依据**；可执行的原子任务清单与进度状态见 [`EXECUTION_PLAN.md`](./EXECUTION_PLAN.md)（按本文档拆解而来）

## 0. 拍板的范围决策（2026-05-12 对齐结果）

执行版与原设计稿的差异（覆盖 Section 8 四问 + Section 5 时间）：

| 决策项 | 设计稿假设 | 执行版决策 | 理由 |
|---|---|---|---|
| Model Router | 本地双实例：32B-Thinking + 32B-Instruct | **混合云**：敏感/原始画像走本地 14B，方案生成/审核走云端 API | 本地 Metal 算力无法同时承载两个 32B；混合云在合规与成本间取平衡 |
| MCP 选型 | 未定 | **Spring AI MCP 主力 + Python FastMCP 补 RAG/Memory** | 项目主体 Java，MCP 业务 Tool 自然落在 Java 侧；Python 保留 RAG 专长 |
| GraphRAG / Neo4j | Phase I-2 上线 | **暂不上**，先做 Hybrid Retrieval（向量+BM25），按 RAG 评测结果再决定 | 教育案例总量未必撑得起图谱投入，先看 Hybrid 收益 |
| 阶段范围 | G+H+I 全 9 周 | **G 全做 + H 瘦身 + I 最小**（约 5-6 周） | 简历级项目，砍重投入保深度 |
| G-1 Prompt Caching | "Spring AI 1.1+ 启用 cache 开关" | **llama.cpp slot cache 路线**：稳定 prompt 前缀 + 抽 timings.cached_n 监控 | 当前用 Spring AI 1.0.0-M6 + llama.cpp，1.1+ 客户端 cache 协议（cache_control）不适用 |

**Phase H 瘦身后范围**：仅 student-data + knowledge-rag 2 个 MCP Server + 主 Agent Loop + 4 个 Skill 文件（H-3 保留）+ Model Router（H-4 改为本地/云端双路由而非两个本地 32B）；H-5/H-6 视进度。

**Phase I 最小范围**：仅 I-1 Hybrid Retrieval + I-5 干预反馈闭环。I-2 GraphRAG / I-3 Subagent / I-4 合规框架 / I-6 时间线 暂列储备。

---

---

## 一、执行摘要

将 EduCare 从"4 阶段硬编码 prompt 流水线"演进为 **"MCP 工具生态 + 主 Agent + Skills/Subagents + Reasoning Model 路由"** 的现代架构，分 3 个阶段、约 8-10 周完成。核心是把工具、编排、记忆、观测四层**全部切到 2025 年成型的开放标准**，而不是自研或绑定单一框架。

---

## 二、设计原则（2026 年）

1. **协议优先于框架**：MCP > LangGraph/AutoGen 这类全家桶。工具、记忆、上下文都走开放协议。
2. **轻 Agent + 强工具**：主 Agent 只负责"想清楚下一步"，所有能力下沉到工具/Skill，避免 LangChain-style 巨型链。
3. **推理模型分级路由**：判断密集（风险识别、合规审核）→ thinking 模式；生成密集（方案文本）→ 快模型；不要一个模型打天下。
4. **Cache First 经济学**：system prompt + 学生画像 + 知识 chunks 都走 prompt caching，目标 cache hit rate ≥70%。
5. **Eval 驱动迭代**：每个 Agent / Tool 都有离线 eval 集，prompt/模型变更必须过 eval gate。
6. **合规即代码**：未成年人合规约束写进系统提示词 + 工具守卫 + 审计日志三层，而不是事后审核。

---

## 三、目标架构

```
                           ┌──────────────────────────────────────┐
                           │  前端 Vue3（含 SSE 实时进度、反馈UI）  │
                           └──────────────────┬───────────────────┘
                                              ↓
                                   Gateway (Spring Cloud)
                                              ↓
        ┌─────────────────────────────────────────────────────────────────┐
        │              agent-service（轻 Agent Runtime + Spring AI）         │
        │  ┌──────────────────────────────────────────────────────────┐   │
        │  │  主 Agent Loop（think→tool→observe，参考 Claude Agent SDK） │   │
        │  └──────────────────────────────────────────────────────────┘   │
        │  ┌──────────┐  ┌───────────┐  ┌─────────┐  ┌───────────────┐   │
        │  │Model      │  │Memory     │  │Cache    │  │Observability  │   │
        │  │Router     │  │Manager    │  │Manager  │  │(Langfuse SDK) │   │
        │  └──────────┘  └───────────┘  └─────────┘  └───────────────┘   │
        └────────┬───────────────────────────────────────────┬────────────┘
                 │ MCP Protocol (stdio/SSE)                  │ HTTP
                 ↓                                           ↓
   ┌──────────────────────────┐                ┌────────────────────────┐
   │   MCP Servers (多个)       │                │  推理模型集群             │
   │  ├─ student-data-server   │                │  ├─ Qwen3-235B-Thinking  │
   │  ├─ knowledge-rag-server  │                │  │  （审核/风险）         │
   │  ├─ intervention-server   │                │  ├─ Qwen3-32B-Instruct   │
   │  ├─ report-server (PDF)   │                │  │  （方案/对话）         │
   │  └─ memory-server (Mem0)  │                │  └─ BGE-M3 + reranker-v2 │
   └────────────┬─────────────┘                └────────────────────────┘
                ↓
   ┌──────────────────────────┐
   │  ai-inference-service     │
   │  ├─ Hybrid Retrieval      │  (向量 + BM25 + GraphRAG)
   │  ├─ Eval Runner (RAGAS)   │
   │  └─ Embedding/Rerank      │
   └──────────────────────────┘
                ↓
   ┌──────────────────────────┐
   │  存储层                    │
   │  ├─ Milvus (向量)          │
   │  ├─ Neo4j (案例图谱) ★新增  │
   │  ├─ MySQL (业务+审计)      │
   │  └─ Redis (缓存+幂等)      │
   └──────────────────────────┘
```

**关键变化**（vs 当前）：
- ❌ 去掉：`AgentTaskServiceImpl.doExecute()` 里的 4 阶段 if-else
- ✅ 新增：MCP Server 集群（5 个独立服务进程）
- ✅ 新增：Model Router、Memory Manager、Cache Manager 三个核心组件
- ✅ 新增：Neo4j 知识图谱、Langfuse 全链路 trace
- 🔄 重构：Python ai-inference 收缩为"检索 + 评测 + 模型推理"，业务逻辑迁回 Java MCP

---

## 四、分层改进方案

### 4.1 工具层：MCP 协议化（替代原 P2 改进）

**核心动作**：把当前所有"Java 端硬编码的数据操作"和"Python 端的 RAG/Plan/Audit"都抽象为 MCP Server。

| MCP Server | 提供的 Tools | 实现语言 | 位置建议 |
|-----------|-------------|---------|---------|
| `student-data-server` | `get_student_profile`, `get_academic_history`, `get_mental_indicators`, `get_attendance` | Java (Spring AI MCP) | `backend/mcp-servers/student-data/` |
| `knowledge-rag-server` | `search_cases`, `search_policies`, `search_psychology`, `graph_query_similar_students` | Python | `ai-inference-service/mcp/rag/` |
| `intervention-server` | `create_intervention_task`, `assign_to_counselor`, `schedule_followup`, `record_outcome` | Java | `backend/mcp-servers/intervention/` |
| `report-server` | `generate_pdf`, `render_chart`, `export_excel` | Java | `backend/mcp-servers/report/`（吸收现有 ExportController） |
| `memory-server` | `recall_student_history`, `save_episode`, `summarize_long_term` | Python (Mem0/Letta wrapper) | `ai-inference-service/mcp/memory/` |

**收益**：
- 工具可被任何 MCP 兼容的客户端复用（包括运维人员用 Claude Desktop 直接连）
- 替代当前 Feign 强耦合：Python 服务挂掉只影响特定工具，不影响整个流水线
- 工具的输入 schema 自动校验，错误重试由 MCP 协议层处理

**工作量**：每个 Server 约 3-5 天，共约 3 周。优先级 ①student-data ②knowledge-rag ③intervention。

---

### 4.2 编排层：主 Agent + Skills + Subagents（替代原 P1 改进）

**放弃 LangGraph 路线**，采用更轻量的 Agent Loop + Skills/Subagent 模式。

**主 Agent Loop**（在 `agent-service` 新建 `core/AgentLoop.java`）：

```
while (not done):
    1. 收集 context（学生画像 + 历史 memory + 已有观察）
    2. 调用 LLM（带 tools schema 列表）
    3. 解析 tool_calls
    4. 执行 tools（并行）
    5. 把结果作为 observation 加入 context
    6. 判断是否 done
```

**Skills（领域知识包）**——放在 `backend/agent-service/skills/`：

- `risk-assessment.md` —— 学业风险判定准则、阈值、决策树
- `psychological-screening.md` —— 心理风险维度、警示信号
- `intervention-design.md` —— 干预方案设计原则、案例引用规范
- `compliance-audit.md` —— 教育合规检查清单、敏感词表、引用规则

Skill 是 Markdown 文件，由主 Agent 按需加载（参考 Anthropic Skills 设计）。修改 Skill 不需要重新部署代码。

**Subagents（专家视角）**——通过 MCP server 暴露：

- `class-teacher-advisor`（班主任视角 subagent）
- `psychologist-advisor`（心理咨询师视角 subagent）
- `academic-tutor-advisor`（学业导师视角 subagent）

主 Agent 在需要多视角时，**把 subagent 当作 tool 调用**，每个 subagent 独立的 context 窗口和 system prompt。这是 2025 年成熟的 "agent-as-tool" 模式，比 AutoGen 那种自由对话**更可控、更易调试**。

**收益**：
- 新增 Agent = 写一个 Markdown Skill + 注册一个 MCP tool，零 Java 代码改动
- 主 Agent 可以自主决定是否反思、是否多 subagent 投票，不需要硬编码状态机
- Skill 文件支持版本控制和 A/B 测试

**工作量**：核心 Loop 1 周；3 个 Skill + 3 个 Subagent 约 2 周。

---

### 4.3 模型层：Reasoning Model 路由

**问题**：当前 Qwen2.5-32B-Q4_K_M 一个模型扛所有任务，判断和生成混用，质量和延迟都不优。

**方案**：基于任务类型动态路由

| 任务 | 模型选择 | 理由 |
|-----|---------|-----|
| 风险等级判定 | Qwen3-235B-A22B-Thinking 或 Qwen3-32B-Thinking | 需要因果推理、权衡，thinking 模式效果质变 |
| 合规审核 | 同上（Thinking） | 需要细致检查 4 个维度并给出引用 |
| 方案文本生成 | Qwen3-32B-Instruct（快模式） | 偏创作，thinking 收益小 |
| 多轮对话 / 工具调用 | Qwen3-32B-Instruct | 低延迟优先 |
| 召回阶段重排 | BGE-reranker-v2-m3（替代 v1） | 多语言多粒度更强 |
| Embedding | BGE-M3（替代 BGE-large-zh-v1.5） | 支持稠密+稀疏+多向量混合检索 |

**实现位置**：`backend/agent-service/src/main/java/com/edu/agent/router/ModelRouter.java`

```java
public ChatClient route(TaskType type, Complexity complexity) {
    if (type == RISK_ASSESSMENT || type == COMPLIANCE_AUDIT) {
        return chatClientThinking; // 235B 或 32B Thinking
    }
    return chatClientFast; // 32B Instruct
}
```

宿主侧需要起两个 llama.cpp / vLLM 实例（thinking 模型 + 快模型），通过不同端口区分。

**工作量**：1 周（含模型部署和压测）。

---

### 4.4 记忆层：Mem0 集成（替代原报告的"自研 StudentMemory 表"）

**采用 Mem0 或 Letta** 作为长期记忆引擎，通过 MCP server 暴露给主 Agent。

**记忆分层**：

| 层级 | 内容 | 存储 | TTL |
|-----|------|-----|-----|
| Working Memory | 当前任务的工具调用历史 | 内存（context window） | 单次任务 |
| Episodic Memory | 该学生的历次预警事件 + 干预 + 效果 | Mem0 → Milvus | 永久（合规策略内） |
| Semantic Memory | 学生稳定特征摘要（学习风格、性格倾向） | Mem0 → PostgreSQL | 季度刷新 |
| Procedural Memory | 该学生历史上"什么干预方式有效" | Mem0 → MySQL | 永久 |

**新增能力**：
- "这个学生上次被预警是什么原因，干预效果怎样？" —— Mem0 自动检索 episodic
- "这个学生对哪类干预反应好？" —— Mem0 摘要 procedural
- 新预警自动与历史对比，触发**风险升级**逻辑

**工作量**：Mem0 集成 1 周；前端"学生时间线"页面 1 周。

---

### 4.5 RAG 层：Hybrid + GraphRAG（升级 ⭐⭐⭐⭐ 到 ⭐⭐⭐⭐⭐）

**保留**：BGE embedding + reranker 链路、Redis 缓存。

**新增**：

**A. Hybrid Retrieval**（向量 + BM25）
位置：`ai-inference-service/app/services/hybrid_retrieval.py`
- Milvus dense vector + Elasticsearch BM25 并行召回
- RRF (Reciprocal Rank Fusion) 融合
- 解决纯 dense 在专有名词（如"高数 II"、"心理量表 SCL-90"）上召回弱的问题

**B. GraphRAG**（教育场景天然图结构）
新增 Neo4j 服务（docker-compose），构建图谱：
```
(Student)-[HAS_RISK]->(RiskType)
(RiskType)-[ADDRESSED_BY]->(Intervention)
(Intervention)-[USED_FOR]->(Student)
(Intervention)-[HAS_OUTCOME]->(Outcome)
```
查询场景：
- "和该学生情况类似的历史案例" → Cypher 图查询比向量召回精准
- "哪类干预对'学业滑坡+人际孤立'组合最有效" → 图遍历 + 聚合

**C. RAG 在线评估**
集成 RAGAS，每次 RAG 调用采样 5% 走异步评估，指标（faithfulness, answer_relevancy, context_precision）入 Prometheus。

**工作量**：Hybrid 1 周；GraphRAG 2 周（含图谱建模和回填）；RAGAS 集成 3 天。

---

### 4.6 观测层：Cache + Langfuse + Braintrust

**A. Prompt Caching**（立即收益最大）
- Spring AI 1.1+ 原生支持，启用方法：把 system prompt + skill 内容 + 学生画像标记为 cacheable
- 预期：cache hit rate ≥70%，单次 LLM 调用成本和延迟降 60-80%
- 工作量：2-3 天

**B. Langfuse 全链路 Trace**
- Java SDK 接入 ChatClient 拦截器，Python 接入 LangChain callback
- 一次任务的完整轨迹（含每个 tool 调用、每个 LLM 请求的 token/延迟/cache 状态）可视化
- 前端"管理员追踪"页面直接嵌入 Langfuse iframe

**C. Braintrust Eval**
- 离线 eval 集：风险识别 200 例、方案生成 100 例、合规审核 100 例
- CI 流水线：prompt/模型变更必须 eval 通过才能合并
- 工作量：eval 集构建 2 周（最大投入但收益巨大）

---

### 4.7 合规层：教育合规框架（强化原 P3）

**三层防护**：

1. **System Prompt 层**：在所有 Agent 的 system prompt 中固定加入未成年人保护条款（"不得标签化"、"不得传播心理诊断结论给非授权方"等）
2. **Tool Guard 层**：在 MCP server 的 tool 实现里做权限和数据范围校验（如 `get_mental_indicators` 只允许 psychologist 角色调用）
3. **Audit Log 层**：新增 `audit_log` 表，记录每次数据访问的 who/when/what/why（why = 任务 ID）

**新增合规策略文档**：`docs/educare/COMPLIANCE.md`
覆盖：数据保留期、家长知情同意流程、毕业后数据销毁、数据导出权（GDPR Article 20 对标）

**工作量**：1.5 周（含文档 + 后端 + 前端权限改造）。

---

## 五、落地路线（3 阶段，约 9 周）

### Phase G —— 快速赢 + 基础设施（第 1-2 周）

**目标**：立即可见收益，搭建后续阶段地基。

- [ ] G-1 Prompt Caching（**llama.cpp slot cache 路线**） → cache hit rate 监控
  - G-1.1 ✅ 盘点 4 个 LLM 调用点（Java RiskAnalyze / Python risk·plan·audit）
  - G-1.2 ✅ 锁定文档决策
  - G-1.3 抽离 prompts 到独立资源文件（保证字节稳定）
  - G-1.4 透传 `cache_prompt:true` 给 llama.cpp + 显式发送以兼容旧版
  - G-1.5 拦截 LLM 响应，抓 `timings.cached_n / prompt_n`，本地累加 metrics
  - G-1.6 文档化 + 验收脚本（同一 trigger 连发两次，对比 cached_n）
- [ ] G-2 后端字段级权限（原 P8，2 天） → 关闭前端脱敏漏洞
- [ ] G-3 启用 E-1 定时扫描 + Prometheus 监控（2 天，G-1.5 metrics 在此接 Micrometer）
- [ ] G-4 增量知识导入 API（原 P7，2 天）
- [ ] G-5 Langfuse 接入（3 天） → 立刻有 trace 看
- [ ] G-6 Braintrust eval 集启动构建（持续，本阶段先建 50 例）

**验收**：Langfuse 上看到完整 trace；`/agent/api/v1/diagnostics/llm-metrics` 端点显示 cache hit rate ≥50%（同学生重复触发场景下 ≥80%）。

---

### Phase H —— MCP 化 + Agent Loop 重构（第 3-6 周）

**目标**：把硬编码流水线换成 MCP + Agent Loop，是整个改进的核心。

- [ ] H-1 实现 5 个 MCP Server（student-data / knowledge-rag / intervention / report / memory），3 周
- [ ] H-2 实现主 Agent Loop（替换 `AgentTaskServiceImpl.doExecute`），1 周
- [ ] H-3 编写 4 个 Skill markdown 文件，1 周（并行）
- [ ] H-4 Model Router 上线（thinking + fast 双模型），1 周
- [ ] H-5 Mem0 集成 + memory-server，1 周
- [ ] H-6 Braintrust eval gate 接入 CI，1 周

**验收**：旧 4 阶段代码删除；新增一个 Agent 仅需写 Skill + Tool，零 Java 改动；eval 集回归通过率 ≥ baseline。

---

### Phase I —— 高级能力 + 业务闭环（第 7-9 周）

**目标**：补齐 GraphRAG、多视角 subagent、合规、反馈闭环。

- [ ] I-1 Hybrid Retrieval（向量 + BM25）上线，1 周
- [ ] I-2 GraphRAG（Neo4j + Cypher 工具），2 周
- [ ] I-3 3 个 Subagent（班主任 / 心理咨询师 / 学业导师），1.5 周
- [ ] I-4 合规框架（policy 文档 + audit_log + tool guard），1.5 周
- [ ] I-5 干预反馈闭环（前端评分 + 反馈表 + 月度报表），1 周
- [ ] I-6 学生时间线页面（基于 Mem0 数据），1 周

**验收**：完整跑通"学生 A 第二次预警 → 自动对比上次 → 多视角 subagent 投票 → 生成升级方案 → 教师执行 → 1 个月后反馈 → 系统学习"的闭环。

---

## 六、风险与权衡

| 风险 | 缓解 |
|-----|-----|
| MCP 协议在 Java/Spring 生态成熟度仍在追赶 Python | 优先用 Spring AI 1.1+ MCP 模块；若不够用，先用 stdio MCP 桥接 |
| Thinking 模型推理延迟高（5-30s） | 仅在判断密集任务用，前端 SSE 实时显示思考过程 |
| Neo4j 引入运维负担 | 试点阶段用 Docker 单实例；后期评估是否换 Milvus 的 attu-graph 替代 |
| Mem0 是新项目稳定性未知 | 接口层抽象，预留 Letta 切换路径 |
| Skill/Subagent 模式调试比硬编码状态机难 | 重投入 Langfuse trace + Braintrust eval；每个 Subagent 都有独立 eval 集 |
| Phase H 大重构期间业务不可用 | 采用双轨：旧流水线保留作为 fallback，feature flag 切流（如 10% → 50% → 100%） |

---

## 七、与原报告（SubAgent 诊断版）的对照

| 原报告改进 | 本方案 | 差异 |
|----------|-------|-----|
| 改进 4：LangGraph 编排 | Phase H-2：主 Agent Loop + Skills | **放弃 LangGraph**，更轻量 |
| 改进 5：Tool Calling 框架 | Phase H-1：MCP Servers | **不自研接口**，用开放协议 |
| 改进 6：Multi-Agent 协作 | Phase I-3：Subagent as Tool | 不用 AutoGen 对话式，用 agent-as-tool |
| 改进 7：Langfuse 观测 | Phase G-5 + Phase H-6 | **加上 Braintrust eval gate** |
| 改进 8：干预反馈 | Phase I-5 | 保留 |
| 改进 9：未成年人合规 | Phase I-4 | 保留并强化 |
| —— | Phase G-1 Prompt Caching | **新增**，最高 ROI |
| —— | Phase H-4 Model Router | **新增**，质量与成本双优化 |
| —— | Phase I-1/I-2 Hybrid + GraphRAG | **新增**，RAG 现代化 |

---

## 八、需要拍板的关键问题

在开工前，建议先就以下几点对齐：

1. **本地推理算力**：当前宿主 LLM（8091 端口）的硬件规格？能否同时跑 Qwen3-32B-Thinking + Qwen3-32B-Instruct 两个实例？若不能，是否考虑混合云（敏感场景本地、非敏感云端）？
2. **MCP 生态选型**：Spring AI MCP（Java 原生）vs FastMCP（Python）—— 倾向哪一个作为主力？
3. **GraphRAG 是否必要**：Neo4j 引入是否能接受？还是 Phase I 阶段先评估 ROI 再决定？
4. **Phase 时间预期**：9 周是否符合预期？是否需要砍掉 Phase I 先上 G+H？

---

## 附录 A：术语表

- **MCP (Model Context Protocol)**：Anthropic 主导的开放协议，用于 LLM 客户端与工具/数据源的标准化集成。2025 年成为事实标准。
- **Skills**：Markdown 形式的领域知识包，由主 Agent 按需加载，等价于"可热更新的 system prompt 片段"。
- **Subagent / Agent-as-Tool**：把专家 Agent 包装成 tool 暴露给主 Agent；每个 subagent 拥有独立 context，比对话式 Multi-Agent 可控。
- **Reasoning Model / Thinking Mode**：具备显式思考链的模型（o3、Qwen3-Thinking、Claude extended thinking），擅长判断密集任务。
- **Prompt Caching**：将稳定的 system prompt 段在服务端缓存，重复调用时降本 90%、降延迟 70%。
- **GraphRAG**：以知识图谱代替/补充向量库的 RAG 形态（Microsoft 2024 提出），适合结构化领域。
- **RAGAS / Braintrust / Langfuse**：分别是 RAG 评测、LLM 离线评测、LLM 在线 trace 的代表工具。

---

## 附录 B：版本与维护

| 字段 | 值 |
|-----|----|
| 文档版本 | v1.0 |
| 创建日期 | 2026-05-12 |
| 下次评审 | Phase G 结束后（约 2 周内） |
| 关联文档 | `EXECUTION_PLAN.md`（执行进度 + 原子任务）、`architecture.md`（现状）、`COMPLIANCE.md`（待创建） |

**更新约定**：Phase G/H/I 每阶段结束时，本文档同步更新进度勾选与实际工作量复盘。重大决策变更（如放弃 Mem0 改用 Letta）单独在文档底部追加"变更记录"。
