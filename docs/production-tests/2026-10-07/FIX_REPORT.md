# 四项生产故障修复与重新部署

- 日期 / 标识：2026-10-07 / PROD-FIX-20261007
- 环境：[Kamatera 生产入口](https://resource-profile.45-130-165-168.nip.io/login)，既有单节点 K3s。
- 结论：**四项修复代码已部署；注销、学生入口及真实用户分页已完成线上复验。AI 修复通过回归及真实合成请求，完整示例 Agent 尚未复验，等待本次具体数据外发授权。**
- 聚合证据：FIX_RESULTS.json（本地测试产物，不入库：`./FIX_RESULTS.json`）。[修复前报告](./REPORT.md)保留原故障快照，不代表修复后状态。没有归档凭据、令牌、业务行、模型内容或原始日志。

## 修复和实测结果

| 问题 | 最终行为 | 生产证据 |
|---|---|---|
| UI 退出不撤销 JWT | 主动退出等待服务端撤销成功；Redis 原子比较并删除指定会话；JWT 有随机 jti；旧请求不清理新前端会话 | 实际浏览器菜单退出后，原 JWT 业务请求 **401**、Redis 会话为空、临时令牌文件已删除。同秒新旧登录签发不同 JWT，注销旧 JWT 后新 JWT 仍 **200** |
| 学生误入全校面板 | 真实角色驱动落地、递归菜单及路由守卫；学生默认进入本人问卷 | 学生默认 `/student-mental/list`，3 个公开示例问卷正常显示、无加载失败、控制台错误 0；手工访问看板/教师列表跳回允许入口；教师统计面板正常，后端学生读取全校数据仍拒绝 |
| user 分页 total=0 | MySQL 分页插件执行 count/limit；管理用户页移除静态记录，读取真实 GET `/user/list` 的 records/total | 数据库实际匹配 **3**，size=1 的四页记录数 **[1,1,1,0]**，total 均 **3**、各页不重复且无密码字段。浏览器显示 3 个账号；查询 student 后显示 1，重置恢复 3 |
| Groq JSON 400 / 429 | ReAct 严格 JSON schema、最终产物二次校验；400 不重试；429 最多 3 次并遵守 Retry-After；Java/Python 共用 Redis 请求冷却及有界等待；任务独立执行池和 deadline | 新镜像一次纯合成请求 **HTTP 200**、JSON 有效，模型 GPT-OSS-120B，用量 **145+22=167 tokens**；17 个运行配置/隐私断言通过，调用后共享冷却剩余约 59 秒；新 Langfuse `llm.other` 记录有用量/耗时且 I/O 为空。**这不是完整 Agent 验收** |

学生登录进入本人问卷（本地测试产物，不入库：`./FIX_STUDENT_LANDING.jpg`）

用户页采用真实接口总数（本地测试产物，不入库：`./FIX_USER_LIST.jpg`）

## 代码及参数

会话修复见 [user store](../../../frontend/src/store/modules/user.js)、[请求拦截器](../../../frontend/src/utils/request.js)、[Navbar](../../../frontend/src/components/Layout/Navbar.vue)、[auth 注销实现](../../../backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java)、[JwtUtil](../../../backend/common/src/main/java/com/edu/common/util/JwtUtil.java)。主动注销固定原 JWT，不自动重试；失败保留可重试状态，不假装撤销成功。无效/缺失 JWT 拒绝，已撤销的合法 JWT 可幂等注销；原子 compare-and-delete 不会撤销新的会话。

角色入口见 [access.js](../../../frontend/src/router/access.js)、[routes.js](../../../frontend/src/router/routes.js)。用户列表见 [分页配置](../../../backend/user-service/src/main/java/com/edu/user/config/MyBatisPlusConfig.java)、[列表 composable](../../../frontend/src/composables/useUserList.js)、[用户页面](../../../frontend/src/views/admin/users.vue)；支持真实账号/状态查询和分页。后端未实现的 role 筛选不显示；账户类型与角色授权分开。新增、编辑、删除和状态调整仍禁用，不新增写接口。

AI 修复见 [SpringAiConfig](../../../backend/agent-service/src/main/java/com/edu/agent/config/SpringAiConfig.java)、[Java 请求策略](../../../backend/agent-service/src/main/java/com/edu/agent/config/LlmRequestPolicy.java)、[AgentLoop](../../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java)、[输出校验](../../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoopOutputSchema.java)、[任务实现](../../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java)、[Python 策略](../../../ai-inference-service/app/services/llm_request_policy.py)。保持 AgentLoop ReAct 和两个 MCP；可选工具参数 null 去除后按默认值处理，未知工具拒绝。重复 JSON key、非标准/尾随内容和缺失 risk/plan 字段不能落为成功；修复耗尽后显式 VALIDATION_ERROR，不默认 MEDIUM。模型/SQL 参数日志不记录内容。

| 参数组 | 生产值 |
|---|---|
| 响应 | ReAct JSON_SCHEMA；Python raw JSON_OBJECT；max tokens/output budget 2400；reasoning low；输入上限 18000 字符 |
| 请求策略 | 开启策略与跨 Java/Python Redis DB0 冷却；最小请求间隔 60000ms；请求排队容量 2；请求等待预算 180000ms |
| 重试 | 仅暂时限流有界重试；最多 3 次请求（含首次）；单次 Retry-After 上限 120000ms；永久 400 不重试；不叠加 Spring AI/SDK 重试 |
| 任务 | 独立任务执行池并发 1、排队 2；排队满明确 FAILED/503；loop deadline 900s，锁 1200s；Feign 读取 240s，模型 HTTP 连接 10s/读取 60s |
| 隐私 | Langfuse 内容捕获 false；不发送 input/output，自由文本错误只保留固定状态标记 |

仅审查过的 **17 项非秘密参数**同步至本地/VM 环境及 39 个工作负载 Secret 字段；逐项证明其他值和凭据未变。模板/清单见 [cloud.env.example](../../../docker/cloud.env.example)、[K3s workloads](../../../k8s/workloads.json)、[Compose cloud](../../../docker/docker-compose.cloud.yml)。Redis 冷却只协调本项目调用，无法防止同一 Groq 组织的其他应用耗尽额度。

## 发布与验证

| 批次 | 内容 | 完成时间（UTC） |
|---|---|---|
| `prod-fix-20261007-base` | 21 个公开源文件；frontend/auth-service/user-service | 10:32:08 |
| `prod-fix-20261007-ai` | 27 个公开源文件；agent-service/ai-inference-service/knowledge-rag-mcp，17 项参数 | 10:52:47 |
| `prod-fix-20261007-user-ui` | 5 个公开前端文件；frontend | 10:57:58 |

每批先核对源文件白名单、大小和 SHA-256，VM 留原源码及唯一 `pre-<release>` 镜像，新旧镜像同时导入 containerd 后才更新受影响 Deployment。当前 frontend 为第三批、auth/user 为第一批、三个 AI 工作负载为第二批。所有数据 PVC 保留；没有重跑种子初始化、RAG upsert 或新增云资源。

本地验证：common **75**、auth **27**、user **11**、agent **114** 项测试均零失败/错误/跳过，相关 JaCoCo 门通过；前端 **93** 项测试和生产构建通过，lint 零错误（既有 vite.config.js 格式警告）；Python **50** 项实际 SDK 环境测试、K3s **13** 项通过。新增回归覆盖真实 SQL、会话竞态/同秒签发、角色守卫、请求竞态、服务器 total、schema、400/429/Retry-After、排队及 deadline。`git diff --check` 通过。

部署后：公网 **18/18**、内部 **21/21**、三角色业务 **53 通过 / 0 失败 / 1 跳过**、合成 RAG **8/8**；17/17 Deployment Ready、8/8 PVC Bound，无失败 Pod/backoff/restart。HPA 指标可用且当前/目标均 1、最大 2，本轮未施加新的压测负载。公网只测试到 22/80/443 可达，内部端口从当前外部网络不可连接。API 测试和浏览器会话均正式注销，临时浏览器标签关闭，用户原登录标签保留。

## 未完成项与证据边界

- 修复前 JSON_OBJECT/1200 与严格 ReAct JSON_SCHEMA/2400 的固定合成探针均能成功，实测响应头 token limit 为 8000。因此原多轮 400 的准确触发原因尚未证明，不能将 1200 tokens 或 JSON_OBJECT 单独断言为根因。结构修复依据 [Groq structured outputs 契约](https://console.groq.com/docs/structured-outputs)，限流处理依据 [Groq rate limits](https://console.groq.com/docs/rate-limits)。
- 本轮未触发 student1 完整 Agent；先前一次外发授权已使用，正在等待新一次授权。验收须真实工具调用成功、合法 final risk/plan 落库及新时间窗 trace；合成请求、健康、提交成功或失败 trace 均不替代。R-5.2 保持未勾选。
- 任务排队在进程内，不提供重启后 PENDING 自动恢复。免费共享额度仍可能产生 429，超预算时明确失败。
- 所有 4 个学生档案归属同一示例账号，跨学生档案用例仍跳过；不能用不同档案 ID 假装不同 owner。没有验证真实语料质量、50 例风险评测、SSE/导出/压力、异地完整备份恢复、告警和历史凭据轮换；R-5.3/R-5.4/R-6/B-2 不因这次修复变为完成。

复验命令、私有审计位置及逐批回滚见 [RUNBOOK](../../../RUNBOOK.md#prod-fix-20261007四项生产故障修复与复验2026-10-07)。失败应恢复受影响镜像和必要配置并保留卷，不关闭鉴权、减少校验或无限重试。
