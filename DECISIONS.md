# Resource-Profile 架构与工程决策

> 最近更新：2026-10-11
> 记录范围：当前仍有效的项目级决策。历史阶段细节见 [`docs/educare/EXECUTION_PLAN.md`](./docs/educare/EXECUTION_PLAN.md) §6。

每条记录包含背景、选择、放弃方案和后果。被替换的决策不得直接删除，应改为“已取代”并链接新决策。

## ADR-038：Langfuse v4 上报及默认内容边界（LANGFUSE-OTLP-20261007）

- 日期：2026-10-07。
- 背景：用户已填好 Cloud JP 凭据，认证 HTTP 200；现有 Java legacy ingestion 与 Python v2 无法满足 Cloud 2026-11-16 后的生命周期。旧采集同时包含师生提示词、回复和工具摘要，直接启用云采集会改变数据出站边界。
- 选择：Java 保留异步 `traceGeneration` 契约，以现有 RestClient/Jackson 发送完成的 OTLP HTTP/JSON span；Python固定经过依赖解析验证的新 SDK、显式映射 HOST 为 base_url、退出时 shutdown/flush。均保留配置缺失关闭和失败不影响业务。使用 Basic 及 ingestion version 4；默认 `LANGFUSE_CAPTURE_CONTENT=false`，不采集 I/O 与自由文本工具元数据。
- 原因：修正实际停用风险，同时保留现有业务调用和部署变量；内容默认关闭可以验证模型、用量、延迟而不新增真实师生内容传输。
- 放弃方案：只填密钥或继续 legacy 不解决停用；升级所有 AI 依赖会扩大兼容风险；为当前两个 Java 调用新增全套分布式 trace 及 Maven SDK 超出迁移需要；只省略 update 的 input 但继续装饰器自动采集仍会泄漏内容。
- 代价与约束：Java Agent/LLM仍为独立trace，Python仅raw generation，未新增RAG/MCP span；内容关闭限制依赖文本的在线评估。三供应商配置同步原VM，内部九项凭据相等、0600回滚保留，无新增收费资源。Java20项、Python41项真实SDK及部署27项回归通过；重建两镜像、16私有服务、Milvus初始化、合成RAG/双MCP均已有实际证据。公开SQL全行和Milvus全内容来源核验后，自动审批仍要求明确数据外传授权；用户授权具体公开示例资料向Groq传输后仅触发一次。Agent第二轮遇HTTP400 JSON校验与429限流，约20秒FAILED；实际Cloud `llm.chat`、`agent.loop`、`llm.audit`记录的模型/usage/timing及I/O关闭均通过，因此只完成R-5.1，不完成完整生成、质量或生产门。选择保留故障证据和原参数，放弃无证据改协议/开启付费/自动重复示例任务；后续须隔离供应商错误复现并验证最终产物。
- 公网阶段：Caddy经既有发布门启动；外部HTTPS/TLSv1.3、登录200/匿名401/内部403、仅22/80/443可达及20项业务鉴权通过。默认内容采集关闭，实际应用trace确认无I/O；网站可用不替代完整AI与生产验收。临时RAG验收使用固定合成文档和独立持久化/新查询检查，避免缓存旧ID造成假失败；无架构影响，未宣称生产缓存即时失效。
- 协议证据：新 OTLP v4 合成三 span 接口返回200；v2查询三条、父子/模型/用量均正确，无 input/output。见 [官方迁移](https://langfuse.com/integrations/native/opentelemetry/migration-to-v4)、[OTLP映射](https://langfuse.com/integrations/native/opentelemetry)，操作见 RUNBOOK 同名记录。

## ADR-037：Langfuse Cloud 账户准备与旧 ingestion 迁移计划（LANGFUSE-CLOUD-SETUP-20261007）

- 日期：2026-10-07。
- 状态：历史账户准备方案，已由 [ADR-038](#adr-038langfuse-v4-上报及默认内容边界langfuse-otlp-20261007) 的实际迁移与验收取代；下列v2/内容描述只代表准备时状态。
- 背景：用户准备配置Langfuse；当前 [Java客户端](./backend/agent-service/src/main/java/com/edu/agent/config/LangfuseClient.java) 直接调用 `/api/public/ingestion`，[Python依赖](./ai-inference-service/requirements.txt) 固定 `langfuse>=2.36,<3.0`。官方宣布Cloud trace/observation旧ingestion将于2026-11-16停用，仅填key不能解决长期兼容性。
- 选择：推荐Cloud Hobby、日本区域，用专用项目生成Public/Secret Key，当前工程合同仍为 `LANGFUSE_HOST`、`LANGFUSE_PUBLIC_KEY`、`LANGFUSE_SECRET_KEY`。HOST填所选区域站点根地址，JP为 `https://jp.cloud.langfuse.com`。长期接入须将Java改为OTLP/OpenTelemetry、Python迁移受支持的新SDK，明确映射新版 `LANGFUSE_BASE_URL`，保留异步/fail-soft与无凭据关闭行为。
- 原因：Cloud Hobby无需信用卡、每月50000 units与30天历史访问窗口，能够避免增加当前VM的自建可观测工作负载；JP是亚洲部署的区域选择，未测量网络延迟。账户可以先准备，trace功能必须完成迁移和真实验收后才能视为长期可用。
- 放弃方案：仅替换HOST/重启现有v2代码不能消除接口停用；直接升级所有Python依赖可能破坏现有LangChain/FastAPI契约；额外部署旧版Langfuse会增加维护及生命周期负担，本次不创建。
- 代价与约束：Java当前LLM调用与AgentLoop汇总是并列trace，不保证跨微服务父子关联；Python仅已装饰的raw risk/plan/audit调用覆盖，不能宣称RAG/MCP/embedding都有trace。当前会发送prompt、模型回复及部分工具汇总，首次只使用合成演示数据；真实师生数据的脱敏与字段边界须在启用其云trace前落实。Langfuse两项key与模型供应商key不同，私下保存，不使用聊天传递。迁移/trace验证未执行，R-5/R-6不变。
- 来源（2026-10-07）：[Cloud定价](https://langfuse.com/pricing)、[接入及区域地址](https://langfuse.com/docs/observability/get-started)、[旧ingestion停用说明](https://langfuse.com/docs/api-and-data-platform/features/public-api)。实际配置与验收见 RUNBOOK 同名记录及 [Kamatera手册](./docs/deployment/KAMATERA_DEPLOYMENT.md)。

## ADR-036：供应商探针使用明确客户端标识（GROQ-PROBE-UA-20261007）

- 日期：2026-10-07。
- 背景：核对剩余配置时，本地 Groq key 已填写，但默认 urllib 模型目录请求和生成请求均返回 403；目录响应确认边缘错误 1010。明确 HTTP User-Agent 后目录可见选定模型，同一 JSON/low 生成请求返回 200，不能将客户端拦截误报为无效 key。
- 选择：[scripts/k3s-deploy.py](./scripts/k3s-deploy.py) 的 `provider_request` 统一发送 `ResourceProfile-DeploymentCheck/1.0`；保留 Bearer 认证、受信 TLS、原有响应检查和错误脱敏。公开运维脚本同步到已授权 VM 并保留旧版本，未写入/同步 API Key 或更改业务工作负载。
- 原因：修正已实际复现的探针假失败，避免在发布门中无端要求换 key 或换模型；不改变业务客户端或服务架构。
- 放弃方案：重复轮换有效 key、切换模型和盲目重试 403不能解决当前请求特征问题；关闭 TLS 或记录完整错误内容会削弱认证/凭据边界。
- 验证与约束：本地默认目录请求 403/1010、显式标识目录200且目标模型存在；真实 JSON_OBJECT/low 生成200、Cloudflare 中文合成输入返回有限非零1024维向量；修正后的真实探针通过、既有13项离线回归通过。不能把本地探针成功推断为 VM 的认证或完整业务成功；VM 两个模型 key 仍为占位值，剩余部署与 trace/HTTPS 验收见 RUNBOOK。401/403仍可能有其他认证/权限原因，不把本次样本当作所有403的解释。
- 入口与凭据约束：域名已经解析到 VM，public 区尚未声明 http/https 服务，选择在发布前显式准备对应规则并验证外部80/443及受信TLS，放弃现在修改未发布的入口或关闭防火墙。Cloudflare `EMBEDDING_API_KEY` 保存原始 token，由现有 Python 客户端自动添加 `Authorization: Bearer`；不得把 curl 的 `-H` 或 Bearer 前缀写进 key 字段。本次未修改上述规则或密钥。

## ADR-035：沿用 Groq 免费生成并补齐 Cloudflare embedding（GROQ-CLOUDFLARE-DEPLOY-20261006）

- 日期：2026-10-06。
- 状态（2026-10-07）：用户已确认选用；本地与 VM 的非秘密部署参数及真实 Account ID 已填写并静态核对，API Key/内部凭据未改动；模型 key 本地已设置、VM 仍为占位值，真实模型/RAG 验收及公网入口未完成。
- 背景：Kamatera 全栈部署的聊天与向量配置仍是占位值；用户要求免费且适合当前项目，并沿用现有 LLM 提供商。仓库 [render.yaml](./render.yaml) 已使用 Groq `openai/gpt-oss-120b`，公开 API 未提供本项目所需 embedding。
- 选择：生成继续采用 Groq，保留 `JSON_OBJECT`、reasoning effort `low`、最大输出 1200 与关闭 llama.cpp 专用 `cache_prompt` 字段；向量采用 Cloudflare Workers AI `@cf/baai/bge-m3`，独立 token、OpenAI 兼容端点和 1024 维。仅使用 dense 检索，不恢复已删除的 Hybrid/ModelRouter 能力。
- 原因：复用现有 Spring AI/LangChain 调用契约与 ReAct 配置，不需要本机 GPU；BGE-M3 的多语言与维度规格符合中文知识库部署方向。免费方案无需为此次模型接入新增付款方式；端到端速度、中文检索质量和真实 JSON 输出仍须实测。
- 放弃方案：Gemini 有免费生成与 embedding，但当前 Java 路径、URL 门禁和 embedding 默认 3072 维需要适配；OpenRouter 零充值免费模型仅 50 次/日，对多轮 Agent 的余量更少；Cerebras 已改为验证付款方式后 US$5/30 天试用，不能当作持续免费层。未自动开通任何收费计划。
- 代价与约束：两套账户凭据；免费额度不保证 SLA 或任意并发。Groq 当前公开免费上限 30 次/分钟、1000 次/日、8000 tokens/分钟、200000 tokens/日，组织共享、以控制台实际限制为准；画像任务可多次调用模型。Cloudflare 全账户 Workers AI 共享 10000 neurons/日，Free 超额会失败。相同维度不代表相同向量空间，旧知识必须用选定模型重新嵌入后验收。
- 来源（2026-10-06 核查）：[Groq 额度](https://console.groq.com/docs/rate-limits)、[Cloudflare 定价](https://developers.cloudflare.com/workers-ai/platform/pricing/)、[BGE-M3 规格](https://huggingface.co/BAAI/bge-m3)、[Gemini 定价](https://ai.google.dev/gemini-api/docs/pricing)、[OpenRouter 限额](https://openrouter.zendesk.com/hc/en-us/articles/39501163636379-OpenRouter-Rate-Limits-What-You-Need-to-Know)、[Cerebras 试用](https://inference-docs.cerebras.ai/support/rate-limits)。操作与回滚见 RUNBOOK 同名记录和 [Kamatera 手册](./docs/deployment/KAMATERA_DEPLOYMENT.md)。
- 参数落实边界：本次仅写既有供应商的公开参数并保留 API Key；Account ID 不猜测。Java 的 JSON_OBJECT/low 映射通过；Python 客户端没有对应选项，保留现有提示词与解析，未扩大为 SDK 行为修复。Python temperature 0.3/timeout 120 秒由现有清单固定，私有文件显式记录相同值不代表任意修改均会透传。静态解析使用合成 key/Account ID，不能代替真实供应商或结构化输出验收。
- 2026-10-07 / GROQ-CLOUDFLARE-DEPLOY-20261006：用户提供 Account ID 后，仅更新两侧 `EMBEDDING_BASE_URL`，其余字段保持原值。选择保留凭据符合此次“API-key 之外其他参数”的范围，不将本地设置状态当作远端已认证。实际本地 K3s `check` 通过；API、知识检索与上线仍待验证。

## ADR-034：单节点 K3s 与有界 HPA（K3S-HPA-DEPLOY-20261006）

- 日期：2026-10-06。
- 状态：用户已明确选择 K3s + HPA；清单与安全门已实现，实际集群/业务/HPA 1→2→1通过，完整AI/公网验收待真实模型配置。
- 背景：用户要求容器随负载弹性扩缩容，Compose 固定副本不能提供 HPA；现有 Kamatera 单机预算不能默认扩展收费 VM。
- 选择：本次部署采用 [`k8s/workloads.json`](./k8s/workloads.json) 和 [`scripts/k3s-deploy.py`](./scripts/k3s-deploy.py)，17 工作负载沿用 ADR-033 的同机内网/鉴权契约，Docker 构建后导入 K3s containerd。关闭 Traefik/ServiceLB，只有 Caddy hostPort 80/443 公开；Kubernetes 管理经本地 SSH，节点 API/Flannel/kubelet 不对公网放行。SELinux Enforcing、官方 K3s 策略、Secret 加密存储、默认账号真实 bcrypt 改密与 TLS/模型发布门保持有效。
- 原因：metrics-server 与 CPU requests 为 HPA 提供真实指标，先仅 data-service 1–2 副本、CPU 70%、缩容窗口 300 秒，验证指标/负载/回缩后再扩大。基线 requests 1.8 CPU / 9.1875 GiB，最高 limits 17.75 GiB，为系统和控制面保留预算。
- 放弃方案：同时运行 Compose/K3s 会冲突入口并重复资源；所有 Java 服务一起翻倍超过单机余量；managed Kubernetes、多台节点或 VM autoscaler 会新增费用；直接给 agent/MCP/AI 灌库增加副本缺少任务恢复、文件共享、会话及并发写入证据。SSE 已有 Redis fan-out，但缩容排空仍需验收。
- 代价与约束：HPA 不增加 4 核/24 GB 容量，不是节点 HA；8 个 local-path PVC 共申请 46 GiB，仅在本节点持久化。单例 Recreate 升级有中断。Secret 与私有清单需 0600/忽略，不复制 kubeconfig 到聊天。SQL/字体 hostPath 使用持久 SELinux 标签，不能照搬 Compose Z 选项。模型配置待用户提供，不以 dummy key 绕过全栈门。
- 验证：13 项离线清单/秘密/发布/密码边界测试通过；实际 Node Ready、metrics-server、5 PVC/基础设施和12服务Ready，三角色登录/权限/退出撤销20项通过；HPA受控负载1→2通过、自动回缩1已验。双 MCP、TLS、LLM/RAG与完整恢复待验证。步骤及回滚见 [`k8s/README.md`](./k8s/README.md) 和 [Kamatera 手册](./docs/deployment/KAMATERA_DEPLOYMENT.md)。R-5/R-6 不因部署准备完成自动更新。

## ADR-033：Kamatera 试用承载完整单机内网栈（KAMATERA-DEPLOY-20261006）

- 日期：2026-10-06。
- 状态：用户已创建 `resource-profile-sg`（45.130.165.168，Rocky 9.8），US$10 押金到账、试用配置已核验；正式运行编排已由 ADR-034 的 K3s 取代，Compose 保留备选。
- 背景：用户只有预付卡，Oracle 注册验证受阻；完整栈的当前内存预算超过常见免费小实例，仍希望微服务通过内网互调。Kamatera 官方 [付款说明](https://www.kamatera.com/faq/answer/which-payment-methods-do-you-accept/) 接受带持卡人姓名的预付卡，但具体卡片能否通过验证须以注册结果为准。
- 选择：使用新加坡 Rocky Linux 9 64-bit 单机，控制台已选择 Type A 4 核 / 24 GB / 100 GB、无托管与每日备份附加服务，月付报价 US$92、含 1000 GB 流量（超额 US$0.01/GB）。以 [`docker/docker-compose.cloud.yml`](./docker/docker-compose.cloud.yml) 和 [`scripts/cloud_deploy.py`](./scripts/cloud_deploy.py) 作为原 Compose 共享实现（本次运行方式由 ADR-034 取代），Kamatera 与 OCI 提供薄入口，避免复制全栈造成配置漂移。复用 ADR-032 的私网、随机凭据、默认账号改密和 TLS 发布门禁；源码与私有配置分开通过 SSH 上传，初始使用演示数据库，不迁移真实心理/学生记录。入口与费用流程见 [Kamatera 部署手册](./docs/deployment/KAMATERA_DEPLOYMENT.md)。
- 原因：资源预算覆盖当前完整栈，同机容器网络避免跨服务公网往返；明确预付卡政策提高注册可行性。平台费用无需修改业务代码或削减 RAG。
- 部署阻塞修复：[`main.py`](./ai-inference-service/app/main.py) 导入了 `rag_upsert` 但未注册路由，导致真实应用灌库 404；旧 HTTP 测试手工组装路由掩盖了问题。选择在生产 `create_app()` 注册既有 router，并将 [HTTP 回归](./ai-inference-service/tests/test_http_integration.py) 改为实际应用构建，验证正常写入及 401/503/422 拒绝路径。放弃在部署脚本绕过 HTTP 直接写向量库或只测试手工 router 的方案，避免绕过管理 token 与幂等逻辑；不扩大公网路由。
- 上传边界：只归档可构建运行的源码/配置；独立构建发现白名单误排 `com/edu/data`、提示词和自动配置元数据，已修正并增加完整生产源码/POM/资源覆盖检查。拒绝上传整个 checkout 或 Git 历史，避免夹带本地配置/数据；字体与新秘密单独通过 SSH 传输，采用带 OFL 的官方静态 TrueType、只读挂载，避免把 OTF 改扩展名或变量字体当作 PDFBox 可用静态字体。
- Rocky 适配：按用户运维偏好使用 Rocky 9，并保留 SELinux Enforcing 和 Docker SELinux 支持。三个只读 bind 使用 `Z`，共享工具先校验来源，采用能执行 relabel 的 legacy bind 路径；放弃 `create_host_path:false` 的 Mount API 路径（当前 Compose 可遗漏 relabel），也放弃关闭 SELinux 或 privileged 容器来绕过权限。Milvus `configs.content` 由 Compose 注入容器层、模式 0600，避免增加宿主秘密文件。代价是必须通过部署入口启动，且需在实际 Rocky 主机验证标签、只读访问及 firewalld/Docker 规则。
- 放弃方案：Oracle 仍要求当前无法完成的银行卡验证；长期免费小实例没有完整栈容量依据；多个免费 Web Service 仍存在公网往返和休眠；CPU 主机本地运行 14B 模型未经容量验证。仅停机不能结束计费，费用告警也不是硬性封顶。
- 代价与约束：根据 [试用条款](https://www.kamatera.com/faq/answer/what-are-the-terms-of-the-free-trial/)，试用为 30 天、单台服务器配置不超过 US$100、1 TB 流量，到期自动转收费。创建前核对实际报价、试用标记及所有附加项；US$10 押金由账号所有者自行完成，免费部署授权不包含超额或续费授权。最终公钥/凭据设置与创建条款由用户接手。Type A CPU 资源不保证，尚不能承诺性能。模型 API 用量独立计算；域名、外部生成及 embedding 接口待确定。历史凭据轮换 B-2 与 R-5/R-6 继续独立验收。
- 验证：Python 最小环境 32 项、部署工具 13 项通过；安全包独立解压后 JDK21 全后端 `package -DskipTests`、前端生产构建、Python factory/import/401 与 JAR 关键资源检查通过。官方静态字体以项目 OpenHTMLToPDF/PDFBox 实际渲染和双 PNG 视觉检查通过，提取文本存在 U+F9E4 兼容字，NFKC 后断言通过。Compose/Caddy 静态检查见手册；可选 chat/MCP SDK 替身不是实际模型证据。账号/押金/主机创建及 SSH 跨通道身份校验已通过，Rocky Enforcing、Docker SELinux/hello-world 和 firewalld 实测通过；Linux 镜像构建进行中。实际容器、TLS/模型/RAG/恢复待验证，不宣称已上线。

## ADR-032：OCI 单主机内网编排，分阶段开放入口（OCI-DEPLOY-20261006）

- 日期：2026-10-06。
- 状态：单机编排方案保留；OCI 平台选择已由 ADR-033 取代，未创建 OCI 资源。
- 背景：Render 免费 Web Service 的互调经过公网；现有基础 Compose 也未包含完整业务服务，无法作为完整 OCI 迁移入口。免费 CPU 主机不适合同时承载当前大模型，生成与 embedding 供应商可能使用不同密钥。
- 选择：新增独立 [`docker/docker-compose.oci.yml`](./docker/docker-compose.oci.yml)，补齐 9 Java + 2 Python 与数据层，禁用 Nacos，使用 Docker DNS 显式 URL。前端由 Caddy 处理同源 API/SSE 与自动 TLS；仅入口发布 80/443。保留 AgentLoop、双 MCP、dense RAG 和各层鉴权，默认不启用可选 reranker。增加独立 embedding key，空值兼容共享模型 key。
- 原因：把业务与数据库/MCP 往返移回一台主机内网，降低跨服务公网延迟，并保留现有领域与安全契约。[`scripts/oci-deploy.py`](./scripts/oci-deploy.py) 将私网启动、默认账号改密和公网发布分开，避免新建数据库默认密码直接暴露。
- 放弃方案：继续分散到免费公网 Web Service 仍保留网络和休眠成本；将现有 Compose 当作完整生产栈会缺失业务容器；直接 `up` 开公网会暴露默认种子密码；在 12 GB 免费 VM 上承诺完整 Milvus 栈没有容量依据；静默去掉 RAG 或改用付费 shape 不满足用户目标；本机 14B 模型复制到免费 CPU 主机缺少可用推理容量。
- 代价与约束：单主机非高可用；内存上限合计 17.25 GiB，目标 24 GiB、启动门 ≥20 GiB，真实容量仍待验证。Oracle 纯免费与付费租户 A1 月度免费用量不同，账号升级和超额度不能自动授权。外部模型 API 有独立额度与数据处理边界，迁移真实学生/心理数据前明确授权。新部署凭据私有保存；历史凭据轮换 B-2 仍未完成。暂不撤除现有 Render 服务。
- 验证：本地 Python 30 项、部署安全 7 项通过；Compose 17 服务/端口/profile/DNS/内存断言、前端构建、Caddy validate/adapt 通过。ARM manifest 已核验；完整镜像构建、容器、OCI、受信任 HTTPS、真实 LLM/RAG/trace 与恢复均待验证。见 [OCI 部署手册](./docs/deployment/OCI_DEPLOYMENT.md)，R-5/R-6 状态不变。

## ADR-001：业务微服务使用 Spring Boot + Nacos

- 状态：已采纳
- 背景：用户、教师、学生、心理、统计和 AI 编排有不同数据访问与发布节奏。
- 选择：Java 21（2026-09-26 起，原 Java 17，见 ADR-028）、Spring Boot 3.2.5、Spring Cloud 2023.0.1、Nacos 服务发现/可选配置；gateway 按 `/auth`、`/user`、`/teacher`、`/student`、`/mental`、`/data`、`/agent` 路由。
- 原因：保持领域边界，复用 Spring/MyBatis 生态，并允许 Agent 通过 Feign 组合已有服务。
- 放弃方案：单体应用会把心理、用户和 AI 发布周期绑在一起；服务间共享 mapper 会绕过授权和领域所有权。
- 后果：本地完整联调必须启动多个进程并保证 Nacos 可达；跨服务契约需要控制器/Feign/MCP 契约测试。

## ADR-002：Java 负责业务编排，Python 负责模型与向量能力

- 状态：已采纳
- 背景：业务状态机、权限和 MySQL 事务更适合现有 Java 栈；LLM、embedding、Milvus 与 FastMCP 的 Python 生态更成熟。
- 选择：`agent-service` 拥有任务状态与业务编排；`ai-inference-service` 提供风险/方案/审核、RAG、知识 upsert；Python 还以独立进程提供 knowledge-rag MCP。
- 放弃方案：把全部 AI 逻辑移入 Java 会复制 Python 向量生态；把业务状态机移入 Python 会削弱现有鉴权、MyBatis 和服务治理的一致性。
- 后果：必须维护 Java↔Python HTTP 契约；Python 故障需要明确 fallback，不能让合规审核默认放行。

## ADR-003：敏感数据默认使用本地 OpenAI 兼容模型

- 状态：已采纳
- 背景：学生画像包含未成年人身份、学业和心理数据，同时项目需要可控成本与离线演示。
- 选择：生成 LLM、embedding、reranker 运行在宿主 :8091/:8092/:8093；Java Spring AI 与 Python LangChain/httpx 都使用 OpenAI 兼容接口。
- 放弃方案：云端 ModelRouter 已在瘦身中删除；直接绑定某个闭源 SDK 会增加数据外发与供应商锁定。
- 后果：部署方负责模型权重、GPU 和启动脚本；真实模型稳定性必须在 R-5 验证，桩模型只能证明机械链路。

## ADR-004：AgentLoop ReAct 是默认路径，legacy 四阶段作为回退

- 状态：已采纳
- 背景：固定 risk→rag→plan 流水线可控但工具选择僵硬；完全隐式原生 tool-calling 又不利于观察 thought/action/observation 和精确测试。
- 选择：`EDUCARE_AGENT_LOOP_ENABLED=true` 默认走最多 8 轮 ReAct JSON；模型可调用 7 个 MCP 工具并输出双 JSON `final_answer`。中高风险仍复用独立合规审核。`false` 回落 legacy。
- 放弃方案：删除 legacy 会失去回退与对比基线；默认 native tool-calling 会降低细粒度可观测性。native 保留为可选协议，但必须具有同等 ToolGuard、validator 和 hooks 语义。
- 后果：prompt、工具名和 final schema 都是稳定契约；解析失败、工具失败或校验失败不能伪装成 COMPLETED。

## ADR-005：只保留两个 MCP server，并使用 Streamable HTTP

- 状态：已采纳
- 背景：Agent 需要读取业务数据和检索知识，但过多 MCP 服务会提高运维成本。
- 选择：保留 Java `student-data` :8094（4 个只读工具）和 Python `knowledge-rag` :8095（3 个检索工具）；采用 Spring AI 1.1.6/FastMCP 的 Streamable HTTP `/mcp`。
- 放弃方案：旧 SSE transport 已被新协议替换；memory-server 因未接入主链且投入产出低被删除；把 MCP 全收回内联会失去清晰工具边界和独立契约展示。
- 后果：agent-service 启动时会初始化两端工具清单，MCP 不可达可导致启动失败；生产必须配置共享 MCP token 和网络隔离。

## ADR-006：RAG 使用 Milvus dense 检索，可选 reranker

- 状态：已采纳
- 背景：当前知识库规模有限，已有 Milvus 2.4 与 BGE embedding 服务。
- 选择：1024 维 dense recall，候选扩大后可用 BGE reranker 精排；集合为 cases/psychology/policies/success。
- 放弃方案：Hybrid Retrieval/BM25 已删除，因为默认长期未启且在当前规模下过度工程；Elasticsearch 增加重型基础设施；迁移 pgvector 会破坏已有 Milvus 灌库链。
- 后果：专有名词检索能力取决于 embedding/reranker 质量；必须以 R-5.3 的真实语料 baseline 判断是否需要重新引入 lexical 检索。

## ADR-007：MySQL 是业务事实源，Redis 与 Milvus 可重建

- 状态：已采纳
- 背景：任务、反馈和用户数据需要事务与备份；缓存、锁、会话和向量索引具有不同恢复方式。
- 选择：MySQL 保存业务记录；Redis 保存会话、幂等、锁和缓存；Milvus 保存从知识源派生的向量。
- 放弃方案：把任务状态只放 Redis 无法可靠审计；把源文档事实只放 Milvus 难以恢复和版本化。
- 后果：生产必须备份/演练恢复 MySQL；Redis 丢失要求用户重登；Milvus 可通过 upsert 重新构建。

## ADR-008：鉴权采用多层 fail-closed 防线

- 状态：已采纳
- 背景：系统处理学生心理和身份敏感信息，单靠前端菜单或网关网络边界不足以防 IDOR 与直连绕过。
- 选择：gateway JWT+Redis 会话；下游 `AccessGuard` 对象/角色授权；`FieldPermissionAdvice` 字段过滤；agent-service 自鉴权；MCP 共享 token；LLM 前脱敏与 Prompt 清洗。
- 放弃方案：只依赖前端权限属于 UX 控制；只验证 JWT 而不校验 Redis 无法立即吊销；明文信任网关身份头更易伪造。
- 后果：Redis 鉴权故障返回 503 而不是放行；新增接口必须同时补准入、对象授权和字段权限测试。

## ADR-009：使用 llama.cpp `cache_prompt` 与字节稳定 prompt

- 状态：已采纳
- 背景：本地大模型重复 system prompt 成本高，Spring AI 通用 cache metadata 与 llama.cpp 扩展并不等价。
- 选择：Java HTTP interceptor 和 Python raw chat 请求显式注入 `cache_prompt:true`；system prompt 放资源文件并保持字节稳定。
- 放弃方案：依赖不存在/不匹配的 Spring AI `cache_control` 无法保证 llama.cpp slot cache 命中；运行时拼接不稳定前缀会降低命中率。
- 后果：修改固定 prompt 必须重新测缓存；真实模型目标命中率 ≥80%，属于 R-5.4。

## ADR-010：Langfuse 自托管 v2，可选且 fail-soft

- 状态：已采纳
- 背景：需要观察 `agent.loop` 和每次 `llm.chat`，但开发/测试不能硬依赖 trace 平台。
- 选择：compose profile 提供 Langfuse v2 + PostgreSQL；Python 使用 v2 SDK，Java 直接调用 ingestion HTTP；key 为空或上报失败时主流程继续。
- 放弃方案：v3 需要 ClickHouse、Redis、worker，对当前规模过重；非官方 Java SDK增加维护风险；Langfuse v2 不采用 OTel 路线。
- 后果：trace 不是业务事务的一部分；“已接代码”不等于“活 trace 已验收”，R-5.1 完成前必须标待验证。

## ADR-011：CI 采用全测试 + 安全关键类定向覆盖率门

- 状态：已采纳
- 背景：历史模块总体覆盖率不均，立即设置全局 80% 会产生大量与风险不成比例的阻塞。
- 选择：Java 全模块 `clean test`，并对 Auth/JWT/Prompt/用户教师授权/MCP token 等已补测类设置行覆盖率 ≥80%；Python 无外部服务 ASGI/unittest；前端 lint/build/size/audit；eval 数据集无条件校验，真 LLM 阈值按环境开启。
- 放弃方案：只跑受影响模块易漏跨模块安全回归；当前直接设置全局高覆盖率会把补历史测试与功能变更绑死。
- 后果：新增安全关键类应进入 JaCoCo includes；长期仍应逐步提高全局覆盖，而不是把定向门当终点。

## ADR-012：生产只公开 nginx，密钥由 preflight 硬门

- 状态：已采纳
- 背景：gateway、Agent、MCP、数据库和观测面板不应直接暴露公网，开发默认密码不能进入生产。
- 选择：nginx 80/443 做 TLS、SPA 和 `/api` 反代；其余 published ports 绑定 `127.0.0.1`；`docker/.env` 不入库；preflight 拒绝占位/弱 JWT/弱 Redis/MCP 凭据。
- 放弃方案：直接公开 gateway 或 MCP 会扩大绕过面；仅在文档“建议改密”无法形成可执行门禁。
- 后果：证书、密钥和默认账号轮换是发布前置条件；不得用 `docker compose down -v` 做常规回滚。

## ADR-013：上线声明以 R-5/R-6 证据为准

- 状态：已采纳
- 背景：代码测试已覆盖大量机械链路，但真实 Qwen/BGE/Langfuse 与完整生产 compose 尚未全部形成当前证据；基础 compose 还未声明六个普通业务服务；`rag_upsert` 的测试 app 手工注册了 router，而生产 `create_app()` 当前没有注册它。
- 选择：在 R-5/R-6 全部勾选前，只能称“代码侧完成/待环境验收”，不得称“完整生产已上线”。
- 放弃方案：用桩 LLM、单测或部分 compose 健康代替真实模型/全栈签字会掩盖部署缺口。
- 后果：发布负责人必须按 [`RUNBOOK.md`](./RUNBOOK.md) 收集 CI、模型、RAG、trace、安全、备份恢复和监控证据；HTTP upsert 在主应用实际暴露并验收前不得计入完成面。

## ADR-014：修复方案必须同步三份项目文档

- 状态：已采纳
- 背景：历史文档同时存在保留能力与已删除能力的描述，修复若只改代码会继续制造操作和架构认知偏差。
- 选择：只要提出或落地缺陷修复，同一变更必须维护 `ARCHITECTURE.md`、`DECISIONS.md`、`RUNBOOK.md`；即使无架构影响也要留下同标识记录。
- 放弃方案：只在 commit/PR 描述记录无法为后续本地任务提供稳定上下文；只更新“受影响的一份”容易漏掉验证或取舍。
- 后果：三份文档是修复完成定义的一部分。纯格式/拼写机械修改不触发该规则。

## ADR-015：Render 预览部署使用显式服务 URL，持久数据层不伪装成免费 Web Service

- 状态：部分已采纳；数据层方案待部署方确认
- 日期/修复标识：2026-09-01 / RENDER-DEPLOY-20260901
- 背景：Render Blueprint 已关闭 Nacos，但 agent-service 与 mcp-student-data 的 Feign client 仍只配置
  服务名，MCP 服务启动卡在 load-balancer 创建阶段；Agent 又以 20 秒默认窗口初始化两个会冷启动的
  免费 MCP 服务。线上合成登录探测同时证明 `edu-portrait-mysql.onrender.com:3306` 返回 JDBC connect
  timeout，因为 Web Service 的公网入口不是任意 TCP 代理。
- 选择：无注册中心部署为 Feign client 注入显式 HTTPS URL；Agent 保留 MCP 启动 fail-fast，但 Render
  预览环境把请求窗口扩到 120 秒。MySQL 只能选择付费 Private Service + `/var/lib/mysql` 持久盘，
  或部署方提供外部托管 MySQL；Redis 改用 Render Key Value 或外部托管 Redis。未获得计费授权前不
  自动创建付费数据库，也不把当前红灯掩盖成“已上线”。
- 原因：显式 URL 与当前关闭 Nacos 的事实一致，120 秒覆盖 Render 官方说明的 50 秒以上冷启动；
  持久数据层必须使用能接收对应协议且能保存数据的产品形态。
- 放弃方案：关闭 MCP client/延迟到业务请求只会让 Agent 表面启动而运行时缺工具；继续使用
  `*.onrender.com:3306/6379` 会稳定超时；把 MySQL 塞进免费 Web Service 即使偶尔启动也会在重启或
  部署时丢数据；未经确认直接升级计费计划不符合变更授权边界。
- 后果：免费预览的首次请求仍可能较慢；生产发布需要数据库/Redis 连接信息、模型供应商三元组
  `LLM_BASE_URL/LLM_MODEL/LLM_API_KEY` 与 RAG 基础设施。MySQL 镜像若被采用，会按顺序执行
  `sql/init/01`~`05` 全部脚本。
- 证据：Render 失败部署 `dep-daaie3ffdruc73ajt8pg`、`dep-daaiic5g1s2s73d9t920`；2026-09-01
  gateway `/actuator/health` 返回 `UP`，合成不存在账号请求返回 `CannotGetJdbcConnectionException`，
  auth 日志底层为 `SocketTimeoutException: Connect timed out`。本地 JDK 17 定向 Reactor 125 例与
  前端构建通过；修复后的云端复验待完成。

## ADR-016：Render 免费预览采用 Aiven MySQL，并在未配置供应商时关闭 AI 启动依赖

- 状态：已采纳；云端凭据与数据导入待完成
- 日期/修复标识：2026-09-01 / AIVEN-RENDER-20260901
- 背景：部署方已选择 Aiven Free MySQL，并明确暂不配置 LLM 供应商。原 Blueprint 把 MySQL 和
  Redis 作为普通 Free Web Service 暴露，二进制协议不可达；Agent 还会在启动时初始化 MCP，从而让
  尚未配置的 AI 依赖阻塞整套系统。
- 选择：关系库使用外部 Aiven Free MySQL，JDBC 强制 `sslMode=REQUIRED`；七个连接池上限统一为 3、
  最小空闲为 0。Redis 会话改用 Render Free Key Value 的私网连接串。Aiven 凭据仅在 Render 环境组
  手工维护，`render.yaml` 不包含连接秘密。无 LLM 阶段关闭 Spring AI MCP client 和 AgentLoop，
  前端构建时不注册 AI 预警/LLM 追踪路由。
- 原因：Aiven 提供真正的 MySQL 协议、持久存储与 TLS，能被 Render 公网出站连接；小连接池适配免费
  实例连接上限。Render Key Value 与 Spring Data Redis 协议兼容，且其私网连接串可以由 Blueprint
  自动注入。显式关闭 AI 比保留一个必然报错的入口更符合当前可用能力。
- 放弃方案：继续使用 Render Web Service 承载 MySQL/Redis 已被线上 timeout 证伪；Render 付费私有
  MySQL 需要计费授权；TiDB 不是原生 MySQL且需要额外兼容验证；用假 LLM key 或 mock 端点会把演示
  能力误报成真实供应商能力。
- 后果：Aiven Free 单节点没有生产 SLA；Render Free Key Value 重启会清空会话，用户需重新登录。
  AI 页面在重新构建前不可见，直连 Agent API 也不作为当前验收面。恢复 AI 时必须同时配置
  `LLM_BASE_URL/LLM_MODEL/LLM_API_KEY`、可达 MCP/RAG 服务，将两个开关打开并重建前端。
- 证据：[`render.yaml`](./render.yaml)、七个服务的 `application.yml` 与
  [`frontend/src/router/index.js`](./frontend/src/router/index.js)；JDK 17 Reactor 177 例、前端
  `VITE_AI_ENABLED=false` 生产构建和 YAML 语法解析已通过。Aiven 实例创建、SQL `01`~`05` 导入、
  Render Blueprint 同步与线上登录仍待验证。

## ADR-017：Render 健康检查必须复用 FastAPI 已注册路由

- 状态：已采纳
- 日期/修复标识：2026-09-01 / AI-HEALTH-20260901
- 背景：`ai-inference-service` 已在 Render 启动并监听 8090，但 Blueprint 探测
  `/api/v1/health`；生产 app 实际直接注册 `health.router`，公开路径是 `/health`，连续 404 使部署
  长期停留在 Deploying。
- 选择：将 `edu-portrait-ai-inference` 的 `healthCheckPath` 改为 `/health`，不为部署探针额外复制
  一个别名路由。
- 原因：健康检查应以运行应用现有的公开契约为准；单一路径避免文档、测试与部署配置继续漂移。
- 放弃方案：在 FastAPI 增加 `/api/v1/health` 兼容别名会扩大无业务价值的 API 面；继续等待无法让
  固定 404 自愈；关闭健康检查会掩盖进程不可用。
- 后果：无架构和业务行为变化；后续修改 health router 前必须同步 Render 探针与 RUNBOOK 验证命令。
- 证据：Render 部署 `dep-dabdn8jtqb8s73fjjsh0` 日志显示 Uvicorn 启动完成、随后
  `GET /api/v1/health` 持续 404；[`app/api/health.py`](./ai-inference-service/app/api/health.py) 声明
  `prefix="/health"`。修复后云端复验待完成。

## ADR-018：登录客户端不复制服务端密码长度策略

- 状态：已采纳
- 日期/修复标识：2026-09-02 / LOGIN-VALIDATION-20260902
- 背景：线上登录页预填并展示默认账号 `admin/admin`，数据库与 `auth-service` 均接受该账号，但前端
  将密码最小长度硬编码为 6，导致五字符默认密码无法提交，线上浏览器验收被客户端校验阻断。
- 选择：登录表单只校验用户名和密码非空，不在客户端限定已有账号的密码长度；凭据正确性与账号策略
  继续由 `auth-service` 判定。
- 原因：登录是既有凭据的验证入口，不是设置新密码的入口；复制一份不同步的长度策略会拒绝服务端合法
  账号。新密码强度约束应放在注册、重置或修改密码流程，并由服务端作为最终权威。
- 放弃方案：把前端下限从 6 改成 5 仍会形成第二套易漂移策略；把默认管理员密码改成六位以上会影响
  已初始化数据库与验收账号，且不能解决其他历史合法密码被客户端误拒绝的问题。
- 后果：任意非空密码都可提交到认证接口，但错误凭据仍由服务端拒绝；真实使用前仍应轮换默认账号密码。
- 证据：[`frontend/src/views/login/index.vue`](./frontend/src/views/login/index.vue)；线上浏览器修复前显示
  “密码长度不能少于6位”，后端网关验收脚本使用同一默认账号登录成功；2026-09-02 前端 lint 0 error
  （1 条既有 Prettier warning）且生产构建通过。修复后的线上复验待完成。

## ADR-019：Render 免费预览使用 GroqCloud，并显式隔离 llama.cpp 扩展字段

- 状态：已采纳
- 日期/修复标识：2026-09-02 / GROQ-CLOUD-20260902
- 背景：部署方选择 GroqCloud Free 作为在线 LLM。现有 Java interceptor 与 Python raw client 无条件
  注入 llama.cpp 专用 `cache_prompt`，标准 OpenAI 兼容供应商可能拒绝未知字段；Java 与 Python client
  对 base URL 是否包含 `/v1` 的约定也不同。
- 选择：增加 `LLM_CACHE_PROMPT_ENABLED`（默认 true，保留本地 llama.cpp 行为），Render Groq 模式
  设为 false；Java 显式使用 `https://api.groq.com/openai`，Python 使用
  `https://api.groq.com/openai/v1`，两侧使用同一 Groq 模型。API Key 只保存在 Render secret 配置；共享
  环境组作为集中来源，若存在服务级同名变量则必须删除或保持一致。
- 原因：用显式能力开关兼容本地与云端供应商，避免按域名硬编码判断；服务专属 base URL 避免修改两个
  client 的既有路径拼装契约。
- 放弃方案：让 Groq 忽略未知字段缺少契约保证；删除 `cache_prompt` 会损失本地 llama.cpp 已验收能力；
  把 API Key 写入 Blueprint 会泄露持久凭据。
- 后果：Render 恢复 AgentLoop、MCP client 与前端 AI 路由；免费供应商受配额和可用性约束。未部署
  Milvus/BGE 时 knowledge-rag 仍降级为空结果，不能把 LLM 接通等同于完整 RAG 上线。
- 证据：[`render.yaml`](./render.yaml)、[`SpringAiConfig.java`](./backend/agent-service/src/main/java/com/edu/agent/config/SpringAiConfig.java)、
  [`llm_client.py`](./ai-inference-service/app/services/llm_client.py)；2026-09-02 Java/Python 各 2 项兼容
  测试、Python 语法检查与前端生产构建通过，云端验收待完成。

## ADR-020：knowledge-rag MCP 使用独立的规范 endpoint

- 状态：已采纳
- 日期/修复标识：2026-09-02 / MCP-TRAILING-SLASH-20260902
- 背景：Render 首次开启双 MCP client 后，student-data 完成 initialize，但 FastAPI 中挂载的
  knowledge-rag 对 `POST /mcp` 和 `GET /mcp` 返回 307 到 `/mcp/`；Spring AI 的 Streamable HTTP
  transport 未将该重定向当作 MCP 响应，agent-service 因 fail-fast 初始化失败退出。
- 选择：保留每个 MCP server 独立 endpoint；knowledge-rag endpoint 支持环境变量覆盖，并在 Render
  显式设为 `/mcp/`。student-data 继续使用其原生 `/mcp`。
- 原因：直接请求服务端规范路径可保留初始化 fail-fast、共享 token 和工具发现语义，也兼容本地独立
  knowledge-rag server 的历史路径。
- 放弃方案：让客户端跟随 POST 重定向受 Java HTTP/MCP transport 行为限制；关闭 MCP 虽可启动但会
  违背开启 AgentLoop 工具链的目标；统一修改两个服务路由会扩大兼容性影响。
- 后果：线上双 MCP 初始化不再依赖重定向；新增的 endpoint 环境变量必须与实际 ASGI mount 路径一致。
- 证据：Render 日志中 `POST /mcp`、`GET /mcp` 均为 307，agent 随后在
  `McpSyncClient.initialize` 退出；修复后云端复验待完成。

## ADR-021：父 FastAPI 显式托管 FastMCP lifespan

- 状态：已采纳
- 日期/修复标识：2026-09-03 / MCP-LIFESPAN-20260903
- 背景：规范 endpoint 生效后，Render 日志显示请求进入 FastMCP，但
  `StreamableHTTPSessionManager task group was not initialized`；父 FastAPI 创建时没有注册自身已有的
  lifespan，也没有注册 `mcp_app.lifespan`。
- 选择：在创建父应用前构造 MCP ASGI 子应用，通过通用组合器依次进入 ai-inference 与 FastMCP
  lifespan，再把组合结果传给 `FastAPI(lifespan=...)`。
- 原因：这是 FastMCP ASGI 挂载的生命周期契约；组合器保留现有启动/关闭逻辑，并保证逆序释放资源。
- 放弃方案：在请求时惰性调用 SessionManager 私有 API 会绕开框架契约；把 MCP 改回独立 Render 服务
  会增加免费实例和冷启动面；关闭 MCP 会破坏 AgentLoop 工具链目标。
- 后果：父进程启动期间会同步初始化 MCP task group；若 MCP 构造失败，HTTP 推理服务仍按既有降级逻辑
  启动，但不会挂载损坏的 endpoint。
- 证据：Render 线上堆栈明确指向缺失 `mcp_app.lifespan`；新增生命周期顺序单元测试；修复部署后 Agent
  日志已显示 student-data 与 knowledge-rag 均完成 initialize/list tools，服务成功 Live。

## ADR-022：Render 采用 Groq 组织允许的模型并显式处理服务级覆盖

- 状态：已采纳
- 日期/修复标识：2026-09-03 / GROQ-RUNTIME-CONFIG-20260903
- 背景：新 Groq Key 经官方 `/openai/v1/models` 验证有效，但 Render 在线调用先返回 401，随后在修正
  Key 后返回 `model_permission_blocked_org`。检查发现 Render 服务级 `LLM_API_KEY` 占位值覆盖了共享环境组，
  且组织 Allowed Models 当前只允许 `openai/gpt-oss-120b`。
- 选择：在 agent-service 与 ai-inference-service 的服务级环境变量中保存同一 Groq Key；Blueprint 两侧
  模型统一改为 `openai/gpt-oss-120b`。共享环境组继续保存 Key 作为集中来源，但发布验收必须检查服务级
  同名变量是否覆盖。
- 原因：使用账户已经允许且官方请求返回 200 的模型可直接恢复功能；同步 Blueprint 可避免后续同步把
  Dashboard 热修复恢复为被禁用模型。
- 放弃方案：扩大 Groq 组织的 Allowed Models 会改变账户级权限且不是必要条件；仅修改共享环境组无法
  覆盖既有服务级同名变量；继续使用 qwen 会稳定返回 403。
- 后果：LLM 可用性受 `openai/gpt-oss-120b` 免费配额与 Groq 组织权限约束；若将来调整 Allowed Models，
  Java/Python 两侧必须同步改模型。密钥不得写入仓库、日志或前端变量。
- 证据：Groq `/openai/v1/models` 与 `openai/gpt-oss-120b` 最小 completion 均返回 200；Render 线上
  Python chat 返回 `GROQ_OK`，Agent 日志确认真实 LLM、AgentLoop 与双 MCP 初始化完成。完整业务任务受
  Aiven DNS 故障阻塞，不能据此宣称数据库链路已验收。

## ADR-023：先核验 Aiven 当前端点，不猜测替换失效主机名

- 状态：已采纳
- 日期/修复标识：2026-09-04 / AIVEN-DNS-20260904
- 背景：Agent 已成功启动且 liveness/readiness 均为 UP，但聚合健康检查返回 503；Render 日志中的
  MySQL health probe 以 `UnknownHostException` 失败，当前配置的 Aiven 主机名无法解析。
- 选择：登录既有 Aiven 项目，从 MySQL 服务的 Connection Information 读取当前 host、port、user 与
  TLS 要求；与 Render secret 逐项比对，仅更新不一致值，然后重新部署数据库消费者。
- 原因：Aiven 端点可能因服务删除、重建或停用而变化；以控制台当前连接信息为唯一事实来源，避免把
  猜测地址写入 Render 或仓库。
- 放弃方案：根据旧命名规则猜测 DNS 会扩大故障；临时关闭数据库健康组件会掩盖所有 CRUD/任务落库
  实际不可用；未经授权重建数据库可能丢失数据或改变费用。
- 后果：修复需要已登录的 Aiven 会话；若原服务已不存在，必须先确认数据保留与免费计划后再决定是否
  新建实例。任何数据库凭据仍只保存到 Render secret，不进入仓库或日志。
- 证据：线上 `/actuator/health` 返回 DOWN/503，而 `/actuator/health/liveness` 和
  `/actuator/health/readiness` 均返回 UP/200；同一时段日志明确记录 Aiven host 的 DNS 解析失败。恢复原
  Free-1-1gb 服务后 DNS 重新发布，Agent 聚合 health 已恢复 UP/200，无需改动连接凭据。

## ADR-024：Groq 免费层 429 采用定向有界退避

- 状态：已采纳
- 日期/修复标识：2026-09-04 / GROQ-429-RETRY-20260904
- 背景：真实 AgentLoop 第一轮已成功调用 MCP 工具，第二轮携带 observation 请求 Groq 时触发组织级
  8,000 TPM 限制；Groq 返回 429 并明确建议约 14 秒后重试。Spring AI 默认把 4xx 作为不可恢复错误，
  任务因此直接进入 FAILED。
- 选择：使用 Spring AI 1.1.6 原生 `spring.ai.retry` 配置，仅将 429 列入可重试 HTTP 码；起始退避
  15 秒、最大 30 秒、最多 3 次。自定义 `OpenAiApi` 注入自动配置的 `ResponseErrorHandler`，自定义
  `OpenAiChatModel` 注入自动配置的 `RetryTemplate`；401/403 等错误仍保持不可重试。
- 原因：等待服务端已给出的配额窗口可保留完整 prompt、工具 observation 与 final_answer 输出预算；
  使用框架重试层避免在 AgentLoop 中复制 HTTP 供应商逻辑。
- 放弃方案：把输出预算压到不足以稳定生成双 JSON 会降低质量；升级付费计划不符合当前免费部署目标；
  将全部 4xx 设为可重试会掩盖密钥或模型权限错误。
- 后果：命中 TPM 限制时单任务可能额外增加 15–45 秒延迟；超过 3 次仍明确失败。Render/Gateway 超时
  必须容纳该上限，且日志应保留 429 与重试次数用于验收。
- 证据：线上任务 2/4 在 MCP 工具调用后返回 Groq 429，响应给出 TPM limit=8000、建议等待约 10–14 秒；
  任务 4 仍为 `NonTransientAiException`，反证原自定义 Bean 绕过自动配置。Spring AI 1.1.6 字节码确认
  error handler 先按 `on-http-codes` 把 429 转为 `TransientAiException`，chat model 再由 RetryTemplate 执行
  退避。配置级测试固定两个依赖必须注入；修复后线上复验待完成。

## ADR-025：Groq ReAct 使用 JSON Object Mode

- 状态：已采纳
- 日期/修复标识：2026-09-04 / GROQ-JSON-MODE-20260904
- 背景：`e736507` 上线后已确认 429 会进入 Spring AI RetryTemplate；在无并发的任务 7 中，Groq 请求
  成功但 GPT-OSS 连续两轮未产生可解析的 ReAct JSON，任务以 `PARSE_ERROR` 结束。自由文本提示不足以
  保证协议语法，同时 2048 最大输出与默认 reasoning 会增加 8,000 TPM 免费额度压力。
- 选择：仅在 Render Blueprint 为 agent-service 设置 `response_format=json_object`、reasoning effort `low`
  和 1200 最大输出 tokens；自定义 `SpringAiConfig` 显式把这两个 Spring AI 选项传给 chat model。
- 原因：Groq 官方声明 GPT-OSS 120B 支持 JSON Object Mode，且项目提示已明确要求 JSON；使用供应商约束
  解码比继续扩展宽松字符串解析更可靠，降低 reasoning/output 预算也为多轮工具调用保留 TPM 空间。
- 放弃方案：记录或持久化完整原始模型输出会扩大敏感数据面；切换已下线的 Llama 模型不可行；在解析器
  猜测并修补任意非 JSON 文本无法提供稳定协议保证；升级付费层不符合当前部署目标。
- 后果：Render 上的每轮输出必须是 JSON 对象，复杂最终方案受 1200-token 上限约束；本地端点默认保持
  TEXT 兼容。若最终方案被截断，应先压缩 prompt/schema，再审慎调高预算并重新核算 TPM。
- 证据：线上任务 7 单线程执行、无 429，却连续两轮 parse error；Groq 官方 API 文档与 GPT-OSS 模型页
  明确列出 JSON Object Mode。配置级测试覆盖 response format 与 reasoning effort；线上复验待部署后执行。

## ADR-026：main 即生产，Render 在 CI 通过后按服务增量部署

- 状态：已采纳
- 日期/修复标识：2026-09-15 / RENDER-CD-20260915
- 背景：Render 服务未声明 `branch`（隐式跟随仓库默认分支 `main`），并使用已废弃的 `autoDeploy: true`
  （每个提交立即部署）。CI 只在 `pull_request` 上运行，main 的 ruleset 只要求走 PR、不要求检查通过，
  因此 CI 红的 PR 合入后照样上线，被部署的 main 提交本身从未验证。10 个 Docker 服务均无 `buildFilter`，
  任何提交（含仅改文档）都会全部重建；Hobby 工作区每月 500 分钟构建额度耗尽后 Render 停止构建，届时
  合入 main 也不再部署。
- 选择：`render.yaml` 为每个服务显式声明 `branch: main` 与 `autoDeployTrigger: checksPass`；10 个 Docker
  服务按 Maven 反应堆依赖声明 `buildFilter`（自身模块 + `backend/common/**` + 父 `backend/pom.xml` +
  自身 Dockerfile；ai-inference 为 `ai-inference-service/**`）；`backend-ci`/`frontend-ci` 增加 main 的
  push 触发，路径覆盖全部 buildFilter 与 `render.yaml`。
- 原因：以「被部署的那个提交」的 CI 结果门控上线，而非 PR 头提交；全部落在仓库配置内，可审阅、可回滚，
  不引入新密钥。buildFilter 让构建额度只花在确实变化的服务上。
- 放弃方案：① 保持 `commit` 触发、在 ruleset 加必需状态检查——路径过滤未触发的 workflow 会让 PR 永远
  等待必需检查，且仍不验证合并后的提交；② 关闭自动部署、由 GitHub Actions 调用 11 个 Deploy Hook——需
  维护 11 个秘密 URL 并自行实现按路径部署，重复 Render 已有能力；③ 不设 buildFilter——每次提交 10 次
  Maven 全量 Docker 构建，额度数周内即可耗尽。
- 后果：上线延迟增加一次 CI 时长（后端全量单测数分钟）。Render 在提交上检测不到 check 时不部署，
  buildFilter 与 CI push 路径必须同步维护；`npm audit` 遇新公布的 high/critical 漏洞会阻断该提交涉及的
  全部部署，需修依赖或经 Dashboard Manual Deploy。改 `common` 或父 POM 仍会重建 9 个 Java 服务。
  Blueprint 首次同步本变更时各服务可能各重建一次。GitHub 在每个提交上为 `coderabbitai` 自动建空 check
  suite（queued、0 runs）；Render 文档未说明是否计入，推断不计入（Dokploy #5284 实测 Render 自身的 App
  也会产生同类空 suite），首次合入须确认，若被阻塞按 RUNBOOK 关闭该 App 的自动建 suite。
- 证据：[`render.yaml`](./render.yaml)、[`backend-ci.yml`](./.github/workflows/backend-ci.yml)、
  [`frontend-ci.yml`](./.github/workflows/frontend-ci.yml)。线上前端 `index.html` 的 `last-modified` 为
  2026-09-13 08:49 UTC（#11 合入 main 后约 38 分钟），bundle 含 #11 的重试文案、不含未合入的 #13，佐证
  当前按 main 提交即部署；main 头提交 `bcb01c4` 的 check-runs 为空。本地 Schema 校验与路径覆盖自检见
  RUNBOOK 同名条目；Blueprint 同步与首次门控部署待验证。

## ADR-029：下游服务入口要求 JWT 或内部凭证，「无 token」不再等于内网

- 状态：已采纳（补全 ADR-008）
- 日期/修复标识：2026-09-14 / INTERNAL-AUTH-20260914
- 背景：Render Blueprint 中 student/mental/data/teacher/user/agent/mcp-student 都是 `type: web`，各有公网
  `https://edu-portrait-<name>.onrender.com`；免费实例收不到私网流量，内部 Feign 也只能走公网 HTTPS。
  `AccessGuard.allowSelfRoleOrInternal` 把「无 Authorization」当可信内网放行，`FieldPermissionAdvice` 对无 token
  请求不脱敏，`MentalController`、`/student/ids` 等端点更没有任何端点级校验，于是任何人不带 token 直连下游
  都能拿到完整数据（含未成年人心理数据、预警名单）。2026-09-14 只用聚合端点实测：直连
  `edu-portrait-data.onrender.com/data/dashboard/statistics` 无 token 返回 200，经网关同路径返回 401。
  ADR-008 的多层防线默认了下游不可公网直达，这个前提在 Render 上不成立。
- 选择：① 新增共享密钥 `EDUCARE_INTERNAL_TOKEN`（Render env group `generateValue`）；agent-service 的
  student/mental/data 与 mcp-student-data 的 student/mental Feign 客户端按客户端挂 `InternalCallFeignConfig`，
  附 `X-Internal-Token`。② common 新增 `ServiceAuthFilter`（默认开）：服务入口要求合法 JWT（签名+过期）或
  内部凭证（SHA-256 摘要 + `MessageDigest.isEqual`），否则 401，`/actuator/health` 豁免；auth-service、
  agent-service、mcp-student-data 显式关闭，分别由「只公开 `/auth/**` + Spring Security」、`AgentSelfAuthFilter`、
  `McpTokenFilter` 把守。③ `AccessGuard` 与 `RoleContextFilter`/`FieldPermissionAdvice` 只把「无 token 且凭证匹配」
  视为内部调用，带 token 一律按端用户处理，匿名只留 PUBLIC 字段。④ 网关 `InternalHeaderStripFilter` 最先剥掉
  客户端自带的同名头。⑤ 未配置密钥时所有 profile 一律不承认内部调用（fail-closed）；开发默认值只写在
  compose 与 RUNBOOK，preflight 拒绝该默认值和不足 32 字符的值。
- 原因：Render 免费层没有私网隔离，只能用正向凭证区分内部调用；入口统一拦截能覆盖所有端点（含没接
  `AccessGuard` 的历史端点）；端点与字段两层保留作纵深防御，入口门被关掉时也不会退化为放行。
- 放弃方案：只改 `AccessGuard`/字段权限 —— `MentalController` 等无校验端点仍可被匿名读写；Render 私有服务 ——
  免费层不提供，需计费授权；Feign 改走网关 —— 网关要求用户 JWT，定时扫描等系统任务没有用户上下文；
  按 profile 名决定未配置时是否信任无 token 请求 —— profile 名写错就回到漏洞状态；全局 Feign 拦截器 ——
  会把凭证发给 ai-inference 等不需要它的目标。
- 后果：部署后匿名直连下游返回 401。滚动部署期间若下游已升级而 agent/mcp 尚未升级，AI 取数会短暂 401
  并走 fallback。残余风险：下游入口不查 Redis 会话，已登出但未过期的 token 仍可直连下游（≤24h）；单一共享
  密钥，泄露需全服务轮换。原先 `MentalController` 缺少角色授权的问题现由 ADR-030 收口；其余残余风险仍有效。
- 证据：`ServiceAuthFilterTest`(9)、`AccessGuardTest`(17)、`FieldPermissionAdviceWalkTest`(9)、`RoleContextFilterTest`(5)、
  `InternalCallCredentialTest`(4)、`InternalCallFeignConfigTest`(2)、`InternalHeaderStripFilterTest`(4)、两个 Feign
  挂载测试，以及 student/mental/data/teacher/user/agent 各 controller 的匿名拒绝 / 内部凭证放行用例；JDK 17 全后端
  `mvn -B -ntp clean test` 11 模块 222 例通过，JaCoCo 定向门（新增 6 个类）通过；`scripts/test-preflight-prod.sh`
  通过。线上复验待部署后执行，只探聚合端点（RUNBOOK §6.1）。


## ADR-030：心理问卷端点授权与作答视图

- 状态：已采纳（补全 ADR-008、ADR-029）
- 日期/修复标识：2026-10-05 / MENTAL-AUTHZ-20261005
- 背景：内部凭证入口门只能确认调用身份，无法阻止已登录学生读取预警名单、完成情况或增删改问卷。
  学生侧复用完整问卷 DTO 会下发选项分值、计分规则和等级阈值，允许作答者按分挑选项；完成情况返回
  Map 行，注解驱动的字段脱敏无法覆盖原始 score。本条补齐代码中既有 `MENTAL-AUTHZ-20260914` 的文档。
- 选择：管理读取使用 `STAFF_VIEW`；写入问卷、题目及等级规则使用 `MENTAL_WRITE`（admin/psychologist）；
  完成情况原始 score 只对 `EXTREME_VIEW`（admin/psychologist）保留，其余教职工返回 null。管理端仅
  `/mental/analysis` 接受已验证内部凭证，其他端点只认可 JWT 角色。学生模板仅认可合法 JWT，使用
  `getForRespondent` 去掉选项 score、scoringRules 和 levelRules，并拒绝未开始或状态为空的问卷；已结束
  问卷保留去分值模板以支持本人结果回显。问卷页补齐缺失的 `canWrite` 计算，依据 Pinia 用户角色只显示
  admin/psychologist 写入口；角色缺失时默认隐藏。403 提示不会注销有效会话。
- 原因：计分规则影响心理等级、预警与后续 AI 输入，修改权限需要与心理专业角色绑定。控制器拒绝在领域
  service 前完成，覆盖直接调用 API；作答响应裁剪与服务端按数据库原题计分保持分离，避免更改数据库结构。
- 放弃方案：只隐藏按钮不能阻止直接 HTTP 调用；依赖统一字段注解无法处理 Map 行和嵌在选项 JSON 内的
  分值；让所有教职工修改问卷会扩大对筛查规则的控制权；全部管理端接受共享内部凭证会扩大凭证泄露影响；
  拒绝已结束问卷模板会破坏结果页回显；将学生模板也视为内部端点没有实际调用方支持，因而不放行内部凭证。
- 后果：教师失去问卷修改权限但保留设计读取和完成情况查看；学生无法读取管理端；普通教职工完成情况
  score 为 null，前端应展示空值。选项 JSON 非法时作答视图不下发选项，管理人员须修正题目；这里移除的是
  已定义的 score 属性，新增计分属性须同步裁剪和测试。学生模板没有用户/问卷分配绑定，任何已登录用户
  仍可取开放或已结束的去分值模板。当前不增加数据库迁移，不改变个人记录的本人/内部调用授权。
- 证据：[MentalController](./backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java)、
  [StudentMentalController](./backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java)、
  [QuestionServiceImpl](./backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java)、
  [Roles](./backend/common/src/main/java/com/edu/common/security/Roles.java)；回归用例见
  [MentalControllerTest](./backend/mental-service/src/test/java/com/edu/mental/controller/MentalControllerTest.java)、
  [StudentMentalControllerTest](./backend/mental-service/src/test/java/com/edu/mental/controller/StudentMentalControllerTest.java)、
  [QuestionServiceImplTest](./backend/mental-service/src/test/java/com/edu/mental/service/impl/QuestionServiceImplTest.java)。
  2026-10-05 已核对代码与端点规则；全后端 249 例测试、JaCoCo 门和前端构建/lint 通过，真实 HTTP 请求与浏览器角色验收待验证，命令与判据见 RUNBOOK。

## ADR-031：本地密钥文件不入库；已入库即轮换，不改写历史

- 状态：已采纳（轮换待部署方执行）
- 日期/修复标识：2026-09-14 / ENV-AUDIT-LEAK-20260914
- 背景：`docker/.env.audit` 在 `122ab6d`（2026-08-26，U-2 preflight 实测当天）随一次无关的 e2e 修复被提交，
  再经 #4（`51b8410`）squash 进入公开仓库的 main。文件第 22-35 行是按 `.env.example` 生成命令产出的 14 个
  强随机值（MySQL/Nacos/MinIO/Redis/JWT/MCP/Langfuse/Grafana）。`.gitignore` 只忽略 `docker/.env`，不覆盖
  改名副本；仓库内没有任何脚本、CI 或 compose 引用该文件。
- 选择：`git rm --cached` 移出版本库（本地副本保留）；`.gitignore` 用 `docker/.env.*` 忽略所有变体，仅放行
  `.env.example`；`preflight-prod.sh` 拒绝被 git 跟踪的 `ENV_FILE` 以及仓库内任何 `docker/.env.*`（模板除外），
  CI 的 preflight 回归兜底；14 个泄露值不论能否确认在用，一律按 RUNBOOK 清单轮换。不改写 git 历史。
- 原因：公开仓库的历史已推送到多个远端分支，可能已被克隆或缓存，改写历史撤不回已公开的内容，只有轮换
  能让泄露值失效。preflight 检查补上 `.gitignore` 管不到的场景：旧分支（`origin/dev` 等）的合并基点早于
  该文件，合并时会把已跟踪的文件重新带回。
- 放弃方案：`git filter-repo`/BFG + force push——撤不回已公开数据，GitHub 仍可按 SHA/PR ref 访问旧提交，
  还会打断所有协作分支，且未获授权；只补 `.gitignore`——对已跟踪文件和合并带回无效；只轮换"确认在用"
  的值——本机没有 `docker/.env`、Docker 未运行、生产主机不可见，无法证明未被使用。
- 后果：轮换 `JWT_SECRET` 使全部已签发 token 失效（全员重新登录）；MySQL/Postgres/Grafana 的密码只在数据卷
  首次初始化时读取，已有实例须先在服务内改密再改 `.env`；轮换 `LANGFUSE_SALT` 会使既有 Langfuse API key
  失效。`origin/dev`、`docs/progress-audit-20260826`、`feat/ui-glass-refactor` 的分支末端仍含该文件，合并前须先
  在分支上删除，否则 CI preflight 回归变红。Render 的 `JWT_SECRET`/`EDUCARE_MCP_TOKEN` 由 `generateValue`
  生成，Aiven 凭据独立，按设计不来自该文件；若曾在 Dashboard 手工覆盖成该文件中的值，同样须轮换。
- 证据：`git log --all -- docker/.env.audit` 只有 `122ab6d` 与 `51b8410` 两个提交；`git grep env.audit` 无引用；
  值的长度与字符集和 `openssl rand -hex 24` / `-base64 48` / `-base64 32` 一致（全程未输出值）；
  `test-preflight-prod.sh` 新增"被跟踪 env 文件"反例。轮换本身待验证。

## 新决策模板

```markdown
## ADR-NNN：标题

- 状态：提议 / 已采纳 / 已取代 / 已废弃
- 日期/修复标识：YYYY-MM-DD / FIX-NNN
- 背景：发生了什么，约束是什么。
- 选择：最终方案。
- 原因：为何适合当前项目。
- 放弃方案：至少列出评估过的替代方案和放弃原因。
- 后果：收益、代价、兼容性、迁移/回滚约束。
- 证据：代码、测试、运行记录链接；未执行则标“待验证”。
```

## 维护记录

| 日期/标识 | 变更 | 决策影响 |
|---|---|---|
| 2026-10-06 / K3S-HPA-DEPLOY-20261006 | K3s/Secret/PVC、分阶段业务与data HPA | Node/metrics、12私有服务、11镜像、默认改密/三角色鉴权20项、MySQL隔离还原20表通过；HPA1→2已验，自动回缩1已验；完整AI/TLS待模型配置 |
| 2026-10-06 / KAMATERA-DEPLOY-20261006 | 新增 ADR-033，选择 Kamatera 30 天试用；复用单机部署配置 | 取代 ADR-032 的 OCI 平台选择，保留业务/安全契约；试用到期、模型用量和 CPU 保证边界明确 |
| 2026-09-01 / DOC-BASELINE | 从当前实现与执行计划整理有效决策，并建立修复文档规则 | 新增 ADR-001~014；未改变运行时代码 |
| 2026-09-01 / RENDER-DEPLOY-20260901 | 新增 ADR-015，明确 Render 预览调用链与数据层授权边界 | 禁止把免费 Web Service MySQL/Redis 误报为可用生产数据层 |
| 2026-09-01 / AIVEN-RENDER-20260901 | 新增 ADR-016，拍板 Aiven MySQL、Render Key Value 与无 LLM 降级模式 | 取代 ADR-015 中待确认的数据层分支 |
| 2026-09-01 / AI-HEALTH-20260901 | 新增 ADR-017，校准 ai-inference 的 Render 健康检查路径 | 不增加兼容别名，以实际 FastAPI 路由作为唯一探针契约 |
| 2026-09-02 / LOGIN-VALIDATION-20260902 | 新增 ADR-018，移除登录页与服务端不一致的密码长度门槛 | 客户端只做非空校验，认证策略由 `auth-service` 统一裁决 |
| 2026-09-02 / GROQ-CLOUD-20260902 | 新增 ADR-019，选择 GroqCloud 并隔离 llama.cpp 专用字段 | Render 恢复外部 LLM；API Key 不进入仓库，RAG 降级边界保持显式 |
| 2026-09-02 / MCP-TRAILING-SLASH-20260902 | 新增 ADR-020，按服务配置 MCP 规范 endpoint | 保留 fail-fast 与双 MCP 工具链，消除 FastAPI 307 握手失败 |
| 2026-09-03 / MCP-LIFESPAN-20260903 | 新增 ADR-021，父 FastAPI 显式托管 FastMCP lifespan | 保留单进程挂载方案并初始化 Streamable HTTP SessionManager |
| 2026-09-03 / GROQ-RUNTIME-CONFIG-20260903 | 新增 ADR-022，修正服务级凭据覆盖并采用组织允许模型 | 不扩大 Groq 组织权限；保持 Java/Python 模型一致并固化 Blueprint |
| 2026-09-04 / AIVEN-DNS-20260904 | 新增 ADR-023，以 Aiven 当前连接信息修复失效 DNS | 不猜测端点、不绕过健康检查，数据库凭据继续仅存 Render secret |
| 2026-09-04 / GROQ-429-RETRY-20260904 | 新增 ADR-024，对 Groq TPM 429 使用有界退避 | 保留完整 AgentLoop 质量；401/403 继续 fail-fast |
| 2026-09-04 / GROQ-JSON-MODE-20260904 | 新增 ADR-025，Render GPT-OSS 改用 JSON Object Mode 与低推理预算 | 保留 ReAct 主路径；本地 OpenAI 兼容端点维持 TEXT 默认值 |
| 2026-09-15 / RENDER-CD-20260915 | 新增 ADR-026，main 即生产、CI 通过后按服务增量部署 | 部署以被部署提交的 CI 为准；buildFilter 与 CI push 路径须同步维护 |
| 2026-09-26 / JDK21-UTF8MB4-20260926 | 新增 ADR-028，后端统一 JDK 21，种子脚本显式 utf8mb4 | Enforcer 改为 `[21,22)`；ADR-001 的 Java 版本随之更新 |
| 2026-09-14 / INTERNAL-AUTH-20260914 | 新增 ADR-029，下游服务入口要求 JWT 或内部凭证，`AccessGuard`/字段权限不再把无 token 当内网 | 补全 ADR-008：下游公网可达时仍 fail-closed；`EDUCARE_INTERNAL_TOKEN` 成为生产硬门 |
| 2026-10-05 / MENTAL-AUTHZ-20261005 | 新增 ADR-030：心理问卷端点授权与作答视图 | 补全 ADR-029 的管理角色缺口；仅心理专业角色可改计分规则，学生模板移除计分信息 |
| 2026-09-14 / ENV-AUDIT-LEAK-20260914 | 新增 ADR-031，误入库的 `docker/.env.audit` 移出版本库并要求全量轮换（待部署方执行） | 不改写历史；`docker/.env.*` 统一忽略，preflight 拒绝被跟踪的 env 文件 |

## ADR-027：生产诊断与待实施修复（PROD-AUDIT-20260916，2026-09-16）

背景：[生产验收报告](./docs/production-tests/2026-09-16/REPORT.md)实测 Agent 列表429/SSE握手超时，日志复现 MCP 初始化429 → `mcpSyncClients`失败 → Agent退出；user首次429，唤醒后列表6.647秒→0.764秒，池初始化约2.98秒。user健康路由不存在但HTTP200；多个前端原型未接入已有后台，Langfuse缺配置。

选择（尚未落地）：先取得具体 MCP connection/platform 状态，恢复依赖并隔离依赖未就绪与业务服务启动；保留鉴权与MCP契约。补真实健康语义并评估 readiness 中的必要初始化；接入真实用户/学业API，修订误导提示；恢复trace并降低/脱敏网关请求头日志；单独核对发布配置漂移。代码证据为 [MCP启动配置](./backend/agent-service/src/main/resources/application.yml)、[UserController](./backend/user-service/src/main/java/com/edu/user/controller/UserController.java)、[学生学业控制器](./backend/student-service/src/main/java/com/edu/student/controller/StudentAcademicController.java) 和报告中的前端链接。

放弃作为当前解释/修复：仅增加前端重试或MCP超时不能处理立即返回的429；绕过鉴权、删MCP或伪造健康会破坏既有交付边界；无SQL耗时/模型调用证据，不归因于慢SQL、连接配额或Groq限流。

代价与约束：依赖恢复可能涉及套餐或部署时间；初始化前移延后readiness；启动隔离需要明确依赖错误语义；Agent定时任务不可机械套lazy初始化。所有方案待实施、修复后待验证，本轮不改变R-5/R-6状态，不宣称生产可上线。

## PERF-500MS-20260916：消除请求线程重任务与首次初始化（2026-09-16）

- 背景：生产诊断发现冷请求 Hikari 初始化约2.98s、Agent MCP握手429导致启动失败；代码审查确认 AI/PDF 同对象 @Async 调用实际同步执行，CallerRunsPolicy 会将满队列重任务交给请求线程。持久连接热态 auth/userInfo 已约400ms，原逐次 curl 测量包含新建连接成本。
- 选择：显式后台提交并拒绝过载；MCP依赖启动解耦、后台按30s重连，完整工具集才可使用；生产 eager Bean/Servlet 初始化及 DB SELECT 1 预热、连接池保留2条空闲连接/keepalive；shared Actuator 让健康探针实际存在；统计合并数据库往返、可索引时间谓词；Server-Timing 区分应用与网关；关闭生产SQL stdout/gateway DEBUG并开启响应压缩。实现见 ARCHITECTURE 的同名记录及性能报告。
- 原因：优先消除已证实的阻塞并保留响应/权限契约，不能用超时、快速错误或缓存他人敏感记录冒充性能达标。
- 放弃：削弱密码哈希/JWT/Redis/角色检查；将免费实例地址改成不能接收私网请求的内部主机名；给所有请求设置500ms强制中断；未经预算确认升级付费；把内部 LLM/RAG 结果改成异步ID而破坏 Feign/MCP 契约；未经慢查询证据在生产自动DDL加索引。
- 代价：eager 初始化增加冷启动阶段工作；空闲数据库连接占配额（7个领域服务每个最少2条、最多3条）；MCP后台重连仅在Agent进程存活时进行，未就绪任务仍明确失败；有界队列满载返回业务503。模型/PDF提交时间与后台完成时间分开度量，SSE衡量握手/首帧，不衡量长连接总时长。500ms是需要指定地区、热态和负载的验收目标，不是已证实的任意请求硬保证。
- 验证：完整 Maven 回归/安全覆盖率、新增异步提交及MCP失败恢复、数据库预热失败测试通过；Python28项、前端构建/体积门及既有61条逻辑断言通过，ESLint零错误/一个既有格式警告。Render Blueprint验证通过。生产部署与500ms结果以 PERFORMANCE.md 的实测记录为准。

PERF-500MS-20260916 补充（2026-09-16）：问卷Excel逐题 INSERT 为N次数据库往返，选择每100题参数化批量写入，在同一事务内同步题目数，并手动保留自定义SQL不执行的auto-fill。放弃本次将导入改为异步ID，因为前端目前依赖同步完成后读取题目，直接切换会破坏契约；分批约束单条SQL的参数/报文大小。实际MyBatis/H2验证JSON、中文/引号、可空值、默认删除值和整体回滚通过；205题回归确认100/100/5三次写入。无生产问卷写入验收，不声称任意文件上传/解析500ms。

PERF-500MS-20260916 启动回归修正（2026-09-16）：日志证实首轮Agent在toolCallbackResolver创建时因MCP未就绪失败。选择配置条件内动态resolver而不是返回空工具或吞掉503，避免静态空工具快照且保留失败显式语义。新增真实ToolCallingAutoConfiguration启动及刷新工具解析回归通过；默认本地resolver不变。 实现见 [DeferredMcpConfiguration](./backend/agent-service/src/main/java/com/edu/agent/config/DeferredMcpConfiguration.java)。

2026-09-16 / PERF-500MS-20260916：生产 Server-Timing 出现重复 app 指标，原因是领域服务扫描 common 的 @RestControllerAdvice，同时自动配置再次创建 advice。以 ConditionalOnMissingBean 保证唯一注册，并限制 servlet 条件；启动上下文覆盖扫描/不扫描两种注册路径。此项无架构边界变化，影响 common RequestTimingConfiguration。网关首轮现已 live（09:20:08Z），内存可见样本约233MB，不能据此把此前无报错重启归因为OOM。最终指标必须以全部服务稳定发布后复测为准；回滚恢复原提交和原始 render-performance-plan 配置。

2026-09-17 / PERF-500MS-20260916：生产静态站的外部rewrite未及时转发SSE首帧，Agent和网关直连首帧可用。Warning.vue通过VITE_SSE_BASE_URL直连网关，仍携带Bearer并经过网关会话鉴权；普通API保持原路由。选择绕过静态代理，放弃调长超时及绕过鉴权的Agent直连。render.yaml记录构建变量。验证：前端构建/体积门通过，lint零错误一个既有格式警告；网关CORS预检允许Authorization和站点Origin，直连hello测试通过。前端最终部署待验证；回滚前端提交和该构建变量恢复原路由。完整生产验收记录见 docs/production-tests/2026-09-16/；不能据此宣称所有请求满足500ms。

2026-09-17 / PERF-500MS-20260916：热登录仍超过预算；AuthServiceImpl 对成功且服务处理≥500ms的登录仅记录用户查询、密码校验、角色查询、JWT签发和Redis会话五阶段耗时，不记录身份、密码、令牌或SQL。无架构边界变化，不降低bcrypt成本、不缓存密码校验、不取消角色/会话检查；用于区分CPU校验和外部依赖成本，避免猜测原因。复测热登录并读取 Slow login phases 日志，若任一阶段仍超预算，则500ms未通过。此诊断实际生产结果待验证，回滚该提交移除计时日志。

2026-09-17 / PERF-500MS-20260916：分阶段日志证实首次登录还有Redis连接及JWT初始化成本，热密码校验为主要耗时。新增 auth AuthDependencyWarmupConfiguration，在已有预热开关开启时于readiness前PING Redis并初始化内存签名；不写会话、无业务用户/凭证日志，Redis失败不能就绪。无架构影响，auth仍依赖原Redis白名单。放弃密码缓存/降低bcrypt及依赖故障时假健康；验收需验证启动、热登录阶段和401/403。新增预热成功只读与故障阻止就绪测试；生产行为待验证。回滚此auth提交移除额外启动预热，保留强验证与原配置。

2026-09-17 / PERF-500MS-20260916 维护记录：相关代码已逐批经PR/main CI部署既有生产服务，SSE前端实际产物已核对，Auth依赖预热的阶段日志已确认生效；完整验收记录见 docs/production-tests/2026-09-16/ 的 FINAL_REPORT.md 与 JSON 证据。无新增架构边界或收费资源。500ms全接口目标未通过：热密码验证、跨区域传输、休眠唤醒仍超预算，真实写入/推理最终产物/下载尚未完整生产验收，不得将应用序列化前计时视作端到端结果。后续须明确验收网络区域、负载和可用常驻预算，不能通过降低密码成本、权限豁免或快速失败充当达标。回滚沿用前述提交及原配置plan；这条最终验收记录随上述目录一并提交，证据已脱敏，不含凭证、令牌或完整运行时元数据。

## ADR-028：后端统一 JDK 21；种子脚本显式 utf8mb4（JDK21-UTF8MB4-20260926）

- 状态：已采纳
- 日期/修复标识：2026-09-26 / JDK21-UTF8MB4-20260926
- 背景：Claude Code 云端开发环境只预装 JDK 21，父 POM Enforcer 锁 `[17,18)` 导致 validate 直接失败；
  另在全新 MySQL 卷上起栈时，`docker-entrypoint-initdb.d` 的 mysql 客户端按 latin1 连接执行种子脚本，
  中文被双重编码（`赵同学` 存为 `èµµåŒå­¦`），业务接口与 LLM 输入都拿到乱码。
- 选择：`java.version`/`maven.compiler.*` 升到 21，Enforcer 改为 `[21,22)`（仍保留 fail-fast，防止 JDK 26 +
  Lombok 静默失效复发）；全部 `docker/Dockerfile.*` 改用 `eclipse-temurin:21-{jdk,jre}-jammy`；`backend-ci`
  改 `java-version: "21"`。`sql/init/*.sql` 首行 `SET NAMES utf8mb4;`，本地 compose 的 mysql 与生产
  `Dockerfile.mysql` 一样加 `--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci`。
- 原因：21 是当前 LTS，Spring Boot 3.2.5、Lombok 1.18.30、JaCoCo 0.8.12 均支持；本地、CI、镜像、云端统一
  一个 JDK。字符集在脚本内声明，不依赖客户端/容器 locale。
- 放弃方案：① 放宽 Enforcer 为 `[17,22)`——CI 与镜像仍各用各的 JDK，失去"单一验证版本"的意义；
  ② 云端跳过 Enforcer——绕过门禁；③ 只加服务端 `--character-set-server`——客户端握手仍按 latin1，
  不能单独修复。
- 后果：开发者本地须切到 JDK 21，否则 validate 按提示失败。已有的 MySQL 数据卷不会重跑 initdb，
  已乱码的旧卷需自行重建或按 utf8mb4 重新导入；Render 上的 Aiven 库不受本变更影响。
- 证据：JDK 21 下 `mvn -B -ntp clean test` 11 模块 198 例全绿、JaCoCo 门达标、`scripts/test-preflight-prod.sh`
  通过，JDK 17 validate 按提示失败；全新 `mysql:8.0` 容器以新脚本初始化后 `student_info.name` 为正确 UTF-8
  （`E8B5B5…`）。`eclipse-temurin:21-*-jammy` 镜像可拉取；完整镜像构建与 Render 部署待验证。

## SAFE-PUSH-20261005：提交前排除本地凭证文件

- 日期：2026-10-05。
- 背景：发布本地待提交改动时，发现旧基线仍跟踪 `docker/.env.audit`；它包含非模板凭证，不适合进入新的分支快照。
- 选择：保留本地文件，取消 Git 跟踪；以 `.gitignore` 的 `docker/.env.*` 规则阻止副本再次入库，并保留模板例外。源码中的明确测试夹具和开发默认账户可保留。
- 原因：仅不暂存一个已跟踪文件，不能把它从新分支快照排除；必须提交取消跟踪。
- 放弃方案：删除本地文件会破坏本地配置；只忽略已跟踪文件不能生效；本次不改写已发布 Git 历史。
- 代价与约束：这些值仍存在于此前的历史提交，曾实际使用的凭证需由对应部署环境轮换；本次不声称已完成轮换。
- 本地开发脚本使用 JDK 21（`JAVA21=/path/to/jdk21` 可指定），与 main 的 Enforcer/CI/镜像一致；运行 PID 和日志仅写入忽略的 `.local-run/`，配置与停止边界见 MERGE-MAIN-20261005。

## MERGE-MAIN-20261005：保留现行主线契约后整合四个分支（2026-10-05）

- 背景：四个待合入分支基于不同日期的 main；内部鉴权、心理授权与凭证排除文档使用了重复 ADR 编号，
  开发脚本仍要求 JDK 17、固定中间件密码，并按进程名停止服务。这与 main 的 JDK 21 与已有性能配置不一致。
- 选择：保留 main 的 JDK 21、性能与发布链路，同时保留已提交的内部鉴权、心理权限与 env 排除修复；
  内部鉴权、心理授权、env 管理分别使用 ADR-029/030/031，原 ADR-026/027/028 保持含义。
  [common 自动配置清单](./backend/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
  同时登记原有性能配置与服务入口鉴权。
  [开发脚本](./scripts/local_dev.sh) 改为 JDK 21，并由 `docker compose config --format json` 提供容器和宿主
  共用的凭证；进程停止须确认 `.local-run/` 中记录的 PID/身份归属，无法读取身份时不终止进程。
- 原因：合并不能用旧分支覆盖已经验证的主线契约；同一开发栈必须共用配置，停止本项目不能影响其他项目。
- 放弃方案：整份选择旧分支会丢失性能与 JDK 21 变更；只保留主线会丢失安全修复；跳过 Enforcer 会降低
  版本一致性；固定密码与全局 `pkill` 无法保证配置和进程范围。
- 代价与约束：本地脚本需 Docker Compose v2、Python、npm、JDK 21；读取 Compose 配置时不将凭证写入
  仓库或输出。进程身份不可读取时需人工处理残留进程。已有数据库卷不因脚本切换自动改密。
  合并后 JDK 21 全量测试已通过，真实起栈待验证；历史分支的 JDK 17 测试只作为原分支证据。
  env 文件不恢复 Git 跟踪，历史值轮换继续按 ADR-031/B-2 执行；R-5/R-6 不因合并自动完成。
- 验证：2026-10-05 合并代码在 JDK 21 下全后端 11 模块、54 套测试、267 例通过（0 失败/错误/跳过），
  10 个代码模块 JaCoCo 门通过；前端构建、30/30 重试断言、体积门通过，lint 0 errors、1 条既有
  vite.config.js 格式警告；combined preflight 回归通过。开发脚本 Bash 5/macOS Bash 3.2 语法和临时
  stub 验证通过，覆盖非默认共享凭证与特殊字符、无凭证输出/临时配置残留、PID 归属/陈旧记录与无关进程安全。
  npm 生产依赖 audit 0 vulnerabilities；Render/Compose/backend-CI YAML 解析与 JDK 21/POM/自动配置清单联合断言通过。
  未执行真实全栈、GPU 或生产验收。

2026-10-06 / K3S-HPA-DEPLOY-20261006 实机验收补充：K3s Node/metrics与12私有服务Ready；11镜像原生构建导入、三角色业务鉴权20项通过，data HPA在受控CPU负载下自动1→2→1，保持300秒缩容稳定窗口、无手工scale。MySQL合成快照在隔离库还原比对20表通过；备份留在VM，本地导出因自动审批要求明确数据导出授权而未执行。临时域名已解析配置，完整模型/embedding配置缺失，AI/MCP/RAG与HTTPS公网发布仍待验证，R-5/R-6不变。详见k8s/README.md与Kamatera手册；回滚停frontend并保留PVC。

## 维护记录：SSH-PASSWORD-STATE-20261006（2026-10-06）

背景：将“创建页未输入/部署未保存root密码”理解为“服务器无密码”缺少运行证据，客户端只用公钥也不证明服务器禁止密码。选择只检查passwd -S root及按当前连接条件的sshd -T；结果PS、PermitRootLogin/PasswordAuthentication/PubkeyAuthentication=yes、PermitEmptyPasswords=no。保留现有密钥登录，不在用户询问密码位置时自动改凭据。放弃读取密码哈希或尝试恢复明文（不能提供原密码），也不擅自重置以消除不确定性；若用户需要密码，须明确选择后在其SSH终端执行passwd私下设置。实际密码来源仍未知，没有进行重设或SSH配置修复；操作见[部署手册](./docs/deployment/KAMATERA_DEPLOYMENT.md)。

2026-10-07 / LANGFUSE-OTLP-20261007 最终运行快照：17/17 Deployment Ready，frontend Ready；data HPA当前/目标均1、上限2，指标可用且ScalingActive=true。只读快照未施加新负载，不替代既有1→2→1扩缩容证据。

2026-10-07 / LANGFUSE-OTLP-20261007 私网最终复验：修正版 `--stage private-ai` 全部8项通过，Agent触发次数0；三库固定合成文档持久化、1024维有效向量、非fallback检索、双MCP鉴权/工具及Python生成有效。既有upsert按固定doc前缀替换同批旧合成chunk，行数每库由2变1；此前全部行已核验为相同公开合成文本，没有删未知资料或清Redis。VM私有结果 `k3s-ai-acceptance-20261007-private-ai.json` 为0600；该阶段不代表完整Agent通过。

## 维护记录：LOGIN-CREDENTIAL-UI-20261007（2026-10-07）

背景：生产登录页仍显示“默认账号：admin / admin”，且表单硬编码相同账号和密码，即使种子密码已轮换也会暴露错误的默认登录习惯。选择在[登录组件](./frontend/src/views/login/index.vue)永久移除提示/样式并把两个初始值置空；放弃仅CSS隐藏（仍留在产物）和只删提示（仍预填密码），也不保留生产/开发开关。代价是开发者须自行输入专用测试凭据；未新增账号、修改密码或禁用正常浏览器密码管理器。仅更新Kamatera前端镜像，保持既有鉴权与数据；本地构建、体积门、组件lint（零错误/警告）通过；旧源码及镜像已留回滚，新前端Ready且浏览器实际确认提示消失、两输入为空，HTTPS/login200、health200及匿名API401通过。回滚和判据见RUNBOOK。

## 维护记录：PROD-SMOKE-20261007（2026-10-07）

背景：[本轮实测报告](./docs/production-tests/2026-10-07/REPORT.md)同时检查公网、三角色 API 和浏览器，发现 API 正式注销正常但 UI 退出未撤销令牌；学生统一落地教职工 dashboard；用户分页 total=0；唯一新授权 Agent 再次遇 Groq JSON/限流失败。基础健康不构成全功能通过。

选择的修复方向（均**未落地/待验证**）：显式 UI 退出调用现有注销接口并等待结果，保留失效令牌的本地清理；按角色使用已存在的学生入口并限制路由/菜单；在 user-service 按其他模块模式注册分页插件；先隔离固定合成 JSON 请求错误，再按实际共享额度验证队列、token 预算及有界退避。对应代码见 ARCHITECTURE 同标识记录与报告。

评估并放弃：降低网关会话/角色检查不能修复 UI 调用缺失；给学生开放全校数据会破坏授权边界；把当前页长度当 total 会掩盖数据库分页问题；无因果证据就改协议/参数、无限重试或收费升级会扩大不确定性与代价。保留默认不采集 Langfuse 内容及所选模型组合。

代价与约束：正确退出增加一次服务端请求，并需处理注销失败；角色入口涉及路由/导航体验；分页修复要验证数据库 count/limit 与相邻页；模型方案需具体请求证据及新一轮明确外发授权。当前只记录诊断，不在冒烟中重建或部署修复；失败 Agent 记录保留作审计，测试会话清理。所有学生示例记录同一 owner，因此 foreign 档案用例跳过，不能弱化断言后算绿；密码键/内容/bcrypt 计数均 0，未发现该项泄漏。R-5/R-6、B-2状态不因基础通过改变。


## 维护记录：PROD-FIX-20261007（2026-10-07）

- 背景：已实测的四项故障见 [PROD-SMOKE-20261007](./docs/production-tests/2026-10-07/REPORT.md)，用户授权修复和重新部署。代码审查另发现旧请求 401 误清新前端状态，以及后端旧 JWT 注销无条件删新 Redis 会话的竞态；这两项属于同一注销边界。
- 选择：前后端按指定会话撤销，主动注销固定原 token、不自动重试；失败如实提示并保留可重试状态，401 使用本地清理避免递归。真实角色驱动学生落地、路由和递归菜单，全校数据后端权限保留。user-service 注册真实分页插件，使用实际 SQL 而非模拟 total 进行回归。代码见 [store](./frontend/src/store/modules/user.js)、[导航](./frontend/src/router/access.js)、[分页配置](./backend/user-service/src/main/java/com/edu/user/config/MyBatisPlusConfig.java)。
- 用户页补充：原页面静态记录没有消费接口，选择只读接入真实 records/total、账号和状态筛选；后端未实现的 role 过滤移除，userType 仅标记账户类型。放弃用 records.length 修正 total、假部门及扩大到 CRUD，实现与授权范围一致。
- 模型依据与实施：固定无个人资料探针证明 JSON_OBJECT/1200 与严格 ReAct JSON_SCHEMA/2400 都能 HTTP200，8000 token 限制有当时真实响应头证据；所以不将 1200、reasoning low 或 JSON_OBJECT 单独断言为既有 400 根因。保持 ReAct、采用动态严格 schema 和最终产物二次校验；可选工具 null 按默认值处理。Java/Python raw 共用 Redis DB0 冷却、60s 间隔、180s 请求预算；永久 400 不重试，429 遵守 Retry-After 至多 3 次/单次最多 120s，无叠加 SDK 重试。独立任务池 1/排队 2 与 MCP 并行池分离，loop 900s/锁 1200s、队满 FAILED/503、有限 HTTP/Feign 等待。选择这些有限等待以约束免费共享额度及线程占用，不切原生工具协议、不默认风险值伪造成功。
- 放弃方案：仅藏退出提示不能撤销会话；学生开放全校面板破坏授权；前端 records.length 冒充 total 不能分页；无限重试/默认 MEDIUM/关闭 validator 会伪造 AI 成功；付费升级或改平台不属于已授权修复。
- 代价和约束：单例组件升级有短暂中断，三批发布前保留旧镜像/配置与源文件，不动 PVC。模型队列和等待提高任务延迟，队列不持久化、不做重启恢复；Redis 仅协调本项目，无法保证同组织其他应用不会造成 429。维持 Langfuse 不采集内容，同时关闭模型原文/SQL 参数日志；不降低既有权限或内部鉴权。
- 实测：common75/auth27/user11/agent114、Python50、前端93、K3s13 项通过，三个发布批次 Ready；真实 UI 注销旧 JWT401、旧新会话竞态、新学生入口及真实 MySQL/页面 total 通过。新镜像纯合成 Groq200/JSON及冷却、Cloud新 trace空I/O通过；完整学生 Agent 未再触发，等新授权，不将合成探针算 R-5.2。详见 [修复报告及限制](./docs/production-tests/2026-10-07/FIX_REPORT.md)；上节 PROD-SMOKE 的待实施项是保留的历史快照。

## 维护记录：LOGOUT-DIALOG-CSS-20261007（2026-10-07）

背景：用户指出退出确认框在左上角且看不到正常卡片；真实生产复现为透明、全宽、overlay static，前一轮只检查退出行为，漏掉视觉问题。Navbar 显式导入服务对象，Element Plus模板解析器不会为已声明对象补CSS。选择在 [main.js](./frontend/src/main.js) 自定义样式前导入 message-box/message 的官方 `style/css`，自动包含弹窗依赖，统一覆盖所有现有服务式调用。

放弃：写一个仅修坐标的 scoped/CSS补丁不能恢复缺失的卡片/图标/关闭布局，并可能不命中 body teleport；全量 Element Plus CSS 增加不必要样式且扩大影响；改用另一弹窗会重复既有确认/撤销流程。代价是约9KB未压缩服务样式，JS结构和体积预算保持。没有新增业务写入口或变更注销逻辑。

实际：93项前端回归、构建、体积门及目标lint通过；唯一前端镜像更新后Ready，公开CSS含所需选择器，真实浏览器居中白色卡片、取消/关闭保留登录、确认回登录全部通过，临时测试会话注销。报告与回滚见 RUNBOOK 同标识；本次没有触发学生Agent或改变其授权状态。

## 维护记录：FIELD-CONTRACT-AUDIT-20261007（2026-10-07）

背景：用户指出教师政治面貌无表字段、无接口返回，要求全面查找同类问题。只查列名会漏掉已有能力未接、JSON属性形态、派生统计口径、未落实请求参数以及合法脱敏；反过来会误把正常空值当成缺列。

选择：按UI→API→controller/service/DTO/entity/SQL逐层核对，并只读生产列元数据。覆盖22视图、7前端API模块、20表220列，按独立问题组去重61项，区分42项契约缺陷/语义错配、18项未开放/来源能力缺口和1项权限UI适配。具体证据、影响、修复建议及误报边界见[完整报告](./docs/audits/FIELD_CONTRACT_AUDIT_2026_10_07.md)和JSON清单（本地测试产物，不入库：`./docs/audits/FIELD_CONTRACT_AUDIT_2026_10_07.json`）。同组件详情路由身份问题涉及7页但只计1项，方案的不同结构错配分别列项。

审计时提出的修复方向（实施状态现见下方 FIELD-CONTRACT-FIX-20261007）：先统一服务端字段权限和学生身份范围，再修MCP分析输入、问卷计分/历史、版本化ReAct/legacy方案DTO及UI/PDF，最后按业务口径交付新来源。政治面貌与排名需先决定采集/计算规则；教师账号联系方式不等于个人教师联系方式；共享账号不能直接加UNIQUE(user_id)，须确认绑定模型及存量数据。保留空值语义，禁止未知成绩当零分、总分冒充因子分、假人数或缺数据冒充未执行。

放弃方案：给所有占位批量加可空列不能修复现有API和JSON契约；前端隐藏敏感内容不能代替API/PDF裁剪；关闭脱敏填满UI破坏现有权限；直接复用管理员用户写接口不能交付普通用户资料修改；用旧冒烟截图重复报告已修复故障会混淆现状。

代价与约束：这次只交付审计文档，不增加运行依赖、收费资源或数据迁移。代码静态结论与离线虚构验证不替代角色矩阵、真实存储和浏览器验收；生产只读取列定义，不盘点真实师生重复绑定。后续修复和迁移必须分别获得对应任务范围并保留回滚，R-5/R-6及完整Agent外发授权状态保持原状。

## 维护记录：FIELD-CONTRACT-FIX-20261007（2026-10-07）

背景：原61组审计问题混合了缺来源、既有API未接、JSON结构错配、统计口径与敏感权限缺口；把所有问题当缺列会制造重复或虚构数据。
选择：已有成绩/账号联系先接真实API；政治面貌、个人联系、教学科研/评价/素质记录新增真实存储；GPA排名派生，未知保持null；
心理计分集中校验并保存提交版本快照，不用当前题库伪造历史。共有账号歧义明确409，新绑定用行锁校验，现有数据需按身份核实后处理。
放弃：默认填0、假活动、任意LIMIT1认领学生、按最新题干回显旧答卷、给共享绑定直接加UNIQUE，以及页面隐藏替代服务器脱敏。

不可变方案DTO兼容ReAct/legacy；敏感risk JSON通过allowlist响应投影，PDF三端点限专业角色。
角色目录是真实DB读取，canonical角色是授权上限，permissions进一步限制教职工业务请求；目录仅允许分配真实端点支持的权限。管理员维护权限固定，学生本人能力由身份与本人校验控制，两者不提供无效勾选。教师默认学生维护权限与实际端点一致；不允许随意角色码自动获得专业数据。
账号/角色授权修改和登录claims发行使用admin角色行短协调锁，密码校验放在锁前，目标user锁保证绑定与删除顺序；接受小范围串行开销以保证撤销可靠。
刷新cookie仅HTTP传送、JavaScript不可读且SameSite，7天采用原JWT与Redis的绝对截止，避免滑动延期；代价是刷新会旋转当前访问令牌。

身份目录迁移保留有效旧心理角色关联；已有停用canonical角色与有效legacy冲突时拒绝迁移，需人工决策而非擅自恢复权限。
目录DML与版本标记事务提交，SQL hash守卫防修改已执行迁移；新库种子与增量迁移分离，旧库不重置凭据、不生成虚构记录。
无外部SMS/SSO配置，按用户回答置灰；开通/找回使用本系统申请与人工核验，不假称已发送验证码或邮件。
完成于2026-10-07：10个应用镜像已发布，三份增量迁移通过且业务记录数保留。生产两个学生本人接口仍因既有共享账号返回409；选择保留并明确报告身份归属阻塞，待管理员核实后重绑，拒绝为让冒烟全绿而自动猜测归属。无当前开放问卷的一项模板检查保留跳过。实施、验证、限制及部署证据见[修复记录](./docs/audits/FIELD_CONTRACT_FIX_2026_10_07.md)与同名RUNBOOK。完整真实模型验收仍单独授权。

## 维护记录：LANGFUSE-INLINE-20261008（2026-10-08）

背景：用户要求在LLM追踪页直接显示数据，无须跳转。Cloud日本区响应头拒绝iframe，已有页面仅提供外链。用户于2026-10-08明确选择Public API与站内追踪面板。
选择：复用已有服务器项目密钥，增加管理员专用的有界只读观测代理与本页列表，继续使用Cloud；无新基础设施、数据库或模型请求。与Cloud完整控制台相比，站内页面提供模型/耗时/token/观测级别及分页，不复制其他后台管理功能。运维数据使用字段白名单，不展示学生标识、提示词、回复或自由错误正文；代价是维护一次接口投影与UI契约。

放弃恢复iframe：Cloud CSP实际拒绝；不移除其安全头、不共享Cloud登录cookie、不在浏览器保存项目secret。自托管整套控制台的认证与运维负担超过当前需求。保持真实数据语义：agent.loop未记录的模型/token为空，DEFAULT不解释为成功，独立span不能假造完整树；当前页摘要不冒充全时间段总量。读取失败显式提示配置/鉴权/限流/超时，不能用空记录掩盖故障。

依据：[官方Public API](https://langfuse.com/docs/api-and-data-platform/features/public-api)、[官方v2观测契约](https://github.com/langfuse/langfuse/blob/main/fern/apis/server/definition/observations.yml)及公开页面响应头实测。验证和发布结果见[交付记录](./docs/production-tests/2026-10-08/LANGFUSE_INLINE_REPORT.md)，完整学生Agent验收及R-5/R-6不随此项自动完成。

完成于2026-10-08：复用已有密钥的生产读取成功，无新增Cloud会话或基础设施。两个镜像发布后17/17工作负载Ready，21项公网检查通过；7/30天各11条、24小时0条，空窗口不作为接口失败。302项后端/126项前端及质量门通过。选择结束验证会话并撤销令牌，保留管理员正常登录入口；浏览器当前停在登录页，登录后视觉验收待验证，不能由API通过推定。

发布缺陷补充：首次frontend v1入口内容仍旧，尽管镜像运行层匹配且新资源存在；选择在构建输入上更新mtime并禁用构建层缓存，以同一已测试制品生成独立v2镜像，加入导入前/上线后的逐文件SHA门。最终镜像、实际Pod与公网全部64个文件及三个HTML入口通过。固定mtime=0、同名同长度入口触发BuildKit元数据增量复用属于有官方实现支持的推断，详见交付记录；不把清浏览器缓存或再次确认Ready当成修复。代价是本次重传构建输入、前端一次Recreate，未重新触发业务/模型或改变UI源码。

## 维护记录：DEMO-DATA-20261009（2026-10-09）

背景：用户要在已部署生产环境演示，现有测试资料包含占位姓名、共享学生账号、缺出生/入学日期及无答卷的测评；用户确认没有真实师生/业务资料，选择1,200学生/80教师/4年级/8专业。选择显式、固定种子的完整合成业务批次，按已部署列生成并独立重算业务关系，替换已确认测试业务。比例是演示假设，遵守本科年限、校历和课程逻辑；不伪称真实学校统计或临床患病率。

放弃逐表均匀随机填值：会让新生有已结课成绩、学分与绩点失配、题目/答卷/测评断链。放弃共享默认学生账号与默认密码：每档案单独账户，新增密码随机 bcrypt，明文丢弃；保留两个代表账户的凭据与权限。放弃大规模自动 init seed 与全库清空：本批次只作用于明确业务白名单，使用新 ID，保留身份安全/迁移/真实历史，代价是显式应用和短维护窗口。

不生成假完成的 AI/PDF/模型观测：当前执行产物没有独立离线来源契约，完整 Agent 的演示应来自另行授权的真实运行。心理自编筛查、处置情景和校内活动明确注明合成；未知联系方式/政治面貌不硬填。需要新增演示登录时由管理员设置独立强密码，不能公开或共用生成口令。详细分布见[脚本说明](./sql/demo/README.md)，实施/验证/备份收口见[交付报告](./docs/production-tests/2026-10-09/DEMO_DATA_REPORT.md)。

完成于2026-10-09：采用明确模拟规则并从明细核算，生产统计/画像/测评链90项全部通过，管理员与原始权限/历史保护成立。发现验收脚本误用Long数字比较时，选择适配已有序列化契约，放弃修改API或数据来迎合断言；原受保护历史仅新增6次真实测试登录与1次真实应用审计。安全应用幂等复验通过，备份实际恢复与试导入完成；未借此扩大为真实LLM外发授权或R-6签字。

## 维护记录：AI-PROD-TEST-20261011（2026-10-11）

背景：[本次生产页面测试](./docs/production-tests/2026-10-11/AI_REPORT.md)提交的三个真实分析任务均首轮 `NonTransientAiException` → loop `TOOL_ERROR` / iterations=1 → `FAILED`，未形成risk/plan/audit或RAG搜索。普通学生task/observability读取业务码403，但学生SSE实际HTTP200并收到其他学生的任务终态。另确认失败完成时间为空、任务与追踪时间展示相差8小时、页面没有可操作的失败分类。MCP七工具目录、Groq models GET200及17个工作负载Ready不能替代模型生成成功；`DEFAULT`观测级别不表示业务成功。

选择的修复顺序（**仅提出方案，尚未实施、回归或部署**）：

1. 先闭合 [SSE角色入口](./backend/agent-service/src/main/java/com/edu/agent/sse/WarningSseController.java)、[事件分发](./backend/agent-service/src/main/java/com/edu/agent/sse/WarningSubscriberConfig.java)与连接中的会话/权限撤销范围，使长连接遵循业务角色和数据范围，而非所有有效JWT都能接收全校终态。
2. 为 [任务链](./backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java)、[模型loop](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java)及 [报告视图](./frontend/src/views/agent/ReportDetail.vue)建立固定失败阶段/安全error code/实际HTTP状态与trace关联；供应商自由错误先白名单投影，保留内容采集关闭，不记录PII、提示词、模型回复、工具参数或凭据。三个Langfuse `agent.loop`已在metadata包含`status=TOOL_ERROR`/`iterations=1`，站内代理目前不取该字段；只向页面白名单映射这两个固定技术字段，不开放自由metadata，也不把`DEFAULT`当成功。
3. 在保留隐私的条件下核对首轮真实请求协议、供应商结构校验和当前配置，取得因果证据后针对修复。保留严格schema、最终产物校验、共享冷却及有界重试；先前400/429历史不能作为本轮同一根因的证据。
4. 统一COMPLETED/REJECTED/FAILED终态时间；API明确RFC3339时间偏移，前端按当前浏览器时区显示，保证任务列表、报告、PDF与Langfuse对同一时刻解释一致。

评估并放弃：放宽鉴权或依靠菜单隐藏无法防止已实证的SSE泄露；盲目更改schema、降低校验、默认风险/方案、无限重试或付费升级不能解释首轮失败；输出完整请求、供应商错误正文或临床资料虽便于排错但会扩大敏感信息流向。重放假成功任务来测试PDF/反馈会污染真实执行证据；本轮将二者记为依赖阻塞。

独立RAG公开合成探针HTTP200/0.462秒，新缓存为1024维非零有限向量，Cloudflare BGE-M3真实调用与dense三个chunk/fallback=false成立，机械通路验收（本地测试产物，不入库：`./docs/production-tests/2026-10-11/RAG_PROBE.json`）通过，但不能代替完整Agent或真实语料质量。Milvus经逻辑`count(*)`严格核验仅**3条逻辑活跃合成探针（物理9）**，case/psychology/policy各1条、success0；物理统计含旧/删除记录，不能宣称九条有效知识。[KnowledgeChunk](./ai-inference-service/app/api/rag.py)未投影存储中非空、非URL的source测试marker，后续选用真实来源标识的明确投影而不补造URL；代价是API/MCP/报告来源契约需一并回归。

1023px实际窗口还确认预警表格固定操作列与创建时间透底重叠、时间覆盖“查看报告”。选择未来修复 [Warning.vue](./frontend/src/views/agent/Warning.vue) 固定列背景/层叠并保障横滚；放弃只靠扩大窗口或隐藏时间列掩盖正常演示窗口问题。无架构影响；代价是不同宽度、横滚及固定列的浏览器视觉回归，本轮未改源码、未验证修复效果。

代价与后续约束：SSE范围与生命周期需新增服务器策略和角色/会话测试；结构化错误字段可能涉及兼容迁移、DTO及观测关联；时间契约可能影响历史无偏移数据的解释，迁移不得猜测改写原业务时刻。所有实施都需独立回归、生产发布及明确回滚，当前未执行这些动作。已有失败任务保留作审计；R-5.2/R-5.3/R-5.4/R-6/B-2保持未完成。

## 维护记录：AI-PROD-FIX-20261011（2026-10-11）

背景：用户要求依据[生产AI测试](./docs/production-tests/2026-10-11/AI_REPORT.md)分析原因并修复。七组契约/权限/能力缺口已有源码因果依据；AI-01的三个首轮模型异常仍缺准确HTTP证据，既有TOOL_ERROR命名掩盖模型阶段，不能以历史400/429替代本次根因。选择先补安全诊断并保留真实失败历史，取得新的具体外发授权后做有界协议诊断和真实任务复验；当前实现及状态见[AI_FIX_REPORT](./docs/production-tests/2026-10-11/AI_FIX_REPORT.md)。

选择：SSE与普通任务统一STAFF_VIEW/agent:read，并在每条事件/心跳前复核JWT和Redis活跃会话；Redis异常不向已有连接继续发资料。既有全校教职工读取范围沿用，不在缺少教师班级映射时伪造精细授权。前端正常EOF可退避重连，401/403停止。只保存固定错误类别/阶段、合法HTTP状态及真实根trace，不存供应商原始error/failed_generation；Langfuse和站内观测只投影受限技术metadata。放弃仅隐藏学生菜单、只校验签名、开放自由metadata或记录完整请求来排错，因这些方案不能关闭已实证泄露且扩大敏感资料流向。

时间选择：把既有Agent UTC语义明确为API `Z`，显示固定校园Asia/Shanghai（UTC+8）；跨业务混合待办仅在Agent分支转换，不给所有历史日期加8小时。终态与完成时间同条SQL写入，COALESCE及非终态条件防止重复交付改写原结局。旧空完成时间用既有updated_at补近似，不宣称恢复精确结束时间，不推断旧失败HTTP/category/trace。代价是新增四个向后兼容列及迁移前备份，客户端旧无偏移兼容仅限Agent记录；真实MySQL备份恢复、重复迁移与跨页面UTC8已通过；正常成功PDF仍待合法产物。

知识选择：现有Milvus schema不做破坏性迁移，保留source原值并区分集合、文档标识与合法URL；超过source64字节、title255字节或未切片正文4096字节明确422，不截断损坏出处。修改正文之外的持久化metadata/切片也会替换，成功upsert刷新检索revision；删除失败停止插入。公开知识采用短原创摘要与明确虚构支持情景，12条覆盖学业/资助/校园心理保密与转介/压力/睡眠/活动/适应；7官方来源与权利说明内置于[工具manifest](./scripts/public-rag-corpus.py)。来源只支持原则，不支持该演示个案真实存在或干预成功；success保持0，空召回保持无依据，不用虚构政策或疗效填满界面。WHO压力指南摘要注明CC BY-NC-SA 3.0 IGO、非WHO翻译或认可，限当前非商业演示；公开可读不等于取得全文复制许可，商业复用应重新审查。代价是现有URL长度限制、少量起步语料及需后续专业审核/检索质量集，不以数量替代R-5.3。

无架构影响的表格选择：为固定操作列补不透明背景，包含条纹、hover及高风险状态；保留横滚和时间信息。放弃要求用户扩大窗口或隐藏时间列，因无法保障实际1023px演示窗口中的按钮操作；实际1023×1104、横滚、收侧栏和1440×1000视觉回归已通过。

未采用：盲目放宽JSON schema、默认MEDIUM/空方案冒充成功、无限重试、付费升级、伪造成功报告/反馈或删除失败任务。保持严格最终产物校验、供应商冷却与有界等待。协议探针的拟发送工程MCP/schema曾被自动审批以授权范围不足拒绝，尚未外发；已单独问明确授权，新的三样本资料验收也待回复。离线修复无需等待，真实外发不能以时间经过视为授权。

当前验证：目标SSE26、前端28/build、Python58通过/1真实Langfuse SDK可选依赖跳过、语料工具4通过；实际Spring AI本机HTTP transport验证选项合并/序列化及安全分类。受影响后端clean verify381通过，生产迁移/发布及角色/API/UI/公开RAG复验通过；真实Agent/PDF反馈及完整质量baseline未完成。新增兼容列在镜像回滚时保留；语料回滚需私有向量备份和本批次明确doc_id，禁止重建collection或清全库来回退。R-5.2/R-5.3/R-5.4/R-6/B-2保持未完成。

同标识追加：选择[两阶段探针隔离](./scripts/public-rag-probe-quarantine.py)，先备份三个固定`accept_*_20261007_0000`的实际PK、字段与1024维向量，复制到VM私有耐久存储并核对digest，然后精确删除。放弃按source前缀、`like`、物理num_entities或全集合清理，因为测试记录可有历史版本，未知资料不能推断为可删除。删除前后强一致核验并轮换cache revision，Redis不可用时停止；发生部分变更可用同一备份继续核验/执行或恢复。代价是私有完整向量快照需妥善保留、应用维护期避免并发写该固定文档，auto-ID恢复不能复用原物理PK，但逻辑身份及实际向量保留。10项离线回归通过；生产备份/隔离3条及缓存更新已实际通过；恢复演练仍未执行。

同标识交付决策补记（2026-10-11）：发布选择兼容新增列先迁移、VM私有备份在隔离库恢复并重复迁移，再滚动五组件；放弃重放初始化、DROP列或删除失败任务。20表行数与历史更新时间保留，数据库恢复已演练，应用回滚及Milvus恢复仍待验证。公开12条和旧3探针隔离均实际完成，四类公开问题检索及真实缓存来源保真通过，仍不以小样本证明完整生成质量。首次backup安全拒绝NumPy数值后，规范numbers.Real为实际JSON float并新增回归，未改变精确文档范围或向量值。前端构建开关遗漏经生产复查补发v2，选择保留既有AI=true、/api、Cloud JP；今后发布需显式带构建开关并检查菜单，Pod环境不能改变已编译Vite产物。另选择保留ExportService的固定BusinessException业务409，而不吞成500；未知异常正文不返回/记录。新MVC7与四套导出34例通过，生产409/404且无导出任务写入通过。原发布时AI-01原因未知；后续用户授权的GROQ-SCHEMA-FIX-20261011已复现并修复当前严格schema400，新三样本资料外发仍待授权，不盲放宽schema/预算或伪造完整生成通过。

## 维护记录：GROQ-SCHEMA-FIX-20261011（2026-10-11）

[原因与证据](./docs/production-tests/2026-10-11/GROQ_SCHEMA_FIX_REPORT.md)：用户授权工程schema/system prompt外发后，第1次生产7工具schema请求HTTP400明确拒绝action.anyOf包装；仅展平后第2次HTTP200并通过本地JSON schema校验。选择最小改结构，保留工具对象、并行数组、null、strict及最终产物校验；放弃关闭strict、改模型、无限重试或凭猜测调预算，因为这些无法修复已确认编译错误且会削弱契约。code=null的400仅匹配固定schema拒绝前缀，放弃保存原始供应商消息/failed_generation或把所有invalid_request_error归为schema问题。代价是需要受支持的union结构回归；新3项schema回归及8项分类回归随common80/agent307共387通过。两次协议授权已用完，学生资料未发送，完整生成/审核/PDF仍待具体授权；不追填旧任务错误。发布/只读复验通过，实际运行制品摘要相等、17/17 Ready/0重启、旧FAILED3个/导出0且无新业务任务。保留v2回滚但承认其含旧阻断，回滚尚未演练。

## 维护记录：AI-FULL-ACCEPT-20261011（2026-10-11）

背景：用户已授权本轮完整学生Agent、PDF及反馈验收；最多3任务中的第1个task6/student750首轮FAILED，PROVIDER_JSON_SCHEMA / LLM_REQUEST / HTTP400，trace `e058fadc0cbc4c82a44e292251055bf9`。现有安全分类合并schema编译拒绝与生成JSON校验失败，未保存原始正文，因此不能仅凭code判断子型。实际v3 JAR及structured=true、Groq120B/low/2400/18k/cache关闭参数已核对；实际McpSchema/SyncMcpToolCallback离线生成的schema与上一轮真实200请求完全相等，排除手工fixture绕过adapter导致结构差异的猜测。本轮没有成功完整产物，PDF/反馈依赖成功生成，保持待验证。

选择修复[AgentLoop](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java)已确认的提示契约矛盾：严格模式完整四字段示例、未用字段null、final_answer直接嵌入对象；不再使用缺字段示例、允许字符串最终答案或将plan字段描述为可省略。保留[严格schema](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoopOutputSchema.java)、本地最终产物校验和显式MCP执行。这项修复改善指令一致性，但新真实证据前不宣称已确定task6的400根因。

诊断选择：[LlmResponseErrorHandler](./backend/agent-service/src/main/java/com/edu/agent/config/LlmResponseErrorHandler.java)从最多65536字节响应识别固定schema拒绝前缀或json_validate_failed，转换为[LlmCallException](./backend/agent-service/src/main/java/com/edu/agent/config/LlmCallException.java)的SCHEMA_COMPILATION / JSON_GENERATION / UNKNOWN三值reason；AgentLoop仅记录固定reason/code/HTTP/轮次，不保留原始cause或供应商自由内容。reason只扩展运行日志，不扩展数据库、对外API或Langfuse任意metadata。代价是UNKNOWN仍无法还原失败细节，需要剩余授权任务实测，并以[分类测试](./backend/agent-service/src/test/java/com/edu/agent/config/LlmFailureDiagnosticsTest.java)、[异常测试](./backend/agent-service/src/test/java/com/edu/agent/config/LlmCallExceptionTest.java)和[日志测试](./backend/agent-service/src/test/java/com/edu/agent/core/AgentLoopFailureReasonTest.java)证明无正文外泄。

评估并放弃关闭strict、改非严格JSON掩盖不合规输出、虚构COMPLETED/默认风险报告/假反馈、删除FAILED历史和无限重试；这些无法证明真实链路恢复，并破坏输出及验收边界。也不为排错记录failed_generation、系统提示、学生档案或工具观察值。本轮已用1任务、剩余2次须修复回归与上线后执行，不补额外任务，不复用已用完的两次工程协议额度；保留冷却、deadline和重试上限。回滚点为已核验的agent v3镜像及对应源码，只退应用，保留真实task6和兼容数据库列。新增修复/部署、正常报告/PDF/反馈仍待验证，R-5/R-6/B-2不据此勾选。

同标识进展（2026-10-11）：v4已发布，common80/agent336共416项回归通过；57,407字节最小包仅修改6个既有entry并新增1枚举，SHA256=`84619aec50955dd6e58566dbd28828ca9acaedf2a8948e39be8232538cb992f7`。task7/student175实际UTC07:29:33至07:29:37首轮FAILED，PROVIDER_JSON_SCHEMA / LLM_REQUEST / HTTP400、trace=`628dfb900e364f9090217c163b625d5d`，固定reason明确JSON_GENERATION，故不再将这个任务当作schema编译错误。提示矛盾修复有独立契约依据但未使完整任务通过，不能把它写成已证实根因或反填task6子型。实际v3本机HTTP fixture、成功200请求及v4配置类摘要一致，排除编译配置差异猜测，但无实际失败生产请求线上内容证据，结论保留范围。

选择下一增量为固定请求契约/失败JSON结构诊断，只保留白名单结构指标，不留供应商正文、failed_generation字段值、师生资料或提示内容；由[请求策略](./backend/agent-service/src/main/java/com/edu/agent/config/LlmRequestPolicy.java)与[错误处理](./backend/agent-service/src/main/java/com/edu/agent/config/LlmResponseErrorHandler.java)执行有界检查。选择[业务首轮提示](./backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java)明确档案工具action，减少开放起步的协议歧义，不提供虚构工具结果。选择[AgentLoop](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java)对HTTP400且reason=JSON_GENERATION在整个run最多1次纠错，消耗同一max8轮及900秒deadline；二次拒绝、SCHEMA_COMPILATION、UNKNOWN或其他错误仍失败，原429有界冷却策略不变。纠错只给固定协议指引，不重放失败JSON或伪造business observation；代价是单次额外合法模型机会且剩余轮次减少，须用跨轮、多错类型和内容隔离回归验证。

放弃在HTTP层对所有400自动重试、每轮重置纠错额度、关闭strict、扩大预算、无限纠错或写入假成功。保留本地最终JSON/风险规则/审核及实际工具事实；不新增数据库、API或外部服务。本轮授权已用2任务、剩1，v5新回归/发布与最后真实任务尚待验证，PDF/反馈不提前记成功。当前稳定增量回滚点可用v4及对应源码，保留v3更早备份与全部失败历史；两版本都不承诺完整生成恢复。R-5/R-6/B-2继续未完成。

同标识最终验收补记（2026-10-11）：v5已发布，common80/agent370共450项verify全部通过；81,778字节制品只改4个class、无新增entry，SHA256=`205e3946f05b08588fc49bfd8c1e90386b1bbdd35558e137fbb0d7b8003b914c`，详见[实际验收报告](./docs/production-tests/2026-10-11/AI_FULL_ACCEPTANCE_REPORT.md)。task8/student346在UTC07:38:50至07:39:51运行2轮FAILED、trace=`f6e6f8a52f7b47e48739656fb61f7b7d`，两次JSON_GENERATION/HTTP400；一次纠错及二次失败关闭均按计划运行，但没有使真实任务成功，代码回归不等于业务恢复。

新增固定诊断真实记录两次交接参数为JSON_SCHEMA/strict TRUE/react_schema true/tools false/stream false/2400；两次失败JSON可解析、四个ReAct顶层字段存在标记全false。选择只将这些记为实际结构证据，**不推断未记录的根类型、工具参数或嵌套final形态，不定性为供应商bug或确切根因**。没有业务工具执行、风险/方案/审核产物或RAG调用。继续保留strict、本地最终校验及真实工具事实；放弃凭可解析JSON绕过schema、将第二次拒绝转成功、继续增加纠错或构造报告测试PDF/反馈。

本轮task6/7/8三个具体资料外发额度已全部用完；正常PDF/反馈因无合法报告依赖阻塞、创建均0，不能宣称成功或另建任务补足结果。继续诊断可以离线进行，新的模型或资料外发复验须取得新的明确授权，不由旧验收授权无限延展。当前AI-PROD-FIX与R-5/R-6/B-2不完结。保留v3/v4制品、源码和真实FAILED历史；v5直接回滚v4方案仍未演练，v4自身有已知生成400，不把可回滚写成可恢复成功。健康及会话清理以独立最终核查为准。

AI-FULL-ACCEPT-20261011收尾实测（2026-10-11）：[最终验收报告](./docs/production-tests/2026-10-11/AI_FULL_ACCEPTANCE_REPORT.md)及安全证据（本地测试产物，不入库：`./docs/production-tests/2026-10-11/AI_FULL_ACCEPTANCE.json`）已汇总。实际运行Agent v5 JAR SHA256 03599cb00f989cef0e1706185be5e48d5a4ecdd1a5479ac35ef6cd901c9a9073与审核制品相等，17/17部署和17/17 Pod Ready、0重启；3新任务均FAILED、终态结束时间无缺失、导出/反馈各0。task8真实站内trace为2轮/61,299ms/模型请求失败，DEFAULT级别不表示成功。测试管理员已正常注销、旧JWT401、Redis会话撤销，临时浏览器及本机/服务器临时凭据副本已清理。完整生成、成功PDF/反馈仍依赖阻塞，次数已用完；R-5/R-6/B-2及主修复任务保持未完成，新的模型外发须新有界授权；回滚未演练。

AI-FULL-ACCEPT-20261011下一步候选（2026-10-11）：[两份A/B工程请求](./docs/production-tests/2026-10-11/AI_NEXT_DIAGNOSTIC_PLAN.md)已离线准备；唯一差异为action候选从7工具缩至档案工具，消息/最终schema/选项保持相同，虚构ID0示例经schema校验通过；v5类/资源/依赖一致，本机stub2次、Groq/MCP/业务调用0。选择单变量比较而非改提示与schema多个因素或直接关闭strict，以隔离工具候选范围的影响；代价是每份单次结果不足以证明稳定性，且新的最多2次外发需另获授权，不能沿用已消耗额度。后续最多3次完整复验也须新授权。

### GROQ-400-CAUSE-20261011（2026-10-11）

背景：合法且strict的请求仍出现json_validate_failed生成400，必须把静态合法与真实生成区分。[原因报告](./docs/production-tests/2026-10-11/GROQ_400_CAUSE_REPORT.md)与证据（本地测试产物，不入库：`./docs/production-tests/2026-10-11/GROQ_400_CAUSE_EVIDENCE.json`）采用虚构ID0、固定摘要、次数预占和无自动重试进行16次对照。A/B/K/O失败否定仅缩工具或只删raw Schema；短system的C/F、删完整工具目录的H/L成功。M由实际adapter/编译类/Spring AI捕获，与L逐值相同，M/N/P均严格200；原始请求重复O仍400。因此选择仅移除system重复工具文字目录的修复，保留原user、七工具正式Schema、2400输出预算、low和所有安全规则。

放弃关闭strict、泛化args类型、吞掉400、永久单工具和改为原生工具路径；暂不把ToolDefinition.description搬入formal Schema，避免引入第二兼容变量。代价：formal只有参数说明，通用新增工具及子代理选择准确率需另验；本项目基础提示/skills已解释固定七工具。本轮确认当前工程首轮的应用侧触发条件与可验证修复，没有确定Groq内部解码机理或证明全量提示永不失败。失败样本为空，不能把旧任务四字段缺失或工具提示/schema可选null表示差异直接称准确根因。

[最小源码](./backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java)、[提示契约](./backend/agent-service/src/test/java/com/edu/agent/core/AgentLoopPromptContractTest.java)及[真实传输契约](./backend/agent-service/src/test/java/com/edu/agent/config/StructuredRequestTransportTest.java)最终452项verify通过。未通过的K制品没有发布；实际v6只更新1个类/1源文件，17/17健康、运行制品一致及工程P严格通过。用户后续明确工程授权已解除历史审批阻止；旧完整学生/PDF/反馈三次额度用尽，本轮不扩大为业务数据外发，R-5/R-6/B-2保持未完成。回滚仍可回v5服务版本，但会恢复已知生成400，且本轮未演练。

## AI-PROD-RETEST-20261011（2026-10-11）：按真实业务链确认剩余问题

背景与证据：[复测报告](./docs/production-tests/2026-10-11/retest/REPORT.md)中task9/10/11各只触发一次，首轮及档案Observation后的第三轮均被JSON_GENERATION400拒绝；第二轮档案调用真实成功且未截断。决定继续将AI恢复和R-5/R-6视为未完成，保留FAILED历史；不将工程协议200、空检索快照、无产物PDF409或汇总DEFAULT级别当业务成功。当前不能认定具体供应商内部原因；三次完整额度已使用，不盲目增加模型请求。

已提出、尚未落地的方向：RT-01按真实首轮/Observation后消息分别定位并保留strict/产物校验/有界纠错；RT-02缺失任务用业务404，RT-03非法筛选用输入400，继续既有HTTP200+Result约定，放弃只改两端点的全HTTP协议变动；RT-04无风险/方案/审核产物显示“未生成”，放弃靠产物存在或failureStage补造完整执行历史；RT-06标签条选择有权限的角色首页，路由与后端继续拦截。选择这些最小方向是因为本轮实测问题分别在错误分类、文案语义和导航，不需要新服务或数据库字段。

RT-05属追踪关联能力改善：循环开始先生成真实trace/root span，同步拦截器显式继承、异步上报捕获不可变参数，明确父span；不能仅依据时间邻近关联独立llm.chat。放弃给根汇总复制某次token或扩大任意metadata/input/output来排错；代价是要处理并发隔离、嵌套上下文恢复、异常路径与未知消费量，须专项回归。现有汇总模型/token为null本身不是消费数据丢失证据。

本轮完成测试/记录与三份文档同步，未实施新应用修复或部署。正常PDF/反馈、任务内RAG、P4仍依赖有效报告；后续验证判据见报告§5。可选Secret值解码被自动审批拒绝后采用实际角色/注销/匿名请求，未读配置值不宣称已验证；不为此绕过审批。修复上线后仍需新的明确完整任务范围，且保留此前安全修复与B-2凭据轮换门。

## PAGE-PERF-PLAN-20261012（2026-10-12）：先优化首屏体积与异常等待

背景：[实测与候选方案](./docs/production-tests/2026-10-12/PAGE_PERFORMANCE_PLAN.md)显示静态服务处理快，而客户端网络/资源有成本；公共包全量293图标存在减量空间，旧Render规则把网络失败提示为唤醒、最多6次退避共215秒。决定先建立浏览器冷/暖缓存与登录阶段基线，再优先评估按需图标/登录chunk、网络中性提示及总等待预算；当前只是方案，没有改代码/配置/部署，具体收益与用户此前等待的归因尚待测。

放弃先升级CPU/RAM、删图表功能或重复开启已生效gzip/immutable缓存：当前没有服务器压力，匿名登录不加载charts。放弃只缩短每次timeout却留下长退避链、把任意HTTP429当Render边缘层未执行、或按时间延迟宣布冷启动；重试必须尊重可重放语义及会话隔离。代价是新预算需按真实请求延迟和长操作分别验证，组件拆包需平衡体积与新增请求。静态CDN列后续候选，域名要可控制，nip.io不能当自己的zone接管；API/SSE不能共享缓存，也不保证任何网络的CDN新路线更快。实施/回归/发布全部待执行，不代替AI与上线验收。

## PAGE-PERF-FIX-20261013（2026-10-13）：实施首屏减量与有界等待

背景：[旧性能方案](./docs/production-tests/2026-10-12/PAGE_PERFORMANCE_PLAN.md)确认当前常驻、压缩/缓存已开启，全量图标与Render长等待可优化。选择22真实图标和login-core聚合，保留9资源/两阶段预加载；完全自动拆分略小但增加资源数，后台组件过度聚合则把体积重新带回登录。放弃升级CPU/RAM、删charts或重复开启gzip；实际传输正文减少47.9%，不把体积变化称FCP/LCP降幅。

选择standard默认15秒单次/30秒总预算，仅只读两次短重试；写入即使429也不重放，下载/显式timeout独立处理；Render长读/预热必须显式选择。放弃默认把网络失败说成冷启动、只缩timeout却保留长退避，以及不绑定会话的401处理。代价是必须覆盖真实Axios和浏览器原生边界，不能只用箭头fake-clock。

实施中引入的两项回归已处理：eager store参数在真实三模块环触发TDZ，改lazy getter；实例属性调用Window timer导致成功响应清理失败，改自由调用包装。v1/v2均实际回滚，v3先通过真实浏览器对生产API的登录/列表/注销再发布；两类阴性控制进入回归。发布CAS拒绝或结果未知不补写/回滚，仅确认本助手切换后才对后续失败回退。保留旧hashed文件，避免旧tab懒加载404。

[最终报告](./docs/production-tests/2026-10-13/PAGE_PERFORMANCE_FIX_REPORT.md)记录42请求/27会话及完整前端检查通过、17Ready与真实角色UI通过。只独立发布frontend，保留旧镜像/PVC和大量既有改动；CDN需要可控域名，本轮不创建平台/收费资源。源码在工作区和服务器归档，未来重建先同步已审阅版本；未推送及未远端跑CI。完整AI400/R-5/R-6/B-2保持未完成。

## JAVA-AI-MIGRATION-20261015（2026-10-15）：统一 Java AI 运行时，保留既有向量布局

背景：默认 AgentLoop 已用 Spring AI，但审核、legacy 方案、RAG 与知识 MCP 仍依赖 Python，运行时和模型策略分散。用户明确要求所有运行功能 Java 化，允许在 Spring AI 不满足时使用 Spring AI Alibaba。

选择：继续使用已锁定的 Spring AI 1.1.6，ChatClient 承担生成/审核，OpenAiEmbeddingModel 承担向量调用，Spring AI MCP WebMVC 承担知识工具。上述能力均可直接实现，本轮没有引入 Alibaba 或连带升级 Boot。Milvus 现有 auto-ID/字段布局不同于默认 VectorStore，采用有界 REST v2 兼容适配；放弃立即重建集合或双写迁移，避免改变存量物理数据和引入不可逆回滚成本。保留既有服务 DNS/端口与 HTTP JSON 外形；生成兼容入口转发到 agent-service，统一限流/trace，而业务主路径直接调用 Java 服务。

安全取舍：不自动回退 Python、不通过失效审核；审核只发 studentId/grade/major 元信息与方案，要求四维完整且总判定一致。知识读取与生成兼容接口改为内部凭证强制校验，导入和 MCP 分别使用 admin/MCP 凭证；8095 不暴露写 API。导入先算完向量、集合级写锁、先插后删精确旧 PK；先失效旧 hash/revision，避免失败后错误跳过修复。Milvus 无跨操作事务，部分插入/删除失败可能留重复片段，应重新导入同一完整文档；这优于先删旧文档后的数据丢失，但不能宣称事务原子性。

代价：保留一个兼容存储适配层、两个 Java 进程；需要实测 JVM 内存和 Milvus 2.4 REST 行为。Python 源目录暂留作迁移对照，不再打进默认运行包；运维/eval 脚本仍使用 Python。顺带修正运行包遗漏已有 `sql/init/10_agent_diagnostics.sql`，不新建/执行数据库迁移。旧镜像和配置须成套保存以回退；本轮不自动部署、调用真实模型或改存量向量。验证与限制见 [迁移记录](./docs/educare/JAVA_AI_MIGRATION_20261015.md) 和 RUNBOOK 同标题。

### LOCAL-JAVA-AI-TEST-20261015（2026-10-15）：Milvus 2.4.1 读一致性

真实实例证明仅在 REST 请求中添加 `consistencyLevel: Strong` 无效：首次导入成功后立即检索为空；官方 v2.4.1 `handler_v2.go` 的 query/search 硬编码 BoundedTimestamp。因此采用官方 Java SDK 强一致性读取，保留现有集合与 REST 管理/写入接口。未选择固定 sleep（不能保证可见性）、关闭空缓存（不能避免替换漏删），也不为此升级生产 Milvus。SDK 2.4.1 默认的 rpcDeadlineMs 在客户端构造时设置绝对截止时间，改用拦截器逐 RPC 设置 30 秒，避免长运行后请求全部超时；排除未使用的 Hadoop/Parquet/云存储批量导入依赖。代价是增加 gRPC 依赖及协议适配测试；本地通过也不等于生产切流完成。

### BUSINESS-INTEGRATION-20261015（2026-10-15）：整合工作区业务改动

用户明确选择“当前全部业务改动”，因此整合此前尚未提交的账号权限、师生画像、心理测评、前端性能/可观测、部署/SQL工具与本次Java AI迁移，而非仅摘取迁移文件造成依赖缺失。采用独立分支整合远端 main，保留已合入的 AgentRunner 抽象和缓存修复；现有其他迁移PR不自动合并或关闭。排除密钥、临时文件、截图和原始测试结果，保留实现、回归测试与叙述文档。合并须检查本地回归与远端CI，不绕过仓库分支保护；代码合并不等于生产退役或完整模型验收。

## RAG-CACHE-RERANK-20261013：RAG 管线缓存键纳入 rerank 开关（2026-10-13）

- 背景：`retrieve_from_collection` 的缓存键只含集合、query、top_k，不含是否 rerank。切换 `RERANKER_ENABLED` 或按请求传 `enable_rerank` 后，在 `RAG_CACHE_TTL` 内会返回另一种排序的旧结果。
- 选择：在计算缓存键前确定 `do_rerank`（显式参数优先，否则取配置），并写入 [`_pipeline_cache_key`](./ai-inference-service/app/services/rag_pipeline.py)；同时在 requirements 中固定 `setuptools<82`（pymilvus 2.4 运行时依赖 `pkg_resources`）。
- 补充（评审）：请求了 rerank 但精排失败或全零分、回退为 Milvus 排序时，结果不写入缓存，下次请求重试精排；`setuptools<82` 为临时规避，升级到不再 import `pkg_resources` 的 pymilvus 后删除。
- 放弃方案：切换配置时清空 Redis（运维易漏，且无法覆盖按请求开关）；缩短 TTL（只缩小窗口）。
- 代价与约束：上线后旧格式的键不再命中，首轮请求全部回源一次；旧键按 TTL 自然过期。
- 验证：[单测](./ai-inference-service/tests/test_rag_pipeline_cache_key.py) 在修复前 3 例失败、修复后通过；全量 33 例在 Python 3.11 与 3.10（Dockerfile 版本）下通过（外部服务均 mock）。真实 Milvus/Redis/reranker 联调待验证。

## AGENT-RUNNER-20261013：AgentTaskServiceImpl 与 AgentLoop 之间引入 AgentRunner 接口（2026-10-13）

- 背景：后续要做 AI 辅助助手，未来可能换 Graph 编排或多 Agent 结构；业务层直接依赖 `AgentLoop` 具体类，替换时要改上层。
- 选择：新增 [`AgentRunner`](./backend/agent-service/src/main/java/com/edu/agent/core/AgentRunner.java)（`run(req)` / `run(req, validator)`），`AgentLoop` 作为首个实现，`AgentTaskServiceImpl` 只注入接口。纯重构，行为不变。
- 命名：不用 `AgentOrchestrator`，避免与 orchestrator-workers 模式混淆，该名留给未来的多 Agent 实现。
- 放弃方案：直接迁 Spring AI Alibaba Graph（当前无流程类需求，收益不足）。
- 约束：容器内只能有一个 `AgentRunner` bean；新增实现时需用 `@Primary`/`@Qualifier` 或配置开关选择。`SubAgentToolCallback` / `SubAgentRegistry` 也改为依赖 `AgentRunner`；`run(req)` 只保留接口默认方法。
- 验证：`mvn -q -pl agent-service -am test`（JDK 21）通过，agent-service 96 例 0 失败。

2026-10-15 / BUSINESS-INTEGRATION-20261015 补充：整合门禁发现Vue/server-renderer和source-map-js高危公告，选择兼容范围内修复锁文件并完整复跑前端，而不强制跨主版本升级开发工具。前端生产依赖审计已归零；以已通过Maven验证的JAR加官方JRE完成真实容器回归，明确区分运行镜像通过与生产多阶段源码镜像尚未构建完成。

2026-10-15 / BUSINESS-INTEGRATION-20261015 CI修复：PR30首轮CI暴露AsyncSubmissionTest的隐式MyBatis缓存前置条件，单独执行在本机同样失败。选择在该测试BeforeEach建立元数据，不放宽QUEUE_FULL/FAILED/异步不执行等业务断言，不靠重跑碰运气或跳过测试。
