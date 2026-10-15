# LOCAL-JAVA-AI-TEST-20261015：本地真实依赖联调

日期：2026-10-15。用户开启 Docker 后授权执行。本文保留首次联调批次结果；后续Java运行镜像37项检查与12项重启检查已通过，详见[整合报告](./BUSINESS_INTEGRATION_20261015.md)。**本地数据链通过；完整 Java 容器验收尚未通过。**

## 实际环境与范围

Docker Desktop 29.8.2、ARM64、14 CPU、约 8 GB 内存。独立项目 `rp-java-migration-test` 使用真实 Redis 7、Milvus 2.4.1、etcd 3.5.5 和 MinIO；独立合成数据卷，不连接生产。API（18090）和 MCP（18095）运行在宿主机 JDK 21，使用 Maven 构建的同一个 knowledge-service JAR。

嵌入调用经过真实 Spring AI HTTP 客户端，目标为本地固定 1024 维非零向量协议桩。无外部模型请求，不能证明语义召回效果或完整学生 Agent、审核、PDF、反馈成功。

## 已发现并修复

1. **Milvus REST 业务成功码兼容**：实际 2.4.1 返回 `code:200`，原适配器只接受 0，初始化失败。现兼容 0/200，其他业务错误仍失败并脱敏。
2. **立即查询不可见与空缓存**：真实导入后立即查询返回空；后续直接查 Milvus 能看到数据。固定版本[官方 handler_v2.go](https://github.com/milvus-io/milvus/blob/v2.4.1/internal/distributed/proxy/httpserver/handler_v2.go) 的 query/search 硬编码 BoundedTimestamp，REST 参数 Strong 无效。改用官方 Java SDK 的 STRONG gRPC 查询/检索，同一 19530 端口；REST 继续管理集合和写入，保留原 schema。
3. **SDK 请求截止时间**：官方 SDK 2.4.1 的 rpcDeadlineMs 在客户端构建时附加截止时间。改为每次 RPC 设置独立 30 秒，连接按需建立并在应用关闭释放。实际进程运行超过 30 秒后仍能完成新的查询与 MCP 调用。

源码：[MilvusStore.java](../../backend/knowledge-service/src/main/java/com/edu/knowledge/MilvusStore.java)、[SDK依赖与裁剪](../../backend/knowledge-service/pom.xml)、[回归测试](../../backend/knowledge-service/src/test/java/com/edu/knowledge/MilvusStoreTest.java)。未添加 Spring AI Alibaba；问题在旧 Milvus REST 契约，不在 Spring AI 能力。

## 通过证据

| 验证 | 结果 |
|---|---|
| JDK21 `mvn -pl knowledge-service -am verify` | 27测试，0失败/错误/跳过，BUILD SUCCESS |
| 从空合成库运行完整 smoke | 37检查全部通过 |
| Redis、Milvus和两个Java进程重启 | 12检查全部通过；全新查询绕过此前查询缓存 |
| Milvus停机故障注入 | 未缓存查询返回503，恢复后可检索 |
| Redis停机故障注入 | 未缓存查询返回503，恢复后完整smoke通过 |
| 脚本语法与 `git diff --check` | 通过 |

完整 smoke 覆盖：健康、匿名/错权限拒绝、初始化幂等、空集合fallback、三类知识导入、幂等跳过、来源字段、同doc_id精确替换、相似前缀子文档保留、旧查询缓存失效、嵌入故障保留旧文档、none/fixed_size/sentence切片、非法来源422、MCP-only写入404、MCP匿名401、initialize/session/tools/list、三个真实工具调用。

初轮 smoke 暴露了 REST 一致性缺陷；修复后的第一轮 MCP 检查遇到测试客户端未解析 `id:` 开头的 SSE，修正测试解析器后，从空库重新执行的最终37项完整检查全绿。未隐藏失败、未通过等待几秒绕过一致性问题。

结构化结果：LOCAL_JAVA_AI_TEST_20261015_EVIDENCE.json（本地测试产物，不入库：`./LOCAL_JAVA_AI_TEST_20261015_EVIDENCE.json`）。复跑脚本：[scripts/local-java-ai](../../scripts/local-java-ai/README.md)。本地临时原始日志：`/tmp/rp-local-java-verify4.log`、`/tmp/rp-local-smoke-complete.json`、`/tmp/rp-local-persistence.json`、`/tmp/rp-docker-build.log`。

## 尚未通过与环境收尾

实际执行标准 Dockerfile 构建，但 Temurin Ubuntu 基础层下载报 `short read ... unexpected EOF`，构建未完成。备用官方 Alpine JRE 拉取长时间未完成，已停止。**没有成功构建或启动本轮新的 Java 容器**；不能将宿主机JDK21结果写作完整镜像验收。后续需恢复镜像下载后执行真实 Dockerfile 构建与全容器回归，再测 JVM 内存/延迟。

本轮 Java/fixture 进程与独立测试容器已停止，测试卷保留供复查；用户原有两个运行容器未变更。没有生产发布、旧Python线上退役、生产模型请求或业务数据库迁移。JVM-4、R-5/R-6及B-2保持未完成。三根现状文档已以同一修复标识同步。
