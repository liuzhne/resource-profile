# Resource-Profile · 师生资源画像系统

面向学校的师生资源画像与教育风险干预平台：管理教师 / 学生档案、心理问卷与测评、数据看板，并内置 **EduCare AI 子系统**——以 **AgentLoop（ReAct）+ MCP 工具 + Dense RAG + OpenAI 兼容 LLM** 生成学生风险画像与干预方案，中高风险经合规审核后才会放行。

技术栈：Vue 3 · Spring Boot 3.2.5 / Spring Cloud 2023 / Spring AI 1.1.6 · Python FastAPI · Milvus · MySQL / Redis / Nacos。

> 项目级现状以根目录 [`ARCHITECTURE.md`](ARCHITECTURE.md)（架构）、[`DECISIONS.md`](DECISIONS.md)（决策）、[`RUNBOOK.md`](RUNBOOK.md)（运行手册）为准；EduCare 任务进度见 [`docs/educare/EXECUTION_PLAN.md`](docs/educare/EXECUTION_PLAN.md)。

---

## 功能概览

| 模块 | 说明 |
|---|---|
| 登录鉴权 | JWT（access 24h / refresh 7d）+ Redis 会话白名单，登出 / 改密即时吊销；角色 admin / teacher / student |
| 系统管理 | 用户、角色管理 |
| 教师 / 学生画像 | 档案、成绩、考勤 |
| 心理健康 | 问卷设计、发放、作答、结果分析；学生端测评与历史 |
| 数据看板 | 统计聚合 + ECharts 可视化 |
| EduCare 智能预警 | 风险任务触发、AgentLoop 画像、干预方案、合规审核、报告导出、干预反馈闭环、SSE 实时预警 |

安全基线：网关统一 JWT 门禁（`/_internal/**` 一律 403）、`AccessGuard` 防越权（IDOR）、`@SensitiveField` 字段级权限默认开启、送模前 Prompt 清洗与脱敏。

## 架构

```
浏览器 ─► frontend (Vue 3) ─/api─► gateway :8080 ── JWT + Redis 会话校验
                                      │
        ┌──────────────┬──────────────┼───────────────────────────────┐
        ▼              ▼              ▼                               ▼
   auth :8081   user/teacher/student/mental/data :8082-8086   agent-service :8087
                                                                      │
             ┌────────────────────────┬───────────────────────────────┼──────────────┐
             ▼ MCP                    ▼ MCP                           ▼ ChatClient   ▼ Feign（审核 / legacy）
   student-data MCP :8094    knowledge-rag MCP :8095             LLM :8091     ai-inference-service :8090
   （4 个只读学生数据工具）  （3 个知识检索工具）                                 （risk / plan / audit / rag）
                                      │
                    Embedding :8092 · Reranker :8093 · Milvus :19530
```

EduCare 默认链路（`educare.agent.loop.enabled=true`）：

1. `POST /agent/api/v1/task/trigger/{studentId}` 创建任务（Sentinel 限流 + Redis 幂等 / 任务锁）。
2. AgentLoop 最多 8 轮 ReAct，按需调用 7 个 MCP 工具：`get_student_profile`、`get_academic_history`、`get_mental_indicators`、`get_attendance`、`search_cases`、`search_policies`、`search_psychology`。
3. 最终答案经 `FinalAnswerValidator` 校验，产出 `risk_analysis` + `intervention_plan`。
4. NONE / LOW 直接完成；MEDIUM / HIGH 必须经 Python `/api/v1/agent/audit` 合规审核，结果为 `COMPLETED` 或 `REJECTED`，审核异常不静默放行。

关闭 AgentLoop 时回落 legacy 流水线（聚合画像 → 风险 → RAG → 方案 → 审核）。详见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

## 目录结构

```
backend/                 Maven 多模块（Java 17）
  gateway/               8080  路由、JWT 门禁、CORS
  auth-service/          8081  登录 / 刷新 / 登出
  user-service/          8082  用户管理
  teacher-service/       8083  教师档案
  student-service/       8084  学生档案、成绩、考勤
  mental-service/        8085  问卷与心理评估
  data-service/          8086  看板统计
  agent-service/         8087  风险任务、AgentLoop、审核编排、导出、SSE
  mcp-student-data/      8094  学生数据 MCP server
  common/                      Result、JWT、AccessGuard、字段权限、Prompt 清洗
ai-inference-service/    Python FastAPI：legacy risk/plan/audit、dense RAG、knowledge-rag MCP（:8095）
frontend/                Vue 3 · Vite · Element Plus · Pinia · ECharts
docker/                  开发 / 生产 / 监控 compose、各服务 Dockerfile、nginx、Prometheus/Grafana
sql/                     初始化与生产改密脚本
eval/                    风险识别离线评测（CI eval-gate）
scripts/                 冒烟、压测、备份、模型服务启动脚本
docs/                    部署、EduCare 设计与验收文档
render.yaml              Render Blueprint（云端演示部署）
```

## 快速开始（本地开发）

**前置：** JDK 17 · Maven 3.8+ · Node.js 18+ · Docker（compose v2）· Python 3.10+。本地模型建议 Apple Silicon、内存 ≥ 24GB。

```bash
# 1. 基础设施 + Python 推理服务
docker compose -f docker/docker-compose.yml up -d mysql redis nacos milvus-standalone etcd minio ai-inference-service

# 2. 后端构建（再按 RUNBOOK §4.4 顺序逐个 spring-boot:run，agent-service 最后启动）
cd backend && mvn clean install -DskipTests

# 3. 前端
cd frontend && npm install && npm run dev   # http://localhost:5173
```

默认账号 `admin` / `teacher` / `student`，密码与用户名相同（生产环境务必用 `sql/prod/` 脚本改密）。

启用 EduCare AI 还需要在宿主机提供 OpenAI 兼容的 LLM（:8091）、Embedding（:8092）、Reranker（:8093），并初始化 Milvus 知识库：

```bash
docker compose -f docker/docker-compose.yml exec ai-inference-service python -m scripts.init_milvus
docker compose -f docker/docker-compose.yml exec ai-inference-service python -m scripts.seed_knowledge
```

完整启动顺序、健康检查、验收与排错见 [`RUNBOOK.md`](RUNBOOK.md)。

## 部署

| 场景 | 入口 |
|---|---|
| 生产（自建服务器） | `docker/docker-compose.prod.yml` + nginx TLS，见 [`docs/DEPLOY.md`](docs/DEPLOY.md) |
| 监控 | `docker/docker-compose.monitoring.yml`（Prometheus + Grafana） |
| 云端演示 | [`render.yaml`](render.yaml)：Render + Aiven MySQL + GroqCloud LLM，见 `RUNBOOK.md` 的 RENDER / GROQ 章节 |

## 测试

```bash
cd backend && mvn clean install                      # Java 单测（JUnit 5 + Mockito）
cd ai-inference-service && python3 -m unittest discover -s tests   # Python 单测
python3 eval/run_eval.py --validate-only             # 评测数据集体检
```

CI：`.github/workflows/` 下的 backend-ci、frontend-ci 与 eval-gate。

## 常用配置

| 变量 | 作用 |
|---|---|
| `NACOS_SERVER` / `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 连接 |
| `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis |
| `JWT_SECRET` | JWT 签名密钥（≥ 32 字节，放 Nacos `common.yml` 或 `.env`，勿提交） |
| `LLM_BASE_URL` / `LLM_MODEL` / `LLM_API_KEY` | OpenAI 兼容 LLM 端点 |
| `EMBEDDING_BASE_URL` / `RERANKER_BASE_URL` | 向量化 / 重排 |
| `MILVUS_HOST` / `MILVUS_PORT` | Milvus |
| `EDUCARE_AGENT_LOOP_ENABLED` | AgentLoop 主路径开关（默认开） |
| `educare.field-permission.enabled` | 字段级权限（默认开） |

## 文档索引

- [ARCHITECTURE.md](ARCHITECTURE.md) · [DECISIONS.md](DECISIONS.md) · [RUNBOOK.md](RUNBOOK.md)
- [生产部署](docs/DEPLOY.md) · [EduCare 执行计划](docs/educare/EXECUTION_PLAN.md) · [MCP 设计](docs/educare/MCP_DESIGN.md) · [字段权限](docs/educare/FIELD_PERMISSION.md) · [E2E 运行手册](docs/educare/E2E_RUNBOOK.md)
