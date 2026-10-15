# Python 运行时退役说明（2026-10-15）

JAVA-AI-MIGRATION-20261015 已将默认运行配置切到 Java。此目录仅保留迁移参考与旧版本回滚对照；不再进入默认源码发布包/运行镜像/服务测试CI。不要再按本目录旧Dockerfile部署新环境。

- 生成/审核：`backend/agent-service` 的 `AgentGenerationService`。
- RAG/导入/初始化/MCP：`backend/knowledge-service`。
- 默认服务名8090/8095保留；REST读取/生成现在要求内部凭证，写入/MCP分别要求admin/MCP凭证。
- Python运维与离线eval工具继续保留，不属于部署运行服务。
- 生产镜像尚未切换，不能据此声明线上退役完成。完整验证与回滚见 `docs/educare/JAVA_AI_MIGRATION_20261015.md` 和根RUNBOOK。
