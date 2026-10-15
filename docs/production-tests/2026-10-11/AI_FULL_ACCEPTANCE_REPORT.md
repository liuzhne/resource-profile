# AI-FULL-ACCEPT-20261011 完整 Agent、PDF 与反馈验收

> 后续工程更新（2026-10-11 / GROQ-400-CAUSE-20261011）：原始A/O生成400，移除strict重复工具文字目录的L/M/N/P严格200；452回归及最小Agent v6发布、运行制品一致/17健康通过。见[原因验证与修复结论](./GROQ_400_CAUSE_REPORT.md)。本文原task6/7/8失败记录保持不变，完整学生/P4/RAG/PDF/反馈没有重新验收。


日期：2026-10-11。本轮用户明确授权最多 3 次完整任务，资料范围为已生成的三名合成学生（346/750/175），目标为既有 Groq、Cloudflare 及仅安全指标的 Langfuse 日本区。之前两次工程协议诊断已用完，本轮未另行创建协议探针。

## 最终结果：验收未通过

| 任务 | 学生 | 镜像 | 实际结果 | 工具/产物 |
|---|---:|---|---|---|
| 6 | 750 | Agent v3 | 首轮 FAILED，LLM_REQUEST / PROVIDER_JSON_SCHEMA / HTTP400；历史亚型未知 | 工具0，无风险/方案/PDF/反馈 |
| 7 | 175 | Agent v4 | 首轮 FAILED，HTTP400 / JSON_GENERATION | 工具0，无风险/方案/PDF/反馈 |
| 8 | 346 | Agent v5 | 两轮 FAILED，两次 HTTP400 / JSON_GENERATION | 工具0，无风险/方案/PDF/反馈 |

task6 trace `e058fadc0cbc4c82a44e292251055bf9` 与站内真实 `agent.loop` 观测一致，1轮、4,111ms、模型请求失败，DEFAULT 级别没有误显示为业务成功。task7 trace `628dfb900e364f9090217c163b625d5d`。失败报告准确显示校园 UTC+8 完成时间、失败阶段/HTTP、关联追踪；PDF按钮禁用，反馈入口隐藏，没有伪造成功产物。

## 已执行修复与证据边界

实际 MCP adapter 生成 schema 与先前真实 HTTP200 工程请求相等；生产 v3 配置/拦截器类与本机 fixture 摘要相同，使用生产类运行本机 HTTP fixture 的完整序列化请求也与先前 HTTP200 请求相等。这些离线核查未执行模型或业务工具，不能替代本轮实际发送的协议形态证明。

v4 修复结构化提示的四字段/嵌入双对象矛盾、技能中的额外审核字段及缺失证据推断，增加固定 SCHEMA_COMPILATION / JSON_GENERATION / UNKNOWN 亚型。416项 common/agent verify 通过，发布6旧entry和1安全枚举entry。task7由此明确是生成校验400，未证明原提示矛盾是该400根因；不能反推task6或旧task3/4/5的准确亚型。

v5 增量：任务首轮明确 get_student_profile 的四字段和动态 studentId，后续依工具结果继续；严格模式且 typed JSON_GENERATION/HTTP400 时全run最多一次固定纠错，仍消耗原max8轮及900秒deadline，二次或其他错误真实失败。保留 strict、工具守卫及本地最终完整 schema/分数校验，不重播已执行工具、不降级宽松格式、不编造成功或执行真实干预。固定 request contract/failed JSON 结构日志只记枚举、布尔和预算整数，不保存正文、failed_generation值、未知字段、提示、凭据或学生内容。

v5 common80 + agent370 共450项 verify通过（0失败/错误/跳过），包含一次纠错→真实工具→合法双对象、二次失败、身份/限流/编译不纠错、轮次/截止、输入示例及日志泄露检查。v5已发布，实际运行JAR SHA256 `03599cb00f989cef0e1706185be5e48d5a4ecdd1a5479ac35ef6cd901c9a9073` 与审核制品相等；17/17 Deployment和17/17 Pod Ready、0重启。最后task8仍FAILED（UTC07:38:50→07:39:51），两轮、trace `f6e6f8a52f7b47e48739656fb61f7b7d`。两次实际传输均JSON_SCHEMA/strict TRUE/所选ReAct schema/无tools/非流式/2400 tokens；两次failed JSON均可parse，但四个ReAct顶层字段全部不存在，根JSON类型与具体内部参数未记录，不能据此猜测准确供应商根因。一次纠错没有解决本次兼容故障，不能将其写为生成修复通过。Groq官方说明支持该模型严格schema；真实供应商错误仍以本轮固定证据为准：[Structured Outputs](https://console.groq.com/docs/structured-outputs)。

实际安全证据（本地测试产物，不入库：`./AI_FULL_ACCEPTANCE.json`），首个失败页面（本地测试产物，不入库：`./AI_ACCEPT_TASK6_FAILED.png`），真实追踪页面（本地测试产物，不入库：`./AI_ACCEPT_TASK6_TRACE.png`）。三根现状文档与执行计划同步；R-5.2/R-5.3/R-5.4/R-6/B-2保持未完成。

## 阻塞与后续边界

本轮3次新任务授权已全部用完，未创建第4次；没有业务MCP执行、RAG、风险/方案或真实合规审核，因此PDF和反馈成功路径依赖仍阻塞。本次未创建导出/反馈，数据库导出任务0、反馈记录0；全部终态结束时间存在。网站及服务健康通过不能替代完整AI通过。新的完整/协议请求须取得相应新的有界外发授权；可继续离线核查/修复，但不能调用模型来绕过次数限制。下一步应继续定位严格输出下接受请求却生成非ReAct结构的原因；不盲目增大预算/关闭strict/切换为假成功或把旧失败任务改成成功。

最后任务页面（本地测试产物，不入库：`./AI_ACCEPT_TASK8_FAILED.png`），全部任务列表（本地测试产物，不入库：`./AI_ACCEPT_FINAL_WARNING.png`）。450项回归/镜像发布是真实通过，完整Agent、成功PDF、反馈及R-5.2仍未通过。

收尾核验：task8站内真实 `agent.loop` 为2轮/61,299ms/模型请求失败，与数据库trace及终态相符，DEFAULT未表示成功；最后任务追踪（本地测试产物，不入库：`./AI_ACCEPT_TASK8_TRACE.png`）。测试管理员已退出，旧JWT实测401、Redis会话不存在；临时浏览器关闭，本机/服务器临时凭据副本已删除。未读取或保存原始模型/失败正文。

下一步的[A/B工程诊断候选](./AI_NEXT_DIAGNOSTIC_PLAN.md)已离线准备并校验，仅action工具范围不同，实际Groq/MCP/业务调用0。须取得新的明确授权后才能外发，当前验收结论不变。
