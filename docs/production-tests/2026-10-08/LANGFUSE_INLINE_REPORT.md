# LANGFUSE-INLINE-20261008：站内LLM追踪面板

日期：2026-10-08。用户已选择Public API与站内页面，代码、自动验证、生产发布与只读接口验收完成；浏览器登录后的视觉复核待验证。

- [站内页面](../../../frontend/src/views/admin/trace.vue)：近24小时/7天/30天、刷新、每页20/50条、稳定时间范围的游标上一页/下一页、实际观测列表、明确空态与固定错误提示。原版控制台保留次要入口，不自动跳转。
- [管理员读取接口](../../../backend/agent-service/src/main/java/com/edu/agent/controller/LangfuseObservationController.java)：`GET /agent/api/v1/observability/observations`；admin JWT专属，其他角色/匿名/只有内部凭证不放行，网关继续撤销旧会话。
- [Cloud读取与投影](../../../backend/agent-service/src/main/java/com/edu/agent/service/LangfuseObservationService.java)：固定HTTPS区域域名、无重定向、31天窗口/1–50条/4096字符游标、256KiB响应与完整交换超时，只选所需字段再按DTO白名单投影。模型/token缺失保持未知，agent.loop未记录用量不显示0，DEFAULT仅表示默认观测级别；不假造任务调用树或全项目汇总。
- 密钥只在服务器；input/output、metadata、学生标识、模型参数和原始错误正文不返回。未知操作名使用通用标签，防自由文本泄露。

## 自动验证

Java：JDK21执行 `mvn -o -Daether.syncContext.named.factory=noop -f backend/pom.xml -pl agent-service -am verify`，common80 + agent-service222（含新增72）=302项；失败/错误/跳过均0，既有覆盖率门通过，Boot JAR重新打包成功。

前端：126项回归及角色权限契约通过；AI路由开启、日本区URL注入的生产build通过；体积2076.9KiB、体积门通过；lint0错误/0警告。[13项新增页面契约测试](../../../frontend/tests/observability.test.mjs)覆盖分页/范围重置/迟到响应/卸载、未知与0、错误/空态、敏感内容不进入状态/DOM。新增后端[服务测试](../../../backend/agent-service/src/test/java/com/edu/agent/service/LangfuseObservationServiceTest.java)及[控制器测试](../../../backend/agent-service/src/test/java/com/edu/agent/controller/LangfuseObservationControllerTest.java)使用合成HTTP，覆盖角色、内部凭证、编码游标、字段过滤、凭据反射、非2xx、重定向、超限与缓慢响应体；没有真实模型调用。

## 生产发布与验收

发布制品 `prod-langfuse-inline-20261008-v1`，仅agent-service/frontend；制品SHA-256 `bce9906b5b63424a80746f2fb81ba6ed5dfeeb93cc33e4c80fa52dcc6701dab8`。服务器按Boot JAR条目校验，保留原镜像配置、字体、env、Caddy/TLS及PVC。agent-service使用v1，frontend经过下述入口修复后使用v2；实际运行镜像配置/文件层与已构建制品逐一校验，网关恢复。全栈最终就绪数量以聚合证据为准。

脱敏聚合证据（本地测试产物，不入库：`./LANGFUSE_INLINE_VALIDATION.json`）仅含固定断言、状态码与数量，不含密钥、JWT、观测正文/ID或学生资料。公网HTTPS21项均通过：

- 匿名401；admin登录后近24小时0条、7天11条、30天11条，返回字段与空值语义通过。
- 每页1条的两页游标查询通过，固定相同时间边界且记录不重复。
- limit51、超过31天和非法游标拒绝400；既有用户API200。
- 有效student/teacher令牌拒绝403；三个验收会话均已注销，旧令牌401。

浏览器当前停在登录页；尚未取得登录后实际页面的视觉证据。UI状态/渲染/竞态已通过自动契约测试，不能把它们描述为生产浏览器视觉检查。管理员登录后打开 [LLM追踪页面](https://resource-profile.45-130-165-168.nip.io/admin/trace)，选择近7天即可核对已有记录；默认24小时当前为空。

前端初次v1静态发布检查失败：发布包和构建目录的入口SHA为 `2caa847ca4cddfcbdec382648800a4def511579dc457052920116b76ef7ced67`，镜像及Pod中仍为旧 `4a382ab8f89c0ccfde8bc55740fc454b87cc75930810a331bd11f0fb679bc609`，导致入口引用不存在的旧脚本/样式。推断与发布包固定mtime=0、同名HTML长度相同及BuildKit增量元数据同步有关；依据[BuildKit local source](https://github.com/moby/buildkit/blob/master/source/local/source.go)与[fsutil差异判断](https://github.com/tonistiigi/fsutil/blob/master/diff_containerd.go)，不能仅由COPY步骤无缓存排除此机制。

同一制品重新构建前刷新构建输入mtime、禁用层缓存，以独立frontend `prod-langfuse-inline-20261008-v2` 上线；导入前镜像内及Ready后实际Pod内全部64个静态文件SHA与manifest一致。agent-service与数据库未再次更新，UI源码/构建产物字节不变。最终公网64/64文件HTTP200、正确MIME及SHA匹配；`/index.html`、`/login`、`/admin/trace`三个HTML入口均匹配制品。实际入口引用新trace JS/CSS及正确Element Plus样式。17/17 Deployment与Pod Ready、8 PVC保留；静态检查仍不等于登录后的视觉验收。

服务器上的检查命令和回滚标签见 [RUNBOOK同名条目](../../../RUNBOOK.md#langfuse-inline-20261008站内追踪列表2026-10-08)。回滚镜像为两个应用的 `pre-prod-langfuse-inline-20261008-v1`，公开源码备份保留在VM；回滚为预案，未实际执行。

无新增数据库/SQL迁移/模型请求/Agent触发。本页只展示运维观测，不能替代R-5.2完整模型/工具链验收或R-6上线签字。现有未授权采集的提示词与回复仍不启用。
