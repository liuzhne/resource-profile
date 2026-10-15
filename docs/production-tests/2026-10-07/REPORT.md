# Kamatera 生产冒烟测试

- 日期 / 标识：2026-10-07 / PROD-SMOKE-20261007
- 目标：https://resource-profile.45-130-165-168.nip.io
- 结论：**未全部通过。基础部署、权限边界、合成 RAG 和可观测可用，仍有四处问题待修复。**
- 聚合证据：RESULTS.json（本地测试产物，不入库：`./RESULTS.json`）。原始响应、密码、令牌、会话指纹、学生资料、模型提示词/回复及原始日志均未归档。

## 实测结果

| 范围 | 本轮结果 | 判据与限制 |
|---|---|---|
| 公网入口 | 18/18 通过 | 登录与 SPA 深链接、5 个构建资源、受信 TLS 1.3、HTTP 308 到 HTTPS、HSTS/nosniff；匿名业务 401，内部/Actuator 403；从当前外部网络仅 22/80/443 可连接，14 个管理/内部 TCP 端口不可连接 |
| 集群与内部依赖 | 21/21 通过 | 17/17 Deployment、17 Pods Ready，无重启/backoff/失败；8 PVC Bound；单节点 Ready 无压力；健康接口、MySQL SELECT 1 / 20 张应用表、Redis PING、双 MCP 握手及缺凭证拒绝 |
| HPA | 当前状态通过 | data-service 当前/目标均 1，指标可用、ScalingActive/AbleToScale 正常；本轮未做压力或扩缩容负载测试 |
| 三角色业务 API | 52 通过、1 失败、1 跳过（共 54） | admin/teacher/student 真实登录、角色矩阵、字段脱敏、模板去评分、心理接口越权拒绝、正式注销后的旧令牌 401、三个出厂密码拒绝；辅助复查 9 项通过 |
| 登录页浏览器 | 通过 | 未登录访问业务路由跳回登录页；输入初始为空；默认账号提示消失；空表单提示齐全，控制台无错误 |
| 登录后浏览器 | 部分通过 | 教师数据面板正常；学生问卷列表正常显示 3 张示例问卷且均已过作答窗口；学生默认面板失败，见问题 2 |
| 合成 RAG | 8/8 通过 | case/policy/psychology 各 1 条固定夹具、success 为空；3 次新查询及 1 次 knowledge-rag MCP search_cases 命中当前夹具，HTTP 200、fallback=false；新查询缓存前无/后有的向量均为 1024 维有限非零，配置为 Cloudflare BGE-M3；无 upsert/删除 |
| 完整 Agent | **失败** | 本轮用户明确授权 student1 的公开示例资料外发 Groq；经公网仅触发 1 个新任务，RISK_ANALYZING → FAILED，约 20.3 秒；一条单工具成功记录，后续 LLM 调用失败，未验收最终风险/干预产物 |
| Langfuse | 本轮新记录通过 | 本次时间窗口有 1 条 agent.loop（TOOL_ERROR）、1 条 llm.chat（真实模型/token 用量）；时间齐全、I/O 为空。按名称与时间窗口关联，没有共享 task trace ID；不宣称完整分布式调用树或 aggregate span 的真实模型用量 |

`/api/auth/userInfo` 不带头时为 HTTP 200 / 业务 code=500、data 为空；该 `/auth/**` 路由由自身控制器拒绝，与网关业务路由的 HTTP 401 区分处理。所有检查同时核对 HTTP 和业务码，不能将 HTTP 200 单独视作成功。

## 待修复问题（本轮未改变生产代码或部署）

### 1. P1：浏览器退出未撤销服务端会话

实测教师账号点击“退出登录”并确认后已回 `/login`，但同一个会话仍留在 Redis；旧令牌调用受保护的 `/api/data/dashboard/statistics` 仍为 200。随后通过正式 `/auth/logout` 清理，旧令牌变为 401；本轮所有测试会话已清理。

源码：[Navbar.vue](../../../frontend/src/components/Layout/Navbar.vue#L82) 调用 [userStore.logout](../../../frontend/src/store/modules/user.js#L53)，仅清浏览器状态，没有调用 [已存在的 logout API](../../../frontend/src/api/auth.js)。后端注销/网关 Redis 白名单链路本轮均正常。

建议（**未落地，待验证**）：显式退出增加服务端注销 action 并在 Navbar 等待完成，再清本地状态；保留 401 场景的本地清理路径，避免拦截器递归。服务端注销失败应明确提示未成功撤销，不能无条件显示已安全退出。验收须同时检查 UI 回登录页与同一旧令牌访问业务接口 401。不要通过关闭会话检查或缩短所有用户登录时间替代修复。

### 2. P2：学生登录进入无权访问的全校面板

学生登录成功后统一进入 `/dashboard`；四个数据接口受角色保护，页面报“数据加载失败”，控制台有 4 条加载错误。手动进入 `/student-mental/list` 则正常。后端拒绝学生全校读取是正确行为。

源码：[登录 action](../../../frontend/src/store/modules/user.js#L40)、[默认 dashboard 路由](../../../frontend/src/router/index.js#L19) 与 [数据面板](../../../frontend/src/views/dashboard/index.vue#L270)。建议（**未落地，待验证**）：学生默认进入已实现的本人问卷/画像入口，为全校面板和师生管理路由/菜单补角色边界，并保留后端 403；不要给学生开放全校数据。验收覆盖学生默认落地、直接访问受限路由及教师/管理员面板。

学生登录后默认面板失败（本地测试产物，不入库：`./STUDENT_DASHBOARD.jpg`）

### 3. P2：管理员用户列表分页 total 不正确

`GET /api/user/list?page=1&size=5` 实测 HTTP/code=200，`records_count=3`，`total=0`；密码键、非空密码和 bcrypt 内容计数均为 **0**，没有发现密码内容外泄。

源码：[UserServiceImpl.selectPage](../../../backend/user-service/src/main/java/com/edu/user/service/impl/UserServiceImpl.java#L18) 使用 Page，但 user-service 没有其他领域服务已有的 `PaginationInnerInterceptor` 配置，与现象一致。建议（**未落地，待验证**）：按现有模块模式注册 MyBatis Plus 分页插件；验证 total 等于实际匹配数量、每页不超 size、两页记录不重叠且密码仍不返回。不要前端用当前页长度冒充 total。

### 4. P1：Groq 完整 Agent 生成失败

本轮日志限定在新任务的时间窗口，只归档固定标记：HTTP 400 / `json_validate_failed` / `invalid_request_error`、HTTP 429 / `rate_limit_exceeded`；本任务出现 LLM 调用异常，无完成最终产物。没有据此将错误归因为 MCP 鉴权、数据库或特定模型参数；这些根因尚未隔离。

建议（**未落地，待验证**）：先用无个人资料的固定合成请求隔离 JSON 400，确定所选模型的实际响应契约；再按实测共享额度安排模型任务的队列、token 预算和有界退避，明确区分暂时限流与不可恢复请求错误。保留 AgentLoop/双 MCP/结构校验与显式失败；不盲目切换协议、无限重试或升级收费套餐。修复后重新授权一次具体示例资料的完整验收，要求工具成功、合法 final_answer 及落库产物，而非仅提交接口 200。

## 数据、会话与覆盖边界

- 本轮只读快照重新证明 5 张业务表与仓库公开 SQL 一致，心理作答/题目表为空。4 张学生档案全部归属同一账号；因此跨学生档案用例 **跳过**，不把不同档案 ID 当作不同所有人。心理 userId 越权拒绝实际通过；真实跨学生档案仍需有独立所有人的受控测试夹具。
- 业务 API 不写资料、不作答、不提交反馈、不导出报告。唯一业务写入是已明确授权的新 Agent 验收任务，其失败记录保留作审计；RAG 仅查询并产生正常缓存。
- API 与浏览器测试会刷新三个示例账号的会话；已分别通过正式注销/旧令牌 401 清理。临时浏览器标签关闭，原登录标签保留。未重建镜像、改参数、重启 Pod、改域名或增加收费资源。
- 空题目/完成记录/反馈只能验证路径、权限和结构，不能证明逐行脱敏；没有完成真实语料检索质量、50 例风险评测、压测、CSV/PDF/SSE 全链、跨主机备份恢复、监控告警和历史凭据轮换。
- R-5.2 / R-5.3 / R-5.4 / R-6 保持未完成。本轮不能作为生产全功能签字或所有请求 500ms 的证据；公网计时来自当前外部网络，认证业务计时来自 VM 访问自身公网入口，内部检查是私网计时。

## 复现与修复后通过判据

本机忽略目录 `.local-run/kamatera/` 保存脚本和 0600 聚合证据，VM 同名脚本位于 `/opt/resource-profile`；原凭据只从 VM 私有种子文件/Pod 环境读入内存。使用既有固定主机身份的 `/usr/bin/ssh -F .local-run/kamatera/ssh/client.conf kamatera-resource-profile`，不输出 `.env`、Redis token、kubeconfig 或原始日志。

1. 可重复的只读部分：`smoke-public-20261007.py`、`smoke-runtime-20261007.py`。`smoke-business-20261007.py` 还会刷新和清理示例账号会话；复跑时不得与浏览器/Agent 使用同一账号并发。
2. UI 问题按上述真实点击步骤验证；正式 `/auth/logout` 的 401 不能替代浏览器菜单退出的同令牌验证。
3. RAG 只使用固定合成资料、不同的新查询并核对当前存储来源；不通过旧缓存、零向量或 fallback 充当成功，不重跑 upsert。
4. 生成脚本 `smoke-agent-20261007.py` 已有一次触发记录，会拒绝再次执行。新一轮须先获得相同具体数据/接收方的授权、使用新的唯一证据路径并保持单次触发；不得为了全绿反复重触发。
5. 四处修复目前均 **待实施/待验证**，不改变已有部署。后续发布应单独留旧镜像、滚动状态及功能证据；回滚恢复受影响组件的已知镜像/配置并保留 PVC，禁止删卷或以权限豁免绕过失败。
