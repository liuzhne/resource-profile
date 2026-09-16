# Resource-Profile 运行手册

> 最近更新：2026-09-16
> 适用范围：当前仓库的开发、测试、排错和发布准备。真实 AI/生产签字状态以 [`docs/educare/EXECUTION_PLAN.md`](./docs/educare/EXECUTION_PLAN.md) R-5/R-6 为准。

## 1. 前置条件

| 工具 | 要求 | 说明 |
|---|---|---|
| JDK | **17，且只能是 17.x** | Maven Enforcer 要求 `[17,18)` |
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

每个 Java 终端先设置同一个开发 JWT 密钥；缺失或少于 32 字节时 `JwtUtil` 会让服务 fail-fast：

```bash
export JWT_SECRET=edu-portrait-dev-jwt-secret-change-in-prod-0123456789
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
| Maven 在 validate 直接失败 | `java -version`、`mvn -version` | 把 `JAVA_HOME` 切到 JDK 17；不要跳过 Enforcer |
| gateway 返回 503 | Redis 日志、`token:{userId}` | gateway 会话校验 fail-closed；恢复 Redis 与正确 `REDIS_PASSWORD` |
| 有 JWT 仍 401 | JWT secret、Redis 当前 token、是否重新登录 | gateway/auth/agent 必须使用同一 `JWT_SECRET`；重新登录取得当前 token |
| gateway 返回 503/404 service unavailable | Nacos readiness 和服务列表 | 确认目标服务已启动并注册；基础 compose 不含六个普通业务服务 |
| agent-service 启动失败 | :8094/:8095 health、MCP initialize 日志 | 先启动两个 MCP；核对 URL、endpoint `/mcp` 和 token |
| MCP 返回 401 | 三端 `EDUCARE_MCP_TOKEN` | 使用相同非空值；生产至少 32 字符 |
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

前提：JDK17、现有 Render workspace/API凭证、演示账号只读授权。报告不得包含JWT、完整响应记录或原始 DEBUG 日志。使用 [bench-production-api.py](./scripts/bench-production-api.py) 顺序发送持久连接请求，外部完整响应耗时与 Server-Timing 分列，快速429/503不算通过。第一笔连接和休眠唤醒独立保留；热态统计取稳定样本并同时报告最大值，不能仅报告最佳值。

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
