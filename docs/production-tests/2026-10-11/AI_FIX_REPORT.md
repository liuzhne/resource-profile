# 生产 AI 问题修复进展

> 后续工程更新（2026-10-11 / GROQ-400-CAUSE-20261011）：原始A/O生成400，移除strict重复工具文字目录的L/M/N/P严格200；452回归及最小Agent v6发布、运行制品一致/17健康通过。见[原因验证与修复结论](./GROQ_400_CAUSE_REPORT.md)。本文原task6/7/8失败记录保持不变，完整学生/P4/RAG/PDF/反馈没有重新验收。


日期 / 标识：2026-10-11 / `AI-PROD-FIX-20261011`。依据：[八组生产测试问题](./AI_REPORT.md)。原报告保留为修复前快照；本文件记录当前交付及剩余工作。

**权限、诊断契约、终态时间、RAG来源及页面修复已部署，17/17服务Ready、0重启。后续授权两次协议诊断复现Groq严格schema首轮400，展平action分支后200，修复已发布Agent v3；完整学生生成、正常PDF和反馈仍未验收，不能声明完整AI已恢复。**

既有task3/4/5均真实FAILED，保留历史和原产物，不补造成功任务、风险、引用、反馈或trace。前次三名学生的三次具体外发授权已使用完；用户已单独授权两次工程协议诊断且已执行，无师生资料/工具执行。新的完整学生验收授权仍待回复。

## 逐项原因、实施及待验证状态

| 问题 | 原因 / 因果边界 | 本轮实施 | 当前验证边界 |
|---|---|---|---|
| AI-01 首轮生成失败 | 旧三次仅保留NonTransientAiException且误归TOOL_ERROR，确切历史HTTP不可恢复；本次生产7工具schema实际请求HTTP400明确拒绝action.anyOf的anyOf-only包装 | [输出schema](../../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoopOutputSchema.java)展平具体工具分支，保留strict/参数/最终校验；[错误处理](../../../backend/agent-service/src/main/java/com/edu/agent/config/LlmResponseErrorHandler.java)仅将400固定编译拒绝前缀安全归PROVIDER_JSON_SCHEMA，模型异常仍LLM_ERROR | [两次真实协议诊断](./GROQ_SCHEMA_FIX_REPORT.md)仅改schema后HTTP200/1,127ms，6223token且JSON schema有效；387后端回归、编译请求完整JSON相等、Agent v3生产复验（本地测试产物，不入库：`./GROQ_SCHEMA_DEPLOYMENT.json`）通过。**当前首轮协议阻断已修复；完整学生Agent仍未验收，AI-01全链路保持进行中，旧错误不猜测回填** |
| AI-02 学生收到他人SSE | SSE只验证JWT，遗漏普通task控制器的教职工权限；连接注册未保存活跃会话，广播不复核 | [共享会话校验](../../../backend/agent-service/src/main/java/com/edu/agent/security/AgentSessionVerifier.java)要求签名/到期/Redis白名单及`STAFF_VIEW`/`agent:read`；[SSE入口](../../../backend/agent-service/src/main/java/com/edu/agent/sse/WarningSseController.java)拒绝学生；[注册表](../../../backend/agent-service/src/main/java/com/edu/agent/sse/SseEmitterRegistry.java)每条事件及30秒心跳前复核，注销、会话替换、到期或Redis异常时关闭连接；前端EOF有界重连，401/403停止 | 生产角色证据（本地测试产物，不入库：`./AI_FIX_SSE_ROLES.json`）：匿名401、学生header/query403、teacher200+hello；logout后已有流4,979ms EOF，旧JWT SSE/task401。未生成合成warning，逐事件复核有单测覆盖，真实新业务推送尚未单独触发 |
| AI-03 失败不可诊断 | 失败只存状态，报告无固定原因/阶段；Langfuse已有status/iterations但站内代理未投影 | 新增`failure_code`、`failure_stage`、`failure_http_status`、`trace_id`；仅保存固定枚举/合法HTTP状态/实际根trace。站内观测只投影白名单status、iterations、错误类别/阶段/状态，支持trace筛选；报告显示安全失败信息及管理员追踪入口 | 生产站内观测已显示旧TOOL_ERROR/1轮，与DEFAULT级别分开；旧任务显示“未记录”，未猜测回填。本地固定错误/任务/trace关联通过；**新真实失败四字段和Cloud关联待授权模型调用验证** |
| AI-04 失败无结束时间 | 完成时间只在部分完成路径写入，FAILED/REJECTED没有统一终态处理 | [mapper](../../../backend/agent-service/src/main/java/com/edu/agent/mapper/AgentTaskMapper.java)在状态转换同条SQL使用`COALESCE(completed_at,UTC_TIMESTAMP())`；失败更新限定非终态，不覆盖既有终态/首次诊断/时间。迁移对旧空终态使用`updated_at`补齐 | 真MySQL备份恢复、迁移重复执行与生产验证通过：20表记录数及历史updated_at保留，缺失终态完成时间0；旧task3/4/5仍FAILED，结束时间为原updated_at近似，并非精确耗时 |
| AI-05 相差8小时 | Agent持久化UTC裸时间；前端直接截字符串，观测已按时区显示；面板混合Agent UTC与其他校园时间 | Agent API时间明确带`Z`，新填充/SQL终态均UTC；[前端时间](../../../frontend/src/views/agent/agentTime.js)按`Asia/Shanghai`显示UTC+8，旧无偏移兼容仅限Agent；观测同一校园时区，PDF明确UTC+8；[面板SQL](../../../backend/data-service/src/main/resources/mapper/DashboardMapper.xml)只转换Agent待办 | 真实task3 API为03:49:56Z，列表/报告/面板/观测为11:49:56 UTC+8，列表/报告结束11:49:57；loop与任务可能有秒级采样差。PDF模板跨日离线验证通过，真实成功PDF仍依赖合法产物 |
| AI-06 只有三条探针语料 | 机械通路有数据，未准备覆盖校园支持问题的来源清单；物理计数含旧记录 | [公开语料工具](../../../scripts/public-rag-corpus.py)提供12条：政策4、心理支持4、明确标为原创演示场景4，7个官方出处；success为0。无真实个案/成功结局造假；正文为短原创摘要。空API召回改为`fallback=true,chunks=[]`，移除虚构补助金额及“4周改善”降级 | RAG复验（本地测试产物，不入库：`./RAG_FIX_REGRESSION.json`）：12条逻辑有效语料、1024维有限非零，4类公开问题API/MCP top1/top4命中预期，3个知识搜索工具实际调用；success返回fallback=true/chunks=[]。重复导入（本地测试产物，不入库：`./AI_FIX_CORPUS_IDEMPOTENCE.json`）12条全部skipped。**不替代dense baseline/完整Agent质量验收** |
| AI-07 source丢失 | Milvus查询有source，Python模型/管线和Java快照白名单均舍弃；upsert还将来源静默裁为64字符 | [来源投影](../../../ai-inference-service/app/services/knowledge_provenance.py)保留原source；doc_id仅按已知upsert `_NNNN`规则恢复，URL仅对合法HTTP(S)原值投影。API/MCP→[Java快照](../../../backend/agent-service/src/main/java/com/edu/agent/core/RagSnapshotCollector.java)→报告显示出处/文档。upsert按UTF8字节拒绝超容量，不截断；幂等hash含出处/标题/切片，成功写入刷新检索缓存revision | 真Milvus12条标题/正文/source与审核manifest相等，API/MCP及两侧cache来源保真，本次召回无旧probe/未知来源。Java快照/报告来源与URL安全离线通过；**新生成报告的真实RAG关联待完整Agent**。旧已截断来源不能自动恢复，64字节schema仍为显式容量限制 |
| AI-08 固定列重叠 | 固定列背景透明，较窄窗口横滚时底层创建时间透出 | [预警表格](../../../frontend/src/views/agent/Warning.vue)固定操作列使用不透明背景，覆盖表头/条纹/hover/高风险行并保留横滚 | 视觉结果（本地测试产物，不入库：`./AI_FIX_UI.json`）：1023×1104、键盘横向滚动、收起侧栏和1440×1000实测，固定操作列背景为不透明RGB且无时间透底；宽屏完整显示创建/完成时间 |

附带一致性防护：学生ID需存在后才能建任务；导出创建要求COMPLETED/REJECTED终态，与前端边界一致。这两项原报告为源码疑点，不改写为曾在生产复现的缺陷；实际成功PDF/反馈仍依赖真实产物验收。

## 本地验证与实际未执行项

| 验证 | 已知结果 |
|---|---|
| SSE/会话目标回归 | 26项通过 |
| 前端目标回归及生产构建 | 28项通过，build通过 |
| Python全部最小依赖回归 | 59项：58通过，1项真实Langfuse SDK测试因可选依赖未安装跳过 |
| 公开语料工具离线测试 | 4项通过；check无网络调用 |
| 固定旧探针隔离工具离线测试 | 10项通过：精确fixture、完整私有向量备份/digest、邻接与未知资料保留、Redis失败关闭、部分失败幂等继续及实际向量恢复；生产备份/隔离通过，生产恢复未执行 |
| 实际Spring AI本机HTTP transport与安全诊断 | 选项合并/序列化及受控错误分类通过，不能算真实Groq推理通过 |
| 完整受影响后端 clean verify | common80 + data15 + agent286 =381，0失败/错误/跳过，JaCoCo安全门通过；后续终态11例含新增3例通过 |
| 导出影响回归及制品 | 新增真实MVC7例通过并package；四套Export回归34例通过，该导出补修v2已部署并保留于最终Agent v3 |
| 数据库迁移/备份恢复、镜像及生产复验 | 隔离恢复/幂等迁移通过，20表记录数和历史更新时间保留；17/17 Ready、0重启；角色/API/UI/公开RAG实际证据通过 |
| 本次真实学生Agent、正常报告/审核、PDF/反馈、dense baseline | **未执行/未通过：新具体外发授权待回复，AI-01未关闭**；公开小样本相关性另已通过 |

协议诊断曾被自动审批以工程MCP/schema授权范围不足拒绝，原拒绝请求未发出。用户随后明确授权这些工程内容，已发送最多2次公开协议请求并完成修复；详情见[协议修复](./GROQ_SCHEMA_FIX_REPORT.md)。该授权不包含新的学生资料外发，旧三样本次数不能复用，新的完整验收仍待授权。

## 公开语料的出处与限制

完整来源标题、发布机构、核对日期、正文依据及权利说明保存在[工具manifest](../../../scripts/public-rag-corpus.py)。资料来自[教育部学生管理规定的国务院公报](https://www.gov.cn/gongbao/content/2017/content_5220900.htm)、[教育部直属中心转载的高校心理健康纲要](https://www.csdp.edu.cn/article/4105.html)、[中国政府网资助手册](https://www.gov.cn/zhengce/202507/content_7032288.htm)、[WHO压力支持指南](https://www.who.int/publications/i/item/9789240003927)、[WHO焦虑知识页](https://who.int/news-room/fact-sheets/detail/anxiety-disorders)、[WHO低落/抑郁知识页](https://www.who.int/news-room/fact-sheets/detail/depression)及[WHO身体活动知识页](https://who.int/news-room/fact-sheets/detail/physical-activity)。政府网部分直抓返回403，内容通过官方索引与教育部原文交叉核对；WHO两条无www短链接已验证重定向同一官方正文，不是将链接截短。

未复制整篇原文，原创情景由项目作者构造、来源仅支撑所引用原则，不声称发布机构有该真实个案或疗效。WHO压力指南相关摘要限当前非商业演示，注明CC BY-NC-SA 3.0 IGO、非WHO翻译或认可；其他内容为原创短事实摘要，原始资料权利归发布机构，不凭“公开可读”宣称获得全文复制许可。没有新增收费订阅或真实临床资料。具体学校规则、金额政策及个体评估仍须人工核实。

离线审核摘要：`81606c2d5827f7a38ad09a10ff9f37b0c3309fbeb3e87cf81ba68395835eecbd`。导入工具只接受该明确审核摘要、调用本机已授权管理API，凭据从私有环境或0600文件读取；禁止重定向，不输出凭据或供应商错误正文。12份正文经应用生成embedding，不调用Groq。旧三份探针已按固定chunk_id/source/标题/正文精确核对，先耐久私有备份实际字段/向量再隔离；未处理未知来源或同前缀其他知识。

[固定探针隔离工具](../../../scripts/public-rag-probe-quarantine.py)已准备：分别为`accept_case_20261007_0000`、`accept_policy_20261007_0000`、`accept_psychology_20261007_0000`，必须同时匹配原`synthetic-acceptance-20261007`、旧验收固定标题及正文。backup阶段保存实际向量和PK，文件0600并核对SHA256；复制VM私有存储后才apply，删除精确PK并刷新现有缓存revision。不会处理带其他后缀的历史记录或未知资料，source标记本身不构成删除依据。恢复保留实际向量及逻辑chunk_id，auto-ID生成新物理PK。生产备份、精确隔离与检索回归已通过，生产向量恢复仍待验证，具体证据见下文。

生产只读核对确认三个活跃探针正是上述固定base IDs，source/标题/正文全部相同；并非带8位后缀的旧版本。首个backup在写文件或apply前被向量校验拒绝：pymilvus返回普通list中的NumPy数值，过严的Python内置float/int类型判断不兼容。工具已用numbers.Real/Integral检测并将实际SDK值规范为JSON float/int，未重新生成向量、未扩大身份匹配；1024维有限非零只读核对通过，该兼容场景纳入第10项离线测试。后续生产备份和精确隔离已通过，详见下文真实结果；恢复尚未演练。

## 迁移、发布和回滚结果

部署/API证据（本地测试产物，不入库：`./AI_FIX_DEPLOYMENT.json`）、SSE角色（本地测试产物，不入库：`./AI_FIX_SSE_ROLES.json`）、UI（本地测试产物，不入库：`./AI_FIX_UI.json`）、RAG（本地测试产物，不入库：`./RAG_FIX_REGRESSION.json`）为本轮实际安全证据。基本批次prod-ai-fix-20261011-v1于04:52:00 UTC完成agent/data/Python/knowledge MCP/frontend五组件更新；最终frontend为prod-ai-fix-20261011-v2、agent经协议修复追加为v3，data/Python/MCP为v1，其余12服务未替换。17个Deployment/17个Pod全部Ready、0重启，无PVC删除或新付费服务器。

前端首次构建遗漏VITE_AI_ENABLED，生产复查发现“预警中心”入口隐藏后已补发v2：显式AI=true、API=/api、Langfuse JP，实际菜单恢复。后续补修[ExportController](../../../backend/agent-service/src/main/java/com/edu/agent/controller/ExportController.java)：BusinessException保留业务码和固定提示，未知异常只记录类名/返回固定提示。最终生产FAILED task3导出为HTTP200、业务code409；不存在任务code404，导出记录数未变。这是项目Result语义，不称HTTP状态本身为409。

[增量迁移](../../../sql/migrations/20261011_01_agent_diagnostics.sql)与[新库脚本](../../../sql/init/10_agent_diagnostics.sql)一致。VM私有完整MySQL备份恢复到隔离库后先执行并重复迁移，20表行数一致，再应用生产；历史updated_at和业务行数保留，旧空completed_at取原updated_at近似，旧原因/HTTP/trace不猜测，未重放初始化/种子或改既有迁移checksum。

耐久私有备份目录位于VM /opt/resource-profile/.local-run/kamatera/prod-ai-fix-20261011-v1/：database-before.sql.gz、source-before.tar.gz、rag-probe-backup.json。实际备份/向量不复制到公开报告或Git。三份探针的备份SHA256为2f79823172b662c544d7590d5c991d52e94ad45392c80e16a5c2f9f0f56603c8；实际changed_rows=3、revision更新，0新embedding/Groq。[工具](../../../scripts/public-rag-probe-quarantine.py)恢复直接写保存的字段/实际向量，auto-ID会生成新物理PK，逻辑chunk_id保持。

实际运行中，初次向量backup因Milvus返回NumPy实数而被过严类型校验安全拒绝，尚未创建文件或删除行；numbers.Real规范至JSON float及新增回归后，三份记录身份/source/title/content仍逐项相等，再成功备份/隔离。管理员探测最初误读accessToken，按实际DTO token修正后完整重跑并注销；这些是工具兼容问题，不是生产向量损坏或登录产品故障。

准确发布前镜像pre-prod-ai-fix-20261011-v1和源码已保存，回滚应恢复稳定原镜像并保留兼容新增列；不要选遗漏AI开关的中间前端制品作为最终恢复目标。**MySQL备份恢复及迁移幂等已演练；应用镜像回滚和Milvus向量恢复尚未实际演练，仍待验证。**不DROP列、清全库/会话或重建PVC。

浏览器临时页已关闭、尺寸覆盖已重置、本机临时凭据副本已删除；teacher/student/admin测试会话均注销，最后旧admin JWT401。原五组件发布阶段未追加Groq请求；后续本次工程协议诊断实际2次，不含师生资料、不执行工具、未创建新Agent任务。既有task3/4/5仍FAILED，正常PDF/反馈不能用假成功任务绕过依赖。

修复后的生产预警列表（本地测试产物，不入库：`./AI_FIX_WARNING_WIDE.png`）

同步：[ARCHITECTURE](../../../ARCHITECTURE.md)、[DECISIONS](../../../DECISIONS.md)、[RUNBOOK](../../../RUNBOOK.md)、[执行计划](../../educare/EXECUTION_PLAN.md)。已完成[Groq协议阻断修复](./GROQ_SCHEMA_FIX_REPORT.md)，下一步是取得新的三样本具体资料外发授权，再完成完整Agent/真实产物/trace/PDF/反馈验收。R-5.2/R-5.3/R-5.4、R-6及B-2保持未完成。

AI-FULL-ACCEPT-20261011补记（2026-10-11）：本轮3次新授权task6/7/8均FAILED，task7/8明确生成校验400。v4/v5提示、固定亚型及结构日志、一次有界纠错已发布，450后端verify及17/17健康通过；真实strict请求仍生成缺四字段结果，无工具/有效报告，成功PDF/反馈仍阻塞，次数已用完。详见[最终验收](./AI_FULL_ACCEPTANCE_REPORT.md)，不能将前述协议200或离线通过扩写为完整生成成功。
