---
name: deploy
description: 本地/测试环境拉起 EduCare 全栈并冒烟验证（Docker Compose + 健康检查 + smoke 脚本）。当用户要求"部署/起栈/deploy/一键起/跑起来看"时使用。
---

# 部署与冒烟工作流（EduCare）

> 生产/部署一律走 `docker/docker-compose.yml`。无 GPU 环境的真跑见 `docs/educare/E2E_RUNBOOK.md`。

## 1. 校验编排
```bash
cd docker && docker compose config   # 仅解析，不需 daemon
```

## 2. 起基础设施 + agent 全栈
```bash
docker compose up -d                 # mysql:8.0 / redis:7-alpine / nacos / milvus / gateway /
                                     # ai-inference / agent-service(8087) / mcp-student-data(8094) /
                                     # knowledge-rag-mcp(8095) / memory-mcp(8096)
docker compose ps                    # 等四个 agent 相关容器 healthy
```
- agent-service `depends_on: service_healthy` 会等两个 MCP server 探活后启动。

## 3. 灌库（首次）
```bash
for f in 01_init 03_agent_init 04_student_extras 05_intervention_feedback; do
  docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < ../sql/init/$f.sql
done
```

## 4. 冒烟
```bash
bash scripts/mcp_smoke_test.sh                 # 7 个 MCP tool happy path
bash scripts/smoke_test_agent.sh               # 端到端任务（trigger→poll→产物校验）
```

## 5. 真跑 AgentLoop
- 宿主起 llama.cpp(8091)；或无 GPU 时按 RUNBOOK 用 `scripts/mock_llm_server.py` 桩。
- `EDUCARE_AGENT_LOOP_ENABLED=true`，`curl -XPOST :8080/agent/api/v1/task/trigger/1` → 轮询到 COMPLETED。

## 通过判据
- 容器全 healthy；smoke 脚本 exit 0；任务终态 COMPLETED 且 risk/plan 落库。

## 红线
- 不要 `docker compose down -v`（清库卷）；不要 `git push`/`mvn deploy`，除非明确要求。
