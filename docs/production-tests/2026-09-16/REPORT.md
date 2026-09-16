# 生产环境功能与接口验收（PROD-AUDIT-20260916）

日期：2026-09-16。目标：https://edu-portrait-frontend.onrender.com/ 。

接口测量窗口：北京时间 16:24–16:34，保存 53 笔脚本探测记录；另有独立登录/健康唤醒检查及生产页面验证。日志同时覆盖本次唤醒与 9 月 14 日的历史对照。

## 结论

核心业务读取可用；AI 链路当前不可用。用户服务最初 429，唤醒后恢复，但首笔列表查询慢。多个画像与管理功能仍是原型或未接线，不能把它们描述成已交付功能。未修改生产配置、未部署、未提交问卷、未创建或删除业务数据。

证据见 [接口明细](./interface-table.md)、[接口原始测量](./interface-results.json)、[后台关键日志与网关耗时](./backend-evidence.json)、[服务运行配置清单](./service-inventory.json)。保存的证据不包含登录凭据、JWT 或个人心理记录。

## 方法与边界

- 经生产 `/api` 入口低频请求，同一接口通常两次，最多三个并发；每笔记录 UTC、HTTP 状态、业务码、TTFB 和总耗时。后端 Result 信封必须同时检查业务码。
- 慢接口工作阈值：普通读取超过 2 秒；超过 10 秒或 60 秒仍无首字节单独标记严重。样本很小，不代表 p95/p99，也不是压力测试。
- 浏览器实际检查：登录、数据面板、教师详情、学生详情、问卷管理/查看、个人资料、用户管理、角色管理、LLM 追踪、预警中心。
- 经用户明确追加授权，使用教师/学生演示账号只读验证心理概览、问卷列表和评估历史，只保存状态与耗时。
- Render CLI 只读查看服务、部署和日志。应用日志最初为空后，直连 Agent 请求触发了新的唤醒，因此同时核对历史失败与本次复现，未将历史错误直接当成本次根因。
- 写接口、真实分析任务触发、问卷提交、PDF 导出/反馈写入、备份恢复、完整权限矩阵与真模型质量没有实跑；AI 服务启动失败使其下游验收无法继续。这些为未验证，不虚标通过或逐项声称已复现失败。

## 不可用与未接入功能

| 标识 | 功能 / 现象 | 原因与证据 | 状态 |
|---|---|---|---|
| F-01 | AI 预警任务列表 HTTP 429；页面长时间加载、显示 0 个任务 | 本次 Agent 在 MCP initialize 时收到 429，`mcpSyncClients` 创建失败，`Application run failed`；不是成功返回的空任务列表 | 持续故障，复测还出现 60 秒无首字节 |
| F-02 | AI 实时预警 SSE 无法建立 | 先前浏览器日志记录握手 429；本次 `/agent/api/v1/warning/stream` 两次 60 秒没有响应首字节；与 Agent 无法正常启动一致 | 不可用；超时发生在握手前，不是正常 SSE 长连接 |
| F-03 | 用户管理查询/新增/编辑/启停未接线 | 页面为硬编码演示数据，点击“新增用户”提示暂未开放；[users.vue](../../../frontend/src/views/admin/users.vue) 没有调用真实用户 API | 前端未实现；“后端无接口”文案不准确 |
| F-04 | 角色新增、编辑、删除及权限保存 | 实点“新增角色”无变化；[roles.vue](../../../frontend/src/views/admin/roles.vue) 硬编码列表，新增/编辑/删除/确定没有处理函数 | 前端未实现；未执行权限修改 |
| F-05 | 个人资料保存、修改密码 | 保存按钮实点提示“该功能暂未开放”；[profile](../../../frontend/src/views/profile/index.vue) 对应处理器为空功能提示 | 前端未接入；密码修改未实点提交 |
| F-06 | 教师教学成果、科研项目、教学评价/评分 | 页面占位，教学成果标签实测显示暂无来源；[detail.vue](../../../frontend/src/views/teacher/detail.vue) 对应标签没有数据调用 | 尚未交付；基础档案接口可用 |
| F-07 | 学生课程成绩、综合素质、详情页心理面板 | 学生成绩页显示原型占位；[detail.vue](../../../frontend/src/views/student/detail.vue) 仅调用基础档案 API。补测 `/student/1/academic` 返回 6 条数据 | 学业属于已有后台但前端未接线；综合素质需单独评估数据源，不能笼统说后端没有成绩表 |
| F-08 | LLM 全链路追踪 | 生产页面明确显示“未配置 Langfuse URL”；08:27:43 UTC Agent 日志明确 host/public/secret 任一为空，trace 上报关闭 | 前后端配置缺失；不是慢接口 |
| F-09 | 短信验证码、统一身份认证、扫码/忘记密码 | [login](../../../frontend/src/views/login/index.vue) 短信按钮禁用，其他入口使用暂未开放提示 | 源码确认未接入；账号密码登录通过 |
| F-10 | 面板待处理事项 | [dashboard](../../../frontend/src/views/dashboard/index.vue) 明确硬编码静态事项；不是实时事项接口 | 演示内容；面板统计和趋势 API 可用 |
| F-11 | 用户服务健康检查误判 | 直连 `/actuator/health` 45.987 秒，HTTP 200 但 body.code=500；后台 `No static resource actuator/health` | 健康路由不存在，不能作为可用性通过判据 |
| F-12 | 直接查询用户列表最初两次 429 | 当时用户应用没有本次请求日志；直连触发唤醒后，应用启动、首笔列表成功、第二笔成功 | 瞬时不可用，已恢复；没有证据判定为 SQL 故障 |

F-03 的真实后端 [UserController](../../../backend/user-service/src/main/java/com/edu/user/controller/UserController.java) 已有 CRUD；本次 `/user/list` 唤醒后返回业务码 200。F-07 的真实后端 [StudentAcademicController](../../../backend/student-service/src/main/java/com/edu/student/controller/StudentAcademicController.java) 已有学业接口；前端无来源文案应按真实接线情况修订。

## 慢接口与后台原因

| 请求 | 实测 | 对应后台证据 | 判断 |
|---|---|---|---|
| `/api/user/list?page=1&size=10` 唤醒后的第一笔 | 6.647 秒；下一笔 0.764 秒 | 网关下游 5.578 秒 → 0.100 秒；Hikari 08:32:40.124 Starting → 08:32:43.103 Start completed，约 2.98 秒 | 已确认首笔初始化/建连开销；其余时间没有逐阶段 span，不能全部归因于 TLS 或 SQL |
| user 直连 `/actuator/health` | 45.987 秒；HTTP 200 / 业务 500 | 新进程 Started 用 28.696 秒、process running 33.396 秒；DispatcherServlet 首次初始化又花 2900 ms，随后路由不存在 | 冷启动、调度与首次初始化导致等待；路由缺失另为功能错误 |
| Agent 直连 `/actuator/health` | 60.003 秒仍没有首字节 | JAVA_TOOL_OPTIONS 只有 MaxRAMPercentage；08:26:13.697 Starting → 08:28:56.994 run failed，约 163.3 秒 | 慢启动并最终失败，非健康通过；不是模型推理慢 |
| `/api/agent/api/v1/warning/stream` | 两次约 60 秒，HTTP 000、0 字节 | Agent 同时陷入 MCP 初始化失败/重启 | 握手不可用，不能以“流式接口本就长连接”解释 |
| Agent 列表初测 | 429，0.771 / 0.698 秒 | 网关转发 0.049 / 0.062 秒返回 | 快速失败，不属于慢 SQL；后续复测又出现 60 秒无首字节 |

其余普通业务初测均低于 2 秒，约 0.73–1.86 秒。相对较慢的是 recentLogins 1.856 → 0.874 秒、问卷 full 1.467 → 0.771 秒。网关下游分别约 0.053 → 0.077 秒、0.572 → 0.085 秒：端到端差额发生在客户端到网关的网络/代理链路及未观测阶段，不能仅凭这个差额进一步区分 DNS、TCP、TLS、CDN 或地区延迟。教师/学生角色登录为 1.913 / 1.236 秒；心理读取为 0.89–0.97 秒。

普通业务测量时段查询 mental/data/student/teacher 的 WARN/ERROR，未返回相关错误；这不证明没有慢 SQL。当前日志没有 SQL 每笔耗时、连接池等待分解和完整 trace，因此没有慢 SQL、连接配额耗尽或 Groq TPM 限流证据。

## Agent 根因证据链

本次 UTC 时间（北京时间加 8 小时）：

1. 08:25:44 `Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=75.0`；生产未带仓库建议的 C1/SerialGC 参数。
2. 08:26:13.697 Agent Starting；08:27:33 Root WebApplicationContext 初始化已耗时 78801 ms，尚不等于服务完整就绪。
3. 08:27:43.994 `Langfuse 未配置 ... trace 上报关闭`。
4. 08:28:55.595 `mcpSyncClients` 创建失败，取消 ApplicationContext refresh。
5. 08:28:55.797 `McpTransportException: Invalid SSE response. Status code: 429 Line: Too Many Requests`。
6. 08:28:56.994 `Application run failed`；08:28:58 又出现 JVM 启动输出，08:29:22 Starting，形成重启。

9 月 14 日 07:50 和 07:53 UTC 已出现相同错误，本次确实复现。最近部署被 Render 标为 live，部署成功状态不能证明冷唤醒后应用仍正常。当前 Agent 部署提交为 `8f8a489c24e3e4bdf147ccdaf54e016826c98b68`，不可直接以本地仓库最新配置推定运行配置已更新。

确定的直接根因是 **MCP 启动硬依赖收到 429，导致 Agent 整体启动失败**。错误日志没有标明是哪一个 MCP connection；两个 MCP 服务在查询时段没有匹配日志。429 的更底层原因（休眠唤醒、平台限制、实例状态）仍需 Render 平台事件/具体 connection 状态证据。不能断言为 Groq 限流、MySQL 失效或“延长超时就一定能修复”。

## 待实施修复方案与复验

以下仅为方案，未修改配置、未部署，所有修复后验收均待验证。

1. **P0 Agent/MCP**：按实际部署提交及服务级覆盖核对 connection URL、token、规范 `/mcp/` endpoint 和启动参数；取得具体 connection 的平台状态后先恢复依赖，再恢复 Agent。鉴权不变，MCP 契约不回退。短期隔离“依赖不可用”与业务启动，需显式报依赖未就绪，不能返回成功但没有工具。单纯再增加 120s timeout 或前端重试无法处理立即返回的 429。
2. **P1 健康检查/冷启动**：为 user 服务提供真实健康路由或选择真实存在的探针；探针同时验证语义，不能只看 HTTP 200。评估数据库初始化时机，或在就绪检查中完成必要初始化，使首位用户不承担全部 3 秒建连成本；代价为延后 readiness，不得让应用伪就绪。缩短 JVM 启动的参数须按服务验证；agent 含定时任务，不能直接套 lazy initialization。
3. **P1 既有后台接线**：优先接入真实 `/user` 与 `/student/{id}/academic`，再评估其他画像数据源；无接口或未交付的按钮显示明确状态。修订个人资料“字段不返回”提示：本次 userInfo 返回 email/phone/deptName 字段，但不能将空值误写成字段不存在。
4. **P1 可观测**：Langfuse URL 与后端项目凭据完整配置后再做活 trace 验收；密钥留 secret。网关目前 DEBUG observation 会输出 Authorization 请求头，调到适当级别并对敏感头脱敏，避免诊断日志保存有效 token。增加请求关联、connection 名与 SQL/池等待分阶段指标后，才能准确归因剩余耗时。
5. **P2 发布一致性**：服务清单实际 `autoDeployTrigger=commit`，Agent 没有 buildFilter，和 [render.yaml](../../../render.yaml) 的 checksPass/增量声明不一致；同步配置应单独验收，不能把本报告视为同步已完成。

修复后的通过判据：冷/热两轮验证列表 HTTP 200 且业务 200；user 真实 health 语义正常；Agent 冷唤醒成功 Started、双 MCP initialize/list tools 完成、不再重启，SSE 在约定握手时限内返回 `text/event-stream`；真实任务/PDF/反馈写入另用明确测试记录验收。首请求预算需产品确认；本报告的 2 秒仅诊断阈值。

执行计划 R-5/R-6 不因这轮诊断勾选完成：没有真模型质量、活 trace 和上线总验收证据。
