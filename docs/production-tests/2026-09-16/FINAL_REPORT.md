# 生产优化与验收记录

最近更新：2026-09-17。**代码优化已分批部署；所有接口端到端≤500ms尚未达成。** 本报告及完整运行时证据已提交于同目录 JSON，已脱敏（不含凭证、令牌或完整运行时元数据）。

修复包含：Agent/PDF显式后台排队及满载拒绝，MCP后台连接与动态工具解析，数据库启动预热、连接keepalive和真实健康探针，统计查询合并及趋势谓词调整，关闭SQL/网关详细日志，响应计时与压缩，Excel每100题参数化批量写入。三份项目现状文档同步。

## 已确认的原因与修复

| 问题 | 证据和原因 | 处理 |
|---|---|---|
| Agent无法启动 | MCP握手429；后续Spring AI启动解析器读取未就绪工具导致BeanCreationException | 后台重连、调用时动态解析；工具缺失仍明确503 |
| AI/PDF提交长时间阻塞 | 同对象调用@Async不经过代理；CallerRuns在HTTP线程执行 | 显式Executor提交，满载AbortPolicy并返回失败 |
| 首查询慢 | 既有Hikari启动约2.979s，用户列表首次6.647s；免费实例休眠唤醒 | 启动SELECT1、eager初始化、连接池keepalive；首次mapped SQL/JVM运行仍可能慢 |
| Excel逐题写库 | N条题目N次INSERT | 100题一批；同一事务、绑定参数；205题三批测试及真实SQL回滚通过 |
| SSE前端超时 | 静态站代理5/5约20s超时；Agent和网关直接收到hello | 前端配置直连网关，保留Bearer及会话鉴权；前端已live，线上Warning-Bc4LLhjS.js确认包含直接网关配置（PR#17/main5ffe28b） |
| 普通热读仍超过500ms | 同一请求可见应用45ms、网关85ms、客户端755ms | 已消除多余查询；当前剩余耗时包含客户端网络、静态代理、传输，不能归因全部SQL |
| 热登录仍慢 | 5次服务处理572–633ms，客户端956–1844ms | 新增5阶段无身份耗时日志，日志已确认热密码校验432–663ms，首次Redis连接3589ms、JWT初始化887ms（auth-login-phase-evidence.json）；启动只读预热修复PR#18/main a3d3c7e已部署；不降低密码强度 |

## 实测结果

基础修复9个Java服务提交a0a2a588920d15acb0888c8bca12be2bc71a35d4全部live；后续SSE前端5ffe28b已live，Auth预热最终版本a3d3c7e已live，健康/就绪接口均UP。Agent启动88.597s；学生数据4个工具和知识RAG3个工具均连接成功。

管理员25条只读URL各5次，共125次业务成功；其中71次完整响应≤500ms。每条第2–5次共100个热样本，应用处理（序列化前）23.68–204.58ms，中位46.05ms；客户端完整响应中位473.88ms、样本P95 651.51ms、最大1107.10ms。每URL只有4个热样本，此P95为跨接口样本汇总，不能作为各接口的持续SLO保证。

教师11/11、学生16/16样本成功。学生问卷模板另5/5成功，完整响应503–646ms；单独保存。未登录401、内部路径403、学生越权拒绝均验证通过。

直连网关SSE 5/5收到hello并立即关闭；452.94–1131.08ms，其中1次≤500ms。CORS预检200，允许前端Origin与Authorization头。长连接总时长不作为握手延迟。

冷启动健康接口约53–64s，隔夜登录首次15s超时；首次新发布登录7777ms、用户列表9999ms、Agent列表3326ms。这些首次慢样本保留，不从全请求统计中删除。

## 覆盖和限制

源码盘点56个业务REST端点、34个GET。直接生产只读覆盖含SSE及补测问卷模板27个GET路由；没有为了验收创建真实心理评估、修改真实问卷或触发真实学生推理/干预。无现成任务/导出产物的详情、下载，以及个体评估详情和反馈报告未做完整生产耗时验收。内部Python推理/MCP工具及大文件最终生成/完整下载不在这些125条热读统计中。列表脚本的pageNum/pageSize与后端page/size参数名称不同：旧样本使用后端默认大小（普通列表10，Agent20），不能把样本误称为全部明确10条分页。

Java全量196项及后续2项Auth预热测试，当前198项0失败/错误，安全覆盖率通过；Python28项通过；前端lint零错误一个既有格式警告，生产构建/体积门及既有逻辑断言通过。H2 SQL/合成测试不是生产导入延迟证明。

当前免费实例会在15分钟无流量后休眠，不能保证任意冷请求500ms：[Render官方说明](https://render.com/docs/free)。本次未升级付费或改变部署区域。要继续接近端到端500ms，需要确定验收网络/区域、请求规模与负载，再评估常驻CPU资源和网络部署；付费本身也不保证任意客户端网络或LLM最终结果500ms。

## 证据和回滚

performance-after-final.json、performance-final-summary.json、performance-role-*-final.json、auth-login-hot-final.json、sse-surface-final.json、sse-gateway-acceptance.json、health-production-final.json、security-production-final.json、deployment-final.json均只含状态/耗时及必要运维元数据，不保存密码、令牌、个体响应记录。原始后台日志仅存临时目录。

配置回滚优先使用render-performance-plan.json原值；不使用超时重试后可能缺失旧值的applied作为唯一依据。代码回滚到此前已验证提交，前端移除VITE_SSE_BASE_URL会恢复旧代理链路，也会恢复SSE超时缺陷。无需生产DDL或数据回滚。

前端最终发布5ffe28b8524b3ce738072accc06093ee0e731ff5已live，编译产物核对通过（frontend-sse-bundle-final.json）；Auth同版本已live且日志诊断生效。

登录阶段证据：首次 lookup314/password594/roles110/JWT887/session3589ms；后续 password432–663ms、lookup22–42ms、roles22–107ms、JWT0–66ms、session1–52ms。热请求服务器550–785ms、客户端938–1095ms。主要持续成本为bcrypt校验，不是SQL大扫描；启动预热只消除连接和签名首次初始化，不能承诺密码校验或客户端总耗时500ms。

本地合成bcrypt10对照（不代表生产时延）：C1约65ms/次，常规分层编译约56ms/次。仅调整JIT对密码成本的改善有限，并可能增加免费实例启动成本，未贸然改变生产JVM编译策略；也没有降低密码工作因子。

最后补充修复：AuthDependencyWarmupConfiguration在readiness之前只读PING Redis、初始化内存JWT签名，不写Redis token白名单、不记录合成token。认证回归及2项只读/失败就绪单测通过；PR和main全量CI通过，合并a3d3c7e，最终日志确认Redis/JWT预热生效。

最终Auth a3d3c7e0e7f44444ae98541040043ab1deb5c709于01:08:27Z live。发布后首登录3288.81ms（服务2029ms），后两次1101.07/1004.85ms（服务728.55/628.90ms）。首次阶段 JWT2ms/session51ms，后续JWT1–47ms/session2–50ms；此前首次JWT887ms/session3589ms已消除。热密码校验仍507–587ms，首mapped查询和HTTP链仍有初始化成本，未达到500ms。报告不把不同发布的首次对比当成恒定性能保证。
