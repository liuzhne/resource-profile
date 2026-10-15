# JAVA-AI-MIGRATION-20261015（2026-10-15）：Spring AI 全 Java 运行时迁移

## 交付状态

用户已明确要求 RAG、知识导入、knowledge-rag MCP 一并 Java 化，最终退役 Python 服务。本次完成源码、默认构建/部署配置与本地回归；**未发布生产镜像、未改线上服务、未调用外部模型、未改写生产向量**。因此“代码已迁移”与“线上 Python 已退役”分开记录；执行计划 JVM-4、R-5/R-6/B-2 保持未完成。

当前 Spring AI **1.1.6** 能满足本轮需要，无须引入 Spring AI Alibaba。保留既有 Spring Boot/JDK 基线，避免把框架升级与运行时迁移混在一起。

## 功能映射

| 原 Python 功能 | Java 实现 | 兼容与安全边界 |
|---|---|---|
| 风险/方案/审核生成 | [AgentGenerationService](../../backend/agent-service/src/main/java/com/edu/agent/service/AgentGenerationService.java) | 复用 ChatClient、HTTP限流/用量/trace；plan/audit业务主路径直接调用，无Python回退 |
| 审核结果处理 | [AgentTaskServiceImpl](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java) | 审核失败/无效输出进入REJECTED与人工复核；保留安全错误码/HTTP状态 |
| 旧HTTP生成/聊天/诊断接口 | [兼容入口](../../backend/knowledge-service/src/main/java/com/edu/knowledge/GenerationCompatibilityController.java) → [agent内部入口](../../backend/agent-service/src/main/java/com/edu/agent/controller/InternalGenerationController.java) | 8090路径保留，新增X-Internal-Token强制校验；内部controller独立校验并返回真实401/503，网关不开放_internal |
| Embedding | [KnowledgeConfig](../../backend/knowledge-service/src/main/java/com/edu/knowledge/KnowledgeConfig.java) | Spring AI OpenAiEmbeddingModel；保留供应商URL前缀，独立embedding密钥；模型/URL/维度隔离缓存 |
| 多query、多集合dense检索 | [RagService](../../backend/knowledge-service/src/main/java/com/edu/knowledge/RagService.java) | 保留IP排序、候选扩展、可选HTTP rerank；按集合+chunk_id去重，保留source/doc_id/source_url；真实零结果为空，依赖故障503 |
| Milvus读写/初始化 | [MilvusStore](../../backend/knowledge-service/src/main/java/com/edu/knowledge/MilvusStore.java) | REST v2管理/写入 + Java SDK STRONG读取，保留原auto-ID/embedding/chunk_id布局，不重建旧集合；64位PK精度保留；HTTP200内业务错误仍判失败 |
| 切片/知识导入 | [TextChunks](../../backend/knowledge-service/src/main/java/com/edu/knowledge/TextChunks.java)、RagService.upsert | none/fixed_size/sentence；UTF-8字节、来源、chunk数上限；向量拒绝零值/NaN/Inf/错维度；内容+元数据+策略+embedding身份幂等 |
| 知识MCP三个工具 | [KnowledgeTools](../../backend/knowledge-service/src/main/java/com/edu/knowledge/KnowledgeTools.java) | Spring AI Streamable HTTP `/mcp`，原search_cases/search_policies/search_psychology及top_k默认/上限保留 |
| FastAPI鉴权 | [KnowledgeSecurityFilter](../../backend/knowledge-service/src/main/java/com/edu/knowledge/KnowledgeSecurityFilter.java) | health公开；API内部凭证、导入/初始化admin凭证、MCP凭证分离；未配置503、错误401 |
| Python运行容器/初始化命令 | [Java镜像](../../docker/Dockerfile.knowledge)、[K3s](../../k8s/workloads.json)、[Cloud Compose](../../docker/docker-compose.cloud.yml) | 同一jar两进程；8090 API，8095 MCP-only且关闭HTTP写入/生成；Render同端口合并API/MCP时显式开启MCP并使用无尾斜杠`/mcp` |

Java默认生成方案采用现有Agent最终答案schema（deadline/goal/milestones/horizon等），不再输出旧Python的另一套嵌套字段。审核只发送studentId/grade/major及方案，要求privacy/necessity/ethics/third_party四维唯一且完整，禁止总判定通过却有失败项或manual_review_required=true；严格JSON解析拒绝重复键/尾随文本。所有结果仍属于模型建议，保留人工审核流程。

导入采用集合级Redis租约（15分钟，embedding工作预算10分钟）阻止并发写入；向量全部完成后先插入新片段，再删除查询到的精确旧PK，避免先删后写的数据丢失。写前清旧幂等hash并更新revision，写后再次更新revision；写入失败不会误判原文档已成功恢复。Milvus操作不是事务，部分失败可能暂存重复片段；停写后重试完整同doc_id文档，详见RUNBOOK。旧Python导入不识别新锁，禁止混合版本并发导入。

## 已执行验证

| 验证 | 结果 | 证据/范围 |
|---|---|---|
| JDK21全后端 `mvn clean verify` | 首轮724测试，0失败/错误/跳过 | 12个Maven reactor项目（含parent），原有安全覆盖率门通过 |
| 最后变更后的受影响模块 `mvn -pl agent-service,knowledge-service -am verify` | common80 + agent381 + knowledge25 = 486测试通过 | 与未变模块结果合并，当前全后端728测试，0失败/错误/跳过；不是声称728全部在最后一次命令重跑 |
| 真实本地HTTP/MCP | 通过 | Random-port Spring Boot/Tomcat启动、initialize/session、tools/list、tools/call；8095 API关闭；鉴权与响应契约；依赖为合成mock |
| Spring AI embedding实际HTTP协议 | 通过 | 回环stub验证Cloudflare风格路径前缀、独立Bearer密钥、真实OpenAiEmbeddingModel请求/响应；无外部调用 |
| Milvus REST契约、导入恢复、来源、Unicode切片 | 通过 | MockRestServiceServer/服务单测，非真实Milvus；部分失败旧行不删除、旧hash不导致恢复跳过 |
| 部署与打包脚本 | Cloud17 + K3s15 = 32测试通过 | 原安全测试保留；新Java源纳入包、Python运行源排除、SQL诊断初始化文件遗漏修正 |
| Cloud Compose合成配置 | 通过 | 无真实密钥；两个服务使用Java构建/探针、内部凭证、MCP-only开关；RAG进程不接收生成密钥 |
| eval数据集 | 50例validate-only通过 | 无模型评分请求，不能据此勾选R-5.4 |
| 脚本语法/差异空白 | 通过 | Python py_compile、Bash -n、git diff --check |

验证使用工作区已缓存JDK21，未跳过Enforcer。初次迁移时Docker不可达；用户启动Docker后，真实Redis/Milvus、导入/检索/MCP、重启持久化与故障注入已完成，并修复成功码与读取一致性问题；Java运行于宿主机JDK21，镜像构建受基础层下载截断阻塞，详见[本地测试报告](./LOCAL_JAVA_AI_TEST_20261015.md)。上表测试数保留初次迁移批次口径，本轮knowledge-service新增回归后27项通过。测试日志位于本机临时目录 `/tmp/rp-full-backend.log`、`/tmp/rp-migration-final.log`、`/tmp/rp-knowledge-final.log`、`/tmp/rp-cloud-tests.log`、`/tmp/rp-k3s-tests.log`；这些临时文件不作为持久生产证据，测试源码与本目录证据摘要用于复验。

## 退役与剩余验收

- 默认Compose/Cloud Compose/K3s/Render的AI运行入口已换Java；`scripts/cloud_deploy.py`默认源码包不再含旧Python运行目录；CI服务测试改为Java，保留Python部署/eval工具测试。
- `ai-inference-service/`旧目录保留为迁移对照，见[退役说明](../../ai-inference-service/RETIREMENT.md)；不是默认运行组件，不宣称仓库已无Python文件。
- 上线前在隔离环境完成镜像、真实Redis/Milvus、三工具一致性、来源、持久化、内存/延迟、异常及备份恢复验证，再用明确授权的合成资料验证完整Agent、审核、PDF与反馈。此前生产JSON生成400并没有因这次重构被证明解决。
- 发布需要成套切换agent与两个知识运行镜像，保存旧版本；变更不重建现有Milvus集合，不自动修改业务SQL。线上进程确认Java且真实验收通过后才完成JVM-4。
- 模型成本/真实资料授权、R-5/R-6和既有B-2保持原门禁，不把已发布前端的17Ready记录当作本次新镜像证据。

## 实现依据

- [Spring AI OpenAI Embeddings官方文档](https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html)：独立embedding配置；具体构造签名通过本地1.1.6 jar与编译/协议测试确认，未照搬2.x API。
- [Milvus 2.4 REST自定义集合](https://milvus.io/api-reference/restful/v2.4.x/v2/Collection%20(v2)/Create.md)、[Milvus 2.4 REST检索](https://milvus.io/api-reference/restful/v2.4.x/v2/Vector%20(v2)/Search.md)：旧schema兼容适配依据。
- 项目决策、架构和验收命令同步于根目录ARCHITECTURE/DECISIONS/RUNBOOK的同名记录。
