# 本地 Java AI 真实依赖测试

此目录只创建 `rp-java-migration-test` 项目，使用合成数据及本地固定向量模型桩。没有外部模型请求；不能作为语义召回或完整 Agent/PDF 验收。

首选流程见根 `RUNBOOK.md` 的 `LOCAL-JAVA-AI-TEST-20261015`。`smoke.py` 普通模式要求空测试库；`--persist` 模式使用已导入数据。不要在生产环境运行，固定 synthetic 凭据仅用于隔离本地测试。

当 Docker 基础镜像下载失败时，可以让真实依赖在 Docker 中运行、Java 服务在宿主机 JDK 21 运行：

```bash
docker compose -f scripts/local-java-ai/compose.yaml -f scripts/local-java-ai/host-ports.yaml up -d redis etcd minio milvus
mvn -f backend/pom.xml -pl knowledge-service -am verify
# 三个终端分别运行 fixture、API、MCP
python3 scripts/local-java-ai/embedding_fixture.py
REDIS_PORT=16379 MILVUS_PORT=19539 PORT=18090 \
  EMBEDDING_BASE_URL=http://localhost:18092/v1 \
  EDUCARE_ADMIN_TOKEN=synthetic-admin EDUCARE_INTERNAL_TOKEN=synthetic-internal \
  EDUCARE_MCP_TOKEN=synthetic-mcp \
  "$JAVA_HOME/bin/java" -jar backend/knowledge-service/target/knowledge-service-1.0.0-SNAPSHOT.jar
REDIS_PORT=16379 MILVUS_PORT=19539 PORT=18095 \
  KNOWLEDGE_MCP_ENABLED=true KNOWLEDGE_API_ENABLED=false \
  EMBEDDING_BASE_URL=http://localhost:18092/v1 \
  EDUCARE_ADMIN_TOKEN=synthetic-admin EDUCARE_INTERNAL_TOKEN=synthetic-internal \
  EDUCARE_MCP_TOKEN=synthetic-mcp \
  "$JAVA_HOME/bin/java" -jar backend/knowledge-service/target/knowledge-service-1.0.0-SNAPSHOT.jar
python3 scripts/local-java-ai/smoke.py --output /tmp/rp-java-smoke.json
```

重启 Redis/Milvus 和 Java 服务后执行 `smoke.py --persist` 验证持久化。停止时只操作上述测试项目；测试卷保留，重复从空库验证时才能删除本项目的合成测试卷。当前实际执行结果见 `docs/educare/LOCAL_JAVA_AI_TEST_20261015.md`。
