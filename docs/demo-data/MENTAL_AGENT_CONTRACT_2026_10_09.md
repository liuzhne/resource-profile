# 心理测评与 Agent 完全合成演示数据契约（2026-10-09）

本文件供离线生成器使用。只读核对仓库代码及 SQL，没有访问生产、凭据、数据库、外部模型或真实日志，没有执行服务验证。学生基准 1200、教师基准 80；账号数量与角色绑定由根任务另行确定。文中比例、分值分布、时间安排和干预结果均为**演示假设**，不代表学校统计、医学患病率或干预有效性。

先读了 [EXECUTION_PLAN.md](../educare/EXECUTION_PLAN.md) §1：当前保留 AgentLoop、双 MCP、dense RAG、反馈闭环；R-5.2/R-6 未验收。导入合成样例不能替代真实模型/MCP/RAG/可观测验收，也不能改变其完成状态。本文件记录现有契约和生成边界，不提出或实施源码修复。

## 1. 身份与时间边界

- 心理与 Agent 表中的 `student_id` 是 `student_info.id`，不是学号字符串，也不是 `sys_user.id`。
- 提交 API 的 `userId` 是 `sys_user.id`；服务通过 `student_info.user_id` 解析唯一未删除学生。一个账号有多个活跃学生档案会返回 409，不能生成共享学生账号冒充本人作答。
- `creator_id`、`warning_handled_by`、`counselor_id`、导出的 `user_id` 均应关联用户 ID，不能用 `teacher_info.id` 代替。角色映射未知时不要猜现存管理员/咨询师 ID。
- 心理提交、开放窗口、今日完成和预警处置明确使用 `Asia/Shanghai`；该时区与本次环境 `Asia/Kuala_Lumpur` 当前同为 UTC+08，但生成清单仍应明确业务时区。SQL `DATETIME` 不带时区；API 时间用 `YYYY-MM-DDTHH:mm:ss`。
- Agent 和反馈源码的时间使用服务默认 `LocalDateTime.now()`/`YearMonth.now()`；离线生成器统一配置 UTC+08，并在清单记载该假设。
- 本批次所有业务事件严格早于`as_of`日期的UTC+08凌晨，2026-10-09构建时最多发生于2026-10-08；开放问卷的`end_time`可在未来，历史答卷的提交时间必须在该问卷日期闭区间内。问卷当前状态仍以2026-10-09判断。
- 问卷、题目先创建，答卷与测评同一提交时刻，预警处置不早于测评，分析样例不早于其引用的画像/测评，反馈不早于形成方案。真实 PDF 导出的时间由实际渲染产生。

## 2. 心理相关表与约束

DDL 以 [01_init.sql](../../sql/init/01_init.sql)、[02_questionnaire_extension.sql](../../sql/init/02_questionnaire_extension.sql) 和 [07_mental_contract.sql](../../sql/init/07_mental_contract.sql) 为组合基线；存量对应 [20261007_02_mental_contract.sql](../../sql/migrations/20261007_02_mental_contract.sql)。不要重放整个初始化脚本到生产。

| 表 | 必要字段与关系 | 生成注意事项 |
|---|---|---|
| `mental_questionnaire` | `id` 自增；`title`≤200、`type`≤50、`description`≤500；`questions`；日期；`status`；`level_rules` JSON；`creator_id`；`deleted`；创建/更新时间 | 当前状态 0=草稿、1=开放、2=结束；`questions`=未删除题目真实数量。`template_content`是DDL可空列，当前Questionnaire entity不映射；生成行显式写NULL以匹配完整列契约，不臆造上传模板。 |
| `mental_question` | `questionnaire_id`；`sort_order`；`content`≤1000；`question_type`；`options`/`scoring_rules`/`scale_labels` JSON；`scale_min/max`；`required`；`deleted` | 排序为正整数，建议连续且唯一。`required`=0/1。题型严格为 `single_choice`/`multiple_choice`/`text`/`scale`。 |
| `mental_response` | `student_id`；`questionnaire_id`；`answers` JSON；`questionnaire_snapshot` JSON；`score`；`status`；`create_time` | `(student_id,questionnaire_id)` 唯一；canonical 学生提交写 `status=1`。没有 `answer_snapshot` 表或列。 |
| `mental_assessment` | `student_id`；`questionnaire_id`；`response_id`；`score`；`level`；`result`≤500；`suggestion`≤500；`status`；`create_time`；`warning_*` | 新合成答卷都应有 1:1 测评；`response_id` 有唯一索引，学生/问卷必须与答卷一致。该表没有 `deleted` 列。canonical 写 `status=1`、初始 `warning_status=OPEN`。 |

大部分关系只有索引和代码约束，没有 SQL 外键；生成器必须自行校验引用存在。历史导入允许 `assessment.response_id=NULL` 和缺少问卷快照，但这不是新样例的默认形状；不能替真实历史缺失记录发明题目快照。

### 2.1 答案 JSON：数组与零起点索引

权威路径是 [MentalAssessmentServiceImpl](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java) → [MentalAnswerContract](../../backend/mental-service/src/main/java/com/edu/mental/service/MentalAnswerContract.java)。

API `POST /mental/student/assessments`：

```json
{
  "userId": 900001,
  "questionnaireId": 910001,
  "answers": [
    {"questionId": 920001, "optionIndices": [0], "text": "", "scaleValue": null},
    {"questionId": 920002, "optionIndices": [0, 2], "text": "", "scaleValue": null},
    {"questionId": 920003, "optionIndices": [], "text": "", "scaleValue": 7},
    {"questionId": 920004, "optionIndices": [], "text": "[演示合成] 希望了解学习资源。", "scaleValue": null}
  ]
}
```

这是四种题型的形状示例，题目 ID 必须替换为同一真实合成问卷中的单选、多选、scale、text 题目。数据库 `answers` 存 **`answers` 数组本身**，不存整个 API 请求，也不存 `{"题目ID":"A"}` 对象。四字段可显式保留 `null`/空数组/空字符串，避免类型漂移。

- `optionIndices` 是选项数组的零起点位置，不是选项 `value`、选项分数或数据库 ID。
- 单选最多一个索引；必答单选恰好一个。多选索引不重复且不越界。
- 选择题不能同时带非空文本或 `scaleValue`；scale 只能带整数且在闭区间内；text 不能带选项或 scale。
- 答案题目不得重复、不得来自其他问卷；必答题必须满足题型作答条件。可选题可省略。
- 文本≤5000，题目数 1–500，答卷长度不大于问卷题数。

仓库仍有 [QuestionnaireResponseServiceImpl](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireResponseServiceImpl.java) 接受按题目 ID 键控的旧对象，并写 `status=0`；当前学生控制器调用的是上述 canonical 服务。生成器不要模仿旧路径。`01_init.sql` 与扩展 DDL 的状态注释也有历史差异，当前提交实现是判断依据。

### 2.2 题目计分与等级

选择题数据库 JSON 示例（在 API Java String 字段中需要编码为字符串）：

```json
{
  "options": [
    {"label": "有较多可用支持", "value": "A", "score": 10},
    {"label": "需要更多支持", "value": "B", "score": 0}
  ],
  "scoring_rules": {"A": 10, "B": 0}
}
```

这段用于说明数据库 JSON 值，不能原样作为 Java entity 的 `options`/`scoringRules` 对象字段发送。API 采用 `options="[{...}]"`、`scoringRules="{...}"`。

- 选项 2–100 个，每个非空 `label`≤500；`value` 如省略默认是索引的字符串，实际唯一性按其字符串值判断。
- 每个选项必须有自身 `score` 或 `scoring_rules[value]`。两处同时存在必须一致。分值须为有界整数（绝对值≤100000）。
- 单选计一个选项；多选把所有选中分值相加；text 计 0；scale 无映射时计数值本身，有映射时使用 `scoring_rules[数值字符串]`。
- scale 边界是 -1000..1000 内的整数闭区间；非空映射必须覆盖每个可能数值。`scale_labels` 是 `{"min":"较少","max":"较多"}` 等短文本对象。
- 不自动归一化到 100、不自动反向计分、不自动计算 SAS/SDS 标准分，也不产生因子分。需要反向项时显式构造映射。
- `level_rules` 必须是数组，`level` 仅支持 `正常/轻度/中度/重度/高危`；名称和 `minScore` 都不得重复；底部阈值需覆盖最低可能总分。
- 评级为所有 `minScore <= totalScore` 中阈值最大的规则。程序不要求按数组顺序，生成器仍建议降序输出。

现有默认规则（**应用的演示分类，非医学阈值**）：

```json
[
  {"level":"正常","minScore":85,"suggestion":"[演示合成] 继续保持日常学习与支持活动；本分类不用于诊断。"},
  {"level":"轻度","minScore":70,"suggestion":"[演示合成] 可了解学习支持与咨询资源；本分类不用于诊断。"},
  {"level":"中度","minScore":50,"suggestion":"[演示合成] 由授权专业人员复核支持需要；本分类不用于诊断。"},
  {"level":"重度","minScore":30,"suggestion":"[演示合成] 展示专业复核工作流；不代表真实个人状况。"},
  {"level":"高危","minScore":0,"suggestion":"[演示合成] 展示优先人工复核工作流；不代表真实个人状况。"}
]
```

因此 0–29 高危、30–49 重度、50–69 中度、70–84 轻度、85 以上正常，**高分是较好方向**。不要把旧 seed 的手写分数/等级照搬为计算依据。不同问卷可有不同规则，不能只凭分数跨问卷比较。

本批次新建名含 `[演示合成]` 的**自编日常支持问卷**：20个0–5整数scale题，满分100、较高=支持更充分，再使用上述规则。题干只涉及日常学习安排、资源可及性、同伴支持等虚构情景；不要把自编题冠以 SAS/SDS、PHQ 等标准量表名称。当前需求不需要医学事实或患病率，本文件没有引用医学阈值。

### 2.3 正确问卷快照（不是“答案快照”）

`mental_response.questionnaire_snapshot` 保存 `QuestionnaireFullDto`：顶层 `questionnaire` 对象、`questions` 数组、`levelRules` 数组。数据库列本身是 JSON 对象，**但内层 `Question.options`/`scaleLabels` 是 JSON 字符串**。

完整的单题快照形状示例：

```json
{
  "questionnaire": {
    "id": 910001,
    "title": "[演示合成] 快照形状示例",
    "type": "演示自编",
    "description": "完全合成，非诊断工具",
    "questions": 1,
    "startTime": "2026-10-01",
    "endTime": "2026-10-31",
    "status": 1,
    "levelRules": null,
    "creatorId": null,
    "deleted": 0,
    "createTime": "2026-09-25T09:00:00",
    "updateTime": "2026-09-25T09:00:00"
  },
  "questions": [
    {
      "id": 920001,
      "questionnaireId": 910001,
      "sortOrder": 1,
      "content": "[演示合成] 当前可获得的学习支持是否充足？",
      "questionType": "single_choice",
      "options": "[{\"label\":\"较充足\"},{\"label\":\"希望增加\"}]",
      "scoringRules": null,
      "scaleMin": null,
      "scaleMax": null,
      "scaleLabels": null,
      "required": 1,
      "deleted": 0,
      "createTime": "2026-09-25T09:00:00",
      "updateTime": "2026-09-25T09:00:00"
    }
  ],
  "levelRules": []
}
```

该示例只说明快照结构；实际生成时必须包含该问卷全部提交时题目，`questions` 元数据与数组长度一致。不得与上面的四题答案例直接拼接。

快照由 `respondentSnapshot` 做复制与剥离：`questionnaire.levelRules=NULL`、`creatorId=NULL`、顶层 `levelRules=[]`、每题 `scoringRules=NULL`、选项仅 `{label}`，连 `value` 也剥离；保留 scale 边界和 labels。按 ID/选项顺序生成，提交后不得随当前题库变化重写。

学生详情 `GET /mental/student/assessments/{assessmentId}?userId=...` 返回 `assessment`、`questionnaire`、`questions`、`answersJson`（数组的 **JSON 字符串**）、`historySnapshotAvailable=true`。没有快照时返回空题目和历史提示，不能伪造可回显的历史题干。来源见 [student-mental/result.vue](../../frontend/src/views/student-mental/result.vue)。

### 2.4 预警与统计口径

没有独立 `mental_warning` 表。预警是 `mental_assessment.level IN ('中度','重度','高危')` 的视图，处置写回：

| 字段 | 值与时序 |
|---|---|
| `warning_status` | `OPEN/IN_PROGRESS/RESOLVED`。新测评默认 OPEN，包括非预警等级；非预警等级不会进入预警列表。 |
| `warning_note` | ≤1000；非 OPEN 必须有非空说明。合成样例必须明确标“模拟处置记录”，不能冒充咨询记录。 |
| `warning_handled_by` | 已知且正确角色的合成 `sys_user.id`。未处置为 NULL。 |
| `warning_handled_at` | UTC+08；不早于该测评；未处置为 NULL。 |

处置 API 为 `PATCH /mental/warnings/{assessmentId}`，body `{"status":"IN_PROGRESS","note":"..."}`。当前代码允许这些状态间切换，不强制单向；生成器建议按 OPEN→IN_PROGRESS→RESOLVED 合理安排，只有最终值，没有独立状态历史表。

- 心理概览/学院/性别/年级使用**最近 30 天测评记录**，不是学生人数、不是每人最新一次。良好=正常+轻度；关注=中度；干预=重度+高危。
- 概览 `warningList` 最近30天、未 RESOLVED、最多10条；管理预警分页含全部历史（可按 status 过滤）。
- `todayCompleted` 数当日 `mental_response` 提交记录；趋势按最近6个月的测评记录分组。
- 重点人群按等级使用 `COUNT(DISTINCT student_id)`；一个学生不同历史等级可能进入多个组。
- MCP 取最近一次测评；映射 正常→none、轻度→low、中度→medium、重度/高危→high。没有数据 `found=false`；没有真实因子数据 `factorScores={}`，不得按总分拆造因子。

## 3. Agent 表：当前实际使用面

DDL 见 [03_agent_init.sql](../../sql/init/03_agent_init.sql)、[05_intervention_feedback.sql](../../sql/init/05_intervention_feedback.sql)。

| 表 | 当前读写与生成建议 |
|---|---|
| `agent_task` | 当前任务、报告展示和 PDF 的主数据源。`student_id`、状态；`risk_level` enum；四阶段 JSON TEXT 字段；创建/更新/完成时间；`deleted`。当前产品无独立离线来源状态，本批次生成0条数据库记录；AI产物需授权后真实运行。 |
| `intervention_feedback` | 当前有效闭环：`task_id`、冗余 `student_id`、`counselor_id`、1–5 评分、outcome、comment≤1000、时间、deleted。本批次生成0条数据库记录，不构造实际干预历史。 |
| `agent_export_task` | 当前真实异步 PDF 作业。建议初始不生成，通过实际导出形成状态与文件。不得仅填 DONE+虚构绝对路径。 |
| `intervention_report` | DDL 有独立表，`task_id` 唯一、学生、title≤255/summary≤512/risk_level、动作/长期/资源 JSON、谈话文字、pdf_path、pushed、时间/deleted。当前 Java/前端/Python未查到读写引用，展示/PDF不读它；默认生成0条。 |
| `knowledge_citation` | DDL 为 task/source_db/chunk_id/title/excerpt/relevance_score/rerank_score/时间/deleted；当前未查到读写引用，展示直接读任务的检索快照。默认生成0条，不能凭空编造 Milvus chunk_id/相关度。 |
| `audit_log` | DDL 记录 task/actor_role/actor_id/action/target_type/target_id/detail/audit_passed/created_at。当前未查到业务读写引用；默认生成0条并保护现有记录，不生成“真实执行/查看/导出/通过”审计。 |

上述“未查到引用”只表示本次静态检索结果，不能据此清空或删除表。`intervention_report`、`knowledge_citation`、`audit_log` 没有必要为了“表表有数据”补造历史能力。

### 3.1 enum、JSON 与真实生命周期

`agent_task.status`：`PENDING/RISK_ANALYZING/KNOWLEDGE_RETRIEVING/PLAN_GENERATING/COMPLIANCE_CHECKING/COMPLETED/REJECTED/FAILED`。数据库/API `risk_level` 大写 `NONE/LOW/MEDIUM/HIGH`，风险 JSON 的 `risk_level` 小写。

真实路径：PENDING→RISK_ANALYZING→KNOWLEDGE_RETRIEVING→PLAN_GENERATING→COMPLIANCE_CHECKING→COMPLETED 或 REJECTED；NONE/LOW 可在 RISK_ANALYZING→COMPLETED 短路；异常 FAILED。AgentLoop 默认在风险阶段一次给出 risk+plan；检索来自实际 MCP 调用，后续检索/生成状态只保持业务状态语义；中/高风险的合规审核另调 Python/模型。

- 主路径写 `risk_analysis_result`、`risk_level`、`intervention_plan`、`retrieved_knowledge`，随后必要时写 `compliance_audit`。
- 当前源码未给 `risk_score`/`risk_type` 标量赋值；真实新任务可为 NULL。合成生成器可以保持 NULL；若填充便于展示，必须与 JSON 相同，并在清单声明为派生值。
- `completed_at` 是 COMPLETED 的完成时间；当前 REJECTED/FAILED 路径不明确写该列，推荐按当前实际写法保持 NULL，使用 `updated_at` 作为最后状态时间。所有时间至少满足 created≤updated，COMPLETED 时 created≤completed≤updated（允许等值）。
- 不离线插入卡住的 PENDING/运行中任务假装在执行；如果用于状态展示，需单独样例清单及明确“静态场景”标识，不能期待 SSE/执行器接管。
- 离线导入不会产生真实 Redis 幂等键/锁、SSE 事件、Micrometer 计数或 Langfuse trace。不要为了匹配 UI 再调用 trigger API；它会实际请求模型。

### 3.2 严格 final JSON 与储存字段

权威为 [agent-final-answer.schema.json](../../backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json) 和 [AgentLoopOutputSchema](../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoopOutputSchema.java)。每层对象字段集合精确匹配，不能额外塞 `synthetic=true` 等 provenance。字符串必须非空，risk_score 必须是真整数0–100，不能 `50.0`/`"50"`。数组可为空。

```json
{
  "risk_analysis": {
    "risk_level": "low",
    "risk_score": 24,
    "primary_risk_type": "学业滑坡",
    "root_cause_analysis": "[演示合成] 该例依据离线生成的成绩与支持数据构造，未经模型推理，不代表真实学生结论。",
    "key_indicators": ["[演示合成] 本学期平均分较上学期下降4分"],
    "recommended_intervention_types": ["学业辅导"],
    "urgency_reason": "[演示合成] 展示常规支持流程，不构成医学诊断。"
  },
  "intervention_plan": {
    "report_title": "[演示合成] 离线学习支持方案样例",
    "summary": "人工构造的展示方案，未执行LLM、MCP或RAG；建议与后续结果均为演示情景。",
    "immediate_actions": [{"action":"[演示合成] 了解课程辅导资源","owner":"学业导师","deadline":"1周内"}],
    "long_term_plan": [{"goal":"[演示合成] 改善学习安排","milestones":["模拟制定计划","模拟回访"],"horizon":"1个月"}],
    "talk_outline": "[演示合成] 询问学习资源需要，尊重意愿与隐私。",
    "resources": ["[演示合成] 校内学习支持资源展示占位，无真实联系方式"],
    "references": []
  }
}
```

这是 offline fixture 内容，**不得作为已执行模型的 final_answer/trace 发布**。数据库分别存 `risk_analysis` 对象的 JSON 文本和 `intervention_plan` 对象的 JSON 文本，不把整个 final 对象塞进两个列。

关键类型差异：严格模型方案 `resources` 是**字符串数组**，`immediate_actions` item 仅 action/owner/deadline，`long_term_plan` item 仅 goal/milestones/horizon。前端 API 的 [PlanViewNormalizer](../../backend/agent-service/src/main/java/com/edu/agent/service/PlanViewNormalizer.java) 才把资源映射成 `{type,name,link_or_contact}` 并补动作 references、长期 metrics。不能把响应归一化 DTO 当模型 schema。`planViewStatus` 是响应专用字段，不是数据库列。

建议 primary_risk_type 用现有白名单 `学业滑坡/心理健康预警/出勤异常/经济困难影响/社交孤立/综合风险`，recommended_intervention_types 用 `学业辅导/心理疏导/经济援助/家校沟通`；不得把经济困难、家庭标签或性别自动等同于心理问题。不得把健康筛查分类写成确诊、真实危机事实、药物或治疗方案。

### 3.3 检索快照：不可伪装工具执行

AgentLoop 的 `retrieved_knowledge` 已由 JSON Array 旧注释演变为 [RagSnapshotCollector](../../backend/agent-service/src/main/java/com/edu/agent/core/RagSnapshotCollector.java) 的对象快照。离线样例的默认形状：

```json
{
  "schema_version": 1,
  "executed": false,
  "searches": [],
  "chunks": [],
  "reranked": false,
  "fallback": false,
  "truncated": false
}
```

`executed=false` 保证现有报告页显示“未执行”。不要离线填 `executed=true`、`searches[].status=SUCCEEDED`、非空 chunks、top_k/count 或伪造相关度/rerank分数来制造真实 RAG 证据。`fallback=true` 也有真实降级语义，不应因“离线”自动置 true。

真实快照结构用于未来实际验证：search 包含 `tool`（search_cases/search_policies/search_psychology）、`status`、可选 query/requested_top_k/collection/top_k/reranked/fallback/count、returned_count/chunk_ids；chunk 包含 chunk_id/source_db/title/content/score/rerank_score。失败用 status=FAILED/error_type。最多32次search和100片段；结果需来自实际工具回包而不是模型文本中的引用。

若要展示文献/政策，必须使用已验证真实来源并在方案 `references` 明示“人工提供，未经过RAG检索”；此任务不需要知识引用，默认空数组。没有实际检索不得填 `knowledge_citation` 或导入 Milvus。

### 3.4 合规审核、终态与离线展示的取舍

真实 `compliance_audit` 形状：

```json
{
  "audit_passed": false,
  "audit_items": [{"dimension":"system","passed":false,"issue":"[演示合成] 离线样例未执行合规审核，需人工复核；不是已发生的系统故障。"}],
  "redacted_suggestions": ["[演示合成] 仅用于展示，不用于真实干预决策。"],
  "manual_review_required": true
}
```

正常真实审核维度为 privacy/necessity/ethics/third_party；system 用于异常人工兜底。这段仅可作为明确标注的**模拟待复核场景**，不能说模型已审核失败。默认不生成 `audit_passed=true`，不生成“已经合规验收”的记录。

本批次**不导入任何离线Agent任务或反馈**。复核确认当前产品没有独立的离线来源字段/状态，列表上的COMPLETED及导出/反馈功能会把静态样例误读为真实执行；在文字中标注来源不足以改变这些产品语义。因此数据库`agent_task`与`intervention_feedback`生成0条，不能补造COMPLETED、REJECTED或通过/失败审核历史。

生成器已删除离线Agent/反馈记录生成逻辑，`summary`不包含offline_examples、姓名、答卷、报告或反馈行，只提供计数、模拟比例、时间窗口与边界说明，供可公开脱敏的generation-report使用。上述JSON仅留在本契约文档作为无个人信息的结构示例，不会推进生产ID游标、写入表数据或上报Langfuse。

AI任务、真实检索证据、合规审核与反馈必须在单独授权后通过真实业务流程产生。此次仅生成合成心理测评，不调用外部模型。上述JSON只说明未来执行/离线契约检查的结构，不能作为已发生流程的证明。

### 3.5 反馈与导出

反馈 API `POST /agent/api/v1/intervention/feedback`：

```json
{"taskId":930001,"score":4,"outcome":"improved","comment":"[演示合成] 模拟4周后的学习支持跟进，无真实干预效果证据。"}
```

- task存在、未删除，status须 COMPLETED/REJECTED，方案有至少一个非空 action或goal/milestone；只有空方案、FAILED/运行中不能反馈。
- score 1–5；outcome 可空或 `improved/unchanged/worsened/escalated`；comment≤1000。
- student_id 从 task复制；counselor_id由登录用户/内部身份得到，不在请求体里接受任意 actor。离线写入只使用已知合成账号；尚无合成职工账号时建议暂不生成反馈，不能借用现有真实账号。
- 没有 task级唯一约束，允许多次跟进。离线契约样例仅在方案模板形成至少14天后安排模拟反馈；该等待期是演示假设，不是代码硬门。该样例不得写入本批次生产反馈表。
- 月报按 feedback.created_at的年月（`yyyy-MM`）查明细，CSV字段为 id/task_id/student_id/counselor_id/score/outcome/comment/created_at。避免生成以 `= + - @` 开头的自由文本影响CSV表格程序。

导出 `POST /agent/api/v1/task/{id}/export`→ `{jobId}`，GET状态，GET下载；PENDING→PROCESSING→DONE/FAILED。DONE须有真实可读PDF、绝对file_path、准确file_size、finished_at。默认让演示后的实际操作创建 export记录，不预造作业、文件路径或已经下载/推送记录。实际导出无需 LLM，但需登录 admin/psychologist、中文字体与存储目录。此阅读任务没有执行PDF验证。

## 4. 可离线生成的规模与分布建议

以下都是可调整的演示覆盖策略，不是统计结论：

- 当前波次为2026-09-28至2026-10-23，2026-10-09构建时业务事件截于2026-10-08，只在已入学的1200名合成学生中采样75%（900人），其余300人未作答。以固定seed分配，未作答与院系/性别/家庭标签不形成机械偏见。
- 该波次900份答卷使用正常80%（720）、轻度15%（135）、中度4%（36）、重度1%（9），高危0；比例分母是答卷记录。这些均是模拟假设。答案依目标分值构造后独立重算并验证总分/等级，不能任意拼接。
- 两个历史波次为2025-10与2026-04，分别在已入学对象中采样90%，使用独立questionnaire_id；每波使用相同演示分类分布并按最大余数法分配整数人数，保留个体轻微波动。
- 最近30天每人至多1份当前波次记录，同时在manifest提供records与distinct人数，避免混淆人次与人数。
- 预警可覆盖OPEN/IN_PROGRESS/RESOLVED；非OPEN只给正确合成专业用户处置、写模拟说明。不能为了显示“已解决”把学生健康结论改成正常。
- 本批次Agent/反馈数据库记录均为0；少量脱离数据库的方案模板仅检查shape。真实任务风险与心理level是不同领域，不得机械等同。
- 保留“无测评”“未作答”“无Agent分析”“待人工复核”“无反馈”场景，缺失不等于零分或正常。
- 每个波次比例、预警处置比例、任务比例、反馈结果分布在manifest列为 demo_assumptions；禁止称为患病率、真实筛查率或真实干预改善率。

## 5. 角色可见性与保护对象

现有权限必须保持开启。STAFF_VIEW为admin/teacher/counselor/academic_advisor/psychologist；MENTAL_WRITE与EXTREME_VIEW仅admin/psychologist。学生本人不能通过合成数据导入获得专业角色。标准账号规模和绑定方案需独立确定。

- 学生作答模板不暴露计分/等级阈值；学生结果的原始score/result/suggestion等不因“是本人”自动可见。
- `MentalAssessment`原始score/result/suggestion/warningNote为EXTREME，level为MEDIUM。完成情况给非专业教职工的score=NULL，不是0。
- Agent详细风险JSON/检索query由 [AgentTaskViewPolicy](../../backend/agent-service/src/main/java/com/edu/agent/security/AgentTaskViewPolicy.java) 按专业权限投影。不要为了演示改权或将不显示的字段补成零值。
- PDF创建/状态/下载仅已登录admin/psychologist，内部凭证不能代替专业角色。

**生成/导入工具应采用表与ID白名单，保护所有非本批次数据**：

| 对象 | 建议 |
|---|---|
| `sys_login_log` | 不插入/更新/删除；只保留实际认证事件。合成账号真实登录后产生的记录属于真实操作日志，不预填成功率/登录历史。 |
| `audit_log`、既有`agent_export_task`、既有PDF | 不补造、不清空、不改写 actor/action/通过标志。演示导入操作证据单独写工具的本地导入结果，而非“学生业务审计”。 |
| Langfuse trace/generation/tool/score、Prometheus、应用日志 | 不制造、覆盖、回填模型名称/token/cache/latency/tool成功等；不将人工样例当真实链路评测证据。 |
| `sys_config`、角色/权限字典、Nacos、环境变量、密钥、JWT/session/Redis | 不生成或修改；不关闭认证、字段权限、MCP token、审计、限制或调度配置。角色绑定只在root明确限定的新合成用户范围内做。 |
| migration日志、schema、知识库/Milvus、真实questionnaire与rules | 不重置、不迁移、不更改存量问卷；为演示创建新namespace/新ID，不替换已有题库。 |
| 现有task、response、assessment、feedback、用户档案 | 不按日期/“seed外观”猜测归属来覆盖；精确批次ID清单是唯一可回滚范围。 |

批次manifest至少记录 batch_id、schema_version、固定seed、as_of、timezone、完全合成声明、各表精确ID、用户映射、各字段派生算法、score规则快照/hash、demo_assumptions、文件hash、导入前后计数及执行结果。没有来源列的表，用既有title/description/result/suggestion/comment/root_cause_analysis/summary字段显著标 `[演示合成]`；不得修改strict final schema增加来源字段。

## 6. 生成器的离线验收与生产待验证项

[mental.py](../../scripts/demo_data/mental.py)已实现三波生成与独立`validate_mental`。2026-10-09离线用1200名、2023–2026年各300名的合成roster验证：3卷、60题、2520答卷与2520测评，当前900份分类720/135/36/9；固定seed结果一致；分数/快照/未来事件/混入Agent或反馈记录会失败；空roster可验证。生产导入及以下需真实身份/数据库的检查仍待根任务执行，不能把该离线检查当作生产通过。

1. 每个关联存在且属于本批次；user→student唯一；专业处置actor角色正确；ID不碰撞、不用学号替主键。
2. 严格JSON解码、重复key拒绝；answers必须数组；snapshot顶层对象与内层String字段类型正确；题目ID/问卷ID/选项顺序一致。
3. 用与MentalAnswerContract同样的规则重算总分，response.score=assessment.score；评级精确匹配；高分方向正确；published规则覆盖最低可能分。
4. 每生每卷唯一；新response恰好一个assessment、response_id唯一；questionnaire.questions等于活跃题目数。
5. 快照没有选项score/value、scoringRules、creatorId、levelRules评分泄露；保存之后不随题库修改。
6. 时间不倒置、提交在开放窗口内、无未来已发生事件；模拟反馈晚于方案且有有效内容；warning状态/备注/actor/时间自洽。
7. 本批次生产表`agent_task`、`intervention_feedback`必须为空；独立验证器拒绝向dataset混入脱离数据库的offline_examples。仅检查离线模板的risk_level大小写及strict JSON形状，不导入这些产物。
8. 离线模板RAG executed=false、searches/chunks空；不生成真实audit通过、登录历史、trace/token/相关度/虚构PDF DONE；未完成/缺失不能当零或正常。
9. 明确记录每波次records和distinct students，最近30天与6个月统计口径复算；所有比例明确为演示假设。
10. 回滚按manifest先feedback/本批次真实export→task→assessment→response→question→questionnaire的依赖顺序，只删除精确本批次ID；不TRUNCATE、不删除真实登录/审计/追踪记录。

## 7. 脱敏生产读取冒烟工具（生产执行待验证）

[smoke.py](../../scripts/demo_data/smoke.py)仅使用stdlib urllib。运行时读取VM私有0600且非symlink的凭据与dataset；此文档和本次编写没有读取凭据值。兼容既有`{admin:password,teacher:password,student:password}`凭据形状；dataset使用顶层`tables`和`as_of`。只为现有三角色登录，按真实`/auth/userInfo`再匹配唯一档案；新账号不设已知密码或尝试登录。

```bash
python3 scripts/demo_data/smoke.py \
  --credentials /private/path/seed-credentials.json \
  --dataset /private/path/dataset.json \
  --output /private/path/smoke-redacted.json \
  --base-url https://YOUR_HOST/api
```

唯一写入是必要的真实登录与登出，所有业务检查为GET。代码硬拒绝非登录/登出POST及Agent路径；不调用反馈、PDF、RAG、模型或追踪写接口。HTTP响应、JWT、姓名、ID、原始答案、分数及错误body只在内存中处理，不进入stdout或结果文件；公开输出只有固定check名称、count和passed布尔值。

检查范围包括1200/80/1281总量分页、4年级各300/8专业各150、教师画像实际DTO的teaching/research/evaluations及派生指标、既有学生本人归属与学业、admin读取新生无成绩/GPA未知、3卷当前1/20题安全模板、900完成及原始分权限、本人历史快照与答案链、他人403、心理概览/分析/预警分页和角色边界、匿名401、三角色登出旧token401。生产学校级统计按数据集as_of判断，建议完成本批次导入后即时执行。

目前验证通过的是脚本编译、禁止业务写入/Agent路由、模板评分泄漏检测、Decimal比较及输出0600权限；生产HTTP断言均待根任务实跑。真实登录会产生系统认证日志，脚本不能回填或清理这些真实日志。

主要代码证据另见 [mental.js](../../frontend/src/api/mental.js)、[agent.js](../../frontend/src/api/agent.js)、[MentalAssessmentMapper](../../backend/mental-service/src/main/java/com/edu/mental/mapper/MentalAssessmentMapper.java)、[AgentTaskServiceImpl](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java)、[InterventionFeedbackServiceImpl](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/InterventionFeedbackServiceImpl.java)、[ExportServiceImpl](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java)、[ReportDetail.vue](../../frontend/src/views/agent/ReportDetail.vue)。本文件的结论为静态源码契约核对，数据库导入、UI权限、实际PDF和真实模型链路均未在本次执行。
