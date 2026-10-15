# BUSINESS-INTEGRATION-20261015

日期：2026-10-15。用户授权：全部当前业务改动，排除密钥、临时文件和测试产物；本地测试通过后推送并合并main。

整合前基线回归：后端JDK21全量clean verify 730测试通过，0失败/错误/跳过；前端test/lint/build/size通过；Cloud17、K3s15、OCI17、SQL启动门7、演示数据完整性15、公开语料4与探针隔离10通过；preflight通过；50条eval仅校验数据，不调模型。Java+真实Docker Redis/Milvus的37项HTTP/MCP、12项重启检查见本地测试报告。

远端main新增AgentRunner和旧Python RAG缓存修复，须保留。六个其他未合并迁移PR不在本次自动合并范围。截图和原始JSON/CSV结果留本地，仅保留源代码、可复跑测试和叙述文档。

整合后结果：全后端clean verify **732测试通过**；保留Python兼容实现的pytest **63通过、1可选Langfuse SDK跳过、9 subtests通过**。AgentTaskServiceImpl仍依赖AgentRunner，保留实际RAG快照落库；Python兼容缓存键同时包含语料revision和rerank开关，失败精排不缓存。

前端生产依赖审计发现Vue/server-renderer与source-map-js高危公告，兼容更新锁文件（Vue3.5.44、source-map-js1.2.2及相关修复）；更新后npm ci、test、lint、build、size均通过，`npm audit --omit=dev --audit-level=high`为**0漏洞**。开发工具仍有审计提示，未为此强制升级ESLint等主版本。

**Java容器运行验收通过**：通过 `scripts/local-java-ai/Dockerfile` 将已经Maven验证的JAR打入官方Temurin21 JRE镜像。真实Docker Redis/Milvus/API/MCP从空合成库执行37检查全部通过；全部重启后12检查通过（重启未就绪窗口首次503，依赖就绪后再验通过）。镜像JAR与宿主机测试制品SHA256均为`633fd9ee87fb56d2ffac6aaec33420c3ee617d41e4a31cbfc272923de4453b78`。一次内存快照API288.6MiB、MCP246MiB，不当作容量/压测结论。完整生产多阶段Dockerfile仍因JDK基础层下载缓慢未完成，其构建验证不能由运行镜像替代。

状态：本地验收已完成，已推送并创建[PR30](https://github.com/liuzhne/resource-profile/pull/30)，合并状态以PR为准；生产未手动部署，完整模型/PDF/反馈及Python线上退役仍待独立验收。

PR30首轮远端Java CI因AsyncSubmissionTest依赖其他测试类初始化MyBatis元数据而失败；本机单类执行已复现。已添加测试自身的BeforeEach初始化，保留原业务断言；修复后单类3项通过，全量clean verify 732项通过且JaCoCo门通过；远端复验见PR最新检查。
