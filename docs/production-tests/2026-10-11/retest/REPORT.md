# 生产复测报告：AI-PROD-RETEST-20261011

日期：2026-10-11。环境：[Kamatera 生产站点](https://resource-profile.45-130-165-168.nip.io/)。部署基线为 Agent v6，运行 JAR SHA256 `3ecbf71001e7f7e20213798d3c3cb6bbe24da86dff4e3a732f58ea394856471d`。

**完整 AI 仍未通过。** 本轮新授权的三个任务全部失败；新增发现两个接口错误码问题、一个报告状态文案问题、一个追踪关联缺口及一个学生导航问题。学生／教师权限与注销、分页、档案统计及退出弹窗本轮通过。这里记录测试与后续修复方向，没有发布新业务修复。

## 1. 范围、授权与计数

用户明确授权最多三次完整 Agent，指定合成学生方柏言（346）、吴亦竹（750）、罗卓嘉（175），包含有效报告后的 PDF 和示例反馈。三名各触发一次，新任务为 9、10、11；没有自动重试触发或第四个任务。循环内的一次纠错属于同一任务的现有有界恢复。前一轮任务 6／7／8 和工程 A–P 不计入本轮，也不能代替本轮业务结果。

资料外发目的地仍为 Groq GPT-OSS-120B；若实际执行 RAG 才会向 Cloudflare BGE-M3 发送检索文本。本轮 RAG 未执行。Langfuse 日本区仅记录运行元数据，检查确认代理未返回 input、output 或任意 metadata。没有提交任何实际干预反馈，没有修改数据集、密钥、数据库结构、生产配置或镜像。

接口断言共 **103 项，98 通过、5 未通过**：非管理员 45／45，管理员 53／58。五条未通过对应三个任务失败和两个接口边界错误；页面问题另行记录，不混入此计数。正常 PDF、反馈、P4、完整 RAG 属于依赖阻塞，不计成已执行通过或独立功能失败。

安全原始证据见 EVIDENCE.json（本地测试产物，不入库：`./EVIDENCE.json`）。日志只有固定类别、轮次、长度、状态和 schema 存在标记，没有原始模型回复、学生资料、提示、工具参数或凭据。

## 2. 问题清单

| 编号 | 优先级 | 已复现问题 | 影响与后续方向 |
|---|---|---|---|
| RT-01 | P1 | 三个完整 Agent 均为 JSON_GENERATION / HTTP 400 | AI 风险、方案及审核无法生成；需要针对真实首轮及档案 Observation 后的消息协议分别定位，不再以工程单轮 200 作为恢复判据。 |
| RT-02 | P2 | 不存在的任务返回业务码 500 | 应区分资源不存在与系统故障，返回业务 404。 |
| RT-03 | P2 | 非法任务状态筛选返回业务码 500 | 应在输入边界返回业务 400，保留正常枚举查询。 |
| RT-04 | P2 | 已执行并失败的风险识别显示“未执行” | 当前按是否存在风险 JSON 判断执行与否；缺产物应显示“未生成”，不能伪造阶段执行记录。 |
| RT-05 | P2 | 任务关联追踪只有汇总，缺单轮模型调用关联 | 根 trace 在循环结束才创建，调用另建 trace；需在开始时建立并传播共同 trace／父 span。 |
| RT-06 | P3 | 学生页仍有无权限的固定“数据面板”标签 | 点击被路由守卫拦截并返回问卷页，未越权；固定标签应按角色首页建立。 |

### RT-01：400 仍发生于真实业务请求

| 任务／学生 | 创建时间 UTC | 完成时间 UTC | 实际轮次 | 第 2 轮档案长度 | 结果 |
|---|---|---|---|---:|---|
| 9／方柏言 346 | 12:46:23 | 12:48:24 | 1：400 → 2：档案工具成功 → 3：400 | 205 | FAILED |
| 10／吴亦竹 750 | 12:56:55 | 12:58:55 | 1：400 → 2：档案工具成功 → 3：400 | 223 | FAILED |
| 11／罗卓嘉 175 | 13:02:13 | 13:04:14 | 1：400 → 2：档案工具成功 → 3：400 | 213 | FAILED |

上述长度为既有 Java 日志的 `String.length()` 单位，其日志标签虽写 bytes，不能作为网络字节量。均未触及工具／近期历史 4096／2048 的截断上限。三任务各使用一次格式纠错，随后成功执行 `get_student_profile`，第三轮再被供应商拒绝，故不能归因于“档案 MCP 完全不可用”或本轮档案截断。

任务固定字段为 `PROVIDER_JSON_SCHEMA / LLM_REQUEST / 400`；带 task ID 的日志进一步证实六次拒绝均为 **JSON_GENERATION**，不是 `SCHEMA_COMPILATION`。本轮没有 429 固定错误，未观察到限流导致此次失败。

对应实际 trace：

- task 9：`eff76fb31d6d4ca294efc63941ab3c35`
- task 10：`3575a9aa19dc440080603a887de8d82a`
- task 11：`910687d3225649f589a02f6671166203`

限定窗口 12:46:21–13:04:16 UTC 的九条 HTTP 交接契约均为 JSON_SCHEMA、strict=TRUE、react_schema=true、顶层 formal tools=false、stream=false、输出上限 2400。七工具被编入 ReAct 输出 schema，由应用执行；tools=false 不表示没有可用 MCP 工具。六条固定拒绝结构均可解析 JSON，thought／plan／action／final_answer 四字段存在标记为 false。**这些契约／结构日志没有 task tag，只能作为该时间窗证据；未保存值或根类型，不能推断具体错误 JSON、供应商内部机制或单一提示根因。**

每个任务均无风险 JSON、干预方案和审核产物，RAG executed=false、0 searches、0 chunks、0 并行工具批次。`retrievedKnowledge` 内的空快照不是成功检索；对空列表的 all(...) 为 true 不构成出处完整证据。三个失败任务导出请求都返回 HTTP 200／业务 409，没有新增 PDF job；正常生成 PDF、视觉质量、真实报告反馈／月报仍待有效方案。

后续修复应比较生产真实首轮与档案 Observation 后的消息形态，保持严格 schema、最终产物校验、一次纠错与预算／冷却。只读诊断已经足以记录当前故障，新增模型对照及新的完整学生任务须先明确具体次数和资料范围；本轮三次额度已用尽。不能直接放宽 JSON 输出或编造 fallback 报告来通过验收。

### RT-02／RT-03：接口错误码失真

两请求均仅 GET、各一次，没有新任务或模型调用：

- `/api/agent/api/v1/task/9223372036854775807`：HTTP 200，业务 **500**；期望业务 404。
- `/api/agent/api/v1/task/list?page=1&size=3&status=NOT_A_STATUS`：HTTP 200，业务 **500**；期望业务 400。

[AgentTaskController.java](../../../../backend/agent-service/src/main/java/com/edu/agent/controller/AgentTaskController.java) 缺失任务调用无自定义码的 `Result.error`，默认 500。[AgentTaskServiceImpl.java](../../../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java) 对筛选字符串直接 `TaskStatus.valueOf`，非法值进入通用异常。建议分别使用明确的 404 和输入校验 400；保持当前 HTTP 200 + Result 业务码约定，避免仅两端点改变全项目协议。修复和回归尚未执行。

### RT-04：产物缺失被显示为“未执行”

task 11 [真实报告页](https://resource-profile.45-130-165-168.nip.io/agent/report/11) 上方准确显示失败类别／模型请求／HTTP 400，下方四阶段均为“未执行”。风险识别实际已进入 RISK_ANALYZING，发生模型请求并成功读取档案，故阶段 1 文案失真。

[ReportDetail.vue](../../../../frontend/src/views/agent/ReportDetail.vue) 的 hasStage(1) 只检查风险 JSON 是否存在；[后台失败路径](../../../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java) 在保存风险 JSON 前返回。最小修复方向是区分产物与执行：“未生成”；任务失败时显示“未生成，任务执行失败”。RAG 可依真实 executed=false 保留“未执行”。如果需要完整阶段历史，必须增加真实事件记录，不能只靠 failureStage 或终态补造阶段状态。修复待验证。

task 11 失败及阶段文案（本地测试产物，不入库：`./task-11-failure.jpg`）

### RT-05：追踪关联不完整

对三个任务的实际 trace 精确查询，每个只返回一条 agent.loop，状态 LLM_ERROR、iterations=3、错误码／阶段／HTTP 与任务一致；无 input／output／任意 metadata。task 11 点击“查看站内追踪”也只显示这一汇总。显示全部追踪能看到 llm.chat 模型与真实 token，但这些记录使用不同 trace；例如 21:02:13 的 llm.chat 为 `07e5d1ec2a644a19adab36be474f1a70`，与 task 11 根不同。**时间邻近不能证明它属于 task 11，不按时间拼接。**

源码因果：[AgentLoop.java](../../../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java) 的 finishRun 才产生根 trace；[LlmMetricsInterceptor.java](../../../../backend/agent-service/src/main/java/com/edu/agent/config/LlmMetricsInterceptor.java) 上报不带任务上下文；[LangfuseClient.java](../../../../backend/agent-service/src/main/java/com/edu/agent/config/LangfuseClient.java) 缺 ID 时另建根，且未写 parentSpanId。汇总原来的 unknown／0 被[安全读取服务](../../../../backend/agent-service/src/main/java/com/edu/agent/service/LangfuseObservationService.java)有意转成 null，不能单独描述为真实 token 丢失。

后续应在循环开始前建立 trace／root span，同步调用显式传递、异步上报捕获不可变参数，支持并发隔离与 finally 恢复；调用观测引用真实父 span。失败调用也可只记录固定类别，供应商未返回的 token 仍为未知。禁止把汇总当单次模型消费，或给根／调用重复累计 token。实现和验证待执行。

### RT-06：学生固定标签未按角色过滤

学生正常登录进入 `/student-mental/list`，菜单仅问卷和本人画像，页面加载正常。但标签条还显示“数据面板”；点击仍停留／返回问卷页，权限守卫正确阻止进入全校看板。[TagsView.vue](../../../../frontend/src/components/Layout/TagsView.vue) 将 Dashboard 无条件设为 affix。建议按现有角色首页初始化固定标签，并在角色／会话变化时清理无权限标签，继续保留后端和路由授权。属导航体验问题，不是数据泄漏。

## 3. 本轮已通过

- 运行基线（12:29:44 UTC）：17／17 Deployment Ready、17 Running Pod、0 restart、8／8 PVC Bound；10 个应用内部健康均 200，CPU／内存／磁盘无压力。后端、数据库、AI、MCP Service 均 ClusterIP，没有新增公网业务端口。此为该时刻观测，不是可用性 SLA。
- 学生本人档案、32 条成绩、70 条考勤、排名／素质、问卷与评估读取正常；他人数据、全校列表／看板、任务／追踪禁止。心理敏感字段与计分规则未返回。
- 学生 SSE header／query 均 HTTP 403；匿名 SSE 401。教师 SSE 200 + hello，注销后 2.692 秒 EOF、没有注销后 warning；旧令牌业务／SSE 401。没有人为发布 warning，实际 warning 送达仍待验证。
- 管理员与教师任务分页正确。新任务总表 9 条，3 条／页的两页不重复；用户 total=1281、两页 records=3，学生 total=1200、教师 total=80，与数据面板一致。
- 三个新失败任务 completedAt 均有 UTC Z，站点统一 UTC+8；失败诊断、trace 与站内链接正常。完整任务探针每次注销后旧 JWT 401。
- 登录页不展示默认密码，未配置外部认证入口置灰；学生正常登录到问卷，未再出现旧无权限面板加载失败。1023×1104 视窗中预警固定操作列不再透底重叠；退出弹窗居中且文字／按钮完整，已完成管理员和学生退出。

退出弹窗复验（本地测试产物，不入库：`./logout-dialog.jpg`）

## 4. 未通过、未验证及工具限制

完整 Agent 阻塞本轮成功 PDF／反馈、P4 与任务内 RAG。此前独立公开语料小样本检索通过，不等同于这些新任务执行 RAG，也不能代替质量 baseline。没有合法新 PDF job，真实 PDF 的 teacher／student 拒绝检查及版面验证未执行。历史凭证轮换 B-2 与 R-5／R-6继续未完成。

另外一次读取 Kubernetes Secret 中开关实际值并写本地的检查被自动审批拒绝，理由是可能泄露凭据；已停止，没有绕过。改用本轮角色、会话、匿名与越权实际请求验证安全行为。未读取的开关值仍未知，不能声称已检查全部安全配置。

测试诊断 helper 曾因 MySQL JSON_TABLE 的 CTE 推导参数报 1210／HY000，并有 label／时间戳兼容问题；只修改测试 helper 后重新执行，固定投影 SQL／限定日志均成功。该工具故障不计生产业务缺陷。测试证据保留真实失败任务，没有删除或伪造报告。所有测试会话已注销；本轮未进行新的应用修复或部署。

## 5. 后续通过判据

1. RT-01：新明确授权范围内完成首轮 action、真实档案／学业／考勤／心理工具、可验证来源的 RAG、严格风险／方案、真实 P4；需包含工具 Observation 后的模型轮次，不接受单工程请求替代。
2. 有有效方案后验证 PDF 导出、中文／分页／来源和报告字段；提交明确标注的验收反馈一次，核对绑定任务／学生、月报与 CSV。对实际存在的 job 验教师／学生权限。
3. RT-02／03：缺失任务业务 404、非法 status／riskLevel 业务 400，正常列表过滤与分页继续正确。
4. RT-04：失败、运行中、历史无诊断、低风险跳过审核、RAG 空召回的文案与真实产物／执行状态相符。
5. RT-05：共同 trace、真实 parent span、并行任务隔离、异步快照、异常恢复、未知 token／无正文均通过；不得按近邻时间制造关系。
6. RT-06：学生固定首页与权限匹配，切换角色后标签无残留，受限路由与后端拒绝仍生效。

上述修复、离线回归与后续发布均**待执行**，没有将本轮测试结果写为修复完成或 R-5／R-6通过。项目三份现状文档已用相同标识同步。
