---
description: 本地一键起栈并冒烟（docker compose + 健康检查）
argument-hint: "[可选: 服务名，缺省起全套]"
---

请按本项目方式拉起并冒烟 $ARGUMENTS（缺省起全套基础设施 + agent 全栈）。

步骤：
1. `cd docker && docker compose config` 先校验编排。
2. `docker compose up -d`（或指定服务），等容器 `healthy`（mysql/redis/nacos/milvus/agent-service/mcp-*）。
3. 灌库（首次）：`docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < sql/init/01_init.sql`（及 03/04/05）。
4. 冒烟：
   - `bash scripts/mcp_smoke_test.sh`（7 个 MCP tool）
   - `bash scripts/smoke_test_agent.sh`（端到端任务）
5. 报告每个服务状态与冒烟结果；失败给出 `docker compose logs -f <service>` 定位线索。

注意：真实 LLM 需宿主 llama.cpp（8091）；无 GPU 时按 `docs/educare/E2E_RUNBOOK.md` 用桩 LLM。
不要 `docker compose down -v`（会清库）。
