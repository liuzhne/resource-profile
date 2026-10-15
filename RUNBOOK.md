# Resource-Profile 运行手册

> 最近更新：2026-10-11
> 适用范围：当前仓库的开发、测试、排错和发布准备。真实 AI/生产签字状态以 [`docs/educare/EXECUTION_PLAN.md`](./docs/educare/EXECUTION_PLAN.md) R-5/R-6 为准。

## FIELD-CONTRACT-AUDIT-20261007：页面、接口与数据库契约审计（2026-10-07）

状态：以下为修复前审计基线；实现与当前验证状态见FIELD-CONTRACT-FIX-20261007条目。见[完整报告](./docs/audits/FIELD_CONTRACT_AUDIT_2026_10_07.md)、结构化清单（本地测试产物，不入库：`./docs/audits/FIELD_CONTRACT_AUDIT_2026_10_07.json`）、CSV（本地测试产物，不入库：`./docs/audits/FIELD_CONTRACT_AUDIT_2026_10_07.csv`）。22路由视图、7API模块、生产20表220列已覆盖；61项按问题组去重，包括42项契约问题、18项已声明未开放/来源缺口和1项权限UI适配。现有实体映射无缺列；不能将所有61项称为数据库缺列或已发生生产故障。

启动前提：源码与增量SQL可读；生产元数据核对通过既有固定SSH身份和MySQL Pod执行，只读取`information_schema.COLUMNS`。SQL如下，凭据仅由Pod环境传给mysql，不把密码放命令行或输出中：

```sql
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'edu_portrait'
ORDER BY TABLE_NAME,ORDINAL_POSITION;
```

本次已执行：源码字段/调用链及320个证据行锚点核对；生产只读列查询成功；22实体256映射字段、24 Java/1 XML mapper、11 DTO文件14类和20表初始化定义交叉核对。虚构数据重新编译并实际调用当前MCP tool，心理键错配、未知成绩均值和跨学生来源未校验复现；合法ReAct schema对象的页面旧字段投影错配复现。没有真实业务行读取/写入、模型调用、部署或数据迁移。详细可复核结果见报告§4及三个JSON证据文件；离线投影不是浏览器/PDF实测。

快速定位命令（仓库根目录，已执行源码搜索；不会输出凭据文件）：

```bash
rg -n '政治面貌|phone|email|evaluationScore' frontend/src/views/teacher/detail.vue
rg -n 'getStudentDetail|成绩|学业' frontend/src/views/student/detail.vue
rg -n 'deadline_days|goals|metrics|references' frontend/src/views/agent/ReportDetail.vue
rg -n 'riskLevel|questionnaireType|factorScores|totalCredit' backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java
rg -n 'sys_login_log|LoginLog' backend sql
```

后续通过判据（**待验证**）：角色矩阵在任务JSON/学生结果/PDF响应中均正确裁剪；心理查询保留请求学生归属；详情A→B标签切换读写绑定B；MCP有记录时返回真实授权等级和明确来源；量表/选项计分格式一致、非法/缺必答提交拒绝且不落库；历史答案按提交版本回显；ReAct与legacy在页面/PDF均完整；未出分与0不同、日期清空能落库、统计期间/人数/学院口径与标签一致。禁止用真实生产学生写入来复现，优先用隔离库虚构夹具；需要完整Agent时沿用明确的数据外发授权规则。

排错：先区分缺来源、未接已有API、形态/口径不一致和角色脱敏，再决定改DTO/映射/查询或增加业务能力。无列不代表必须加列（排名可派生、JSON属性不独立建列）；有列也不证明主路径在写入。共享userId问题先确定账号/学生绑定模型，再核对重复及历史归属，不直接加唯一约束、不使用LIMIT 1掩盖混档。当前用户分页、注销及退出弹窗是前轮已修复项，本次不重复计数。

回滚：本次没有运行修改，无镜像/SQL回滚需要；审计结论应保留为历史证据，后续修复按编号补状态及验证结果。新修复上线时另记录镜像/配置/源文件与迁移的保留数据回滚方案，不删除PVC或初始化生产数据库。R-5/R-6验收状态保持原状。

## LOGOUT-DIALOG-CSS-20261007：退出弹窗缺失样式（2026-10-07）

状态：修复及生产前端部署完成，浏览器视觉/交互验证通过。见 [报告及截图](./docs/production-tests/2026-10-07/LOGOUT_DIALOG_REPORT.md)和 聚合证据（本地测试产物，不入库：`./docs/production-tests/2026-10-07/LOGOUT_DIALOG_RESULTS.json`）。最新frontend为 `edu-portrait-kamatera-frontend:prod-fix-20261007-dialog-css`，11:36:19 UTC Ready；下节三个发布批次保留其历史快照。

原因/修复：Navbar显式调用的ElMessageBox/ElMessage没有进入模板自动导入CSS链，teleport弹窗因此缺样式。 [main.js](./frontend/src/main.js) 在自定义样式之前导入两项官方服务 `style/css`，自动包含base/overlay/button/input；无需额外坐标补丁或全库样式。

复现/通过判据：真实账号登录 → 用户菜单“退出登录” → 标题/提示/图标及“取消”“确定”/关闭均可见，白色卡片居中；取消或关闭只收起弹窗并留在登录后的页面，确认经原注销API回登录。应同时截屏并读取可见DOM几何，不能只凭AX出现按钮宣称布局正确。本轮1280×720下修复前left/top=0/0、width1280、透明背景、overlay static；修复后left/top=430/296、420×128、白色背景、overlay fixed，中心误差0。实际取消、关闭、确认均通过，临时账号已退出/标签关闭。

已执行：`cd frontend` 后 `npm test`（93/93）、`npm run build`、`npm run size:check`、`npx eslint src/main.js`，均通过。构建和公开入口所引用CSS须包含 `.el-message-box`、`.el-overlay-message-box`、`.el-message`；本轮新 `element-plus-D-tsrTlC.css` HTTP200/227671字节、SHA256 `18d6502499098839d410b67bee8a3779b9c0ce1ec7a288075b331c7754cfd98b`，JS体积2046.1KiB满足门。旧页面需刷新以加载新CSS引用。使用真实生产浏览器验证布局与操作，既有会话回归一并执行。

发布：仅1个公开文件 `frontend/src/main.js` 的增量包，785字节、SHA256 `c045158fd747dbc166ba0766c6cf472e6cb734b12226cec8ffdf276ea15a109e`。VM留 `source-backups/prod-fix-20261007-dialog-css.tar.gz` 及 `pre-prod-fix-20261007-dialog-css` 镜像，导入containerd后只更新frontend。发布/回滚映像在VM私有 `.local-run/kamatera/prod-fix-20261007-dialog-css-{deployment,rollback}.json`，不改后端、Secret、PVC或初始化资料。

排错：若仍在左上角，先刷新并核对HTML引用的新CSS及200状态/三个选择器；确认所有页面引用共享main入口，检查body teleport和覆盖样式，不直接增加margin掩盖缺文件。回滚（准备完成、未执行）：按实际rollback记录把frontend image恢复 `edu-portrait-kamatera-frontend:pre-prod-fix-20261007-dialog-css`，再等待rollout Ready并核查入口；它恢复上一版用户分页/注销修复，但会重新出现本次样式故障。无需改密、重启后端或删除数据。


## PROD-FIX-20261007：四项生产故障修复与复验（2026-10-07）

状态：四项修复代码及三批部署完成；前三项线上复验通过，AI 回归/真实合成请求通过，完整示例 Agent 等待本次具体外发授权。实际结果见 [修复报告](./docs/production-tests/2026-10-07/FIX_REPORT.md)及 FIX_RESULTS.json（本地测试产物，不入库：`./docs/production-tests/2026-10-07/FIX_RESULTS.json`）；下节原冒烟待实施文字保留为历史快照。本次不改变域名/供应商套餐/PVC。

验证必须逐项完成：真实浏览器菜单注销前将该会话 token 仅保存于 VM 私有 0600 文件，注销后同 token GET 业务接口为401且Redis会话为空，最后删除私有文件；旧登录→新登录→旧token注销时，新token仍200，正式注销新token后401。学生真实登录默认本人问卷页面、没有看板错误，直接访问全校面板/师生管理返回允许的入口，后端对应API仍403；教师和管理员的已授权页面不受阻。用户列表生产MySQL total 等于实际匹配行数，size=1的多页不重叠且无password字段。Groq先用无个人内容的合成请求隔离响应格式与429退避，再经用户对具体示例资料/接收方的一次授权验收完整Agent，只触发一次并要求真实工具成功、final JSON校验与风险/计划落库；Cloud新时间窗观测无I/O。

本地新增分页真实 SQL 回归见 [UserPaginationSqlTest](./backend/user-service/src/test/java/com/edu/user/service/impl/UserPaginationSqlTest.java)，前端回归见 [session-access.test.mjs](./frontend/tests/session-access.test.mjs)和 [user-list.test.mjs](./frontend/tests/user-list.test.mjs)。凭据读取既有私有文件，不输出密码/API key/token/业务资料/模型文本/原始日志。使用 JDK21、CI/VM Node22（本地实际 Node26.10 构建通过）；沙盒 ByteBuddy 自附加受限时在测试启动加载已缓存 javaagent，不绕过 Enforcer/JaCoCo。实际 common75/auth27/user11/agent114、前端93、Python50（实际SDK环境）和K3s13项通过，相关覆盖率门通过；frontend build 通过，lint 零错误但有既有 vite.config.js 格式警告。

生产实测：浏览器正式退出后的同 JWT401/Redis 空；同秒新旧 JWT不同且旧注销不影响新会话。学生默认本人问卷无错误、受限 URL重定向；用户真实 MySQL总数3、size1四页[1,1,1,0]且无password，管理页面真实查询3→1→3。公网18/内部21/RAG8全通过、业务53通过0失败1无foreign夹具跳过、17/17 Deployment Ready、8 PVC Bound、HPA当前/目标1且指标有效。测试会话已注销，浏览器临时标签关闭；没有重灌知识或资料。

已执行本地命令：backend 各受影响模块分别 `mvn -o -Daether.syncContext.named.factory=noop -pl <module> -am test`（JDK21，按环境需要启动 javaagent）；frontend `npm test`、`npm run build`、`npm run lint:check`；实际SDK venv 下 `python -m unittest discover -s ai-inference-service/tests`，以及 `python3 scripts/test-k3s-deploy.py`。本轮忽略目录 `.local-run/kamatera/` 留只读 `fix-public-20261007.py`、`fix-runtime-20261007.py` 和会话变更 `fix-business-20261007.py`、`fix-auth-race-20261007.py`、`fix-pagination-20261007.py` 的脱敏审计；VM 对应目录位于 `/opt/resource-profile`。业务/浏览器/Agent复验不要同账号并发。RAG工具 `fix-rag-20261007.py`只做固定合成查询并检查新缓存/1024维非零向量，无upsert。所有临时工具通过已有固定主机身份SSH运行，不能导出私有env、token或原始日志。

AI线上实际参数：严格ReAct schema、输出2400、输入18000字符、reasoning low；Java/Python开启Redis DB0共享冷却60s，请求等待180s/排队2，429至多3次/单次Retry-After最多120s，400不重试；独立任务池并发1/排队2、loop900s/锁1200s、HTTP连接10s/读取60s、Feign读取240s。仅17项非秘密参数同步至39个Secret字段，其他值和凭据保持。模型正文/SQL参数日志关闭，Langfuse内容仍false。新Python镜像单次合成请求200、JSON有效、167tokens、17运行断言通过、调用后共享冷却约59s；Cloud新 `llm.other` 有用量/耗时且I/O空。完整 `fix-agent-20261007.py` 尚未执行，收到本次明确授权后只允许触发一次，工具成功/final校验/落库和新trace全部通过才可勾R-5.2。

排错：400固定状态直接终止，检查schema/最终产物和输入预算，不能将合成成功断言为原400根因；429查看安全状态、共享冷却/Retry-After与Groq组织其他调用，等待预算耗尽明确失败，不升级付费或无限重试。队满返回503并持久化FAILED，进程内排队不提供崩溃后PENDING恢复。deadline900s、连接/读取有限，不能因健康接口正常宣称完整任务成功。

发布路径已执行：公开源码增量包核验SHA → VM原源码/镜像备份 → 原生构建唯一tag → 新旧镜像同时导入containerd → 受影响Deployment更新及Ready。三批为 `prod-fix-20261007-base`（frontend/auth/user）、`prod-fix-20261007-ai`（agent/Python/knowledge MCP）、`prod-fix-20261007-user-ui`（frontend）。VM私有各 `<release>-deployment.json`、`<release>-rollback.json`记录真实镜像；源码备份 `.local-run/kamatera/source-backups/<release>.tar.gz`，构建日志留VM，不对外复制。

回滚（准备完成，**未实际执行**）：按对应记录把 Deployment image设为 `pre-<release>` 并等待Ready；frontend用户页回滚至 `pre-prod-fix-20261007-user-ui`仍保留首批注销/角色修复。AI仅回滚镜像时可保留新非秘密参数和Secret引用；若还需恢复完整参数，先将三个Deployment环境引用恢复VM私有 `source-backups/prod-fix-20261007-ai-env-references-before.json`，再恢复原环境文件 `source-backups/prod-fix-20261007-ai.env`及 `source-backups/prod-fix-20261007-ai-secret.json`、使用旧镜像并重建/等待Ready。不要先删新Secret字段而仍保留新引用，否则Pod会缺配置；先确认其间没有新的合法配置修改，避免覆盖。备份含秘密仅留VM0600，禁止下载/打印。恢复后重新查健康和相关权限，不使用latest restart、删PVC、重跑种子/RAG或关闭权限掩盖失败。

## PROD-SMOKE-20261007：Kamatera 当前生产冒烟（2026-10-07）

[实测报告与复现步骤](./docs/production-tests/2026-10-07/REPORT.md) / 脱敏聚合证据（本地测试产物，不入库：`./docs/production-tests/2026-10-07/RESULTS.json`）：公网18/18、内部21/21、合成RAG8/8及本次新Langfuse记录通过；三角色API共54项，52通过、1失败（user分页total=0）、1跳过（无foreign学生档案夹具）。浏览器教师面板/学生问卷通过，学生默认dashboard失败；UI退出后旧令牌仍200，正式auth/logout后401；一次新明确授权Agent约20.3秒FAILED，当前窗口有Groq json_validate_failed/400和rate_limit_exceeded/429。完整冒烟未通过。

前提：沿用固定主机身份SSH、已有HTTPS域名、0600私有配置/示例密码，不输出令牌、数据或原始日志。认证测试会替换同账号Redis白名单，不与浏览器或Agent并发使用同一角色。完整模型调用只在新一轮具体示例数据/接收方授权后执行；现有单次生成脚本会拒绝重触发。本轮所有测试令牌均已正式注销，临时浏览器标签已关闭，原登录页保留。未重建镜像、改生产配置或改资源。

待实施修复及通过判据：

- UI注销接线：[Navbar](./frontend/src/components/Layout/Navbar.vue)等待[服务端logout](./frontend/src/api/auth.js)，再清本地状态；401的本地清理独立。验证浏览器回登录页且同一旧令牌业务401；注销失败有明确反馈。**修复待实施/待验证**。
- 学生落地：[路由](./frontend/src/router/index.js)/[登录action](./frontend/src/store/modules/user.js)按角色进入本人入口；学生登录不再加载全校接口，手工受限URL仍拒绝，教师/admin正常。**待实施/待验证**；不可开放全校数据规避403。
- user分页：[UserServiceImpl](./backend/user-service/src/main/java/com/edu/user/service/impl/UserServiceImpl.java)对应模块注册分页插件；验证非空列表total正确、每页不超size、两页无重复及密码仍无内容。本轮records=3/total=0，密码计数均0。**待实施/待验证**。
- Agent：先以无个人资料固定请求隔离JSON400，核对实测额度/队列/有界退避；新授权测试须工具成功、合法final_answer与最终产物落库。健康/提交200/失败trace均不算成功。**待实施/待验证**。

排错时同时读取HTTP和业务code；/auth/userInfo匿名为HTTP200/code500且data为空，业务网关匿名为HTTP401，内部/Caddy为HTTP403。用当前来源验证新RAG查询，不能用旧缓存/fallback/零向量当绿。跨学生档案测试须不同所有人的受控夹具；本轮全部4档案同一owner，因此跳过。真实质量、负载/SSE/导出、异地完整恢复、告警和历史凭据轮换未验，R-5.2/R-6不勾选。

本轮没有部署变更，无生产回滚动作。后续修复发布前留受影响组件的已知镜像/配置；失败回到旧版本并保留PVC，禁止删卷、降低授权或无限模型重试。其他代码/配置的历史回滚按其原记录。

## LANGFUSE-OTLP-20261007：Cloud v4 接入验证（2026-10-07）

当前状态取代下一节的账户准备阶段：本地 `docker/.env.kamatera` 已填日本区三项；实际 GET projects 200，OTLP HTTP/JSON v4 三条合成 span 接受200、v2查询验证父子关系及模型/用量。没有发送 input/output 或师生数据。私有聚合证据 `.local-run/kamatera/langfuse-otlp-verification-20261007.json` 为0600。原VM已安全同步三个供应商配置，九项内部凭据相等、未重启服务；旧配置仅保存在VM `.local-run/kamatera/config-backups/env.pre-langfuse-20261007`（0600）。

迁移代码已完成：[Java客户端](./backend/agent-service/src/main/java/com/edu/agent/config/LangfuseClient.java) 改 OTLP `/api/public/otel/v1/traces`、Basic、`x-langfuse-ingestion-version:4`，使用标准 ID/纳秒时间及显式映射；检查HTTP/partialSuccess，限时且fail-soft。[Python](./ai-inference-service/app/services/llm_client.py) 迁移新SDK并在 [应用lifespan](./ai-inference-service/app/main.py) 退出时 shutdown。`LANGFUSE_CAPTURE_CONTENT=false` 由Cloud/K3s映射，默认关闭I/O与自由文本元数据；合成协议探针不能代替应用SDK验收。

已通过：Java9项HTTP合约及相关调用方共20项，Python41项（实际v4 SDK内存exporter）与pip check，K3s13项及Cloud安全打包14项。两个受影响镜像重建导入、16私有服务Ready及Milvus初始化通过。三库合成RAG首次真实写入/检索、双MCP认证/list/call、Python真实生成均通过。Cloud v2查询实际 `llm.audit`、`llm.chat`、`agent.loop`，模型/usage/timing有效，全部I/O为空；Java仍为并列trace，Agent汇总不声明模型token。用户明确授权一次student1公开示例资料向Groq传输后，仅触发一次Agent：约20秒FAILED，ReAct第二轮终止TOOL_ERROR；服务器只读脱敏诊断出现HTTP400 `json_validate_failed` 和HTTP429 `rate_limit_exceeded`，不是Langfuse接入失败，也没有MCP调用失败证据。未自动重触发或调整供应商付费配置。R-5.1可观测验收通过，完整生成及R-5.2~R-5.4/R-6仍未通过。证据：本地 `.local-run/kamatera/langfuse-application-verification-20261007.json`；VM私有 `agent-diagnosis-20261007.json` 和 `k3s-ai-acceptance-20261007-agent.json`。

公网阶段已通过：`publish`创建frontend并受信HTTPS健康200，外部 `/login`200、匿名业务401、内部路径403；TLSv1.3，证书有效至2027-01-05 UTC。外部仅22/80/443可达，已测试的Kubernetes、数据库及服务端口关闭，证据 `.local-run/kamatera/public-verification-20261007.json`；三角色业务20项再次通过。来源严格核验通过：5表所有INSERT业务值、公开JSON规则迁移、其他字段/default、ID序列和行数一致，两答卷/题目表为空；Milvus四库全部内容与固定合成文档相等、1024维有限非零，success为空。证明当前内容一致，不声明历史从未导入其他数据。此前自动审批两次拒绝Agent外传，取得用户对具体示例资料及Groq目的地的明确授权后才执行一次。来源聚合证据为VM私有 `seed-lineage-proof-20261007.json`、`milvus-synthetic-proof-20261007.json`。

验收脚本排错：Java Long经Jackson序列化为字符串，脚本已按契约校准student/任务ID。重复测试随机doc ID与固定查询的1小时Redis缓存冲突，缓存返回上轮ID不能证明新写入失败；临时验收工具以固定测试doc、独立Milvus内容/向量核验及新查询验证真实检索，不清库、不删除未知资料。这不改变RAG运行架构，不能据此声明生产写入即时缓存失效。Agent排错须先分别复现供应商JSON约束与额度错误；`probe-models`基础成功不证明多轮JSON_OBJECT成功。后续生成修复和再次示例任务均待单独验证，不将TOOL_ERROR直接当作MCP故障。

回滚：关闭Langfuse两项key并重启受影响工作负载，保留模型凭据和PVC；恢复备份仅限外部服务字段，不盲目覆盖已有内部密钥。旧镜像保留为 `pre-langfuse-20261007` 本机回滚tag；Cloud legacy停用后不能靠回退v2恢复有效上报。上线失败使用 `stop-public`，保留所有私有数据卷；该操作不终止Kamatera计费。

## LANGFUSE-CLOUD-SETUP-20261007：Langfuse配置与兼容性（2026-10-07）

账户准备：推荐Langfuse Cloud Hobby、日本区域，建立专用项目，在Project Settings的API Keys生成一对Public/Secret Key。当前私有 `docker/.env.kamatera` 填 `LANGFUSE_HOST=https://jp.cloud.langfuse.com`、`LANGFUSE_PUBLIC_KEY` 与 `LANGFUSE_SECRET_KEY`；实际区域不同则使用该区域站点根URL。HOST不要包含 `/api/public/ingestion`，不要将Groq/Cloudflare key复用为Langfuse凭据，也不要给key加Bearer/Basic前缀。Secret Key私下填写；当前代码使用HOST，没有BASE_URL映射。

兼容性硬事实：[Java客户端](./backend/agent-service/src/main/java/com/edu/agent/config/LangfuseClient.java)及 [Python v2 SDK](./ai-inference-service/requirements.txt)使用旧ingestion；[官方说明](https://langfuse.com/docs/api-and-data-platform/features/public-api)Cloud于2026-11-16停用trace/observation旧接口。账户准备不等于长期接入完成；建议正式启用前迁移Java OTLP及Python新SDK/BASE_URL映射，保留异步、fail-soft和无凭据关闭。不能仅升级requirements版本而保留旧装饰器接口。

验收顺序（待执行）：正确区域API凭据认证 → 迁移代码的离线契约/业务回归 → 重建受影响镜像 → 经已验证SSH同步私有配置并部署AI服务 → 触发合成LLM/Agent任务 → Langfuse实际查到生成、模型/token/耗时及Agent记录。需检查SDK批次错误和flush，不能用“trace已启用”日志代替云端记录；当前Java记录并列而非完整父子trace，RAG/embedding/MCP没有独立覆盖。首次只用合成文本，真实资料先处理prompt/output/工具汇总脱敏。

验证状态：仅本地代码/编排及官方文档核查通过；本次未获取Langfuse项目凭据，不做真实云认证、trace或迁移测试。回滚迁移时只恢复上版可观测实现并关闭Langfuse两项key，保留LLM/embedding和数据卷；在Cloud旧接口停用后不把回到v2当作有效trace恢复方案。完整步骤与公开参数见 [Kamatera手册 §3.2](./docs/deployment/KAMATERA_DEPLOYMENT.md#32-langfuse-cloud配置langfuse-cloud-setup-20261007)。

## GROQ-PROBE-UA-20261007：剩余配置核对与供应商探针修正（2026-10-07）

实查：本地模型 key 已配置且真实认证通过，Groq 返回 JSON 对象、Cloudflare 返回中文合成输入的有限非零1024维向量；VM 的 `LLM_API_KEY`/`EMBEDDING_API_KEY` 仍为占位值，因此 VM 完整配置门拒绝。非密钥参数、Account ID 和域名已齐；12项私有 Deployment Ready，data HPA 当前1副本、指标正常且上限2。frontend/AI/MCP尚未部署，未监听公网80/443。

必需剩余输入仅为安全同步现有两个模型 key 到 VM，无须重新注册模型账户或再次索要密钥；同步前核对目标身份和既有内部凭据，值不进入聊天/日志。其余为执行步骤：真实 VM probe、`apply-private`、`init-rag`、导入合成演示知识与检索/双MCP/Agent验证、`publish` 和受信HTTPS验收。`LANGFUSE_PUBLIC_KEY`/`LANGFUSE_SECRET_KEY`/`LANGFUSE_HOST` 当前缺失，属于后续R-5 trace/R-6完整验收要求，不是基础网站启动硬门；不新增Nacos、GPU、reranker或收费服务器。

同次入口核对：域名 A 解析为45.130.165.168；firewalld默认public、服务仅cockpit/dhcpv6-client/ssh、未声明http/https且无额外端口。发布前须准备public区HTTP/HTTPS服务规则并外部实测80/443和证书；不能只凭主机INPUT列表推断K3s hostPort转发结果。本次未改防火墙。Cloudflare key字段只填原始token，现有EmbeddingClient及部署探针会自动加`Authorization: Bearer`；`-H`只是curl参数，不属于env配置。

默认 urllib 客户端在当前 Groq 样本被边缘错误1010/403拦截。修正 [provider_request](./scripts/k3s-deploy.py) 发送明确 User-Agent 后，修正后的本地真实探针通过；`/private/tmp/resource-profile-oci-venv/bin/python scripts/test-k3s-deploy.py` 13项通过。VM脚本已校验并同步，SHA-256 `420f546247d382bdb0cbb807f3b83a55cd70d10433012162d2c33b88b4be3379`；未同步模型 key、修改Pod/PVC或发布公网。旧源码包仍是其制作时快照，后续打包须包含本次脚本修正。

复测命令：`python3 scripts/k3s-deploy.py probe-models --env-file docker/.env.kamatera`；仅合成文本、输出通用成功状态，不暴露模型回复或凭据。若401/403持续，分别核查供应商认证、组织/模型权限及客户端请求特征；不因这次边缘拦截假设所有403都相同。回滚运维脚本可使用VM私有 `.local-run/kamatera/deploy-scripts/k3s-deploy.pre-UA-20261007.py`，保留真实凭据、TLS和数据卷；原默认标识可能再次触发已复现拦截。

## GROQ-CLOUDFLARE-DEPLOY-20261006：免费模型配置准备（2026-10-06）

聊天沿用 Groq GPT-OSS-120B，embedding 采用 Cloudflare BGE-M3；完整公共参数见 [Kamatera 手册 §3.1](./docs/deployment/KAMATERA_DEPLOYMENT.md#31-免费模型参数groq-cloudflare-deploy-20261006)。在忽略的 `docker/.env.kamatera`（0600）私下填写 Groq key、Cloudflare Account ID 与 Workers AI token，不将值发送到聊天或写入跟踪文件。Cloudflare 使用独立 `EMBEDDING_API_KEY`，不能留空后误复用 Groq key。

本次只核查官方额度、现有代码配置和本地非秘密参数；未调用真实供应商、导入知识或上线公网。缺 key/Account ID 时配置门应拒绝；补齐后先运行 `python3 scripts/k3s-deploy.py check`，以已验证 SSH 上传私有 env，远端执行 `probe-models`、`apply-private`、`init-rag` 和合成知识写入/检索验收，再按既有 `publish` 门发布。通过判据为真实聊天内容有效、向量为非零有限 1024 维、中文写入/检索成功、双 MCP/AgentLoop/鉴权通过；健康探针或 embedding 失败回退零向量不算通过。上述真实验收均待验证，R-5/R-6 不变。

同日实际操作：用户确认组合后，公开参数已写入本地 `docker/.env.kamatera` 和 VM `/opt/resource-profile/docker/.env.kamatera`；两侧均复读校验 0600、所有 API Key/内部凭据保持不变，未重启服务。URL 内 `change-me-account-id` 仍待真实 Account ID。以合成凭据运行 `validate_env` 并解析 K3s Secret 环境映射，Java agent 与两个 Python 服务的生成/向量配置通过；真实占位配置门仍正确拒绝。Java JSON_OBJECT/low 有效，Python 未实现这两项，仍依靠提示词/解析；清单固定 temperature 0.3、timeout 120 秒，私有文件记录相同默认值。未进行模型请求或重建镜像，无新增运行时代码变更。

2026-10-07 / GROQ-CLOUDFLARE-DEPLOY-20261006 补充：真实 Account ID 已写入两侧 embedding URL，逐项对比证明其他字段未变。本地 `python3 scripts/k3s-deploy.py check --env-file docker/.env.kamatera` 实际通过（静态解析16私有服务及有界HPA，不调用模型）；本地两个模型 key 已设置，VM 两项仍为占位值。真实 API 认证、向量及业务验收待完成。回滚只恢复 embedding URL 到上一有效配置；此次未修改服务/PVC、无须重启现有业务。手册模板继续使用占位 ID，真实账号标识保留于私有配置。

排错：401/403 核查对应供应商的 key/权限/Account ID；429 核查组织共享额度及有界退避，不擅自升级付费。旧知识如果来自其他 embedding 模型，应单独重嵌入，不能因同为 1024 维混用。回滚恢复上一版有效私有模型参数并重启受影响 AI/MCP 工作负载；没有上一版有效配置时保留现有私网业务阶段，不启公网。保留 PVC，不在模型接入失败时删除数据。

## KAMATERA-DEPLOY-20261006 / K3S-HPA-DEPLOY-20261006：Kamatera 单节点部署（2026-10-06）

完整操作见 [Kamatera 部署手册](./docs/deployment/KAMATERA_DEPLOYMENT.md) 与 [K3s 运行说明](./k8s/README.md)。用户已完成注册、US$10 押金与 VM 创建：新加坡 `resource-profile-sg`，`45.130.165.168`，Rocky Linux 9.8，Type A 4 核 / 24 GB / 100 GB。控制台月付 US$92、1000 GB 流量、超额 US$0.01/GB，无每日备份/托管/额外磁盘；30 天试用不是长期免费，精确到期日期仍需账单核对，关机不等于终止计费。

SSH 专用私钥位于被忽略的 `.local-run/kamatera/ssh/id_ed25519`（0600），只上传公钥。先收集候选 host key，再通过已授权 SSH 仅写公开 nonce；控制台截图同时显示该 nonce 与完整本机 fingerprint 后，才固定到专用 known_hosts 并传源码/新秘密。不得用盲目 ssh-keyscan、StrictHostKeyChecking=no 或上传私钥绕过。Rocky 控制台写入需 O_NOCTTY，普通 shell 重定向到 /dev/tty1 可能 EIO。后续 `/usr/bin/ssh -F .local-run/kamatera/ssh/client.conf kamatera-resource-profile` 使用已固定身份；不输出密码、节点 token 或 kubeconfig。

正式运行路径已按用户选择改为 [`scripts/k3s-deploy.py`](./scripts/k3s-deploy.py)，Docker 只构建镜像，再导入 K3s 的 containerd k8s.io namespace。备用 [`scripts/kamatera-deploy.py`](./scripts/kamatera-deploy.py)/Compose 不与 K3s 同时启动。K3s 固定官方 stable 版本 v1.36.5+k3s1，下载脚本先保存审查并由安装器核对二进制 SHA-256；保持 SELinux Enforcing，启用官方 k3s-selinux 与 Secret 静态加密，禁用 Traefik/ServiceLB。firewalld 信任默认 Pod 10.42.0.0/16、Service 10.43.0.0/16，公网不得开放 6443/8472/10250；控制面可在节点地址监听以支持集群内部 Service。

K3s 清单通过 Secret 引用真实环境变量，Milvus MinIO 配置也是 Secret 文件0600；私有生成 JSON 只写忽略路径0600。SQL初始化目录、静态字体只读 hostPath 用精确源路径 semanage fcontext/restorecon 持久标记 container_file_t:s0；K3s 不会执行 Compose :Z。权限错误查标签、k3s/containerd 配置与 AVC，不用 setenforce0、privileged 或删除卷规避。数据使用8个local-path PVC，共申请46GiB，失败停前端保留卷。

命令和阶段：独立构建/导入镜像 → check/render → apply-private → bootstrap-seeds → init-rag → 合成知识导入及真实检索 → probe-models → publish → 登录、鉴权/注销、SSE、中文PDF、HPA/备份恢复验收。域名和模型 API 尚未填写时，仅完成不依赖这些输入的集群/镜像/基础设施准备；不使用假 API key 绕过全栈门。发布要求真实 bcrypt 改密、DNS到VM、实际生成与 embedding 维度、私有 Pod Ready、受信任HTTPS。init-rag 建空集合不等于真实RAG。首轮 data-service HPA requests100m/目标70%/1–2副本、缩容窗口300秒；验证 metrics.k8s.io 和实际CPU负载→2副本→回缩1，固定副本测试不能代替HPA验收。其余单例采用Recreate，升级会中断。

已审查上传的构建源码包 `.local-run/kamatera/source-rocky-reviewed.tar.gz`：348文件、298,604字节、SHA-256 `9c1bab75cfba2b5f4a07e9de5b991f24790bb4198e01196fec473553f4dfd285`，0600，无Git历史/秘密/业务数据。与前次独立构建包仅Compose/部署工具不同，其余346文件一致。新完整包 `.local-run/kamatera/source-k3s-reviewed.tar.gz` 含350文件、307,631字节，SHA-256 `90cdfcd9675ed4d5ba4fd8883ba197c660977020e59dade53c735bebe1dc641c`；只增加K3s清单/脚本并更新cloud打包白名单，347个旧文件一致。完整包已通过固定身份SSH上传并在VM核对摘要后解压。私有env、seed与官方静态TTF/OFL分别传输，历史.env.audit绝不读取/上传。初始仅合成数据，不迁移真实师生或心理记录。

知识灌库404修复：[`app.main:create_app`](./ai-inference-service/app/main.py) 必须注册 rag_upsert.router。Python最小环境32项通过，实际工厂覆盖正常mock写入、401/503/422；可选SDK替身不是实际MCP或模型证据。真实验收由私网新管理token调用upsert并检索，token通过私有stdin/配置，不能置于命令行/日志。

已执行：注册/押金/控制台报价/VM创建、主机跨通道身份验证、安全源码包传输和一致性、真实Rocky Enforcing、Docker29.8.2/Compose5.6.0、Docker SELinux与hello-world、firewalld启用。此前本地Python32项/部署14项、独立JDK21后端与前端构建、Caddy校验、字体中文PDF及PNG检查通过；K3s13项离线边界测试通过。Linux 11应用镜像构建并导入K3s成功，Node Ready、CoreDNS/metrics-server/local-path就绪、Secret加密与0600 kubeconfig、五基础设施PVC及默认账号bcrypt改密通过；12项私有服务Ready，真实三角色登录/权限/退出撤销20项通过，HPA受控CPU负载自动1→2通过、自动回缩1已验。MySQL合成快照已在新建隔离数据库还原，比对20表行数及种子hash通过，备份0600，测试库清理；完整应用/向量/跨主机恢复未验。待验证：完整AI/RAG/MCP、HTTPS、完整应用/向量备份恢复。R-5/R-6与历史凭据轮换B-2状态不变。

## OCI-DEPLOY-20261006：OCI 注册与完整内网栈（2026-10-06，平台方案已被 Kamatera 取代）

详细操作及回滚见 [OCI 部署手册](./docs/deployment/OCI_DEPLOYMENT.md)。账号所有者在 Safari 官方注册页面完成个人信息、邮箱验证、密码设置、付款验证和条款确认；之后核对 Home Region、实际 A1 免费额度、可用容量及费用边界。域名、模型接口和新建/迁移数据方式尚未确定时，不执行公网发布。当前本地已经生成被忽略的 `docker/.env.oci` 与 `.local-run/oci/seed-credentials.json`（0600）；禁止打印、提交或使用历史 `.env.audit` 凭据。

在满足容量/费用条件的 Linux OCI 主机上：填私有配置 → `python3 scripts/oci-deploy.py check` → `build` → `start-private` → 使用安装了 `scripts/requirements-oci.txt` 的 Python 执行 `bootstrap-seeds` → `init-rag` → 私网业务与模型验证 → `publish` → 受信任 HTTPS、登录/角色、SSE、双 MCP 和真实 RAG 验收。完整命令、DNS、模型 URL/维度与私有传输前提见部署手册；启动至少 20 GiB，不能直接套用纯免费 12 GB VM。

通过判据：17 个容器稳定（发布前 16 个）、只有 frontend 发布 80/443、默认账号密码全部替换、HTTPS 无警告、未登录业务 401/内部路径 403、真实 AI 输出和检索/持久化可核验、备份恢复演练通过。`init-rag` 创建空集合、Docker healthy 或 mock 测试不能代替真实模型/RAG 验收。失败先停 frontend、保留卷；回滚先前已验证版本，禁止 `down -v`。

已执行：本地 Python 30 项、部署安全 7 项、合成配置 Compose 解析及端口/profile/DNS/17.25 GiB 内存断言、私有文件 0600/Git 忽略检查、前端构建、Caddy validate/adapt。待验证：完整 ARM 镜像构建、容器启动、Oracle 账号/VM、实际证书、模型/embedding、知识灌库、备份恢复、R-5/R-6；本机 Docker 启动未成功。此次未迁移业务数据、未创建收费资源、未完成历史凭据轮换。

## 1. 前置条件

| 工具 | 要求 | 说明 |
|---|---|---|
| JDK | **21，且只能是 21.x** | Maven Enforcer 要求 `[21,22)`（ADR-028） |
| Maven | 3.x | 后端多模块构建 |
| Node.js | 22（CI 基线） | 前端 Vite 8 构建 |
| npm | 与 Node 22 配套 | 必须优先使用 `npm ci` |
| Python | 3.10 | AI 服务与测试基线 |
| Docker Compose | Docker Compose v2 | MySQL/Redis/Nacos/Milvus/AI/生产覆盖层 |
| 其他 | `curl`、`jq`；MCP smoke 需 bash ≥4 | 健康检查与自动验收 |
| 可选模型 | OpenAI 兼容 :8091/:8092/:8093 | 生成、embedding、reranker |

先确认版本：

```bash
java -version
mvn -version
node --version
npm --version
python3 --version
docker compose version
```

## 2. 端口速查

| 端口 | 服务 | 端口 | 服务 |
|---:|---|---:|---|
| 5173 | 前端开发服务 | 8080 | gateway |
| 8081-8086 | auth/user/teacher/student/mental/data | 8087 | agent-service |
| 8090 | ai-inference-service | 8091 | 生成 LLM（宿主） |
| 8092 | embedding（宿主） | 8093 | reranker（宿主） |
| 8094 | student-data MCP | 8095 | knowledge-rag MCP |
| 3306 | MySQL | 6379 | Redis |
| 8848/9848 | Nacos | 19530/9091 | Milvus |
| 8000 | Attu | 3000 | Grafana |
| 3001 | Langfuse | 9090 | Prometheus |
| 80/443 | 生产 nginx | — | — |

注意：Langfuse 映射到宿主 3001；Grafana 映射到宿主 3000，二者可同时运行。

## 3. 首次准备

### 3.1 获取依赖并验证构建

```bash
cd backend
mvn -B -ntp clean install -DskipTests

cd ../frontend
npm ci

cd ../ai-inference-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 3.2 开发数据

MySQL 容器首次创建数据卷时会按文件名顺序执行 `sql/init/`。后续修改初始化 SQL 不会自动重放；需要对现有库显式执行新增迁移，或在确认可丢弃本地数据后重建卷。

默认开发账号为 `admin/admin`、`teacher/teacher`、`student/student`。这些账号只用于本地；生产必须执行 `sql/prod/01_rotate_default_passwords.sql`。

## 4. 启动开发环境

### 4.1 重要现状

`docker/docker-compose.yml` **不是完整业务全栈**：它没有 auth/user/teacher/student/mental/data 六个 service 定义。推荐开发方式是 Docker 启基础设施和 Python RAG，Java 业务服务在宿主分别运行。

### 4.2 启动基础设施与 Python 服务

在仓库根目录执行：

```bash
cd docker
docker compose up -d mysql redis nacos etcd minio milvus-standalone attu ai-inference-service knowledge-rag-mcp
docker compose ps
```

若只开发普通 CRUD，可省略 Milvus、Attu、AI 和 knowledge-rag：

```bash
cd docker
docker compose up -d mysql redis nacos
```

### 4.3 启动本地模型

AgentLoop 最少需要生成 LLM：

```bash
curl -fsS http://localhost:8091/v1/models
```

完整 RAG 还需要：

```bash
bash scripts/start-embedding-server.sh
bash scripts/start-reranker-server.sh
curl -fsS http://localhost:8092/v1/models
```

仓库不包含生成 LLM 权重或统一启动脚本；请用本机 llama.cpp/vLLM 在 :8091 提供 OpenAI 兼容 API。

### 4.4 启动 Java 服务

先构建一次，以便各模块解析 `common`：

```bash
cd backend
mvn -B -ntp install -DskipTests
```

然后在独立终端按需运行。普通业务链至少启动 gateway、auth 及目标领域服务；Agent 全链还要启动 student、mental、data、mcp-student-data、agent-service。

每个 Java 终端先设置同一个开发 JWT 密钥与内部调用凭证。JWT 密钥缺失或少于 32 字节时 `JwtUtil` 会让服务 fail-fast；内部调用凭证缺失时服务照常启动，但 student/mental/data 等会以 401 拒绝 agent-service / mcp-student-data 的 Feign 取数（不带 JWT 的请求一律不当内网，INTERNAL-AUTH-20260914）。两个值都必须与 docker-compose 中 agent-service / mcp-student-data 的一致：

```bash
export JWT_SECRET=edu-portrait-dev-jwt-secret-change-in-prod-0123456789
export EDUCARE_INTERNAL_TOKEN=edu-portrait-dev-internal-token-change-in-prod-0123456789
```

```bash
cd backend
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl teacher-service spring-boot:run
mvn -pl student-service spring-boot:run
mvn -pl mental-service spring-boot:run
mvn -pl data-service spring-boot:run
mvn -pl mcp-student-data spring-boot:run
mvn -pl agent-service spring-boot:run
mvn -pl gateway spring-boot:run
```

上面每一行应在不同终端运行。agent-service 默认会在启动时连接 :8094/:8095 并拉取工具列表；两个 MCP 未就绪时它可能启动失败。

### 4.5 启动前端

```bash
cd frontend
npm run dev
```

访问 `http://localhost:5173`。Vite 把 `/api` 代理到 `http://localhost:8080` 并移除 `/api` 前缀。

### 4.6 基础健康检查

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8087/actuator/health
curl -fsS http://localhost:8090/health
curl -fsS http://localhost:8094/actuator/health
curl -fsS http://localhost:8091/v1/models
```

8095 当前用 TCP healthcheck；可用 `bash scripts/mcp_smoke_test.sh` 验证协议和工具。

## 5. 测试与质量门

### 5.1 后端

```bash
cd backend
mvn -B -ntp clean test
```

该命令同时生成各模块 `target/site/jacoco/`，并执行父 POM 中安全关键类行覆盖率 ≥80% 的定向门。

定向测试示例：

```bash
cd backend
mvn -pl agent-service test -Dtest=AgentLoopNativeTest
mvn -pl gateway test -Dtest=JwtAuthGlobalFilterTest
mvn -pl mcp-student-data test -Dtest=StudentDataToolsContractTest
```

### 5.2 Python

CI 使用最小、无外部服务测试依赖：

```bash
cd ai-inference-service
python3 -m venv .venv-test
source .venv-test/bin/activate
pip install -r tests/requirements.txt
python -m unittest discover -s tests -p "test_*.py" -v
```

### 5.3 前端

```bash
cd frontend
npm ci
npm run lint:check
npm run build
npm run size:check
npm audit --omit=dev --audit-level=high
```

`npm run lint` 带 `--fix` 会改文件；CI/检查场景使用只读的 `lint:check`。

### 5.4 Eval 与生产前置脚本

```bash
python3 eval/run_eval.py --validate-only --input eval/risk_assessment.jsonl
bash scripts/test-preflight-prod.sh
```

真模型风险 eval 只有在推理端点可达时才执行；目标等级一致率是 0.85，不能用 `--validate-only` 结果代替模型质量证据。

## 6. 功能验收

### 6.1 登录与网关安全门

要求 gateway、auth、student、Redis 可用：

```bash
bash scripts/gateway_verify.sh
```

通过判据：公开登录、无 token 401、有效 token 200、无效 token 401、`/_internal/` 403、登出后旧 token 401 共 6 项全部通过。

字段权限和 IDOR 另按 [`docs/educare/FIELD_PERMISSION_VERIFY.md`](./docs/educare/FIELD_PERMISSION_VERIFY.md) 用多角色账号验证。

下游服务公网可达的部署（如 Render），还要验证「直连下游必须带凭证」（INTERNAL-AUTH-20260914）。只探聚合端点，不要拿个人数据端点做探测：

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://edu-portrait-data.onrender.com/data/dashboard/statistics
```

通过判据：返回 `401`（修复前为 `200`）；同一路径经网关带教职工 token 仍为 `200`；触发一次 AgentLoop 任务后，student/mental/data 日志无 401，任务没有因取数失败进入 `FAILED`。

### 6.2 MCP 工具契约

```bash
bash scripts/mcp_smoke_test.sh
```

通过判据：student-data 4 个工具和 knowledge-rag 3 个工具全部完成 initialize/list/call，脚本退出码为 0。当前脚本不会附 `X-MCP-Token`，只适用于 token 为空的隔离开发环境；生产打开 `EDUCARE_MCP_TOKEN` 后，应由带相同 token 的 agent-service 发起工具调用，并以 AgentLoop 真跑验证两端互验。

### 6.3 AgentLoop 真跑

先登录：

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.token')

TASK=$(curl -sS -X POST http://localhost:8080/agent/api/v1/task/trigger/1 \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data')

curl -sS http://localhost:8080/agent/api/v1/task/$TASK \
  -H "Authorization: Bearer $TOKEN" | jq '.data | {id,status,riskLevel,riskAnalysisResult,interventionPlan,complianceAudit}'
```

持续轮询直到终态。通过判据：

1. 状态是 `COMPLETED` 或合规拒绝的 `REJECTED`，不得是 `FAILED`。
2. `riskAnalysisResult` 与 `interventionPlan` 都是非空合法 JSON。
3. 日志显示真实 MCP 工具名被调用，且没有 `TOOL_DENIED`/重复 parse error。
4. 中高风险任务存在 `complianceAudit`；审核服务异常必须进入人工审核/REJECTED。
5. 配置 Langfuse 后，UI/API 中同时存在 `agent.loop` 和 `llm.chat` trace。

真实模型完整证据采集以 [`docs/educare/E2E_RUNBOOK.md`](./docs/educare/E2E_RUNBOOK.md) 和执行计划 R-5 为准。当前 `scripts/local_real_run.sh` 是历史桩 LLM 脚本，未覆盖真实 MCP/模型/鉴权全链，不能作为上线证据。

### 6.4 RAG 灌库与检索

当前主 FastAPI app 没有注册 `rag_upsert.router`，所以不要用 `/api/v1/rag/upsert` 做运行验收。先使用仓库脚本灌种子知识：

```bash
cd ai-inference-service
MILVUS_HOST=localhost python -m scripts.init_milvus
MILVUS_HOST=localhost EMBEDDING_BASE_URL=http://localhost:8092/v1 python -m scripts.seed_knowledge
```

`rag_upsert.py` 的实现和隔离测试可参考 [`docs/educare/RAG_UPSERT_DESIGN.md`](./docs/educare/RAG_UPSERT_DESIGN.md)，但只有主应用实际挂载 router、配置 `EDUCARE_ADMIN_TOKEN` 并通过 HTTP 验收后，才能把 `/api/v1/rag/upsert` 记为可用。RAG 质量通过判据是使用真 BGE 向量的质量集达到已固化 dense baseline；仅“返回非空”只能证明机械链路。

## 7. 常见排错

| 症状 | 检查 | 处理 |
|---|---|---|
| Maven 在 validate 直接失败 | `java -version`、`mvn -version` | 把 `JAVA_HOME` 切到 JDK 21；不要跳过 Enforcer |
| gateway 返回 503 | Redis 日志、`token:{userId}` | gateway 会话校验 fail-closed；恢复 Redis 与正确 `REDIS_PASSWORD` |
| 有 JWT 仍 401 | JWT secret、Redis 当前 token、是否重新登录 | gateway/auth/agent 必须使用同一 `JWT_SECRET`；重新登录取得当前 token |
| gateway 返回 503/404 service unavailable | Nacos readiness 和服务列表 | 确认目标服务已启动并注册；基础 compose 不含六个普通业务服务 |
| agent-service 启动失败 | :8094/:8095 health、MCP initialize 日志 | 先启动两个 MCP；核对 URL、endpoint `/mcp` 和 token |
| MCP 返回 401 | 三端 `EDUCARE_MCP_TOKEN` | 使用相同非空值；生产至少 32 字符 |
| AI 取数失败，Feign 调 student/mental/data 报 401 | 各服务 `EDUCARE_INTERNAL_TOKEN`；启动日志是否有「EDUCARE_INTERNAL_TOKEN 未配置」WARN | agent-service、mcp-student-data 与 student/mental/data 使用同一非空值，生产至少 32 字符；不要为排障关掉 `educare.service-auth.enabled` |
| Agent 任务 `FAILED` | agent 日志、`agent_task.status`、LLM 原始输出 | 先区分 LLM 连接、JSON parse、ToolGuard、工具调用和 DB CAS；不要直接改成 COMPLETED |
| RAG 返回空 chunks | :8092、Milvus collection、embedding dim、灌库记录 | 确认维度 1024、集合存在、真语料已 upsert；reranker 可临时关闭定位 |
| `/api/v1/rag/upsert` 返回 404 | FastAPI OpenAPI、`app/main.py` router 注册 | 当前主 app 未挂载 `rag_upsert.router`，不能当作运行能力 |
| `/api/v1/rag/upsert` 返回 503 | `EDUCARE_ADMIN_TOKEN` | 仅在 router 已挂载后适用；端点默认 fail-closed |
| 看不到 Langfuse trace | profile、project keys、`LANGFUSE_HOST` | 容器内用 `http://langfuse-server:3000`，宿主用 `http://localhost:3001`；key 为空时是预期 no-op |
| 前端请求 401 后跳登录 | 浏览器 token 与 gateway 会话 | 检查是否登出/重登导致旧 token 被覆盖；清理前端本地 token 后重登 |
| PDF 中文方块 | `EDUCARE_FONT_PATH` | 提供可读的 Noto Sans SC 字体并重启 agent-service |
| 端口占用 | `lsof -nP -iTCP:<port> -sTCP:LISTEN` | 停止冲突进程或显式调整服务端口和所有调用方 URL |

日志命令：

```bash
cd docker
docker compose logs --tail=200 -f gateway agent-service mcp-student-data knowledge-rag-mcp ai-inference-service
```

不要在排错时使用 `docker compose down -v`，它会删除 MySQL/Redis 数据卷。

## 8. 生产发布

### 8.1 发布前硬门

1. `docs/educare/EXECUTION_PLAN.md` R-5/R-6 的对应证据已经实际完成；未完成时不得宣称生产就绪。
2. Java、Python、前端、eval 与 preflight 测试全绿。
3. 真 Qwen AgentLoop、真 BGE dense baseline、Langfuse trace 已验收。
4. auth/user/teacher/student/mental/data 六个业务服务已有明确部署目标；当前基础 compose 不包含它们。
5. MySQL 备份与恢复、TLS、网关安全、字段权限、IDOR、MCP token、监控告警都已实跑。

### 8.2 密钥与证书

```bash
cp docker/.env.example docker/.env
# 编辑 docker/.env，替换全部 change-me；不要提交该文件
bash scripts/preflight-prod.sh
```

演练或审计用的临时 env 放在仓库外（如 `mktemp` 生成的路径），用 `ENV_FILE=/path bash scripts/preflight-prod.sh` 体检，不要在仓库里另存副本。`docker/.env.*`（模板除外）已被忽略；preflight 会拒绝被 git 跟踪的 env 文件，一旦入库，其中的值按泄露处理并全部轮换（见 `ENV-AUDIT-LEAK-20260914`）。

把 `fullchain.pem` 和 `privkey.pem` 放入 `docker/certs/`。新库初始化后必须轮换 admin/teacher/student 默认密码。

### 8.3 生成与检查部署配置

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml config
```

检查生成配置中所有数据/内部端口只绑定 `127.0.0.1`，确认 nginx 是唯一公网入口。只有在六个普通业务服务已经由外部部署提供或 compose 已补齐后，才执行完整发布：

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

可选观测栈：

```bash
cd docker
docker compose -f docker-compose.yml --profile langfuse up -d langfuse-postgres langfuse-server
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring up -d prometheus grafana
```

### 8.4 发布后验证

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

cd ..
GATEWAY=https://<domain>/api ADMIN_USER=admin ADMIN_PASS='<password>' bash scripts/gateway_verify.sh
```

随后真跑一条中高风险 Agent 任务，以此验证生产 MCP token 互验，并检查 MySQL 产物、Langfuse trace、Prometheus 指标和 Grafana 告警。运行 `scripts/backup-mysql.sh`，并在隔离环境用 `scripts/restore-mysql.sh` 做恢复演练。

## 9. 回滚

- 应用：保留上一版镜像 tag/构建产物和 compose 配置，恢复上一版本后 `docker compose up -d`；不要删除数据卷。
- 配置：恢复经版本管理/变更记录确认的 Nacos 与 `.env` 配置，并重启受影响服务。密钥回滚必须考虑已签发 JWT/MCP 会话失效。
- 数据库：初始化脚本以增量、向后兼容为原则。含破坏性 DDL 的发布必须先准备显式 down migration 或从已验证备份恢复；不得把 `down -v` 当回滚。
- AI：可临时将 `EDUCARE_AGENT_LOOP_ENABLED=false` 回落 legacy，但前提是 Python :8090、LLM 与 RAG 依赖可用；回落后仍须跑安全与结果验收。
- RAG：错误向量可按 doc_id 重灌；回滚 embedding 模型时必须同时匹配维度和重建对应集合/基线。

### RENDER-DEPLOY-20260901：Render Blueprint 启动与数据层排错

- 复现：在 Render Blueprint `exs-da9vgj942hec7392v2vg` 查看 MCP deploy
  `dep-daaie3ffdruc73ajt8pg`，可见 Tomcat 初始化后 Feign 报“URL not provided”并最终端口扫描超时；
  Agent deploy `dep-daaiic5g1s2s73d9t920` 在 `McpSyncClient.initialize` 失败退出。对网关发送一组明确
  不存在的合成账号，业务响应包含 `CannotGetJdbcConnectionException`，auth 日志底层为 MySQL
  `Connect timed out`。
- 修复后验证：

  ```bash
  cd backend
  mvn -B -ntp -pl mcp-student-data,agent-service -am test

  cd ../frontend
  npm run lint:check
  npm run build
  ```

  Blueprint 同步后依次检查：

  1. `edu-portrait-mcp-student` 日志包含 `Tomcat started`，`GET /actuator/health` 返回 200/UP。
  2. `edu-portrait-ai-inference` 的 `GET /health` 返回 200，带正确 `X-MCP-Token` 的 `/mcp`
     initialize/tools/list 成功。
  3. `edu-portrait-agent` 在 120 秒窗口内完成两个 MCP initialize 并出现 `Started AgentServiceApplication`。
  4. 数据库必须是可接收 MySQL TCP 的 Private Service/外部托管实例；不得再使用普通 Web Service
     的 `.onrender.com:3306`。新库存在 `01`~`05` 全部脚本创建的表。
  5. 使用部署方授权的测试账号经 gateway 登录，验证 Redis 会话写入、`/user/info`、一条 Agent 任务
     与登出撤销。不得在未确认时向线上提交密码。
- 排错：先看依赖服务是否仍在冷启动，再核对 `STUDENT_SERVICE_URL`、`MENTAL_SERVICE_URL`、
  `DATA_SERVICE_URL`、`AI_INFERENCE_URL`、两个 MCP URL 与共享 token；若登录返回 JDBC 错误，优先
  修正 MySQL 服务类型/主机，不要增加 socket timeout 掩盖不可路由的地址。
- 回滚：应用代码可回退到 Render 上一 deploy；数据库服务类型和持久盘不在未备份时回退/删除。
  若新持久数据库尚未承载写入，可将服务环境变量切回已验证的外部数据库。MCP 120 秒窗口可恢复默认
  20 秒，但免费实例冷启动时 Agent 将再次 fail-fast。
- 验证状态：线上根因已验证；2026-09-01 本地 JDK 17 定向 Reactor 测试 125 例全绿，前端
  `lint:check` 0 error（1 条既有 Prettier warning）且生产构建通过，`render.yaml` YAML 解析与
  `git diff --check` 通过。修复后 Blueprint 同步、持久数据层与端到端登录仍待验证。

### AIVEN-RENDER-20260901：Aiven Free MySQL + 无 LLM 的 Render 部署

- 前提：登录 Aiven Console 与 Render Blueprint `exs-da9vgj942hec7392v2vg`；本机安装 MySQL 8
  client。Aiven Free MySQL 处于 `Running`，Overview/Quick connect 中可读取 host、port、user、
  password；不要把这些值提交到 Git。
- 初始化数据库：先执行会创建 `edu_portrait` 的 `01`，再以该库为默认库顺序执行 `02`~`05`。

  ```bash
  export MYSQL_HOST='<aiven-host>' MYSQL_PORT='<aiven-port>' MYSQL_USER='avnadmin'
  export MYSQL_PWD='<aiven-password>'

  mysql --ssl-mode=REQUIRED --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" < sql/init/01_init.sql

  for sql_file in sql/init/02_questionnaire_extension.sql \
    sql/init/03_agent_init.sql sql/init/04_student_extras.sql \
    sql/init/05_intervention_feedback.sql; do
    mysql --ssl-mode=REQUIRED --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
      --user="$MYSQL_USER" --database=edu_portrait < "$sql_file"
  done

  unset MYSQL_PWD
  ```

- Render 配置：在 `edu-portrait-common-env` 手工设置 `MYSQL_HOST`、`MYSQL_PORT`、
  `MYSQL_DATABASE=edu_portrait`、`MYSQL_USER=avnadmin`、`MYSQL_PASSWORD`。Blueprint 文件只声明
  `MYSQL_SSL_MODE=REQUIRED` 与连接池上限，不声明凭据。同步 Blueprint 后确认新建
  `edu-portrait-kv`，Gateway/Auth/Agent 的 `SPRING_DATA_REDIS_URL` 均引用其私网
  `connectionString`。
- 无 LLM 判据：`SPRING_AI_MCP_CLIENT_ENABLED=false`、`EDUCARE_AGENT_LOOP_ENABLED=false`、
  `VITE_AI_ENABLED=false`；不填写 `LLM_API_KEY`。前端侧边栏不出现“AI 预警”和“LLM 追踪”，核心
  业务路由仍可使用。
- 线上验证：

  1. Aiven 执行 `SELECT COUNT(*) FROM edu_portrait.sys_user;` 返回至少 3。
  2. `GET https://edu-portrait-gateway.onrender.com/actuator/health` 返回 200/UP。
  3. 使用 `admin/admin` 经前端登录，`/user/info` 成功；登出后旧 token 再请求返回 401。
  4. 管理员数据面板、教师列表、学生列表、心理概览至少各打开一次且无 JDBC/Redis 错误。
  5. Render 日志中 JDBC URL 使用 Aiven host，且没有 MCP initialize 阻止 Agent 启动。
- 排错：TLS 错误先核对 `MYSQL_SSL_MODE=REQUIRED` 与 Aiven service 状态；`Too many connections`
  核对每服务 `DB_MAXIMUM_POOL_SIZE=3`；登录成功但后续 401 时检查三处
  `SPRING_DATA_REDIS_URL` 是否指向同一个 Key Value；SQL 导入失败时从首个失败文件停止，修正后只
  重跑该文件及后续脚本。
- 回滚：应用可回退 Render 上一 deploy；数据库凭据可切回另一个已验证的外部 MySQL。不要回退到
  普通 Web Service MySQL/Redis。Aiven 已承载写入后，删除/重建服务前必须先导出备份。AI 开关只在
  供应商和 MCP/RAG 同时就绪后恢复。
- 验证状态：2026-09-01 本地 JDK 17 Reactor 177 例、前端 lint（0 error、1 条既有 warning）和
  `VITE_AI_ENABLED=false/true` 两种生产构建通过；`render.yaml` 通过 Ruby/Psych 语法解析、Render
  官方 JSON Schema 与 `git diff --check`。Aiven 登录、服务创建、SQL 导入、Blueprint 同步和线上
  业务验收待完成。

### AI-HEALTH-20260901：修正 Render 的 ai-inference 健康检查路径

- 复现：打开 Render 部署 `dep-dabdn8jtqb8s73fjjsh0`；日志显示 Uvicorn 已监听 `0.0.0.0:8090`，但
  每 10 秒一次的 `GET /api/v1/health` 均返回 404，部署持续为 Deploying。
- 修复后验证：同步最新 Blueprint，确认 `GET https://edu-portrait-ai-inference.onrender.com/health`
  返回 200 且 JSON 包含 `"status":"OK"`，部署状态转为 Live。
- 通过判据：Render 内部健康检查不再出现 404，`edu-portrait-ai-inference` 显示 Deployed。
- 排错：若 `/health` 仍失败，先确认容器监听 `PORT=8090`，再核对 `app/main.py` 是否仍
  `include_router(health.router)`；不得通过关闭健康检查掩盖问题。
- 回滚：可把 Blueprint 回退到上一提交，但旧 `/api/v1/health` 会恢复固定 404；只有 FastAPI 同时
  提供该路由时才可回退探针路径。
- 验证状态：根因已由 Render 实时日志确认；修复后 Blueprint 同步与公网 200 待验证。

### LOGIN-VALIDATION-20260902：登录页错误拒绝默认管理员密码

- 复现：访问线上 `/login`，保留预填账号 `admin/admin` 并点击“登录”；修复前表单显示
  “密码长度不能少于6位”，请求不会发送，而同一凭据直接调用 gateway 登录接口成功。
- 修复后验证：执行 `cd frontend && npm run lint:check && npm run build`；部署后在浏览器使用授权测试账号
  登录，确认跳转到系统首页，再依次打开数据面板、教师列表、学生列表和心理概览。
- 通过判据：表单不再显示最小长度错误；登录成功且核心页面无 JDBC/Redis 错误；登出后旧 token 返回 401。
- 排错：若仍出现旧校验文案，确认 Render Static Site 已构建包含修复提交的新版本并清理浏览器缓存；若请求
  已发出但返回失败，按 Aiven MySQL、Render Key Value、JWT/Redis 会话的顺序检查，不要恢复客户端长度限制。
- 回滚：可回退该前端提交，但默认管理员将再次无法通过 UI 登录；回滚前应先将验收账号密码改为满足旧门槛
  的值并更新验收说明。此修复不改数据库结构或服务端认证行为。
- 验证状态：2026-09-02 已在线复现并确认后端接受同一凭据；前端 lint 0 error（1 条既有 Prettier
  warning）且生产构建通过，修复后的云端页面待验证。

### GROQ-CLOUD-20260902：Render 接入 GroqCloud 免费 LLM

- 前提：在 GroqCloud 为 Render 创建独立 API Key；保存到 `edu-portrait-common-env` 的
  `LLM_API_KEY`，并删除或同步 agent/ai-inference 的服务级同名覆盖；不写入仓库、构建日志或前端变量。
- 配置：agent-service 使用 `SPRING_AI_OPENAI_BASE_URL=https://api.groq.com/openai`，Python 使用
  `LLM_BASE_URL=https://api.groq.com/openai/v1`；两侧模型一致且 `LLM_CACHE_PROMPT_ENABLED=false`。
  开启 `EDUCARE_AGENT_LOOP_ENABLED`、`SPRING_AI_MCP_CLIENT_ENABLED` 与 `VITE_AI_ENABLED` 后重建。
- 修复后验证：先以 Groq `/chat/completions` 做不输出密钥的最小请求，再跑 Java/Python 定向测试与前端
  build；线上确认 agent-service 启动、两个 MCP initialize 成功，触发一条 Agent 任务并检查合法
  `final_answer` 落库，最后检查前端 AI 路由。
- 通过判据：Groq 返回 200；请求体不包含 `cache_prompt`；Agent 任务不因 400/401/429 失败；前端 AI
  页面可见。knowledge-rag 无 Milvus/BGE 时允许明确 fallback，但不得伪报检索命中。
- 排错：400 先检查 `cache_prompt` 开关和模型 ID，401 先检查 Render 服务级覆盖再检查环境组 Key，403
  检查 Groq Organization Allowed Models，404 检查 Java/Python base URL 差异，429 按 Groq
  `retry-after` 等待；MCP 启动失败检查免费实例冷启动和共享 token。
- 回滚：将三个功能开关恢复 false 并重新部署；删除/轮换 Groq Key。保留默认 true 的本地
  `cache_prompt`，因此本地 llama.cpp 回退不需要代码回滚。
- 验证状态：2026-09-02 已创建独立 Groq Key 并保存到 Render（未进入仓库）；Java/Python 各 2 项
  兼容测试、Python 语法检查、前端 lint 0 error（1 条既有 warning）与生产构建通过；在线验收待完成。

### MCP-TRAILING-SLASH-20260902：knowledge-rag MCP 初始化被 307 中断

- 复现：开启 Render 的 `SPRING_AI_MCP_CLIENT_ENABLED=true` 后观察 agent deploy；student-data 日志出现
  initialize，而 ai-inference 日志显示 `POST /mcp` 与 `GET /mcp` 均返回 307，agent 随后在
  `McpSyncClient.initialize` 以 status 1 退出。
- 修复后验证：设置 `MCP_KNOWLEDGE_RAG_ENDPOINT=/mcp/` 后重新 Blueprint sync；确认 ai-inference 的
  `/mcp/` 请求不再是 307、agent 日志出现完整 `Started AgentServiceApplication`，再请求
  `/actuator/health` 并触发一条 AgentLoop 任务。
- 通过判据：agent deploy 为 Live；两个 MCP client 均完成 initialize/list；不存在 307 或
  `Failed to send message: DummyEvent`；健康检查为 UP。
- 排错：先从服务端访问日志确认实际规范路径，再核对 URL 与 endpoint 拼接结果、共享 token 及冷启动
  时长；不要用关闭 MCP 掩盖路径错误。
- 回滚：移除 Render 的 `MCP_KNOWLEDGE_RAG_ENDPOINT` 即恢复默认 `/mcp`，但 FastAPI 部署将重新出现
  307 与启动失败；本地独立 MCP server 不设置该变量，继续使用默认路径。
- 验证状态：2026-09-02 已从 Render 双端日志确认根因；修复后云端复验待完成。

### MCP-LIFESPAN-20260903：FastMCP SessionManager 未初始化

- 复现：knowledge-rag endpoint 改为 `/mcp/` 后重启 Agent；ai-inference 日志显示请求已进入
  `fastmcp/server/http.py`，随后抛出 `StreamableHTTPSessionManager task group was not initialized`，
  Agent 仍在 `McpSyncClient.initialize` 退出。
- 修复后验证：运行 `python -m unittest tests.test_lifespan`，再部署 ai-inference；确认启动日志包含父应用
  与 FastMCP lifespan，重新单独部署 Agent 并观察 initialize/list tools。
- 通过判据：`POST /mcp/` 不返回 500；日志不再出现 `task group was not initialized`；Agent deploy 为
  Live 且 `/actuator/health` 为 UP。
- 排错：确认 `FastAPI(lifespan=...)` 收到组合后的 context factory，且 `mcp_app.lifespan` 在 mount 前取得；
  不要在普通 HTTP 请求中手动启动 SessionManager。
- 回滚：回退 lifespan 组合提交会恢复 500，不涉及数据迁移；紧急情况下可关闭 MCP/AgentLoop 保住核心
  CRUD，但这不算 AI 上线完成。
- 验证状态：2026-09-03 已由 Render 线上堆栈确认根因；生命周期与供应商兼容定向测试共 3 项通过，
  语法检查通过。完整 discovery 在本机 Python 3.9 因项目使用的 3.10 联合类型语法无法收集
  `test_http_integration`，生产镜像 Python 3.10 不受此限制。云端 Agent 日志已确认两个 MCP client 完成
  initialize/list tools，部署状态为 Live；聚合健康检查的剩余失败来自独立的 Aiven DNS 阻塞。

### GROQ-RUNTIME-CONFIG-20260903：服务级占位密钥与模型权限覆盖

- 复现：使用共享环境组的新 Key 请求线上 `/api/v1/llm/chat` 仍返回 401；在
  `ai-inference-service > Environment` 检查服务级 `LLM_API_KEY`，确认其覆盖共享环境组。修正后请求变为
  403 `model_permission_blocked_org`；Groq `Organization > Limits > Allowed Models` 仅列出
  `openai/gpt-oss-120b`。
- 修复后验证：分别更新 ai-inference 的 `LLM_API_KEY`/`LLM_MODEL` 与 agent 的
  `LLM_API_KEY`/`SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`，每次保存后等待单服务 Live；用 Groq 官方
  `/openai/v1/models` 和 `/openai/v1/chat/completions` 验证 Key/模型，再请求线上 Python chat、Agent 健康
  检查和一条真实 Agent 任务。任何输出都不得打印 Key。
- 通过判据：Groq 与线上 Python chat 均返回 200；Render 两个服务的成功部署 commit 一致；Agent 两个
  MCP client 初始化完成、健康检查为 UP，真实任务有合法 `final_answer`。
- 排错：401 依次核对服务级同名变量、共享环境组和前后空白；403 读取 Groq Allowed Models，不要只依据
  models 列表；修改隐藏值时先显示再编辑，保存后重新显示核对，避免 Dashboard 受控输入框回退旧值。
- 回滚：把模型恢复到此前值仅在 Groq 组织已允许该模型时可用；紧急降级应关闭 AgentLoop/MCP/前端 AI
  开关。轮换 Key 时同时更新共享环境组和两个服务级覆盖，随后删除旧 Key。
- 验证状态：2026-09-03 新 Key 与 Render 服务级值已核对一致；Groq 官方 models 与
  `openai/gpt-oss-120b` completion 均返回 200。线上 Python chat 返回 `GROQ_OK`；Agent 部署 Live，日志
  确认真实 LLM、AgentLoop 与双 MCP 已初始化。真实任务的数据库读写/落库验收因 Aiven DNS 阻塞待执行。

### AIVEN-DNS-20260904：Render 无法解析 MySQL 主机名

- 复现：请求 Agent `/actuator/health` 得到 DOWN/503，同时 liveness/readiness 分组均为 UP/200；在同一
  部署日志中定位 MySQL health probe，确认根因为当前 Aiven host 的 `UnknownHostException`。
- 修复步骤：登录 Aiven，打开原 MySQL 服务的 Connection Information；确认服务为 Running，并逐项读取
  host、port、user、database 与 TLS/CA 要求。只在 Render secret 中更新不一致项，随后重新部署所有
  MySQL 消费服务。禁止把连接串或密码写入命令输出、仓库、截图或前端变量。
- 通过判据：从 Render 环境可解析并连接 Aiven host；聚合 health 返回 UP/200；管理员登录、一个核心
  CRUD 请求及一条 Agent 真实任务均成功，任务状态与 `final_answer` 可从数据库再次读取。
- 排错：若 Aiven 原服务不存在或已停用，先确认账单、免费计划和数据保留状态，不直接重建；若 DNS 已
  恢复但握手失败，再核对端口、用户名、密码、database 和 `ssl-mode=REQUIRED`。
- 回滚：若新连接值导致回归，恢复 Render 中先前的 secret 版本并重新部署；不得通过关闭数据库健康检查
  或伪造内存数据宣称上线成功。
- 验证状态：2026-09-04 已在线确认 DNS 根因及 AI/MCP 健康边界；Aiven 当前 Connection Information
  已核验且与 Render 一致；原 Free-1-1gb 服务从 Powered off 恢复 Running 后 DNS 与 Agent 聚合 health
  均恢复。管理员登录、任务创建和任务详情回读已通过；最终 Agent 产物验收受独立的 Groq 429 待修复。

### GROQ-429-RETRY-20260904：AgentLoop 第二轮触发免费层 TPM 限流

- 复现：在双 MCP 已初始化、数据库 health 为 UP 后触发真实任务；确认第一轮工具调用成功，第二轮日志
  出现 Groq 429 `rate_limit_exceeded`，响应包含 TPM limit、used、requested 与建议等待秒数，任务随后
  `TOOL_ERROR`/FAILED。
- 修复步骤：配置 `spring.ai.retry.on-http-codes=[429]`、`max-attempts=3`，backoff 初始 15 秒、上限
  30 秒、倍率 2；确认自定义 `OpenAiApi` 注入 `ResponseErrorHandler`、`OpenAiChatModel` 注入
  `RetryTemplate` 后重新部署。不得把 401/403 加入可重试范围。
- 通过判据：日志在 429 后按退避重试；同一任务最终为 COMPLETED 或业务允许的 REJECTED；
  `riskAnalysisResult` 与 `interventionPlan` 均非空且可从任务详情再次读取。
- 排错：若 Groq 建议等待超过当前 backoff，按实际免费配额窗口调整初始值但保持有界；若持续 429，检查
  同组织并发调用和 prompt/token 预算，不无限增加重试次数。401 查 Key，403 查 Allowed Models。
- 回滚：移除 `spring.ai.retry` 段即可恢复默认 4xx fail-fast；该配置无数据库迁移。
- 验证状态：2026-09-04 已在线复现任务 2/4 的 MCP 成功后 Groq TPM 429；任务 4 证明仅加 YAML 时自定义
  Bean 仍绕过重试。现已补齐 error handler/retry template 注入与配置级回归测试；YAML 解析通过，
  `SpringAiConfigTest` 与原 AgentLoop 定向测试共 8 项通过；新实现的部署复验待完成。

### GROQ-JSON-MODE-20260904：GPT-OSS 返回不可解析的 ReAct 轮次

- 复现：部署 `e736507` 后，在没有其他任务并发时触发一条真实 Agent 任务；若日志显示连续两轮
  `parse error` 且终态为 `PARSE_ERROR`/FAILED，同时无 429，则属于输出协议问题。
- 修复步骤：在 Render agent-service 设置
  `SPRING_AI_OPENAI_CHAT_OPTIONS_RESPONSE_FORMAT_TYPE=JSON_OBJECT`、
  `SPRING_AI_OPENAI_CHAT_OPTIONS_REASONING_EFFORT=low`、`LLM_MAX_TOKENS=1200`；确认自定义
  `SpringAiConfig` 将 response format 和 reasoning effort 写入 `OpenAiChatOptions` 后重新部署。
- 通过判据：同一学生只触发一条任务；日志不再出现连续 parse error；任务最终为 COMPLETED 或业务允许的
  REJECTED，且任务详情中的风险分析与干预方案非空。429 可按既有退避恢复，但不得并发重复触发验收任务。
- 排错：先查任务列表排除重复任务，再分别搜索 `Retry error` 与 `parse error`。若 JSON 仍不可解析，核对
  Render 环境变量是否随 Blueprint 生效；若输出截断，检查最终 JSON 长度后再调整 token 预算。
- 回滚：移除三个 Render 环境变量并重新部署即可恢复 TEXT、默认 reasoning 与 2048 tokens；无数据库迁移。
- 验证状态：2026-09-04 已在线复现任务 7 的单线程 PARSE_ERROR，并核对 Groq 官方 JSON Object Mode
  能力；配置和测试已更新，部署后线上复验待完成。

### RENDER-CD-20260915：合入 main 即部署生产（CI 通过后按服务增量部署）

- 复现（修复前）：`gh api repos/liuzhne/resource-profile/commits/bcb01c4/check-runs` 返回空——CI 只在
  `pull_request` 上运行，被部署的 main 提交从未验证；Render 使用 `autoDeploy: true`（每个提交立即部署），
  main 的 ruleset 又不要求 PR 检查通过，CI 红的 PR 合入后照样上线。10 个 Docker 服务没有 `buildFilter`，
  只改文档的提交也会全部重建，消耗 Hobby 每月 500 分钟构建额度。
- 前提（一次性）：Render Blueprint `exs-da9vgj942hec7392v2vg` → Settings 中链接分支为 `main`、Auto Sync
  为 Yes。发布规则与路径对照见 [`docs/deployment/RENDER_DEPLOYMENT.md`](docs/deployment/RENDER_DEPLOYMENT.md) §3.1。
- 修复后验证：

  ```bash
  # 1) 用 Render 官方 JSON Schema 校验（按 YAML 1.2 布尔语义加载，off 不会被当成 false）
  python3 - <<'PY'
  import json, re, urllib.request, yaml, jsonschema
  class L(yaml.SafeLoader): pass
  L.yaml_implicit_resolvers = {k: [r for r in v if r[0] != 'tag:yaml.org,2002:bool']
                               for k, v in yaml.SafeLoader.yaml_implicit_resolvers.items()}
  L.add_implicit_resolver('tag:yaml.org,2002:bool', re.compile(r'^(?:true|false)$', re.I), list('tTfF'))
  schema = json.load(urllib.request.urlopen('https://render.com/schema/render.yaml.json'))
  jsonschema.validate(yaml.load(open('render.yaml'), L), schema)
  print('render.yaml OK')
  PY

  # 2) 官方校验（需先 render login）
  render blueprints validate render.yaml

  # 3) 合入后：main 头提交上应有 CI check 且全部成功
  gh api repos/liuzhne/resource-profile/commits/$(git rev-parse origin/main)/check-runs \
    --jq '.check_runs[] | "\(.name) \(.status)/\(.conclusion)"'
  ```

  Blueprint 同步后在 Render 核对：
  1. 每个服务 Settings 显示 Branch `main`、Auto-Deploy「After CI Checks Pass」，Build Filters 与 `render.yaml` 一致。
  2. 合入只改 `frontend/**` 的 PR：main 提交跑 frontend-ci，通过后只有 `edu-portrait-frontend` 出现新 deploy，
     其 commit 与 main 头提交一致，后端服务无新 deploy。
  3. 合入只改 `docs/` 的 PR：main 提交无 check，Render 无新 deploy。
- 通过判据：新 deploy 均晚于对应 CI check 完成；CI 失败的 main 提交不产生 deploy；无关服务不再随每次
  提交重建。
- 排错：合入后没有部署时依次检查——① main 提交是否有 check（零 check 不部署：改动是否落在 CI push 路径外）；
  ② 是否有 check 失败（`npm audit` 遇新公布的 high/critical 漏洞最常见）；③ 提交信息是否含
  `[skip render]`/`[skip deploy]`/`[skip cd]`；④ Workspace 构建额度是否耗尽；⑤ Blueprint 是否仍链接 `main`
  且 Auto Sync 开启；⑥ CI 全绿仍一直不部署时，查看提交上的 check suite——GitHub 会为有 checks 写权限的
  App 自动建空 suite（本仓库为 `coderabbitai`，app_id 347564，queued、0 runs）。Render 文档未说明是否忽略
  空 suite；若确认被它卡住，关闭该 App 的自动建 suite（它从未在 suite 中产生 run，不影响 PR 评审），或临时
  改回 `autoDeployTrigger: commit`：

  ```bash
  gh api repos/liuzhne/resource-profile/commits/$(git rev-parse origin/main)/check-suites \
    --jq '.check_suites[] | "\(.app.slug) \(.status)/\(.conclusion) runs=\(.latest_check_runs_count)"'
  gh api -X PATCH repos/liuzhne/resource-profile/check-suites/preferences \
    --input - <<< '{"auto_trigger_checks":[{"app_id":347564,"setting":false}]}'
  ```

  服务代码没随改动更新时，核对其 `buildFilter` 是否覆盖全部 Maven 依赖模块。
- 回滚：服务页 **Rollback** 回到上一次成功部署（无数据库影响），或 revert 后走同一流程；紧急修复可用
  **Manual Deploy** 部署指定提交（绕过 CI 门，事后补验证）。配置回退：`autoDeployTrigger` 改回 `commit`、
  删除各服务 `buildFilter`、移除两个 workflow 的 push 触发。
- 验证状态：2026-09-15 本地：Render 官方 JSON Schema 校验通过；两份 workflow 通过 SchemaStore GitHub
  Workflow Schema；路径覆盖自检（每个 buildFilter 均有 CI push 路径覆盖、Java 服务 buildFilter 覆盖 Maven
  依赖、14 类典型改动的重建/CI 预期）全部通过。`render blueprints validate` 因 CLI 未登录未执行；Blueprint
  同步与 main 上首次 CI 门控部署待合入后验证。

### MENTAL-AUTHZ-20261005：心理问卷端点授权与作答视图

- 适用代码：[MentalController](./backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java)、
  [StudentMentalController](./backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java)、
  [QuestionServiceImpl](./backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java)、
  [问卷管理页](./frontend/src/views/mental/questionnaire.vue)及 [Axios 响应处理](./frontend/src/utils/request.js)。
- 启动前提：JDK 21、项目依赖、MySQL 与网关/mental/auth 服务可用；JWT、Redis 会话与内部凭证配置按
  §4.4/§6.1 一致。使用本地合成学生及测试问卷，准备 student、teacher、admin/psychologist 角色账号，
  同时有状态 0（未开始）、1（进行中）、2（已结束）问卷。凭据保存在环境/秘密存储，不输出到日志。
- 复现：在修复前的隔离环境，学生合法 JWT 请求 `/mental/overview` 或问卷管理读写可返回成功；
  `/mental/student/questionnaires/{id}` 可包含选项 score、scoringRules、levelRules。教师完成情况响应
  可包含量表原始 score。只用合成数据复现，禁止在真实学生记录上演示未经授权读写。
- 后端自动验证（从仓库根目录执行）：

  ```bash
  mvn -B -ntp -f backend/pom.xml -pl mental-service -am clean test
  ```

  [MentalControllerTest](./backend/mental-service/src/test/java/com/edu/mental/controller/MentalControllerTest.java)
  验证学生/匿名拒绝、教师可读但不可写、admin/psychologist 可写、内部凭证仅放行 analysis、学生 JWT
  附内部头仍不提权及完成情况 score 分级；[StudentMentalControllerTest](./backend/mental-service/src/test/java/com/edu/mental/controller/StudentMentalControllerTest.java)
  验证本人授权与模板 JWT 要求；[QuestionServiceImplTest](./backend/mental-service/src/test/java/com/edu/mental/service/impl/QuestionServiceImplTest.java)
  验证计分信息裁剪、问卷状态、非法选项 JSON 和完整设计视图保留。
- 前端自动验证（从仓库根目录执行）：

  ```bash
  npm --prefix frontend run lint:check
  npm --prefix frontend run build
  ```

- 运行验证：student 请求管理概览、问卷列表、完整设计视图和写操作均拒绝；teacher 可以读取管理端，写
  请求拒绝且完成情况 score=null；admin/psychologist 可在合成问卷上完成增删改并保留 score。内部调用不带
  JWT 时只能通过管理端 analysis；带学生 JWT 同时附内部头仍拒绝。学生模板请求必须有合法 JWT，问卷状态
  0/null 返回拒绝，状态 1/2 不含选项 score 或 scoringRules，问卷 levelRules=null、DTO levelRules=[]。
  非数组或非法选项 JSON 的 options=null。提交合成答案后核对服务端仍按数据库原始规则计分，结果页可回显。
  浏览器中 teacher 的新建/编辑/删除入口隐藏，admin/psychologist 的入口可用；用户角色未加载时默认隐藏。
  控制台不再出现未定义 `canWrite` 的 Vue 警告；403 显示权限提示且保持登录态。
- 通过判据：后端测试和前端命令成功；拒绝请求不触达领域写入；上述角色矩阵和响应裁剪一致。控制器
  返回 `Result.error(403, ...)` 是响应 JSON 的业务码，HTTP 可能仍为 200；入口缺失/非法凭证则由
  `ServiceAuthFilter` 返回 HTTP 401，验证时同时查看 HTTP 状态与 JSON code。
- 排错：先区分 401 身份/会话错误和 403 角色拒绝，再核对 JWT 的 roles 是否使用项目角色码；合法内部凭证
  在管理端非 analysis 路径被拒是预期。管理员按钮不显示时核对问卷页的角色计算与用户信息加载；模板
  没有选项时先在有权管理视图检查原始 options 是否数组 JSON；不要把剥掉分值的响应写回数据库。
- 回滚：此修复不涉及 schema。可恢复上一应用版本，但会重新放开已关闭的越权与计分信息泄漏；如必须
  紧急降级，先在网关/反向代理限制心理管理路由仅受控教职工可达，并暂停学生作答功能，再回退应用。
  回滚后保留 INTERNAL-AUTH-20260914 的入口凭证门，恢复修复版本后重新跑角色矩阵与测评计分验收。
- 验证状态：2026-10-05 原 heuristic 分支在 JDK 17 下全后端 11 模块、249 例测试通过（0 失败/错误/跳过），JaCoCo 门通过；前端构建与只读 lint 通过（0 errors，既有格式提示）。真实 HTTP 请求及浏览器角色验收待验证。

### ENV-AUDIT-LEAK-20260914：docker/.env.audit 真实密钥进入公开仓库

- 复现：`git log --all --format='%h %ad %s' -- docker/.env.audit` 显示 `122ab6d`（2026-08-26）引入、
  `51b8410`（#4）带入 main；`gh repo view --json visibility` 为 PUBLIC。检查时只看键名和长度、不看值：
  `awk -F= '{print NR": "$1" len="length(substr($0,index($0,"=")+1))}' docker/.env.audit`。
- 修复步骤：① `git rm --cached docker/.env.audit`（本地副本保留），`.gitignore` 增加 `docker/.env.*`（必须排在
  `!docker/.env.example` 之前）；② `preflight-prod.sh` 拒绝被跟踪的 `ENV_FILE` 以及仓库内任何 `docker/.env.*`
  （模板除外），由 CI 的 `test-preflight-prod.sh` 覆盖；③ 按下表轮换全部 14 个值——文件虽已移出，历史提交中
  仍然可见。不改写历史（见 ADR-031）。
- 轮换清单（适用于任何用 docker compose 或宿主进程加载过这些值的环境；新值只写进未跟踪的 `docker/.env`
  或平台秘密存储，生成方法见 `.env.example` 头部，禁止复用旧值）：

| 变量 | 在哪里轮换 | 轮换后需重启/重登 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | 已有数据卷：先在 MySQL 内对 root 执行 `ALTER USER`，再改 `.env`（该变量只在空卷首次初始化时生效） | 业务服务不用 root（Nacos 走 Derby，相关项已注释）；同步 `backup-mysql.sh`/`restore-mysql.sh` 的运行环境 |
| `MYSQL_PASSWORD`（edu） | 同上，对 `edu` 账号 `ALTER USER` 后改 `.env` | 所有连库服务：compose 内 agent-service、mcp-student-data，宿主/外部部署的 auth/user/teacher/student/mental/data |
| `NACOS_PASSWORD` | 先在 Nacos 控制台改 nacos 账号密码，再改 `.env` 与各 Java 服务环境 | 全部 Java 服务重启。当前 compose `NACOS_AUTH_ENABLE=false`（prod 覆盖层未开启），只有开启鉴权的环境才实际校验 |
| `NACOS_AUTH_TOKEN` | `.env`（Base64，解码后 ≥32 字节） | 重启 nacos；已签发的 Nacos access token 与控制台会话失效，Java 服务随后重启 |
| `NACOS_AUTH_IDENTITY_VALUE` | `.env` 与所有 Java 服务环境（必须同值） | nacos 与全部 Java 服务在同一窗口重启 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `.env` | 重建 minio；compose 目前没有把 MinIO 凭据传给 milvus-standalone，非默认凭据下须先补齐 Milvus 接线再验证 `/healthz` |
| `REDIS_PASSWORD` | `.env`（≥32 字符） | redis（requirepass）与全部消费者同时重启：gateway、agent-service、ai-inference-service、knowledge-rag-mcp，以及宿主部署的 auth/user/data；切换窗口内会话校验失败 |
| `JWT_SECRET` | `.env` 与所有 Java 服务环境（必须同值，≥32 字符） | 所有加载 `JwtUtil` 的 Java 服务同时重启；全部 access/refresh token 失效，全员重新登录 |
| `EDUCARE_MCP_TOKEN` | `.env`（≥32 字符，三端同值） | agent-service、mcp-student-data、knowledge-rag-mcp 同时重启；进行中的 Agent 任务需重新触发 |
| `LANGFUSE_DB_PASSWORD` | 已有卷：先在 langfuse-postgres 内 `ALTER USER`，再改 `.env` | 重启 langfuse-server |
| `LANGFUSE_NEXTAUTH_SECRET` | `.env` | 重启 langfuse-server；Langfuse 控制台会话失效需重登 |
| `LANGFUSE_SALT` | `.env` | 重启 langfuse-server；SALT 参与 API key 哈希，既有 project key 失效，须在控制台重建 `LANGFUSE_PUBLIC_KEY`/`LANGFUSE_SECRET_KEY` 并更新 agent-service、ai-inference-service 后重启，否则 trace 静默丢失 |
| `GRAFANA_ADMIN_PASSWORD` | Grafana UI 或 `grafana cli admin reset-admin-password` 改密，再改 `.env`（该变量只在首次初始化生效） | 仅 Grafana 管理员重登 |

- Render：`JWT_SECRET`/`EDUCARE_MCP_TOKEN` 由 `generateValue` 生成，MySQL 用 Aiven 凭据，Redis 用 Key Value
  `connectionString`，按设计都不来自该文件；只有曾在 Dashboard 手工填入该文件中的值时才需轮换。
- 修复后验证：`git ls-files | grep -E '(^|/)\.env'` 只剩两个 `.env.example`；`git check-ignore -v docker/.env.audit`
  命中 `docker/.env.*`，`git check-ignore docker/.env.example` 无输出；`bash scripts/test-preflight-prod.sh`。
  轮换后在各环境执行 `bash scripts/preflight-prod.sh`、`scripts/gateway_verify.sh`，并真跑一条 Agent 任务。
- 通过判据：回归输出 `preflight-prod regression: PASS`；轮换后 preflight 全部通过，旧 JWT 访问业务接口返回 401、
  重新登录后 200，Agent 任务经 MCP 互验完成，启用 Langfuse 时能收到新 trace。
- 排错：改完 `.env` 服务起不来——先确认 MySQL/Postgres/Grafana/Nacos 已在服务内改密（这些变量只在首次初始化
  时生效）；全面 401 查各 Java 服务 `JWT_SECRET` 是否一致；MCP 401/403 查三端 `EDUCARE_MCP_TOKEN`；Redis
  `NOAUTH` 说明有消费者未重启；Milvus 不健康查 MinIO 凭据接线。合并 `origin/dev`、`docs/progress-audit-20260826`、
  `feat/ui-glass-refactor` 会把该文件带回（它们与 main 的合并基点早于该文件入库），CI preflight 回归会报"仓库
  跟踪了本地 env 文件"——先在源分支执行 `git rm --cached docker/.env.audit`。
- 回滚：代码侧可直接 revert，但不得恢复对该文件的跟踪；轮换不回滚到旧值，新值有问题时逐项修正配置。
- 验证状态：2026-09-14 本地已验证：移出版本库、忽略规则、preflight 回归（含"被跟踪 env 文件"反例）、临时
  索引模拟重新跟踪被拦截、`origin/dev` 试合并确认会带回该文件。轮换待部署方执行——本机无 `docker/.env`、
  Docker 未运行、生产主机不可见，按"可能在用"处理。

## 10. 修复方案的运行手册更新模板

每个修复方案在本文件追加或修改可执行步骤，并在维护记录使用与 `ARCHITECTURE.md`、`DECISIONS.md` 相同标识：

```markdown
### FIX-NNN：问题标题

- 复现：最小命令、输入和修复前观察。
- 修复后验证：实际执行的命令。
- 通过判据：状态码、日志、数据或测试断言。
- 排错：验证失败时检查顺序。
- 回滚：恢复方式与数据影响。
- 验证状态：已验证（日期/环境）或待验证（原因）。
```

## 11. 维护记录

| 日期/标识 | 变更 | 验证状态 |
|---|---|---|
| 2026-10-06 / K3S-HPA-DEPLOY-20261006 | K3s/Secret/PVC、分阶段业务与data HPA | Node/metrics、12私有服务、11镜像、默认改密/三角色鉴权20项、MySQL隔离还原20表通过；HPA1→2已验，自动回缩1已验；完整AI/TLS待模型配置 |
| 2026-10-06 / KAMATERA-DEPLOY-20261006 | Kamatera 注册、押金/控制台报价核对、SSH/部署入口、Rocky SELinux 适配及知识导入 404 排错 | 押金到账、US$92 配置、源码包独立构建/一致性、Python 32 项和工具 14 项通过；服务器创建/SSH/Rocky/Docker主机验证通过；真实Pod/模型运行待验证 |
| 2026-09-01 / DOC-BASELINE | 按当前脚本、CI、compose 和执行计划初始化启动/测试/排错/发布手册 | 文档链接与命令静态核对；未执行真实服务和生产发布 |
| 2026-09-01 / RENDER-DEPLOY-20260901 | 增加 Render MCP/Feign 冷启动与 MySQL 协议排错、验证和回滚步骤 | 本地后端 125 例、前端构建通过；修复后云端复验待完成 |
| 2026-09-01 / AIVEN-RENDER-20260901 | 增加 Aiven 初始化、Render 凭据注入、无 LLM 判据与回滚步骤 | 本地后端 177 例、前端构建、YAML 语法通过；云端待登录后验证 |
| 2026-09-01 / AI-HEALTH-20260901 | 增加 ai-inference 探针 404 的复现、判据与回滚步骤 | 根因已在线确认；修复后云端复验待完成 |
| 2026-09-02 / LOGIN-VALIDATION-20260902 | 增加登录表单最小密码长度漂移的复现、验证与回滚步骤 | 已在线复现；本地构建通过，修复后云端复验待完成 |
| 2026-09-02 / GROQ-CLOUD-20260902 | 增加 GroqCloud 接入、双 base URL、缓存字段兼容与回滚步骤 | Key 已安全保存且本地验证通过；线上验收待完成 |
| 2026-09-02 / MCP-TRAILING-SLASH-20260902 | 增加 FastAPI MCP 307 的复现、规范 endpoint、验证和回滚步骤 | 根因已由 Render 双端日志确认；修复后云端复验待完成 |
| 2026-09-03 / MCP-LIFESPAN-20260903 | 增加 FastMCP lifespan 缺失的复现、验证和回滚步骤 | 根因已由 Render 线上堆栈确认；3 项定向测试及语法检查通过，完整 discovery 受本机 Python 3.9 限制；云端待验证 |
| 2026-09-03 / GROQ-RUNTIME-CONFIG-20260903 | 增加服务级覆盖、Groq Allowed Models、验证与回滚步骤 | 新 Key 与允许模型均经 Groq 官方接口验证；线上端到端部署验收进行中 |
| 2026-09-04 / AIVEN-DNS-20260904 | 增加 Aiven DNS 故障复现、凭据核验、验证与回滚步骤 | 原免费服务已恢复 Running；DNS、聚合 health、登录与数据库回读通过 |
| 2026-09-04 / GROQ-429-RETRY-20260904 | 增加 Groq TPM 429 定向退避、验收和回滚步骤 | 根因已由真实任务日志确认；线上复验待完成 |
| 2026-09-04 / GROQ-JSON-MODE-20260904 | 增加 GPT-OSS JSON Object Mode、配额预算与复验步骤 | 线上 PARSE_ERROR 已复现；配置级测试和部署复验待完成 |
| 2026-09-15 / RENDER-CD-20260915 | 增加 main 即生产的发布流程、CI 门控部署验证、排错与回滚步骤 | 官方 Schema 与路径覆盖自检通过；Blueprint 同步与首次门控部署待合入后验证 |
| 2026-10-05 / MENTAL-AUTHZ-20261005 | 增加心理问卷端点授权与作答视图的复现、角色矩阵、回归命令及回滚约束 | 原 heuristic 分支后端 249 例、JaCoCo 门及前端构建/lint 通过；合并回归见 MERGE-MAIN-20261005，真实请求与浏览器验收待验证 |
| 2026-09-14 / ENV-AUDIT-LEAK-20260914 | 增加 `docker/.env.audit` 泄露的处置步骤、14 项轮换清单与旧分支合并带回的排错 | 本地移出版本库、忽略规则、preflight 回归与合并试算均通过；轮换待部署方执行 |

## 生产诊断复现与复验：PROD-AUDIT-20260916（2026-09-16）

前提：已授权生产验收，Render CLI已登录正确workspace；用户明确追加授权后才读取教师/学生演示账号的心理列表与历史。仅保留状态/耗时，凭据、JWT和个人心理记录不进入报告。[生产验收报告](./docs/production-tests/2026-09-16/REPORT.md)与同目录JSON为本次证据。

已执行：经Static Site `/api`入口低频测量接口两次、浏览器验证原型按钮/详情，读取Render服务/部署/应用日志；user列表先429、唤醒后200/业务200（6.647→0.764秒），Agent本次MCP429启动失败、SSE两次60秒无首字节，普通业务与授权角色只读接口通过。user健康探针HTTP200/业务500，不得计为通过。

只读日志复现命令（UTC；输出先脱敏，网关DEBUG可能包含Bearer token）：

```bash
render logs -r srv-da9vk4e7bikc73f168o0 --start 2026-09-16T08:25:00Z --text 'Invalid SSE,cancelling refresh,Application run failed,Starting AgentServiceApplication,Langfuse' --limit 100 -o json
render logs -r srv-da9vk4e7bikc73f168q0 --start 2026-09-16T08:31:00Z --text 'Hikari,Starting,Started,Completed initialization,No static resource actuator/health' --limit 100 -o json
render services -o json
render deploys list srv-da9vk4e7bikc73f168o0 -o json
```

排错：先判断HTTP与body.code，SSE需有成功首字节与正确content-type；对照网关上下游耗时与应用初始化日志，勿把HTTP000当HTTP响应。MCP异常未标connection名，继续查具体connection与Render平台事件；无对应日志不代表依赖健康。user冷启动后复测列表而非拿缺失health路由作判据。

修复后验证（全部待验证）：真实user健康语义通过；Agent冷唤醒成功Started、双MCP工具初始化完成、无run-failed/重启，SSE握手在约定预算内成功；user首请求与热请求各测、记录池初始化/SQL阶段；Langfuse出现活trace；用户/学业前端调用真实API。写入类能力需独立测试记录，未经实跑不标通过。

回滚：本次仅报告与文档，没有生产变更可回滚。后续修复应事前保存服务配置差异与部署ID，再按验证失败项逐项回退；不得回滚鉴权或改密，也不得以关闭健康检查作为故障恢复。

## PERF-500MS-20260916：部署与500ms验收（2026-09-16）

前提：JDK21、现有 Render workspace/API凭证、演示账号只读授权。报告不得包含JWT、完整响应记录或原始 DEBUG 日志。使用 [bench-production-api.py](./scripts/bench-production-api.py) 顺序发送持久连接请求，外部完整响应耗时与 Server-Timing 分列，快速429/503不算通过。第一笔连接和休眠唤醒独立保留；热态统计取稳定样本并同时报告最大值，不能仅报告最佳值。

```bash
cd backend
mvn -B -ntp clean test
cd ..
bash scripts/test-preflight-prod.sh
# 密码通过环境变量设置，不写入报告；角色可选 admin/teacher/student。
python3 scripts/bench-production-api.py --role admin --rounds 5 --output /tmp/performance.json
# 先只读查看配置差异；生产授权后才 --apply。凭证仅在环境/内存。
python3 scripts/sync-render-performance.py --output /tmp/render-plan.json
python3 scripts/sync-render-performance.py --apply --output /tmp/render-applied.json
render blueprints validate render.yaml --output json
```

`BENCH_PASSWORD`/`BENCH_USERNAME` 和 `RENDER_API_KEY` 需调用者预先设置。sync 脚本只更改允许列表的非敏感性能配置、健康检查路径，不改计划/生成密钥/部署；bulk PUT 前读取并保留全部直接设置变量，避免清除既有凭证，变化证据仅保存允许列表。然后发布已通过CI的确定提交至9个Java服务，逐个确认live提交与真实 readiness；前端/Python无代码改变则无须重建。

排错：`app;dur` 为Servlet过滤器进入到响应序列化前，`gateway;dur` 包含JWT/会话/转发至上游响应头，不包含客户端传输；二者不应相加。若应用快而端到端超过500ms，检查网络地区、Static Site rewrite/公网转发和免费实例休眠；若 app 慢，按具体路径查SQL/外部调用/线程池；若MCP未就绪，查后台 connection/errorType 重连日志及依赖探针，不将任务FAILED判为成功。启动预热失败必须阻止ready，不靠静态404/业务500健康响应放行。

回滚：部署各服务上一次live提交；Agent回退旧代码前，恢复 `SPRING_AI_MCP_CLIENT_ENABLED=true` 并禁用 `EDUCARE_MCP_DEFERRED_ENABLED`，否则旧代码无法取得工具。其他性能变量可按render-applied.json的previous值恢复（previous为空时删除服务级覆盖、回到环境组）；保留原有JWT/MCP/数据库凭证。无需数据库回滚（未执行DDL）。本地验证已通过，生产部署和端到端500ms验收在 PERFORMANCE.md 更新实测；未覆盖的写入/内部推理接口标为待验证，不能声称全接口通过。

PERF-500MS-20260916 补充（2026-09-16）：Excel批量写入验证可执行 `mvn -B -ntp test -pl mental-service -am`；QuestionBulkImportTest验证205题只写3批且保留默认值/显式排序，QuestionBulkSqlTest在本地临时H2通过实际MyBatis SQL验证JSON/null/中文及回滚。没有对生产心理问卷执行写入测试，完整上传/解析耗时待带测试夹具验收；回退上一次mental镜像即可，无生产DDL回滚。

PERF-500MS-20260916 启动回归修正（2026-09-16）：若deferred启动仍报toolCallbackResolver/MCP未就绪，确认镜像包含动态resolver补充修复；DeferredMcpConfigurationTest包含真实Spring AI自动装配，必须通过。首轮44a1074 Agent未上线，需用包含补充修复的提交再部署，不能将update_failed记录标为live。 实现见 [DeferredMcpConfiguration](./backend/agent-service/src/main/java/com/edu/agent/config/DeferredMcpConfiguration.java)。

2026-09-16 / PERF-500MS-20260916：生产 Server-Timing 出现重复 app 指标，原因是领域服务扫描 common 的 @RestControllerAdvice，同时自动配置再次创建 advice。以 ConditionalOnMissingBean 保证唯一注册，并限制 servlet 条件；启动上下文覆盖扫描/不扫描两种注册路径。此项无架构边界变化，影响 common RequestTimingConfiguration。网关首轮现已 live（09:20:08Z），内存可见样本约233MB，不能据此把此前无报错重启归因为OOM。最终指标必须以全部服务稳定发布后复测为准；回滚恢复原提交和原始 render-performance-plan 配置。

2026-09-17 / PERF-500MS-20260916：生产静态站的外部rewrite未及时转发SSE首帧，Agent和网关直连首帧可用。Warning.vue通过VITE_SSE_BASE_URL直连网关，仍携带Bearer并经过网关会话鉴权；普通API保持原路由。选择绕过静态代理，放弃调长超时及绕过鉴权的Agent直连。render.yaml记录构建变量。验证：前端构建/体积门通过，lint零错误一个既有格式警告；网关CORS预检允许Authorization和站点Origin，直连hello测试通过。前端最终部署待验证；回滚前端提交和该构建变量恢复原路由。完整生产验收记录见 docs/production-tests/2026-09-16/；不能据此宣称所有请求满足500ms。

2026-09-17 / PERF-500MS-20260916：热登录仍超过预算；AuthServiceImpl 对成功且服务处理≥500ms的登录仅记录用户查询、密码校验、角色查询、JWT签发和Redis会话五阶段耗时，不记录身份、密码、令牌或SQL。无架构边界变化，不降低bcrypt成本、不缓存密码校验、不取消角色/会话检查；用于区分CPU校验和外部依赖成本，避免猜测原因。复测热登录并读取 Slow login phases 日志，若任一阶段仍超预算，则500ms未通过。此诊断实际生产结果待验证，回滚该提交移除计时日志。

2026-09-17 / PERF-500MS-20260916：分阶段日志证实首次登录还有Redis连接及JWT初始化成本，热密码校验为主要耗时。新增 auth AuthDependencyWarmupConfiguration，在已有预热开关开启时于readiness前PING Redis并初始化内存签名；不写会话、无业务用户/凭证日志，Redis失败不能就绪。无架构影响，auth仍依赖原Redis白名单。放弃密码缓存/降低bcrypt及依赖故障时假健康；验收需验证启动、热登录阶段和401/403。新增预热成功只读与故障阻止就绪测试；生产行为待验证。回滚此auth提交移除额外启动预热，保留强验证与原配置。

2026-09-17 / PERF-500MS-20260916 维护记录：相关代码已逐批经PR/main CI部署既有生产服务，SSE前端实际产物已核对，Auth依赖预热的阶段日志已确认生效；完整验收记录见 docs/production-tests/2026-09-16/ 的 FINAL_REPORT.md 与 JSON 证据。无新增架构边界或收费资源。500ms全接口目标未通过：热密码验证、跨区域传输、休眠唤醒仍超预算，真实写入/推理最终产物/下载尚未完整生产验收，不得将应用序列化前计时视作端到端结果。后续须明确验收网络区域、负载和可用常驻预算，不能通过降低密码成本、权限豁免或快速失败充当达标。回滚沿用前述提交及原配置plan；这条最终验收记录随上述目录一并提交，证据已脱敏，不含凭证、令牌或完整运行时元数据。


## SAFE-PUSH-20261005：提交前排除与核对（2026-10-05）

1. 复现：`git ls-files -- docker/.env.audit` 可识别旧基线是否仍跟踪本地凭证文件。只检查路径，不输出文件内容。
2. 排除：`git rm --cached -- docker/.env.audit` 保留本地副本并暂存取消跟踪；`.gitignore` 增加 `docker/.env.*`，模板放行规则保留在其后。
3. 核对：`git ls-files -- 'docker/.env*'` 仅应包含 `docker/.env.example`；`git check-ignore -- docker/.env.audit` 应命中；`test -f docker/.env.audit` 应成功；`git diff --cached --check` 应无输出。
4. 回滚：可回退代码提交，但不得恢复凭证文件的 Git 跟踪；本地副本持续保留。历史凭证的轮换需部署环境另行验证。

验证状态：本次已核对凭证文件取消跟踪、模板保留、忽略命中及本地副本仍在；最终暂存时还须执行 staged diff 检查。

本次 `bash scripts/test-preflight-prod.sh` 已通过；原 heuristic 分支在 JDK 17 下全后端 11 模块、249 例测试通过，JaCoCo 门通过；最新前端构建通过，lint 为 0 errors（存在 Prettier 提示）。浏览器角色矩阵和实际部署验收仍待验证。 实际平台凭证轮换仍待部署方验证。

开发脚本依赖 Docker Compose v2、npm、Python 和 JDK 21；可通过 `JAVA21=/path/to/jdk21` 指定。脚本从 Compose 有效配置读取共享凭证给宿主服务，运行文件写入 `.local-run/`。合并后的完整起停与真实冒烟待验证，验证命令与进程归属检查见 MERGE-MAIN-20261005。

## MERGE-MAIN-20261005：整合分支的验证与本地开发（2026-10-05）

- 复现：旧 `scripts/local_dev.sh` 使用 JDK 17 与固定 MySQL/Redis 参数，整合到 JDK 21 主线会触发 Enforcer
  或内部取数 401；按名称停止 Vite/模型进程会超出当前项目范围。
- 当前启动前提：JDK 21、Maven、Node/npm、Python、Docker Compose v2 及 Docker daemon。
  `JAVA21` 优先于有效的 JDK 21 `JAVA_HOME`；未指定且当前 JAVA_HOME 不匹配时查找本机 JDK 21。
  显式指定不符合 21.x 的 JAVA21 拒绝；status/down 无须配置 JDK。
  Compose 采用标准环境变量与 `docker/.env` 优先级；脚本经 `docker compose config --format json` 读取
  MySQL、Redis、Nacos、JWT、`EDUCARE_INTERNAL_TOKEN`、`EDUCARE_MCP_TOKEN` 并传给宿主 Java 服务。
  凭证文件保持未跟踪，已有卷的真实密码须与配置一致。不要将 Compose JSON 或环境值输出到报告。
- 合并后自动验证（从仓库根目录执行）：

  ```bash
  java -version
  mvn -B -ntp -f backend/pom.xml clean test
  bash -n scripts/local_dev.sh
  /bin/bash -n scripts/local_dev.sh
  bash scripts/test-preflight-prod.sh
  npm --prefix frontend run lint:check
  npm --prefix frontend run build
  npm --prefix frontend run test:retry
  npm --prefix frontend run size:check
  ```

- 本地起停与真实冒烟（待验证）：

  ```bash
  JAVA21=/path/to/jdk21 bash scripts/local_dev.sh up --core --build
  bash scripts/local_dev.sh status
  bash scripts/local_dev.sh smoke
  bash scripts/local_dev.sh down
  ```

  完整 AI 链用 `up --build`，先确认本地 LLM、embedding 与双 MCP 前提。停止操作只处理 `.local-run/` 中
  记录且启动时间/命令路径仍匹配的进程，身份无法确认时跳过；`down --all` 还停止本项目 Compose
  容器并保留数据卷。不要为清理残留进程恢复按全局名称匹配的终止操作。
  已配置 MCP token 时，现有 `mcp_smoke_test.sh` 不附鉴权头，helper 会提示用真实 Agent 工具调用验收；
  该提示不等于 MCP 已通过。
- 通过判据：JDK 显示 21；全后端测试与 JaCoCo 门通过；脚本语法、preflight 回归、前端 lint/build 通过；
  实际服务健康、匿名业务请求 HTTP 401、登录后业务读取成功，内部取数不因共享凭证不一致失败。
  起停验收须证明无关项目进程仍运行；真实 MCP/AgentLoop 仍按 §6 与 R-5/R-6 验收。
- 排错：JDK validate 失败先核对 `JAVA21`/`JAVA_HOME`；服务 401 核对 Compose 和宿主使用的内部凭证是否一致；
  MySQL/Redis 拒绝连接时先检查已有卷的密码，脚本不自动改密；进程归属无法确认时检查本项目 PID 记录，
  保留无关进程。心理接口同时检查 HTTP 状态和 JSON 业务码，字段矩阵见 FIELD_PERMISSION §12。
- 回滚：停止本项目开发进程，回退相关应用提交；不恢复凭证文件跟踪、不回滚到已泄露值、不关闭 JWT/
  内部凭证/心理角色门。生产回滚仍采用 §9 与各修复条目的已记录版本和配置。
- 验证状态：2026-10-05 合并代码 JDK 21 全后端 11 模块、54 套测试、267 例通过（0 失败/错误/跳过），
  10 个代码模块 JaCoCo 门通过；combined preflight 回归通过；前端构建、重试断言 30/30、体积门通过，
  lint 0 errors、1 条既有 vite.config.js 格式警告。脚本 Bash 5 与 macOS Bash 3.2 语法/临时 stub 回归通过，
  覆盖 JDK 21 选择/拒绝 17、非默认 Compose 共享凭证与特殊字符、MySQL/Redis 鉴权探针、无凭证输出、
  无临时配置残留、只停止所属 PID、无关 Vite/mock 进程保留、陈旧 PID 身份保护及无 JDK 的 status。
  npm 生产依赖 audit 0 vulnerabilities；Render/Compose/backend-CI YAML 解析通过，POM 的 JDK 21 与
  自动配置清单完整性/无重复断言通过。忽略的本地 docker/.env.audit 副本已恢复，不参与 Git 跟踪。
  真实 Docker 全栈、GPU、HTTP/浏览器角色及生产验收未执行；原 heuristic 分支的 JDK 17 后端 249 例与
  前端验证保留为历史证据，平台凭证轮换仍待部署方执行。

2026-10-06 / K3S-HPA-DEPLOY-20261006 实机验收补充：K3s Node/metrics与12私有服务Ready；11镜像原生构建导入、三角色业务鉴权20项通过，data HPA在受控CPU负载下自动1→2→1，保持300秒缩容稳定窗口、无手工scale。MySQL合成快照在隔离库还原比对20表通过；备份留在VM，本地导出因自动审批要求明确数据导出授权而未执行。临时域名已解析配置，完整模型/embedding配置缺失，AI/MCP/RAG与HTTPS公网发布仍待验证，R-5/R-6不变。详见k8s/README.md与Kamatera手册；回滚停frontend并保留PVC。

## SSH-PASSWORD-STATE-20261006：root密码状态核对（2026-10-06）

继续使用部署专用SSH公钥连接；只读passwd -S root可核对密码状态，sshd -T -C按当前root/客户端条件核对有效认证选项，不读取/etc/shadow或输出hash。本次实查PS（已设置密码），PasswordAuthentication/PubkeyAuthentication/PermitRootLogin=yes、PermitEmptyPasswords=no；没有原密码明文可查，设置来源未知。客户端配置禁止密码认证只限定部署连接方式，不改变服务器sshd。若账号所有者需要密码，可在已登录的SSH终端自行执行passwd私下设置；这是后续可选操作，本次未执行，也未修改sshd。无需重启应用或PVC。详见[Kamatera手册](./docs/deployment/KAMATERA_DEPLOYMENT.md)。

2026-10-07 / LANGFUSE-OTLP-20261007 最终运行快照：17/17 Deployment Ready，frontend Ready；data HPA当前/目标均1、上限2，指标可用且ScalingActive=true。只读快照未施加新负载，不替代既有1→2→1扩缩容证据。

2026-10-07 / LANGFUSE-OTLP-20261007 私网最终复验：修正版 `--stage private-ai` 全部8项通过，Agent触发次数0；三库固定合成文档持久化、1024维有效向量、非fallback检索、双MCP鉴权/工具及Python生成有效。既有upsert按固定doc前缀替换同批旧合成chunk，行数每库由2变1；此前全部行已核验为相同公开合成文本，没有删未知资料或清Redis。VM私有结果 `k3s-ai-acceptance-20261007-private-ai.json` 为0600；该阶段不代表完整Agent通过。

## LOGIN-CREDENTIAL-UI-20261007：生产登录页移除默认凭据（2026-10-07）

修改[登录组件](./frontend/src/views/login/index.vue)：删除默认账号提示与样式，账号/密码初始为空。本次不做数据库密码重置，生产种子密码此前已随机轮换且默认密码拒绝验收通过；本次不重新打印或测试密码。

实际验证已通过：`npm run build`、`npm run size:check`、`./node_modules/.bin/eslint src/views/login/index.vue`（零错误/警告）；源码和login构建产物无默认账号提示/类，表单两个初始字段为空。经固定SSH上传唯一组件并核对SHA-256 `58b27a4683b61416a46af7a17a930f8f1059b6159ffe5695b6f6bc6f4da97102`；备份VM旧源码 `.local-run/kamatera/source-backups/login.pre-login-ui-20261007.vue` 与镜像 `edu-portrait-kamatera-frontend:pre-login-ui-20261007`，只重建frontend并导入K3s，更新Deployment至 `edu-portrait-kamatera-frontend:login-ui-20261007`，rollout Ready。浏览器刷新实际确认默认账号提示不存在、账号/密码输入为空；受信HTTPS/login200、healthz200及匿名业务401通过。没有提交登录、读取真实密码或触发Agent。私有证据 `.local-run/kamatera/login-ui-verification-20261007.json` 与截图 `login-ui-20261007.jpg`，均0600/Git忽略。浏览器密码管理器的正常自填不属于应用硬编码，不应禁用。

回滚：在VM把 `edu-portrait-kamatera-frontend:pre-login-ui-20261007` 用 `docker save` 导入 `k3s ctr -n k8s.io images import -`，执行 `k3s kubectl -n educare set image deployment/frontend frontend=edu-portrait-kamatera-frontend:pre-login-ui-20261007`，再 `rollout status deployment/frontend --timeout=180s`；保留Caddy证书/PVC和后端。重新恢复修复时set image至 `login-ui-20261007`；旧版本会恢复被删除提示，仅紧急排查时使用。UI删除不替代历史凭据轮换B-2或完整AI/生产验收。

## FIELD-CONTRACT-FIX-20261007：61组契约修复（2026-10-07）

实现与逐项映射见[修复记录](./docs/audits/FIELD_CONTRACT_FIX_2026_10_07.md)。启动前提：JDK21、完整基础表、MySQL/Redis、JWT/内部凭证；
真实SMS/SSO暂无配置，入口置灰。Maven验证须先将JAVA_HOME/PATH切换到JDK21；默认JDK25会被版本门拒绝。首次登录新额外示例学生需管理员开通并设置新密码；现有真实口令不迁移。

验证命令（仓库根，凭据不进入命令行/输出）：
```bash
mvn -o -Daether.syncContext.named.factory=noop -f backend/pom.xml verify
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend run size:check
python3 scripts/apply-sql-migrations.py --transport k3s
```

执行结果：独立MySQL新库初始化、旧库升级、二次迁移、业务记录数量保留、有效旧心理角色绑定转换、后来权限编辑保留已通过。
最终后端verify为472项、零失败/错误/跳过且覆盖率门通过；前端113项回归与角色权限契约、生产构建/体积门/lint（0/0）通过；部署和迁移启动门38项通过。生产三份迁移完成，26表274列/297实体字段及新增类型/nullable、JDBC投影通过，业务记录数保留。10个变更应用已发布 `prod-contract-20261007-v2`，全栈17个Deployment与17个Pod Ready。

实际生产只读冒烟79项：76通过、0失败、2存量绑定阻塞、1无开放问卷跳过；覆盖未完整。退出与刷新后旧access/refresh拒绝、用户分页total及权限/字段检查通过。`student_questionnaire_self`、`student_history_self` 因共享账号返回业务码409；不要按失败数据随意重绑。模板脱敏检查须在授权人员创建合法开放问卷后复验。浏览器核对外部认证置灰、默认账号提示消失、说明弹窗正确显示。业务写入/模型请求/Agent执行均0；脱敏验收JSON（本地测试产物，不入库：`./docs/audits/FIELD_CONTRACT_PRODUCTION_VALIDATION_2026_10_07.json`）保留各项结果。

已有库发布：先核对运行Pod镜像配置与文件系统层并保留准确回滚镜像；确认无在途Agent/PDF后短暂停用网关，保留压缩数据库备份。执行MySQL就绪→20基础表及220旧字段检查→版本化增量迁移→274字段类型/nullable检查→后端/前端镜像→恢复网关。任一迁移失败恢复旧网关并停止新应用发布。
首次全量初始化只用于空库；本地/Compose/K3s失败均须停止后续启动。不要重放01种子、删除PVC、使用down -v或重置旧口令。
部署参数VITE_LANGFUSE_URL在构建时注入，不在Pod运行时覆盖Vite资源；检查image label与静态链接，默认日本Cloud控制台新窗口。

通过判据：所有模块测试及既有覆盖率门通过；非法答案/配置拒绝且无部分落库；历史改题/停用仍按快照回显；无快照明确提示；
姓名/学号路径与返回studentId一致；未知课程成绩不改变已有平均分；角色权限变化撤销旧会话，登录并发不能重签旧权限；
账号删除/档案新增并发不能删除已绑定账号；teacher看不到心理原分JSON/PDF；学生不入全校面板；统计日期/人口与标签一致。

排错：多student_info绑定同userId返回409，管理员核实身份后使用有效独立学生账号重绑，禁止LIMIT1或批量猜测迁移；
既有active mental_teacher与停用/删除psychologist冲突时迁移失败，请先明确角色保留决策；SHA-256变化的已执行迁移不能改旧文件，应增加新版本。
选项配置非法返回400，不能把不完整选择题下发；历史无快照无法推断原题干；新增画像无业务记录显示为空。
申请入口只登记人工流程，管理员通过既有校内渠道核验后自行开通/重置密码，再填写处置说明。peer IP限流在未经可信代理配置时可能共享网关来源，禁止信任任意XFF。

发布制品：本机已验收Boot JAR按zip条目SHA-256去重传输，服务端重建后逐条验证字节与BOOT-INF/lib的STORED模式；整个zip文件SHA可因压缩元数据重建变化，不声称二进制文件完全相同。继承验证相同配置/层的原运行镜像，沿用Caddy、TLS、env引用、字体和PVC。新镜像Ready须观察新generation、updated/available/ready副本与Pod运行配置/层，而非只看旧副本数量。服务器既有Recreate策略有短暂中断，更新后浏览器应刷新旧静态资源。

回滚：发布快照位于服务器 `/opt/resource-profile/.local-run/kamatera/prod-contract-20261007-v2/`，数据库备份为 `database-before.sql.gz`，源码备份为 `source-before.tar.gz`。`release.json` 内 `rollback_images` 保存准确旧镜像，均带 `:pre-prod-contract-20261007-v2` 标签；该文件还含私有运行配置，不要整份打印或分享。保留新增列/表与业务数据，只恢复上一组应用镜像和对应源码；回滚旧客户端可能不能操作新增记录，但不得删除列/表以求回退。
若需数据恢复，先停止写入并使用发布前备份与恢复验证流程；不能在运行中的生产库覆盖恢复。R-5/R-6真实模型与上线签字不随本修复自动完成。

本次镜像回滚命令（在服务器维护窗口、确认无在途Agent/PDF后执行；不执行SQL降级）：
```bash
python3 - <<'PY_ROLLBACK'
import json
import subprocess
from pathlib import Path
stage = Path('/opt/resource-profile/.local-run/kamatera/prod-contract-20261007-v2')
record = json.loads((stage / 'release.json').read_text())
maintenance = json.loads((stage / 'maintenance.json').read_text())
order = ['auth-service', 'user-service', 'teacher-service', 'student-service',
         'mental-service', 'data-service', 'mcp-student-data', 'agent-service',
         'frontend', 'gateway']
assert set(order) == set(record['rollback_images'])
def kube(*args):
    subprocess.run(['k3s', 'kubectl', '-n', 'educare', *args], check=True)
kube('scale', 'deployment/gateway', '--replicas=0')
for name in order:
    kube('set', 'image', 'deployment/' + name,
         name + '=' + record['rollback_images'][name])
    if name != 'gateway':
        kube('rollout', 'status', 'deployment/' + name, '--timeout=600s')
kube('scale', 'deployment/gateway',
     '--replicas=' + str(maintenance['gateway_replicas']))
kube('rollout', 'status', 'deployment/gateway', '--timeout=600s')
PY_ROLLBACK
```
回滚命令为操作预案，未实际执行；如果中途报错，网关保持维护状态，先检查目标镜像与Pod事件，不跳过失败继续恢复对外写入。源码恢复先核对 `source-before.tar.gz` 的成员与目标路径；不得覆盖私有env/凭据。

## LANGFUSE-INLINE-20261008：站内追踪列表（2026-10-08）

启动前提：已有 `LANGFUSE_PUBLIC_KEY`、`LANGFUSE_SECRET_KEY`、`LANGFUSE_HOST`（当前日本区Cloud），管理员账号与有效网关会话，前端AI路由启用。无需新密钥、数据库迁移或自托管Langfuse。模型内容采集仍默认关闭。

复现/验证：管理员打开 `/admin/trace`，默认近24小时，选择近7/30天并刷新、下一页/上一页。列表显示实际时间、操作名、模型、耗时、输入/输出/总token与观测级别；未记录用量不可显示真实0。当前页摘要不能当全窗口总量。接口 `/agent/api/v1/observability/observations` 的时间/页大小/游标必须有界，游标翻页保持时间边界，切换过滤清空游标。公网匿名/注销旧token由网关401，有效学生/teacher令牌业务码403；控制器直接请求时，无管理员JWT（包括仅内部凭证）403且不请求Cloud。后端302项、前端126项及权限契约/build/lint/体积门通过；生产HTTPS21项（业务只读，另含登录/注销）通过。登录后的浏览器视觉检查待验证，证据以[交付记录](./docs/production-tests/2026-10-08/LANGFUSE_INLINE_REPORT.md)为准。

排错：配置缺失、Cloud鉴权失败/限流/超时分别显示固定错误，浏览器不能含secret、input/output、metadata、用户标识或错误正文。无记录与读取失败有不同状态。默认观测级别不等于业务成功；agent.loop为独立根观测，模型/token未记录。旧SDK或未使用v4摄取头的历史数据可能延迟15分钟可读，本项目当前出口已带v4头。保留次要Cloud链接用于原版高级功能，但站内读取不需要Cloud登录cookie。

发布仅更新agent-service与frontend镜像，保留字体、环境变量、TLS与PVC。发布前验证准确原镜像与运行层，确认没有在途Agent/PDF，短暂维护窗口停止外部请求；失败恢复准确旧镜像及已复制源码，确认Ready后再恢复网关。无需SQL迁移或数据库恢复。

完成于2026-10-08：标签 `prod-langfuse-inline-20261008-v1` 已发布，两个应用的实际镜像/运行层校验通过，网关恢复，17/17 Deployment与Pod Ready、8 PVC保留。公网近7/30天各11条，24小时0条；游标两页、参数边界、角色权限、既有用户接口及注销后的401均通过。未触发模型/Agent，无业务写入或SQL迁移。验收账号会话已全部注销，浏览器视觉复核需要正常管理员登录。

实际最终镜像：agent-service为v1，frontend为 `prod-langfuse-inline-20261008-v2`。首次v1前端入口SHA与manifest不符，引用旧资源，静态发布未通过。重新构建时刷新全部构建输入mtime、禁用层缓存，导入前检查镜像内全部64个文件，上线后对active Pod逐文件SHA核对并确认镜像config/rootfs一致；最终公网64/64文件HTTP200/MIME/SHA及三个HTML入口均通过，17/17工作负载Ready。不能只凭镜像名、Ready或新trace文件可GET就认定首页入口正确。此过程未再次修改后端/SQL。

入口排错时对比发布包 `frontend/index.html`、构建目录、独立Docker镜像、实际active Pod和公网的SHA，以及HTML所引用脚本/样式的MIME。本次固定mtime且同名同长度文件可能被BuildKit增量同步视为未变，属于推断；刷新输入mtime后仍须逐文件验证，`--no-cache`单独不足以替代字节校验。服务器修复记录及静态聚合结果见交付记录。禁止用忽略MIME、手工修改运行容器或关闭浏览器安全校验掩盖发布错误。

服务器聚合验收命令（不打印凭据或观测正文）：

```bash
python3 /opt/resource-profile/.local-run/kamatera/langfuse-inline-readiness-20261008.py
python3 /opt/resource-profile/.local-run/kamatera/langfuse-inline-smoke-20261008.py
```

第二条只做登录/注销及只读API；同一账号单会话策略可能使浏览器中的旧会话失效，测试后需正常重新登录。验证报告只保留字段契约、固定断言、数量和状态码。

回滚预案（待验证，未实际执行）：先按同一发布门确认无在途Agent/PDF、暂停网关，再将两个Deployment镜像恢复为下列已保留标签并等待Ready；按当前版本与 `source-before.tar.gz` 的路径清单恢复公开源码，移除本次新增文件，保留私有env/PVC。确认两应用实际运行层与健康检查后恢复网关原副本数。不要对已经部署的发布目录重复运行 `deploy` 阶段。

```bash
k3s kubectl -n educare set image deployment/agent-service agent-service=edu-portrait-kamatera-agent-service:pre-prod-langfuse-inline-20261008-v1
k3s kubectl -n educare set image deployment/frontend frontend=edu-portrait-kamatera-frontend:pre-prod-langfuse-inline-20261008-v1
k3s kubectl -n educare rollout status deployment/agent-service --timeout=600s
k3s kubectl -n educare rollout status deployment/frontend --timeout=600s
```

回滚源码备份位于 `/opt/resource-profile/.local-run/kamatera/prod-langfuse-inline-20261008-v1/source-before.tar.gz`；部署完整配置与凭据留在VM私有目录，不能作为脱敏报告导出。

## 维护记录：DEMO-DATA-20261009（2026-10-09）

启动前提：用户确认目标库仅含演示/测试业务；完整读取实际字段与最大 ID；只读预检无在途 Agent/PDF；合成载荷独立验证和破坏性输入测试通过。工具仅作用于 Kamatera `educare` namespace 的 `edu_portrait`，不触发模型/向量写入。操作说明与生成命令见[sql/demo/README.md](./sql/demo/README.md)。

VM 应用命令（具体执行结果见交付报告；此处不是已执行证明）：

```bash
python3 scripts/demo_data/audit.py --output-dir /opt/resource-profile/.local-run/demo-data-20261009
python3 scripts/validate_demo_data.py --dataset /opt/resource-profile/.local-run/demo-data-20261009/dataset.json --audit-before /opt/resource-profile/.local-run/demo-data-20261009/audit-before.json
python3 scripts/demo_data/apply.py --dataset /opt/resource-profile/.local-run/demo-data-20261009/dataset.json --audit-before /opt/resource-profile/.local-run/demo-data-20261009/audit-before.json --state-dir /opt/resource-profile/.local-run/demo-data-20261009/application --apply
```

通过判据：80/1200有效档案、4年级/8专业、1281账户、唯一身份归属；所有生成字段存在、范围/学期/假期/GPA/学分与答卷/评分一致；业务精确读回与载荷摘要一致；管理员凭据/权限/迁移与原始操作日志不变，只有两演示用户的明确展示列可变。完整备份须在 VM 隔离 `network=none` MySQL 实际恢复，所有表内容摘要一致，再试导入通过后才生产提交。commit 内 `DEMO_DATA_APPLIED` 真实操作标记和私有 receipt 证明幂等，恢复原副本/HPA后 Ready 并公网只读冒烟通过。

排错/回滚：只读预检或离线校验失败即拒绝，不扩大表白名单、不关闭外键；提交前任意错误同连接ROLLBACK并核对原计数/摘要后恢复应用。提交未知或提交后SQL校验失败保持维护，用私有备份原样恢复 edu_portrait 后逐表比对、再恢复原运行状态；不得把“最后一条SELECT成功”当整批成功。外部真实模型验收、PDF或故障回滚演练未执行时必须报告“待验证”。备份留VM且不提交Git/下载原始业务行，保留到本批次验收收口；无需更新容器镜像或重建PVC。

当前执行证据、确切备份位置、生成条数、测试和生产API结果统一见[DEMO_DATA_REPORT.md](./docs/production-tests/2026-10-09/DEMO_DATA_REPORT.md)。实际登录/注销会增加真实日志并撤销相应旧浏览器会话，用户正常重新登录即可。

完成于2026-10-09：离线15项/应用Mock15项/恢复分组与Long契约小检查通过；VM完整备份已实际network=none恢复并试导入，生产提交前后全内容核验通过，幂等返回ALREADY_APPLIED；90公网检查通过、0失败，17Deployment Ready/8PVC Bound及HPA恢复。备份确切路径与SHA、90项证据见交付报告。首轮脚本未处理既有Long字符串导致误判，后按字段白名单规范后复跑通过，不能将误判报告作产品缺陷结论。真正生产故障回滚和登录后逐页视觉验证仍为待验证；不需要重建镜像/PVC、全库初始化或清缓存。

## AI-PROD-TEST-20261011：生产页面AI重点验收（2026-10-11）

状态：**本轮只测试和提出修复计划，未实施业务修复或重新部署**。详细问题、截图及脱敏证据见 [AI_REPORT.md](./docs/production-tests/2026-10-11/AI_REPORT.md)。完整生成尚未通过；三个实际页面任务 task 3 / student 750、task 4 / student 346、task 5 / student 175 均首轮 `NonTransientAiException`，loop结果 `TOOL_ERROR` / iterations=1、任务终态FAILED，risk/plan/audit及RAG搜索均未形成。普通任务/观测学生请求为HTTP200业务403，但学生SSE确实HTTP200且收到他人task 5终态。修复后回归与回滚演练均**待验证**。

启动前提：既有Kamatera HTTPS入口、有效管理员与独立学生会话、已核对来源的合成学生档案、当次具体资料模型外发授权；正常PDF/反馈需由真实任务形成有效方案。凭据只在既有受限配置/浏览器正常登录中使用，不进入命令行、截图、报告或控制台输出。AI预警输入是 `student_info.id` 主键，不是学号或登录账号ID；不为测试无效ID、分页或限流额外提交大量任务。

页面复现与后续修复验收步骤（当前执行证据以报告为准）：

1. 管理员打开 `/agent/warning`，选择已核对的合成学生ID，点击“触发分析”→“立即触发”；记录taskId、提交时间和状态。提交成功不等于生成成功，当前共享冷却为60秒，多轮任务允许有界等待，loop最多8轮/900秒。任务状态、工具实际执行与最终产物分别记录；不要重复点击制造额外模型调用。
2. 打开 `/agent/report/{taskId}` 并刷新，核对学生归属、风险/方案、真实RAG snapshot与审核。当前FAILED页面只显示“系统异常”和未执行阶段，不能据此判定具体HTTP400/429。任务页03:49:56与本地追踪11:49:56相差8小时是已观察问题，后续修复需明确带偏移时间。
3. 管理员打开 `/admin/trace`，切换24小时/7天/30天、刷新与分页，查本次新时间窗。站内观测级别 `DEFAULT`不等于任务通过；没有usage时须显示未知。本轮三个`agent.loop`的metadata实际已包含`status=TOOL_ERROR`、`iterations=1`，当前站内代理不读取metadata；未来只白名单映射这两个固定技术字段，不能开放自由metadata。保留仅安全元数据的隐私边界，不把旧trace或模型目录GET200算作新任务成功。
4. 用有效学生会话对task列表、详情及observability做有界只读请求，应拒绝。SSE新连接 `/api/agent/api/v1/warning/stream` 预期同样拒绝；本轮实际返回hello后又收到其他学生终态，已确认缺口。回归还须验证已有连接在注销、角色撤销和会话替换后停止事件分发，当前此生命周期修复尚未执行。
5. 只有真实终态及有效方案后测试一次PDF与明确标注“合成演示验收、未实施真实干预”的反馈。当前三个任务没有产物，正常PDF/反馈均记为**依赖阻塞、未执行**，不能说测试失败或用假终态补齐。

只读运行验证命令（VM，命令本身不输出秘密）：

```bash
k3s kubectl -n educare get deployments -o custom-columns=NAME:.metadata.name,DESIRED:.spec.replicas,READY:.status.readyReplicas,AVAILABLE:.status.availableReplicas
```

以下SQL应通过既有受限MySQL客户端配置执行；只输出任务元数据及产物长度，不打印完整模型/工具资料。当前已确认FAILED的`completed_at`为空，具体逐任务证据以报告为准：

```sql
SELECT id, student_id, status, completed_at IS NULL AS completion_time_missing,
       CHAR_LENGTH(COALESCE(risk_analysis_result, '')) AS risk_bytes,
       CHAR_LENGTH(COALESCE(intervention_plan, '')) AS plan_bytes,
       CHAR_LENGTH(COALESCE(compliance_audit, '')) AS audit_bytes
FROM agent_task WHERE id IN (3, 4, 5) ORDER BY id;
```

已验证基础路径：MCP七工具目录成功、Groq models经httpx GET200且模型存在、17个Deployment Ready。独立RAG公开合成查询HTTP200、0.462秒；embedding缓存从不存在新增为1024维非零有限向量，证明Cloudflare BGE-M3真实调用，dense召回三个chunk且fallback=false，机械通路验收（本地测试产物，不入库：`./docs/production-tests/2026-10-11/RAG_PROBE.json`）已通过。这不是失败Agent已执行RAG或检索质量通过的证据。Milvus逻辑`count(*)`严格核验仅**3条逻辑活跃合成探针（物理9）**，case/psychology/policy各1条、success0；物理统计含旧/删除记录，不称九条有效知识。存储source非空、非URL且为测试marker，[KnowledgeChunk](./ai-inference-service/app/api/rag.py)只有source_db没有source，需记录来源标识投影缺口，不能说有效URL被删除或为探针编造链接。

预警表格视觉复现：1023px浏览器窗口进入 `/agent/warning`，查看“创建时间”和固定“操作”列；已观察到表头“操作创建时间”与时间覆盖“查看报告”，需要横滚。拟保障固定列背景/层叠不透底及横滚可用，受影响仅Warning.vue，无架构影响。修复后需在该实际宽度及较宽窗口检验表头/按钮可读和横滚中固定列遮挡，**回归待执行**。

修复后的通过判据（**待执行**）：学生SSE拒绝且无其他学生事件；权限/会话变更关闭或失效既有连接；至少一次已授权真实Agent工具调用成功、合法risk/plan落库，按风险等级正确审核；所有终态有完成时间且同一时刻跨页面/PDF/trace一致；HTTP/error code/失败阶段能安全关联task/trace，站内status/iterations白名单如实显示；RAG来源按真实存储标识投影并区分URL；正常PDF与反馈完成后才计入成功；预警表格固定列在实际1023px窗口和横滚中不透底、按钮可读可用。机械探针通过不能替代真实知识相关性验收。

排错与待实施顺序：先修SSE角色/事件scope/会话生命周期，再补固定失败字段和安全HTTP状态关联，然后核验生产首轮实际协议并针对证据修复，最后统一终态时间与RFC3339时区。`TOOL_ERROR`是当前loop结果名，首轮异常不代表MCP已被调用；本轮没有准确HTTP状态日志，不从旧400/429、模型目录成功或工作负载Ready推断根因。保留严格schema、最终产物校验、共享冷却/重试上限以及不采集内容；不打印全env、JWT、供应商原始请求/响应或心理资料。

回滚：本轮没有新镜像、配置或迁移可回滚，保留三个失败任务和真实操作记录，不删除数据库行、重建PVC、清Redis或切换旧schema来消除失败。未来修复发布前须确认无在途Agent/PDF，留准确原镜像/配置；若引入失败字段迁移，保留向后兼容列和数据，只按验证过的发布计划恢复应用，不做在线破坏性降级。该未来回滚预案及修复回归尚未执行；R-5.2/R-5.3/R-5.4/R-6/B-2状态不因本次基础验收改变。

## AI-PROD-FIX-20261011：AI权限、诊断及来源修复（2026-10-11）

当前已发布：受影响后端完整回归、生产兼容迁移、五组件更新和公开语料导入/精确旧探针隔离完成；随后GROQ-SCHEMA-FIX-20261011已定位并修复严格schema编译400；完整学生产物仍待具体资料授权验收。以下执行预案未逐项注明结果时，应以末尾实际证据及AI_FIX_REPORT为准。历史复现见[AI_REPORT](./docs/production-tests/2026-10-11/AI_REPORT.md)，修复映射与最新结果统一维护于[AI_FIX_REPORT](./docs/production-tests/2026-10-11/AI_FIX_REPORT.md)。不以本地Mock响应、Ready、模型目录200或提交task成功替代真实生成验收。

启动前提：保留当前17个Deployment、准确旧镜像/公开源码和VM私有数据库备份，确认无在途Agent/PDF后才进入发布维护；使用既有受限MySQL配置与ADMIN_TOKEN，不打印env/JWT/钥匙或业务正文。新增四列先迁移，再发布依赖该实体的agent-service；同步frontend、受影响Python/MCP及data-service制品。没有更换供应商、收费资源、TLS/PVC/凭据或业务初始化的需求。数据库备份及恢复验证、镜像更新和实际生产结果尚未执行时写“待验证”。

已执行的目标回归：SSE/会话26项、前端28项及build通过，Python59项中58通过/1真实Langfuse SDK可选依赖测试跳过，公开语料工具4项通过；实际Spring AI本机HTTP transport的选项合并/序列化及安全错误分类通过。可复现命令（项目根；运行环境需JDK21、项目Node及Python最小依赖；命令自身不构成已通过证据）：

```bash
cd backend
mvn -pl agent-service -am test -Dtest=WarningSseSecurityTest,AgentSelfAuthFilterTest,StructuredRequestTransportTest,LlmRequestPolicyTest,AgentTaskTerminalTest,RagSnapshotCollectorTest,LangfuseObservationServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

```bash
cd frontend
npm run test:agent-report
npm run test:observability
VITE_AI_ENABLED=true VITE_API_BASE_URL=/api VITE_LANGFUSE_URL=https://jp.cloud.langfuse.com npm run build
```

```bash
cd ai-inference-service
../.local-run/ai-fix-20261011/python-venv/bin/python -m unittest discover -s tests -v
```

```bash
python3 scripts/public-rag-corpus-test.py
python3 scripts/public-rag-corpus.py check
```

SQL迁移流程（**生产及隔离恢复/幂等已通过**）：沿用版本化迁移工具，不对已有数据库使用`--initialize`、不重放种子。新文件[20261011_01_agent_diagnostics.sql](./sql/migrations/20261011_01_agent_diagnostics.sql)与[10_agent_diagnostics.sql](./sql/init/10_agent_diagnostics.sql)新增failure_code/failure_stage/failure_http_status/trace_id；对旧空终态使用其既有updated_at补近似完成时间，不改写历史状态、原因或trace。应保留原updated_at，不让其ON UPDATE属性将历史待办变为迁移执行时间。迁移前后保存任务元数据摘要和库备份，不下载完整合成业务行到公开目录。

```bash
python3 scripts/apply-sql-migrations.py --transport k3s --target deployment/mysql --namespace educare --database edu_portrait
```

通过受限数据库连接做如下只读验证，并重跑迁移证明幂等（**本批次已通过**）：

```sql
SELECT COUNT(*) AS diagnostic_column_count FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_task'
  AND COLUMN_NAME IN ('failure_code','failure_stage','failure_http_status','trace_id');
SELECT id,status,completed_at,updated_at,failure_code,failure_stage,failure_http_status,trace_id
FROM agent_task WHERE id IN (3,4,5) ORDER BY id;
SELECT COUNT(*) AS missing_terminal_end FROM agent_task
WHERE deleted=0 AND status IN ('COMPLETED','FAILED','REJECTED') AND completed_at IS NULL;
```

通过判据：四列存在；旧三失败行仍FAILED，新增原因/HTTP/trace保持未知，旧空完成时间等于迁移前updated_at近似值，updated_at保留；新增真实终态同条SQL写UTC结束，重复complete/fail不覆盖时间、状态或首次诊断，活动/逻辑删除任务不被标完。迁移已应用的文件不得事后改checksum；必要修正另建增量迁移。

公开语料流程（**本批次已实际导入12条、精确隔离3探针**）：先核验/私有备份现有Milvus逻辑行、原source及向量；若隔离旧探针，只处理已确认探针的精确chunk_id并记录数量，不能删来源未知文档。工具只替换自己的12个稳定doc_id，不重建collection。审核`export`中的公共正文、来源、非商业用途及虚构场景标记后，再在应用Pod内通过本机管理API导入：

```bash
python3 scripts/public-rag-corpus.py export
k3s kubectl -n educare exec -i deployment/ai-inference-service -- python - apply --expected-sha256 81606c2d5827f7a38ad09a10ff9f37b0c3309fbeb3e87cf81ba68395835eecbd < scripts/public-rag-corpus.py
```

ADMIN_TOKEN来自Pod的既有私有环境，不传命令行；配置缺失就失败，禁止为导入放开鉴权。工具无重定向、单次请求有界，不调用Groq；12份公共正文由应用发往既有Cloudflare embedding，真实入库条数与费用额度需实际核验。重复相同payload应skipped，metadata修正应替换而非静默跳过，成功upsert更新collection revision使API/MCP检索缓存不读旧结果。现有source64字节、title255字节、content4096字节边界超出会422；应迁移schema或调整合法切片，不能裁坏URL。原已经截断的来源不能从标题补造。

导入验收（**API/MCP小样本和来源已通过，完整dense baseline及真实报告仍待验证**）：逻辑有效doc/chunk至少本批12条，policy/psychology/case各4，success0如实保留；针对挂科/资助/保密转介/压力睡眠/宿舍适应做公共查询，记录是否命中适当collection、doc_id及完整source_url，fallback/精排/实际vector维度与cache分别验证。API、MCP、Java snapshot、报告应保持同一出处；URL为空保持未知，非URLmarker不渲染成链接。来源只支持所述原则，演示场景不能显示为真实成功个案；少量公开知识及机械召回不能替代完整dense质量baseline与专业审核。

生产复验（**1/4/5的角色/列表报告待办观测/视觉已通过；新生成产物/trace/成功PDF与反馈仍待验证**）：

1. 有效学生SSE新握手403，无hello及他人事件；管理员/获准教职工正常连接，JWT失效、注销、会话替换或撤权后下一事件不再发送，最迟下次30秒心跳清理。Redis不可用新握手503、旧连接关闭，不能fail-open；前端EOF退避重连，401/403停止。
2. 在新的具体外发授权内，先有界真实协议诊断，再运行具体合成学生任务；记录实际安全HTTP类别与失败阶段。如仍失败，按INPUT_BUDGET/OUTPUT_BUDGET/COOLDOWN_UNAVAILABLE/PROVIDER_JSON_SCHEMA/RATE_LIMIT/NETWORK/TOOL/FINAL_VALIDATION区分，不记录原始error/failed_generation或完整请求。Groq模型目录成功不代表JSON生成支持当前请求。
3. 至少一次真实MCP工具执行及合法risk/plan产物落库；按风险决定RAG/审核或合理短路。新task.trace_id与真实Langfuse根trace对应，页面/观测显示固定状态、iterations、类别/阶段/HTTP；DEFAULT不是任务成功。旧未知原因不被推断填充，内容捕获保持关闭。
4. 同一任务列表、报告、Agent待办、观测及PDF均显示Asia/Shanghai UTC+8，API为Z；别把学期/问卷校园历史日期再次加8小时。失败终态有结束时间，近似历史补值不宣称精确耗时。
5. 1023×1104、宽桌面、收起侧栏和横向滚动检查固定列背景、表头及“查看报告”按钮；截图不含登录凭据。真实有效终态后再做一次成功PDF/中文排版和明确演示的反馈，不用假成功任务绕过依赖。

外发约束：协议探针原先因工程MCP/schema授权不足被拒绝；用户随后明确授权，GROQ-SCHEMA-FIX-20261011已执行两次公开协议诊断并修复schema400。原学生三次及本次两次工程协议次数均已用完，新的完整学生验收仍待具体资料授权；不跨用途复用或将等待时间当批准。

回滚预案（**待验证**）：恢复发布前实际保存的准确应用镜像与公开源码，先确认健康/角色边界再恢复外部请求；保留新增兼容列和旧失败任务，不DROP列、清Redis或重建PVC。语料只回滚本批明确doc_id并恢复旧探针私有原向量/出处快照；回滚后更新检索revision或等待对应缓存到期，不能flush所有会话缓存。没有VM真实备份和镜像标签证据时不得填造路径或声称演练通过。R-5.2/R-5.3/R-5.4/R-6/B-2保持未完成。

### AI-PROD-FIX-20261011补记：固定旧探针两阶段隔离（2026-10-11）

[隔离工具](./scripts/public-rag-probe-quarantine.py)与[10项离线回归](./scripts/public-rag-probe-quarantine-test.py)已完成本地验证；以下backup/apply已生产执行通过；restore仍**待验证**。范围只有三个精确chunk_id：`accept_case_20261007_0000`、`accept_policy_20261007_0000`、`accept_psychology_20261007_0000`。必须同时匹配旧source `synthetic-acceptance-20261007`及已核对的旧固定标题/正文；任何同ID不同来源、重复行、改动正文、非有限/非1024维向量都停止。没有处理带其他后缀历史探针、未知source、公开新语料或success集合的需求。

本地只读审核及离线测试：

```bash
python3 scripts/public-rag-probe-quarantine.py check
python3 scripts/public-rag-probe-quarantine-test.py
```

维护期不要同时upsert这三个固定文档。将已审核的脚本复制到当前ai-inference-service Pod `/tmp/public-rag-probe-quarantine.py`，在原Pod的`/app`配置环境运行，先只读backup。以下命令在VM执行；运行前按实际部署目录选择私有VM目的地，工具文件也须先复制到Pod。不得把完整备份写入Git、公开测试目录、聊天或页面：

```bash
k3s kubectl -n educare exec deployment/ai-inference-service -- env PYTHONPATH=/app python /tmp/public-rag-probe-quarantine.py backup --file /tmp/rag-quarantine/probes.json
```

backup输出仅`backup_sha256`、行数、维度和`mutation=false`；文件内保存实际PK、chunk_id/title/content/source和实际向量，不包含账户密钥。文件0600、所在目录0700，新建排他文件+fsync+checksum读回，路径含symlink会拒绝。Pod临时目录不能作为唯一耐久备份，先复制到VM：

```bash
install -d -m 0700 /opt/resource-profile/.local-run/ai-fix-20261011/rag-backup
k3s kubectl -n educare exec deployment/ai-inference-service -- cat /tmp/rag-quarantine/probes.json > /opt/resource-profile/.local-run/ai-fix-20261011/rag-backup/probes.json
chmod 0600 /opt/resource-profile/.local-run/ai-fix-20261011/rag-backup/probes.json
sha256sum /opt/resource-profile/.local-run/ai-fix-20261011/rag-backup/probes.json
```

确认VM文件的SHA256等于backup输出，再把该真实值作为下列占位符替换，不从工具fixture digest或公开语料digest代替私有备份digest：

```bash
k3s kubectl -n educare exec deployment/ai-inference-service -- env PYTHONPATH=/app python /tmp/public-rag-probe-quarantine.py apply --file /tmp/rag-quarantine/probes.json --expected-sha256 <实际私有备份SHA256>
```

apply在变更前重验三集合映射和完整记录；只按实际PK+source+精确chunk_id删除、flush并Strong查询确认消失。Redis需可用，持有专用短时互斥锁；每集合前后分别轮换`edu:rag:revision:{collection}`，清该固定doc旧/current两版幂等hash，绝不FLUSHDB/SCAN全缓存。后轮revision防止变更窗口重新填充旧结果。通过输出是`verified=true`、首次`changed_rows=3`且`revision_updated=true`，之后逻辑活跃计数应只反映公开知识，不用num_entities物理统计证明删除。

遇到失败只输出固定`error_code`与`partial_mutation_possible`，保留备份并检查当前状态。Redis异常在变更前停止；变更后的异常不能当作隔离成功，可用同一digest再次apply，已经精确消失的行跳过并刷新revision。记录被别人修改或身份冲突时停止人工核查，不能绕过校验或扩大删除条件。

必要时把VM耐久备份安全复制回当前Pod相同私有路径（0600、父目录0700），使用同一SHA256执行`restore`子命令。恢复无需调用Cloudflare/Groq，直接写回备份里的实际向量与字段，再Strong核对并刷新revision；有同身份不同资料时拒绝覆盖。Milvus `auto_id=true`不能复用原物理PK，因此新物理ID不同，逻辑chunk_id/source/title/content及实际向量相等是恢复通过判据。重复restore会核验既有相同资料而跳过；不清空collection/PVC，也不修改旧失败Agent任务。

### AI-PROD-FIX-20261011实际交付记录（2026-10-11）

部署/API（本地测试产物，不入库：`./docs/production-tests/2026-10-11/AI_FIX_DEPLOYMENT.json`）、SSE角色（本地测试产物，不入库：`./docs/production-tests/2026-10-11/AI_FIX_SSE_ROLES.json`）、公开RAG（本地测试产物，不入库：`./docs/production-tests/2026-10-11/RAG_FIX_REGRESSION.json`）、重复导入（本地测试产物，不入库：`./docs/production-tests/2026-10-11/AI_FIX_CORPUS_IDEMPOTENCE.json`）及UI/截图清单（本地测试产物，不入库：`./docs/production-tests/2026-10-11/AI_FIX_UI.json`）已实际验证。17/17服务、17/17 Pod Ready、0重启；agent/frontend最终v2，data/Python/knowledge MCP v1。SSE匿名401、学生header/query403、teacher200且注销4,979ms EOF、旧JWT401；管理员失败PDF创建HTTP200/业务409、缺失任务404、导出表无新增；任务UTC Z与校园UTC8跨页面一致，旧结束时间仅近似补齐。真实新业务warning推送未生成、真实新失败Cloud trace关联待授权。

验证环境JDK21：受影响后端common/data/agent clean verify381及安全覆盖门通过；后续终态11含新增3、导出真实MVC7/package及四套Export34通过。前端目标28/build/lint通过，Python58通过/1可选真实SDK测试跳过，公共语料4和探针工具10通过。首轮HTTP fixture仅本机，不冒充Groq或生产七工具schema接受。

首次前端产物漏VITE_AI_ENABLED，已补发frontend v2并实际恢复入口。发布构建须带上述AI=true、API=/api和既有Cloud URL，不能只在Pod里设VITE值；发布后验菜单及任务路由。导出排错若Result业务码409，表示无有效COMPLETED/REJECTED产物，不应重试创建或改FAILED为成功；[ExportController](./backend/agent-service/src/main/java/com/edu/agent/controller/ExportController.java)已保留该业务码，未知异常只记录类名，不读取日志原始模型正文。

VM实际耐久私有备份位于/opt/resource-profile/.local-run/kamatera/prod-ai-fix-20261011-v1/，文件database-before.sql.gz、source-before.tar.gz、rag-probe-backup.json；该批次migration-pass.json和部署证据保存SHA及通过判据。MySQL恢复到自建隔离库、重复迁移、20表记录数及历史updated_at保留已通过，隔离库已清理。旧三探针备份SHA256为2f79823172b662c544d7590d5c991d52e94ad45392c80e16a5c2f9f0f56603c8，private文件0600，实际精确changed_rows=3、revision更新，0embedding/Groq；向量NumPy兼容修正后再执行，未扩大删除条件。后续restore需从该VM备份复制至当前Pod私有路径，使用同一digest；auto-ID会变化、逻辑字段及实际向量必须相等，**生产restore仍未演练**。

回滚使用已保存pre-prod-ai-fix-20261011-v1准确旧镜像及源码，保留兼容新增列和真实FAILED任务。frontend v1为遗漏AI开关的中间制品，最终恢复宜用发布前稳定pre镜像；agent v1仍保留核心修复，但导出code修补在v2。**应用镜像回滚待验证**，不能因有标签就宣称已演练。正常成功PDF/反馈需新的合法模型产物，R-5.2/R-5.3/R-5.4/R-6/B-2继续未完成。测试角色会话已注销、临时浏览器/尺寸覆盖和本机凭据副本已清理。

## GROQ-SCHEMA-FIX-20261011：严格schema首轮400（2026-10-11）

复现及边界见[诊断修复报告](./docs/production-tests/2026-10-11/GROQ_SCHEMA_FIX_REPORT.md)。启动前提：既有Groq配置、两个生产MCP可tools/list、明确工程协议外发授权；此次最多2次且已用完。禁止把协议诊断自动升级为新学生资料外发。第1次400明确定位 `/properties/action/anyOf` 的无properties包装；第2次仅展平后200。离线生成请求与成功请求JSON相等、响应Draft202012Validator有效。验证命令：JDK21下 `mvn -o -Daether.syncContext.named.factory=noop -pl agent-service -am verify`，实际common80+agent307共387，0失败/错误/跳过；schema red/green及固定400/nullcode类别通过。不得因为公开query有空格改写重复外发；仅规范空白，仍要求实际schema、工具名、参数类型和终止状态。

发布预案：保留实际运行v2镜像与两份源码，只把两处class替换入原JAR，核对原/新rootfs、配置及entry摘要，更新agent-service为prod-ai-fix-20261011-v3；不重建其他服务、不重放初始化或修改既有新增列。检查17部署Ready/0重启、匿名入口401、历史FAILED/结束时间和任务数量未变；新学生任务/trace/成功PDF仍待授权验收。必要时set image回v2并核对就绪，schema400将随之恢复；不DROP列/PVC。生产发布及上述只读结果通过，实际运行JAR与审核制品摘要相等、17/17 Ready/0重启、登录200/匿名task及SSE401、旧FAILED3个/终态缺失0/导出0；部署证据（本地测试产物，不入库：`./docs/production-tests/2026-10-11/GROQ_SCHEMA_DEPLOYMENT.json`）。应用回滚仍未演练。

## AI-FULL-ACCEPT-20261011：完整学生Agent、PDF和反馈验收（2026-10-11）

本轮授权已取得，沿用方柏言（346）、吴亦竹（750）、罗卓嘉（175）的合成资料，最多3个完整Agent任务。第1次task6/student750已真实首轮FAILED：PROVIDER_JSON_SCHEMA / LLM_REQUEST / HTTP400，trace=e058fadc0cbc4c82a44e292251055bf9、工具执行0；失败计入额度，剩余2次待修复上线后继续。既有两次工程协议诊断额度已用完，不另行外发协议探针。Groq、Cloudflare及Langfuse仍沿用已选提供商；Langfuse内容采集关闭。

排错前提已核对：agent v3运行JAR摘要一致，structured=true、Groq GPT-OSS-120B/low/max_tokens2400/输入预算18000/cache_prompt=false。实际McpSchema.Tool → SyncMcpToolCallback（provider noPrefix）→ responseFormat与上一轮Groq200请求schema结构相等，canonical SHA256为48bdc5fa9f435bd9a2926d9e0ae4a7266da8e703eb95a75176dc054c9fd32da1；in-memory transport connect/send/unmarshal及model/tool全0。task6现有诊断不能区分schema编译与生成校验拒绝，不把adapter差异或上一轮包装问题直接写成本次根因。

[提示构造](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java)已确认缺字段示例、字符串final_answer与可省略plan字段指令和[严格schema](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoopOutputSchema.java)冲突。修复目标为完整四字段示例、未用字段null、最终对象直接嵌入及工具参数与实际schema一致。[错误处理](./backend/agent-service/src/main/java/com/edu/agent/config/LlmResponseErrorHandler.java)/[类型化异常](./backend/agent-service/src/main/java/com/edu/agent/config/LlmCallException.java)新增reason仅允许SCHEMA_COMPILATION、JSON_GENERATION、UNKNOWN；原始正文、failed_generation、请求及业务资料不进入日志/API/数据库。不关闭strict、不伪造终态、不无限重试。

修复回归命令（JDK21，离线，不调用模型；本条新增修复验证当前**待验证**）：

```bash
cd backend
mvn -o -Daether.syncContext.named.factory=noop -pl agent-service -am test -Dtest=AgentLoopOutputSchemaTest,LlmFailureDiagnosticsTest,LlmCallExceptionTest,AgentLoopFailureReasonTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -o -Daether.syncContext.named.factory=noop -pl agent-service -am verify
```

通过判据：严格schema/本地产物校验保留，所有严格模式示例与必填/可空契约一致，reason只从类型化固定标记读取、未知归UNKNOWN，日志无原始正文/cause/学生资料，回归0失败/错误。离线fixture不替代真实完整生成。发布只更新agent-service审核制品与对应源码，保存当前v3回滚点；不重放数据库初始化、不改凭据/PVC/其他服务。核对新运行JAR摘要、17/17就绪及鉴权边界；新修复发布当前**待验证**。

剩余2次任务串行执行，遵守共享冷却、有界429重试与deadline；分别记录触发时间、真实task/trace、阶段及产物校验。若仍400只检查固定reason，不读取供应商自由正文；低风险合法短路按原行为评估，不编造方案。仅对合法产物发起一次PDF创建、有限轮询、下载及文件/页内容核验；FAILED应业务409且无导出写入。反馈使用实际任务提交明确演示反馈并读回/月报核验，不伪造已发生干预效果。新成功任务、PDF、反馈及新trace/报告知识来源链当前全部**待验证**，R-5.2/R-5.3/R-5.4/R-6与B-2仍未完成。

回滚预案（尚未演练）：

```bash
k3s kubectl -n educare set image deployment/agent-service agent-service=edu-portrait-kamatera-agent-service:prod-ai-fix-20261011-v3
k3s kubectl -n educare rollout status deployment/agent-service --timeout=600s
```

同步恢复发布前保存的对应源码，确认运行摘要与v3相等；保留task6及后续真实任务、兼容列、知识库和会话数据，不删表/重置PVC。回滚v3只恢复增量之前行为，不保证解决本次生成400。验收后正式注销测试会话、验证旧JWT401并清理临时浏览器/凭据副本；实际结果在[执行计划](./docs/educare/EXECUTION_PLAN.md)及本轮测试报告同步补记。

### AI-FULL-ACCEPT-20261011进展与下一增量（2026-10-11）

v4提示/三值reason修复已发布：common80+agent336=416项回归通过，最小包6个旧entry+1新增枚举、57,407字节，SHA256=`84619aec50955dd6e58566dbd28828ca9acaedf2a8948e39be8232538cb992f7`，私有deployment-v4.json保存原始发布证据。task7/student175实际UTC07:29:33开始、07:29:37终止，FAILED/iter1、PROVIDER_JSON_SCHEMA / LLM_REQUEST / HTTP400、trace=`628dfb900e364f9090217c163b625d5d`，安全日志reason=JSON_GENERATION。这里只能确认这个任务的生成JSON校验拒绝，不能恢复task6旧子型或把提示矛盾写成根因。最多3次授权任务现已用2，**仅剩1次**，正常PDF/反馈继续依赖真实合法产物。

实际v3类和193依赖的127.0.0.1 HTTP fixture与已200的protocol-request-2完整结构一致（包含messages及选项），strict=true/json_schema/无tools/stream=false；配置及拦截器六类v3/v4 manifest/local target SHA逐字节相等。fixture没有Groq、MCP或业务调用，只排除本地使用不同类或序列化选项的猜测，**不宣称捕获失败生产任务线上请求**。私有结构/摘要证据位于.local-run/ai-accept-20261011，报告只保留固定结论，不复制完整请求至公开文档。

下一增量方案正在落地、回归和v5发布**待验证**：

- [LlmRequestPolicy](./backend/agent-service/src/main/java/com/edu/agent/config/LlmRequestPolicy.java)产出白名单请求契约指标；[LlmResponseErrorHandler](./backend/agent-service/src/main/java/com/edu/agent/config/LlmResponseErrorHandler.java)有界提取失败JSON结构。禁止日志/持久层保存原始正文、失败JSON字段值、系统提示、工具参数、师生档案或密钥；未知形态归固定UNKNOWN，不开放任意字符串诊断。
- [AgentTaskServiceImpl](./backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java)明确首轮调用档案工具action及真实学生ID，后续只能根据实际观察结果推进，不预填工具观察或风险。
- [AgentLoop](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java)仅HTTP400且JSON_GENERATION在整个run纠错1次；同一max8轮/900秒deadline，跨轮不能重置，最后一轮不得越额调用。二次拒绝、编译错误、UNKNOWN或其他错误均失败；不在HTTP层泛化重试所有400，原429有界策略保持。固定纠错提示不携带供应商失败JSON，不改变strict、最终产物和审核校验。

JDK21下使用上节离线目标命令和模块verify；增加本次请求契约白名单/失败结构内容隔离、首轮提示、跨轮仅一次纠错、错误类型限制、轮次/deadline上限回归。**新回归待执行，不沿用416旧结果冒充通过**。发布前核对新制品与实际基线并保存v4源码/镜像，不初始化数据库、不改API/兼容列；v5发布、17/17只读复核与最后任务目前均待验证。

v5若需回滚，当前直接增量回滚点为`edu-portrait-kamatera-agent-service:prod-ai-accept-20261011-v4`，配合上节set image/rollout方法及对应源码，保留v3更早备份。应用回滚未演练，v4已实证仍有生成400，回滚不能宣称恢复成功。最后一次任务后只对合法产物验PDF/真实演示反馈并注销会话；失败仍保留真实终态，不额外补授权任务。R-5/R-6/B-2保持未完成。

### AI-FULL-ACCEPT-20261011最终验收补记（2026-10-11）

v5发布及新增回归已实际完成：common80+agent370=450项verify全部通过；包4个class变更、无new entry，81,778字节，SHA256=`205e3946f05b08588fc49bfd8c1e90386b1bbdd35558e137fbb0d7b8003b914c`。上节本增量回归/发布待验证状态以此更新为准；[报告](./docs/production-tests/2026-10-11/AI_FULL_ACCEPTANCE_REPORT.md)与安全证据（本地测试产物，不入库：`./docs/production-tests/2026-10-11/AI_FULL_ACCEPTANCE.json`）保存具体通过及失败范围，私有发布原始证据仍在.local-run/ai-accept-20261011。

task8/student346实际UTC07:38:50开始、07:39:51结束，FAILED/iter2、trace=`f6e6f8a52f7b47e48739656fb61f7b7d`；两轮均类型化JSON_GENERATION/HTTP400。有限纠错确实只执行1次，第二次拒绝终止同一任务。两条HTTP策略交接前固定日志为JSON_SCHEMA、strict=TRUE、react_schema=true、tools_present=false、stream=false、max_tokens2400。两条失败结构日志为json_parsed=true、thought/plan/action/final_answer四字段存在标记全false；未记录根类型，也无参数或嵌套final校验结果。排错只据这些固定结构指标，不读取/复制原始failed_generation，不推断具体根JSON形态、确切根因或供应商bug；固定交接日志不当作完整线上报文抓包。

业务工具执行0、风险/方案/审核产物均空，RAG未执行；没有可用于PDF或反馈的合法报告。task6/7/8三个本轮授权任务已全部用完，正常PDF/反馈成功路径是**依赖阻塞、未验收**，本轮创建数均0，不能用FAILED任务、假产物或假反馈强行验证。修复450项回归与发布通过并未关闭完整生成故障；AI-PROD-FIX、R-5.2/R-5.3/R-5.4/R-6/B-2保持未完成。后续外部模型或学生资料实测必须取得新的明确授权，不能静默再触发任务；离线诊断可继续。

v5必要回滚的准确目标仍为`edu-portrait-kamatera-agent-service:prod-ai-accept-20261011-v4`，同步恢复v4对应源码并核对运行摘要；保留v3更早备份、诊断列及task6/7/8真实历史，不重放初始化、删除记录或重置PVC。应用回滚未演练，回退v4不能保证业务成功。最终17服务健康、实际产物表计数及测试会话清理由另次只读核查据实补记，当前不预写通过。

AI-FULL-ACCEPT-20261011收尾实测（2026-10-11）：[最终验收报告](./docs/production-tests/2026-10-11/AI_FULL_ACCEPTANCE_REPORT.md)及安全证据（本地测试产物，不入库：`./docs/production-tests/2026-10-11/AI_FULL_ACCEPTANCE.json`）已汇总。实际运行Agent v5 JAR SHA256 03599cb00f989cef0e1706185be5e48d5a4ecdd1a5479ac35ef6cd901c9a9073与审核制品相等，17/17部署和17/17 Pod Ready、0重启；3新任务均FAILED、终态结束时间无缺失、导出/反馈各0。task8真实站内trace为2轮/61,299ms/模型请求失败，DEFAULT级别不表示成功。测试管理员已正常注销、旧JWT401、Redis会话撤销，临时浏览器及本机/服务器临时凭据副本已清理。完整生成、成功PDF/反馈仍依赖阻塞，次数已用完；R-5/R-6/B-2及主修复任务保持未完成，新的模型外发须新有界授权；回滚未演练。

AI-FULL-ACCEPT-20261011下一步候选（2026-10-11）：[两份A/B工程请求](./docs/production-tests/2026-10-11/AI_NEXT_DIAGNOSTIC_PLAN.md)已离线准备；唯一差异为action候选从7工具缩至档案工具，消息/最终schema/选项保持相同，虚构ID0示例经schema校验通过；v5类/资源/依赖一致，本机stub2次、Groq/MCP/业务调用0。外发前核对私有候选摘要；获新授权后每份只1请求、无自动重试/工具执行，HTTP200且完整schema/约定profile action及ID0/null-final通过才记协议通过；400/429据实失败。新外发、完整Agent、PDF及反馈均待验证，纯诊断不改变生产配置，无应用回滚步骤。

### GROQ-400-CAUSE-20261011（2026-10-11）

[原因报告](./docs/production-tests/2026-10-11/GROQ_400_CAUSE_REPORT.md)及脱敏证据（本地测试产物，不入库：`./docs/production-tests/2026-10-11/GROQ_400_CAUSE_EVIDENCE.json`）记录16次工程请求：9个严格200、7个生成校验400、0个HTTP429。A与相同字节O均400；B/K缩工具或仅删raw Schema不足；原A只删system工具文字目录的L成功，实际编译M/独立N/发布后P均成功。400为JSON_GENERATION，固定Schema编译拒绝前缀未出现；B/E/G/I/K/O失败正文为空，不能定位具体内部JSON路径。与先前Schema编译400及429分别排查，不因本轮400降低类型或安全规则。

前提：JDK21、配置模型openai/gpt-oss-120b、现有私有Groq密钥。只发送工程系统/技能提示、工具定义与虚构ID0，不创建学生任务、读取档案或执行返回action/RAG。每份冻结摘要并预占一次，无自动重试；按token重置时间发送。保存固定类型/校验/用量/耗时，不留供应商正文/自由错误文本或密钥。用户已明确允许本轮后续工程请求，历史第三次外发自动审批拒绝已解除；该权限不新增学生完整任务额度。

本机验证命令：`cd backend && mvn -o -Daether.syncContext.named.factory=noop -pl agent-service -am verify`（JDK21）。最终精确候选452例：common80+agent372，失败/错误/跳过均0、覆盖门通过；[提示测试](./backend/agent-service/src/test/java/com/edu/agent/core/AgentLoopPromptContractTest.java)及[真实Spring AI本机传输测试](./backend/agent-service/src/test/java/com/edu/agent/config/StructuredRequestTransportTest.java)确认strict文字目录删除，而正式工具名称、整数类型、必需字段、封闭args、非strict完整目录和空工具提示保留。实际MCP adapter/编译类捕获M，与L逐值相等、相对A只删2413字符工具目录；10类等verify JAR。M/N真实200且schema/profile/ID0/null-final通过后才发布；O原始对照仍400，没有发布失败的K候选。

已发布：`edu-portrait-kamatera-agent-service:prod-ai-accept-20261011-v6`，UTC11:52:40；制品仅更新AgentLoop.class及1源文件，不添加JAR条目，不改DB/PVC/环境或密钥。运行JAR SHA256 `3ecbf71001e7f7e20213798d3c3cb6bbe24da86dff4e3a732f58ea394856471d`与审计制品相等，10使用类等M；UTC12:07:08全部17部署/17 Pod就绪、0重启，登录200及未认证业务401。UTC12:09:54最后工程P为200/919ms，严格Schema及profile ID0/空final通过，未执行action。发布门和部署后工程门均已实测通过，不能把它们当完整学生Agent/P4/RAG/PDF/反馈通过。

回滚：`k3s kubectl -n educare set image deployment/agent-service agent-service=edu-portrait-kamatera-agent-service:prod-ai-accept-20261011-v5`，随后检查rollout与实际运行JAR/就绪；保留v6阶段的source-before对应原源文件。回v5只恢复原服务版本，会恢复其已知AI400；回滚步骤本轮**未演练**。旧task6/7/8仍FAILED，完整业务与R-5/R-6/B-2待新相应授权和真实验收。

## AI-PROD-RETEST-20261011（2026-10-11）：生产复测与剩余问题

结果与证据：[报告](./docs/production-tests/2026-10-11/retest/REPORT.md)、安全JSON（本地测试产物，不入库：`./docs/production-tests/2026-10-11/retest/EVIDENCE.json`）。运行基线17部署/17 Pod、0重启、8 PVC、10应用健康通过；角色45/45、管理员53/58，共103断言98通过。新task9/10/11均FAILED/PROVIDER_JSON_SCHEMA/LLM_REQUEST/400，completedAt有UTC Z，日志带任务标识的iter1/3为JSON_GENERATION，iter2档案成功205/223/213 Java长度单位。无RAG/风险/方案/审核；失败导出业务409，正常PDF/反馈不能执行。工程A–P已通过的单轮对照仅属历史工程判据。

只读复现前提：现有有效管理员会话和真实授权角色，访问当前公网/api；不能在聊天或报告保存JWT、密码、Secret值或模型正文。已实际执行各一次：GET `/agent/api/v1/task/9223372036854775807`→业务500（RT-02，应404）；GET `/agent/api/v1/task/list?page=1&size=3&status=NOT_A_STATUS`→业务500（RT-03，应400）。正常task列表page/size、用户列表、学生/教师与dashboard统计通过。不要用HTTP200代替Result.code检查。修复后须验证这些边界、正常过滤，以及其他riskLevel输入，不把通用500全局改成400。

页面复现已执行：预警中心查看task11→上方安全失败诊断/HTTP400/完成时间正确，阶段1“未执行”（RT-04，实际已执行但无产物）；导出按钮禁用、反馈隐藏。点“查看站内追踪”→精确trace `910687d3225649f589a02f6671166203`仅一条agent.loop，LLM_ERROR、3轮、400与任务吻合，model/token未知；显示全部有另trace的llm.chat，但无共同标识不作任务归属（RT-05）。学生登录到问卷正常，仍有固定数据面板标签，点击被守卫拦截返回问卷（RT-06）。1023×1104截图证明固定操作列不透底、退出弹窗居中；管理员/学生测试会话均已注销，临时页签已关闭。

后续验证**待执行**：RT-01必须覆盖真实首轮、工具Observation后请求、RAG来源、严格产物和真实P4；本轮三次task额度全部使用，不运行历史smoke脚本偷偷创建第四个任务。新增范围明确后，在有效方案上验证真实PDF内容/版面/权限、示例反馈单次提交/绑定/月报/CSV。RT-04验证运行中/失败/历史无诊断/低风险跳审/空召回语义；RT-05验证共同trace与父span、并发及异步上下文隔离、异常清理、未知token、代理无正文；RT-06验证角色切换及固定首页。对应源码和后续选择均见报告§2，相关controller/UI/trace回归尚未修改或执行；R-5/R-6/B-2保持未完成。

排错限制：九条安全HTTP契约和六条拒绝结构没有task tag，只是精确时间窗证据，不能按顺序强行绑定任务或推断生成正文。档案未截断不代表其他尚未执行的大工具没有截断风险；无429只限本轮。可选Secret开关读取/写本地被自动审批拒绝，停止且未绕过；实际权限与会话门已通过功能请求，未知开关值继续标未知。

测试helper排错（已执行）：MySQL JSON_TABLE的CTE推导参数需在函数边界CAST AS JSON（1210/HY000）；Pod按实际app.kubernetes.io/name选择器；Python解析K8s纳秒时间先截到微秒；默认日志到选中任务终态后2秒，排除后来任务。仅修测试helper，真实SQL/限定日志已成功，此工具问题不计生产应用缺陷。

本轮没有新应用/数据库/配置部署，**无需应用回滚**。失败task9/10/11与证据保留，不为让报表通过删除或回填假结果。未来修复应记录新制品与对应源码，必要时回退本轮现有v6基线；该回退未演练，v6仍有真实业务400，不能当AI恢复方案，也不要回退此前已通过的SSE/会话安全改动。

## PAGE-PERF-PLAN-20261012（2026-10-12）：区分页面加载与冷启动

[报告](./docs/production-tests/2026-10-12/PAGE_PERFORMANCE_PLAN.md)、固定字段证据（本地测试产物，不入库：`./docs/production-tests/2026-10-12/PAGE_PERFORMANCE_EVIDENCE.json`）记录已执行只读检查：17/17 Ready、0重启，前端约30.6小时/gateway与auth约70.8小时；内部HTML14–17ms，客户端HTML1.03/1.86s，公共JS压缩232,728bytes、GET1.71/1.28s、HTTP2 200/0redirect。实际响应header为gzip与public,max-age=31536000,immutable。请求独立采样，不加总成浏览器首屏；未登录/读取资料/调用模型，也未抓取过去那次浏览器等待。

复核命令（前述测量已执行；今后采样另记时间）：`curl --compressed -sS --max-time 20 -o /dev/null -w 'dns=%{time_namelookup} connect=%{time_connect} tls=%{time_appconnect} ttfb=%{time_starttransfer} total=%{time_total} bytes=%{size_download}\n' https://resource-profile.45-130-165-168.nip.io/login`。这些时间多为累计值，计算各段要相减；TTFB不是纯后端CPU。测资源必须从当前HTML提取真实hash URL，不猜旧文件；保持域名/SNI，不绕过证书。

后续通过判据/排错：先分“登录框显示前”“点击登录后”“缓存会话恢复”，各测浏览器冷/暖缓存至少3次，记录固定资源timing/可见时间，登录API独列。候选代码回归验证图标/弹窗/菜单、静态压缩字节/依赖与暖缓存；验证timeout/429/5xx/取消/总截止预算、写入不重复、401不清新会话；CDN验证只静态缓存、API不共享、SPA/TLS/登录/SSE保持。上述浏览器采样、实现/回归/部署/CDN均**待验证**，不能标成已提速。本轮无生产变更无需回滚；将来发布保留当前frontend镜像及源码并验证回退，回滚未演练。完整AI400/R-5/R-6/B-2保持未完成。

## PAGE-PERF-FIX-20261013（2026-10-13）：前端加速验证与发布

[交付报告](./docs/production-tests/2026-10-13/PAGE_PERFORMANCE_FIX_REPORT.md)、脱敏证据（本地测试产物，不入库：`./docs/production-tests/2026-10-13/PAGE_PERFORMANCE_EVIDENCE.json`）与浏览器检查（本地测试产物，不入库：`./docs/production-tests/2026-10-13/UI_CHECKS.json`）均为已执行结果：v3在2026-10-13T01:39:46Z部署，102新文件/33旧JS、17/17 Ready、Caddy/PVC及其他镜像保持；实际登录JS/CSS正文315213→164191B，9资源。没有稳定cold/warm FCP/LCP采样，不承诺固定打开秒数。

复核（本轮已执行）：在frontend目录执行npm test、npm run lint:check、npm run size:check。生产build前设置VITE_AI_ENABLED=true、VITE_API_BASE_URL=/api、VITE_SSE_BASE_URL=/api、VITE_LANGFUSE_URL=https://jp.cloud.langfuse.com、VITE_REQUEST_PROFILE=standard、VITE_ENABLE_SERVICE_WARMUP=false、VITE_PREWARM_URLS为空，再npm run build。检查预算门≤200KiB/≤12资源且无chart/table；42请求与27会话回归通过。新[真实模块循环测试](./frontend/tests/request-default-cycle.test.mjs)与[严格timer测试](./frontend/tests/request-policy.test.mjs)分别用旧写法阴性控制复现TDZ和Illegal invocation；只跑Node默认timer不能覆盖Window接收者限制。

实际浏览器判据已通过：先临时仅loopback预览、/api同源代理现有生产API，匿名首帧→admin登录→80教师列表→居中弹窗→注销；发布后在生产新tab复验同流程，student落地问卷/菜单限制与注销通过。现有学生固定数据面板标签RT-06不因本轮通过而关闭。遇到#app空时核对实际hash及真实模块环，不只看HTTP200；登录200却UI无跳转时检查Budget.finish timer自由调用，不先改密码或重发POST。预览进程/测试tabs已关闭，测试会话已注销，凭据/JWT不输出。

最终镜像edu-portrait-kamatera-frontend:prod-page-perf-20261013-v3；精确回滚目标edu-portrait-kamatera-frontend:prod-ai-fix-20261011-v2。在服务器执行k3s kubectl -n educare set image deployment/frontend frontend=edu-portrait-kamatera-frontend:prod-ai-fix-20261011-v2，随后rollout status与匿名登录、表格、退出和17服务检查。v1/v2真实回滚已成功并核对原镜像/rootfs/静态与页面，不能回到失败v1/v2。保留证书PVC/ConfigMap/Secret/数据库，勿运行全栈publish或重放初始化来发布前端。

冻结制品位于/opt/resource-profile/.local-run/page-perf-20261013/prod-page-perf-20261013-v3；同源前端103文件归档在/opt/resource-profile/.local-run/kamatera/page-perf-incoming-20261013-v3/frontend-source.tar，SHA见证据。主服务器源码目录未覆盖，未来重建必须先同步当前已审阅源码，避免旧host源码恢复长重试或大首屏包。CDN未实施；全AI400/R-5/R-6/B-2与主下一步不变，不触发新的模型任务。

## JAVA-AI-MIGRATION-20261015（2026-10-15）：Java AI 运行时迁移与验收

实现与契约见 [迁移记录](./docs/educare/JAVA_AI_MIGRATION_20261015.md)。构建前提 JDK 21、Maven；运行前提 Redis、Milvus 2.4 REST v2、OpenAI 兼容 embedding、agent-service。两个知识服务使用 [Dockerfile.knowledge](./docker/Dockerfile.knowledge)：8090 API/兼容生成，8095 `KNOWLEDGE_MCP_ENABLED=true`、`KNOWLEDGE_API_ENABLED=false`。保留历史服务名 `ai-inference-service`，它现在代表 Java jar。

### 本地验证

```bash
cd backend
mvn clean verify
# 独立知识边界复验：
mvn -pl knowledge-service test
```

部署工具测试：在隔离 Python 环境安装 `scripts/requirements-cloud.txt` 后执行 `python scripts/test-cloud-deploy.py`、`python scripts/test-k3s-deploy.py`。全后端 2026-10-15 首轮 724 测试、零失败/错误/跳过；后续新增恢复幂等回归与最终数量以迁移记录为准。实际 HTTP/MCP 测试使用随机回环端口与合成依赖；真实 Milvus 和生产模型不在这些测试中。

### 部署与通过判据（待验证）

1. 导入维护窗口停止知识写入，保存旧 agent 与两个 Python 服务镜像、环境/清单、完整文档及 Milvus 备份。禁止新旧导入服务并行写；旧 Python 不识别新写锁。
2. 构建 agent 和 knowledge Java 镜像；先启动8090/8095，再启动 agent（仅兼容生成请求依赖 agent，知识服务启动不依赖它）。所有内部端口继续使用私网。配置 `EDUCARE_INTERNAL_TOKEN`、`EDUCARE_ADMIN_TOKEN`、`EDUCARE_MCP_TOKEN`，缺失分别返回503、不匹配401。
3. `python scripts/k3s-deploy.py init-rag` 或现有 Cloud `init-rag` 调用本地 admin 鉴权的 Java 初始化接口；已有集合只验证/加载，不删改 schema。维度不匹配时停止迁移，不能切换 embedding 模型后复用旧向量。
4. 用已授权合成文档验证 none/fixed_size/sentence 导入、同请求 skipped、来源修订重新写入、三 MCP 工具/旧 HTTP 检索结果一致、未知/空来源不编造、真实零召回为空；确认重启后仍可检索。然后验证合法/缺失/错误凭证和8095写接口不存在。
5. 单独授权模型验收后执行完整 AgentLoop、四维审核、报告/PDF/反馈与 legacy 对比，检查 Langfuse `llm.audit/plan/risk/chat`、模型/用量、内容默认关闭；保留 R-5/R-6 未完成，不能把单元测试替代生产证据。
6. 核验两个知识容器的进程均为 Java，无 Python/uvicorn/FastMCP；复测 JVM 内存、服务 Ready、错误率和延迟后，才可以声明生产 Python 退役。

### 排错与回滚

- `/health` 只代表进程存活；真实 Redis/Milvus/embedding 检索单独验收。依赖故障返回503，不能误判为空召回成功。
- 401/503：核对调用方对应内部、MCP、admin 凭证；不要把凭证放入日志/聊天。eval runner 通过环境 `EDUCARE_INTERNAL_TOKEN` 附头，CI 使用 `EVAL_INTERNAL_TOKEN` secret。Cloud/本地容器的 `AGENT_SERVICE_URL` 指向agent内部地址；Render显式使用现有agent地址。
- `Milvus embedding schema mismatch`：核对模型/维度与原集合，先恢复匹配配置，禁止删除集合排错。REST业务错误即使HTTP200也算失败。
- 部分导入失败：旧数据保留但可能多出新片段；检查写锁释放/15分钟租约，重新提交同一完整 doc_id+原文+metadata+策略，不能只补片段。写前清除旧幂等hash，失败后不会误跳过恢复。集合级锁期间检索返回503，导入建议使用维护窗口。
- 回滚需成套恢复旧agent、两个知识镜像与清单；本轮不改Milvus schema。停写后检查是否有未完成导入，必要时按备份恢复目标文档。新Java缓存使用独立命名空间，revision与旧版本共享。不要删除全部Redis键或清空Milvus。
- `verify_prompt_cache.sh` 现在读Java全局累计计数，运行期间须隔离其它生成请求；Groq等不提供本地slot cache指标的供应商不适用该通过阈值。

### LOCAL-JAVA-AI-TEST-20261015（2026-10-15）：隔离真实依赖回归

脚本与合成数据环境：`scripts/local-java-ai/compose.yaml`、`embedding_fixture.py`、`smoke.py`。不要使用生产 .env，勿操作其他 Compose 项目。测试 fixture 是固定 1024 维非零向量，仅验证协议和持久化，不能证明语义召回质量。

```bash
# JDK 21 + Maven 构建；Dockerfile 的 context 必须是 backend
mvn -f backend/pom.xml -pl knowledge-service -am verify
docker build -f docker/Dockerfile.knowledge -t resource-profile-knowledge:local-migration-20261015 backend
# 单独终端保持 fixture 存活
python3 scripts/local-java-ai/embedding_fixture.py
# 使用全新合成测试卷；smoke.py 正常模式要求空库
docker compose -f scripts/local-java-ai/compose.yaml up -d
python3 scripts/local-java-ai/smoke.py --output /tmp/rp-java-smoke.json
docker compose -f scripts/local-java-ai/compose.yaml restart redis milvus api mcp
python3 scripts/local-java-ai/smoke.py --persist --output /tmp/rp-java-persistence.json
# 只停止本测试项目；保留数据卷供复查
docker compose -f scripts/local-java-ai/compose.yaml stop
```

通过判据：初始化幂等、即时检索非空、原文档精确替换且子文档保留、失败嵌入保留旧文档、三种分块、三 MCP 工具、鉴权与 MCP-only 边界、重启持久化均通过。若 `Milvus operation failed`，先核对业务成功码；若导入后立即为空，检查实际运行 JAR 是否包含强一致性 gRPC 读取。禁止用等待几秒作为修复。JRE 镜像下载若报 short read/unexpected EOF 是构建未完成，不能记为镜像已验证。实际结果与未完成项见 `docs/educare/LOCAL_JAVA_AI_TEST_20261015.md`；本地测试不执行生产部署。修复回退可还原本节涉及源码/POM并重新构建，但旧 REST 版本存在已确认的数据可见性缺陷，不能据此批准生产切流。

### BUSINESS-INTEGRATION-20261015（2026-10-15）：推送与合并验证

整合分支 `codex/java-ai-business-integration-20261015`，目标 `main`。推送前执行 JDK21全后端 `mvn clean verify`，前端 `npm test` / `lint:check` / `build` / `size:check`，部署与SQL工具回归及 `scripts/test-preflight-prod.sh`，检查候选提交不含真实凭据和原始测试产物。远端 main 的新增提交合入后重新执行受影响回归，再检查PR CI；实际结果见 `docs/educare/BUSINESS_INTEGRATION_20261015.md`。远端评测变量当前未启用模型调用。Render配置仍含checksPass自动部署规则，合并可能触发仍绑定仓库的Render服务；当前生产Kamatera的发布仍须独立验证，不据PR合并宣称已发布。回退使用经审查的Git revert，不删除数据卷或自动逆执行SQL。

## 维护记录：RAG-CACHE-RERANK-20261013（2026-10-13）

- 验证：`cd ai-inference-service && uv venv -p 3.11 .venv && . .venv/bin/activate && uv pip install -r requirements.txt -r tests/requirements.txt pytest pytest-asyncio && python -m pytest -q`，通过判据为全部通过（含 `tests/test_rag_pipeline_cache_key.py`）。
- 线上排查：切换 `RERANKER_ENABLED` 后，同一 query 的 `reranked` 字段应立即随配置变化，不再等待 `RAG_CACHE_TTL`；reranker 故障期间回退结果不缓存，日志出现 `rerank 回退结果不缓存`，恢复后下一次请求即重新精排。待验证（需真实 Redis/Milvus）。
- 回滚：回退本提交。注意：若本版本运行期间切换过 `RERANKER_ENABLED`，回退后旧格式键（不含 rerank 标记）可能被重新读到，返回切换前的结果；此时需清理 `edu:rag:pipe:*` 缓存（如 `redis-cli --scan --pattern 'edu:rag:pipe:*' | xargs -r redis-cli del`），或等待 `RAG_CACHE_TTL` 过期后再视为结果最新。未切换过配置则无需处理。

## 维护记录：AGENT-RUNNER-20261013（2026-10-13）

- 验证：`cd backend && mvn -q -pl agent-service -am test`（JDK 21），通过判据为 0 失败，含 `AgentRunnerContractTest`。
- 排错：启动报 `expected single matching bean of type AgentRunner` 说明出现了第二个实现，需指定 `@Primary` 或 `@Qualifier`。
- 回滚：回退本提交即可，无配置或数据变更。

2026-10-15 / BUSINESS-INTEGRATION-20261015 补充：补充已执行结果：整合后732 Java回归、63 Python兼容测试（1可选SDK跳过）通过；修复前端锁文件后test/lint/build/size及生产依赖audit（0漏洞）通过。运行镜像验证命令：`docker build -f scripts/local-java-ai/Dockerfile -t resource-profile-knowledge:local-migration-20261015 .`（先执行JDK21 Maven verify）；37容器检查、12重启检查通过。重启时先等Milvus就绪，启动窗口503不是持久化失败。生产多阶段Dockerfile的源码构建仍待网络恢复后单独验证。

2026-10-15 / BUSINESS-INTEGRATION-20261015 CI修复：PR30测试顺序回归：单独运行`mvn -pl agent-service -am test -Dtest=AsyncSubmissionTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`（单类诊断不跑聚合覆盖率门）可复现修复前失败；修复后须单类通过，再全量clean verify（不跳过JaCoCo）通过及PR CI绿。实际结果见整合报告。
