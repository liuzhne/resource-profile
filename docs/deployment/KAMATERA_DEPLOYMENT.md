# Kamatera 单节点 K3s 部署

> 日期 / 修复标识：2026-10-06 / KAMATERA-DEPLOY-20261006
> 最近更新：2026-10-07（LANGFUSE-OTLP-20261007：Cloud认证、v4迁移及默认内容关闭）
> 状态：账号已注册，US$10 押金已到账；`resource-profile-sg` 实例已创建并开启，SSH 主机指纹已通过可信控制台核验。Rocky 9.8 / x86_64、资源容量、SELinux Enforcing、Docker 29.8.2 / Compose 5.6.0、引擎 SELinux、hello-world 和 firewalld active 已实查。正式路径改为单节点 K3s，首轮仅 data-service 在 1–2 副本间 HPA，其他服务固定 1；K3s v1.36.5+k3s1、Node Ready / metrics-server、11应用镜像构建导入、16私有服务和前端共17工作负载全部Ready已核验，三角色登录/鉴权/退出撤销20项通过；HPA受控CPU负载1→2通过、自动回缩1已验，完整AI验收尚未完成；2026-10-07网站已发布，受信HTTPS及外部鉴权/端口边界通过。

## 1. 已选配置和费用边界

已创建实例 `resource-profile-sg` 位于 Singapore，配置为 Type A、4 Availability Cores、24 GB RAM（24,576 MB）、100 GB SSD、**Rocky Linux 9 64-bit / amd64**；根据账号所有者的企业运维偏好选择 Rocky，关闭 Managed Hosting 和 Daily Backup。2026-10-06 实际创建向导月付显示 **US$92/月，含 1,000 GB 流量**；时付另显示 US$0.126/小时 **加 US$0.01/GB 流量**，不能把月付包含的流量套到时付。账号所有者已手动完成创建，本次使用月付的一台试用 VM。此前公开[价格计算器](https://www.kamatera.com/Pricing/)的 Ubuntu 报价已由 Rocky 向导实核替代；最终账单、试用用量及税费仍需核对，不能把配置报价视为已扣款或长期免费承诺。

[试用条款](https://www.kamatera.com/faq/answer/what-are-the-terms-of-the-free-trial/)提供 30 天、1 台服务器、配置价值不超过 US$100、总计 1 TB 入站与出站流量。到期未终止会开始收费；超额或额外服务也可能收费。本次仅使用这台 US$92 配置，不创建第二台服务器，不添加托管、备份、负载均衡、额外磁盘或付费许可证。若创建页报价超 US$100、试用未激活或出现额外付款，应先核实实际订单，不能照旧报价继续创建。

[付款 FAQ](https://www.kamatera.com/faq/answer/which-payment-methods-do-you-accept/)明确包括有持卡人姓名的预付卡；具体卡仍须通过平台验证。密码、条款确认、卡片号码/CVV、银行验证及可能的身份审核由账号所有者完成。登记国家和账单地址使用真实信息，不能用电脑时区推定身份。充值、可退押金和试用后续费不是本地部署脚本的行为；脚本不会操作 Kamatera 付款或资源 API。

2026-10-06 账号所有者已完成 **US$10 押金**。创建前主任务在控制台核验 Balance 为 US$10、Prepayment 警告消失、Server quota 为 0 of 2、Max price US$100，30 天 trial 优惠仍显示；这是创建前页面记录，不代表当前使用量。随后 `resource-profile-sg` 已创建并开启，专用 root SSH 公钥登录和主机指纹核验通过。quota 允许两台不等于试用包含两台，本次仍只使用 §1 的一台。押金余额和优惠显示不等于自动停止计费或应用已经部署；域名、聊天及 embedding API 配置仍待完成。

新栈先使用全新演示数据库。源码包不包含旧数据库、访谈、知识语料、导出文件或历史凭据；迁移这些数据需要另外核实用途、访问权限和恢复流程。外部聊天和 embedding API 的用量属于相应模型供应商，不包含在 Kamatera 试用中。只有真实模型、双 MCP、dense RAG、trace 与业务验收通过后，才能完成执行计划中的 R-5/R-6。

## 2. 正式 K3s 路径及私网调用

| 文件 | 用途 |
|---|---|
| [`k8s/README.md`](../../k8s/README.md) | 正式单节点 K3s 安装、SELinux、镜像导入与 HPA 验收流程 |
| [`k8s/workloads.json`](../../k8s/workloads.json) | 无真实秘密的 17 服务定义、资源 requests、镜像 tag 与持久卷规格 |
| [`scripts/k3s-deploy.py`](../../scripts/k3s-deploy.py) | 正式 K3s 私网应用、账号改密、真实模型 probe、发布及撤下公网入口；生成清单不输出秘密 |
| [`docker/docker-compose.cloud.yml`](../../docker/docker-compose.cloud.yml) | Docker 镜像构建和 Compose 备选部署输入；本次不使用 Compose 启动应用 |
| [`docker/Dockerfile.frontend.cloud`](../../docker/Dockerfile.frontend.cloud) | 启用 AI 页面，API/SSE `/api` 同源 |
| [`docker/caddy/Caddyfile.cloud`](../../docker/caddy/Caddyfile.cloud) | 自动 TLS、SPA、SSE 和内部路径阻断 |
| [`docker/cloud.env.example`](../../docker/cloud.env.example) | 无真实秘密的共享配置模板 |
| [`scripts/cloud_deploy.py`](../../scripts/cloud_deploy.py) | 共享配置检查、安全源码包、字体验证，以及 Compose 备选部署实现 |
| [`scripts/kamatera-deploy.py`](../../scripts/kamatera-deploy.py) | 生成私有配置和安全源码包；Compose 备选入口 |
| [`scripts/requirements-cloud.txt`](../../scripts/requirements-cloud.txt) | 隔离安装部署工具的 bcrypt 依赖 |
| [`scripts/test-cloud-deploy.py`](../../scripts/test-cloud-deploy.py) | 部署和源码包安全回归 |

正式调用链为浏览器 → Caddy :443 → Kubernetes `gateway` Service :8080 → 领域 Service。9 个 Java 服务、2 个 Python 服务和 MySQL/Redis/Milvus/etcd/MinIO 通过 `educare` namespace 的 ClusterIP Service / DNS 私网互调，Nacos 关闭；请求不会绕行 Render 公网 URL。Docker 只负责构建镜像，构建完成后按 K3s README 导入独立的 K3s containerd 镜像库；Docker 中已有镜像不等于 K3s 已可用。只有外部聊天、embedding 和可选 tracing 走外网；reranker 默认关闭。JWT/Redis 会话、内部 token、双 MCP token、角色与字段权限继续开启。

只有 `frontend` 通过 hostPort 发布 TCP 80/443，其他服务保持 ClusterIP；K3s 安装须禁用默认 Traefik/ServiceLB，避免占用 Caddy 的端口，不购买云负载均衡。首次应用仅启动 16 个私有服务，前端由 K3s 发布门单独创建。SQL、导出及其他数据使用单节点持久卷；更新采用 Recreate，不能与备选 Compose 同时启动两套数据库或公网入口。

首轮只给 **data-service 设置 CPU HPA 1–2 副本**：CPU 利用率目标 70%，扩容稳定窗口 30 秒、每分钟最多增加 1 副本，缩容稳定窗口 300 秒。data 的 CPU request 为 100m；其余服务固定 1 副本。当前 17 服务基础 requests 合计 1,800m / 9,408 Mi，第 2 个 data 副本后为 1,900m / 9,792 Mi；这些是调度配置，不是实测使用量。内存 limits 合计 **17.25 GiB → 17.75 GiB**，还需预留 K3s/系统开销并实际压测。HPA 只能使用本 VM 的 4 核/24 GB，不增加物理容量，也不提供跨主机高可用；不为此创建额外 VM。

agent 的任务队列和 PDF、双 MCP 会话及 Python 管理灌库仍有复制约束，本轮固定 1 副本；SSE 已有 Redis 分发，但连接排空/重连仍须验证。MySQL、Redis、etcd、MinIO 和 standalone Milvus 均固定 1 副本，不能通过修改副本数冒充数据复制集。R-5/R-6 仍以真实业务、模型与运维验收为准。

Compose 仅作为 §9 备选。OCI 兼容入口保留为 `docker-compose.oci.yml` 的 `include` 和 `scripts/oci-deploy.py`，复用 Compose 配置；旧 `OCI_SITE_ADDRESS` / `OCI_PUBLIC_IP` 映射仍保留。K3s PVC 与 Compose 卷相互独立，不会自动迁移；不要把既有 OCI、Render 或 Compose 数据卷当作新 K3s 数据卷。

## 3. 本地私有配置和安全源码包

在工作站仓库根目录执行；重复执行不会覆盖已有秘密或源码包：

```bash
python3 scripts/kamatera-deploy.py init-env
python3 scripts/kamatera-deploy.py package-source --output .local-run/kamatera/source-k3s-reviewed.tar.gz
```

生成的 env、seed 和源码包均为 0600，且被 Git 忽略。9 个内部秘密互不复用，三个初始化账号使用随机密码。env 模板中的域名、IP 和供应商字段仍需真实配置，`check` 会拒绝占位值；生成随机内部密码不等于 API、DNS 或注册已经就绪。不得读取/复用历史 `.env.audit`。本地 env/seed 已存在，不重复 `init-env`；K3s资产已纳入EXACT/REQUIRED安全清单，新包独立生成，不覆盖旧归档。

此前已审阅的 **`.local-run/kamatera/source-rocky-reviewed.tar.gz`** 含 348 个运行文件、298,604 字节，SHA-256 `9c1bab75cfba2b5f4a07e9de5b991f24790bb4198e01196fec473553f4dfd285`；它可作为已验证的业务镜像构建输入，但**不包含新 K3s 脚本和 workload 定义，不是本次完整部署包**。当时新脚本独立解压编译和 14 项工具测试通过；新完整包 `.local-run/kamatera/source-k3s-reviewed.tar.gz` 含350文件、307,631字节、SHA-256 `90cdfcd9675ed4d5ba4fd8883ba197c660977020e59dade53c735bebe1dc641c`；仅增加K3s脚本/清单并修改共享打包白名单，347个旧文件一致，已上传并实机核对解压。上传前必须确认包内含 `scripts/k3s-deploy.py` 与 `k8s/workloads.json`，并校验新的 SHA-256；不能沿用旧摘要证明新包。私有配置仅核对权限和 Git 忽略状态，本文不记录任何秘密或真实供应商值。

`package-source` 不依赖域名或模型 key，可在注册等待期间完成。它使用 Git 已跟踪及非忽略的未跟踪文件清单，再限定到运行路径白名单；排除 `.git`、全部 docs、访谈、原始知识库、业务备份、上传/导出、所有真实 env、SSH 密钥、seed 文件、构建产物和私有证书。拒绝跟随符号链接，拒绝覆盖已有归档，缺少关键 prompt / Spring 安全自动配置资源时拒绝打包。仅 Git 清单不会自动证明安全，因此保留运行路径白名单和归档解压构建核验。

归档保留全部 Java 生产源码（含 `com/edu/data` 包）、POM、运行资源、Python/Java prompt、Java skills、Spring `AutoConfiguration.imports`、五个初始化 SQL 和前端构建源。中文字体独立传输，不进入源码包；必须按 §5 安装真实静态 TTF，启动门会拒绝缺失字体。新改动需要新包时使用新文件名：

```bash
python3 scripts/kamatera-deploy.py package-source --output .local-run/kamatera/source-reviewed-2.tar.gz
```

使用私有编辑器填写 env，不向聊天、日志、截图、CI 或 `docker compose config` 的完整输出暴露其内容：

- `CLOUD_PROJECT_NAME=edu-portrait-kamatera` 保持稳定；更改它会改变容器/卷名称。
- `CLOUD_SITE_ADDRESS` 为 HTTPS 主机名，`CLOUD_PUBLIC_IP` 为新 VM 公网 IPv4。
- `CLOUD_EXPORT_FONT_FILE=../.local-run/kamatera/fonts/NotoSansSC-Regular.ttf` 指向独立字体；相对路径从 `docker/` 解析。较早生成的 env 尚无此项时须补充。
- Java `SPRING_AI_OPENAI_BASE_URL` 为 HTTPS API 根；Python `LLM_BASE_URL` 是该根加 `/v1`。
- `EMBEDDING_BASE_URL` 带 `/v1`；`EMBEDDING_MODEL` 和 `EMBEDDING_DIM` 需符合真实模型输出。`EMBEDDING_API_KEY` 可独立设置，留空才复用聊天 key。
- 一般供应商保持 `SPRING_AI_OPENAI_CHAT_OPTIONS_RESPONSE_FORMAT_TYPE=TEXT` 和空 `SPRING_AI_OPENAI_CHAT_OPTIONS_REASONING_EFFORT`。**仅沿用既有 Groq `openai/gpt-oss-120b` 时**设置 `JSON_OBJECT`、`low`、`LLM_MAX_TOKENS=1200`，与当前 Render 配置一致；不对其他供应商强制这些选项。

### 3.1 免费模型参数（GROQ-CLOUDFLARE-DEPLOY-20261006）

2026-10-06 选择沿用现有 Groq 生成接口，Cloudflare BGE-M3 补齐 dense RAG。以下仅为公开参数；实际凭据只填私有 env：

```dotenv
SPRING_AI_OPENAI_BASE_URL=https://api.groq.com/openai
LLM_BASE_URL=https://api.groq.com/openai/v1
LLM_MODEL=openai/gpt-oss-120b
LLM_TEMPERATURE=0.3
LLM_MAX_TOKENS=1200
LLM_TIMEOUT=120
LLM_CACHE_PROMPT_ENABLED=false
SPRING_AI_OPENAI_CHAT_OPTIONS_RESPONSE_FORMAT_TYPE=JSON_OBJECT
SPRING_AI_OPENAI_CHAT_OPTIONS_REASONING_EFFORT=low
EMBEDDING_BASE_URL=https://api.cloudflare.com/client/v4/accounts/change-me-account-id/ai/v1
EMBEDDING_MODEL=@cf/baai/bge-m3
EMBEDDING_DIM=1024
RERANKER_ENABLED=false
```

私下把 `LLM_API_KEY` 替换为现有 Groq key、把 URL 中 `change-me-account-id` 替换为 Cloudflare Account ID，并将 Workers AI token 写入 `EMBEDDING_API_KEY`。按 [Cloudflare REST 指引](https://developers.cloudflare.com/workers-ai/get-started/rest-api/) 使用 Workers AI 权限的专用 token；[OpenAI 兼容接口](https://developers.cloudflare.com/workers-ai/configuration/open-ai-compatibility/) 允许直接调用，无需创建 Worker 或绑定域名。不能留空 embedding key 后复用 Groq key，也不能把 Account ID 占位值当作真实端点发布。

`EMBEDDING_API_KEY` 只填原始 token，不加 `Bearer` 或 curl 的 `-H`。[EmbeddingClient](../../ai-inference-service/app/services/embedding_client.py) 和部署探针自动发送 `Authorization: Bearer <token>`；2026-10-07 实际 Cloudflare HTTP 200 验证通过，认证头不是剩余配置缺项。

参数落实状态（2026-10-06）：用户已确认组合，非密钥参数已写入本地和 VM 的私有 env，均保持 0600、原 API Key/内部凭据不变；Account ID 仍待提供。静态配置与 K3s 三个 AI 工作负载映射已用合成凭据核对，未重启服务或请求真实模型。JSON_OBJECT/low 仅配置 Java 主路径，Python 客户端仍依靠已有提示词/解析；Python 清单固定 temperature 0.3、timeout 120 秒，以上 env 显式记录当前值，其他值需要同时调整编排清单后才能生效。

2026-10-07 / GROQ-CLOUDFLARE-DEPLOY-20261006：真实 Account ID 已按用户提供值写入本地与 VM 的 `EMBEDDING_BASE_URL`，不在公开模板记录账号标识。非密钥参数已齐；本地实际 K3s `check` 通过，本地模型 key 已设置、VM key 仍为占位值，API Key/其余字段保持原值。尚未进行真实模型请求、重启服务或公网发布。

2026-10-07 / GROQ-PROBE-UA-20261007 后续核对：本地两个 key 已实际认证，Groq JSON_OBJECT/low 成功、Cloudflare 返回有限非零1024维向量；VM两个key仍为占位值。默认urllib标识被Groq边缘错误1010/403拦截，部署脚本已增加明确User-Agent，本地真实探针与13项离线回归通过；公开脚本同步VM并留旧版本，未同步模型凭据或改动运行中的服务。后续需同步已有key并执行完整私有栈/RAG/HTTPS验收；Langfuse三项仍缺，仅完整trace/R-5/R-6需要，基础启动不强制。详细验证及回滚见 [RUNBOOK](../../RUNBOOK.md)。

[Groq 免费额度](https://console.groq.com/docs/rate-limits)当前为 GPT-OSS-120B 30 次/分钟、1000 次/日、8000 tokens/分钟、200000 tokens/日，组织共享、实际以账户 Limits 为准；一次多轮画像会消耗多次请求。[Cloudflare](https://developers.cloudflare.com/workers-ai/platform/pricing/)每天全账户共享 10000 neurons，Free 超限失败，00:00 UTC 重置。[BGE-M3](https://huggingface.co/BAAI/bge-m3)为多语言 1024 维，旧模型向量即使同维也须重新嵌入。真实提供商 probe、中文知识检索、AgentLoop、trace 与公网 TLS 仍待凭据完成，不把参数准备视为 R-5/R-6 通过。

§9 的 Compose 备选可显式沿用之前新生成的 OCI 私有配置，但必须同时指定对应 seed 文件，并决定是否保留/更改项目名；这不会迁移 K3s PVC。工具不会自动读取其他秘密路径：

```bash
python3 scripts/kamatera-deploy.py check --env-file docker/.env.oci
# 后续 bootstrap-seeds 须显式带 --seed-file .local-run/oci/seed-credentials.json
```

### 3.2 Langfuse Cloud配置（LANGFUSE-CLOUD-SETUP-20261007）

建议在 [Langfuse日本区域](https://jp.cloud.langfuse.com) 注册/登录，选Cloud Hobby，创建专用项目 `resource-profile-kamatera`。当前 [免费计划](https://langfuse.com/pricing) 无需信用卡，每月50000 units、30天历史数据访问、2位用户；units不等于tokens或完整画像任务数。在项目设置的API Keys生成Public Key与Secret Key，私下填写本地 `docker/.env.kamatera`：

```dotenv
LANGFUSE_HOST=https://jp.cloud.langfuse.com
LANGFUSE_PUBLIC_KEY=你的完整PublicKey
LANGFUSE_SECRET_KEY=你的完整SecretKey
LANGFUSE_CAPTURE_CONTENT=false
```

两项key必须是同一Langfuse项目生成的完整值，不复用Groq/Cloudflare key，不加Bearer/Basic前缀；Secret Key不发送到聊天。HOST填站点根URL，不追加 `/api/public/ingestion`。实际选EU/US区域则HOST分别为 `https://cloud.langfuse.com` / `https://us.cloud.langfuse.com`，必须与项目区域一致。当前代码和编排读取 `LANGFUSE_HOST`，Python v4 初始化将它显式传给 `base_url`；无需额外填写官方示例的 `LANGFUSE_BASE_URL`。

2026-10-07 / LANGFUSE-OTLP-20261007：用户已填写凭据，JP认证200；Java OTLP HTTP/JSON v4、Python SDK4.13.1及默认内容关闭已部署，Java20/Python41真实SDK/部署27项通过。原VM配置安全同步、内部九项凭据不变、0600回滚保留，两镜像重建/导入及16私有服务Ready、Milvus初始化通过；三库合成RAG、双MCP和Python真实生成通过。公开SQL5表全值/default/ID/行数及两空表、Milvus四库全部合成内容已核验。用户明确授权一次student1示例资料向Groq传输后执行唯一Agent，约20秒FAILED，供应商JSON校验/限流错误待修复；Cloud实际 `llm.chat`、`agent.loop`、`llm.audit` model/usage/timing及I/O关闭通过，R-5.1完成，完整生成和R-5其余项/R-6未完成。网站已通过HTTPS/鉴权/端口边界验收；新源码包351项、310212字节、SHA-256 `f9c8192cfc53de7c13f1d196a351325559688b11366f6c6539692412be27fe5d`，不含秘密或数据库。重复RAG验收需分开验证持久化与查询缓存，不能要求新随机ID必进旧缓存top3。细节与回滚见RUNBOOK同名记录。

迁移及部署后用合成数据触发LLM/Agent任务，在该项目实际查到记录、tokens与耗时再验收；启用日志不能替代云端记录。当前Java LLM与AgentLoop汇总是并列trace，Python只覆盖已装饰的raw生成路径；没有完整RAG/MCP/embedding trace树。默认 `LANGFUSE_CAPTURE_CONTENT=false` 禁止自动/手工I/O采集与自由文本工具元数据，仅记录模型/tokens/耗时和受限技术字段；显式true仅用于合成材料验收。旧Compose自建v2 profile与迁移后客户端不兼容，本次不启动该profile。详细验证与回滚见 [RUNBOOK](../../RUNBOOK.md)，R-5/R-6不变。

## 4. 创建 VM、SSH、构建环境和 K3s 前提


账号可用后，在创建确认页再次核对 §1 的唯一服务器和报价。设置该部署专用 SSH 公钥 `.local-run/kamatera/ssh/id_ed25519.pub`；私钥 `.local-run/kamatera/ssh/id_ed25519` 仅在本机使用，不上传、不打包、不提交。云侧若提供防火墙/ACL，管理 TCP 22 限定到当前可信来源，TCP 80/443 供公网访问；UDP 443 可不开。不要为此另购云防火墙服务。Rocky 主机 firewalld 与 [Docker bridge/转发规则](https://docs.docker.com/engine/network/packet-filtering-firewalls/#integration-with-firewalld)均须核对，不能仅凭主机防火墙界面判断容器端口的公网可达性。

从可信 Kamatera 控制台核对 VM 的 SSH 主机公钥指纹，例如控制台终端运行 `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`；第一次本机连接显示的指纹必须匹配。不要用 `StrictHostKeyChecking=no` 或自动信任 `ssh-keyscan` 替代核验。下面以已授权 root 公钥登录为例；若平台实际登录用户不同，应使用其授权用户名和 sudo 权限。

若 VNC 只能查看当前虚拟终端，无法方便地输入命令，可以使用独立候选 known_hosts 做一次只传公开信息的 SSH 核验连接。在本机生成一次性公开随机 nonce，经候选连接仅运行下述显示步骤，再从可信 Kamatera VNC 画面同时核对 nonce 与 SHA-256 主机指纹；两项均匹配后，才把候选公钥晋升到部署专用 `.local-run/kamatera/ssh/known_hosts`。未核验的候选连接不能上传 env、seed 或业务数据，也不能用于安装和部署。普通 shell 重定向到 `/dev/console`、`ttyS0` 或虚拟终端可能返回 EIO；本次使用 Python `O_NOCTTY` 写 `/dev/tty0` 和 `/dev/tty1` 成功，并通过可信 VNC 画面核验：

```python
import os
import subprocess

nonce = "填入本机生成的公开随机nonce"
fingerprint = subprocess.check_output(
    ["ssh-keygen", "-lf", "/etc/ssh/ssh_host_ed25519_key.pub", "-E", "sha256"],
    text=True,
).strip()
message = f"\nSSH verification nonce: {nonce}\nHost key: {fingerprint}\n".encode()
for device in ("/dev/tty0", "/dev/tty1"):
    fd = os.open(device, os.O_WRONLY | os.O_NOCTTY)
    try:
        remaining = memoryview(message)
        while remaining:
            remaining = remaining[os.write(fd, remaining):]
    finally:
        os.close(fd)
```

当前部署已完成该带外核验；后续连接只用专用 known_hosts 且保持严格核验。隔离的本地 SSH 配置为 `.local-run/kamatera/ssh/client.conf`，Host 别名 `kamatera-resource-profile`；已限定部署私钥、禁用 agent/X11/转发和连接复用。可用 `ssh -F .local-run/kamatera/ssh/client.conf kamatera-resource-profile`；下面的显式命令同样指定专用 known_hosts。若指纹变化，停止传输并通过可信控制台重新查明原因。

```bash
# 仅在已核验的真实公网 IPv4 上设置此变量。
KAMATERA_IP='填入真实公网IPv4'
ssh -i .local-run/kamatera/ssh/id_ed25519 -o IdentitiesOnly=yes -o IdentityAgent=none -o UserKnownHostsFile=.local-run/kamatera/ssh/known_hosts -o StrictHostKeyChecking=yes "root@$KAMATERA_IP"
```

确认公钥登录成功并保留控制台恢复通道后，再关闭 SSH 密码登录；若继续使用 root 公钥，配置为 `PermitRootLogin prohibit-password`，校验 `sshd -t` 后重载。正式环境应另建最小权限运维用户。所有安装命令只在新 VM 上执行；现有主机需先检查 Docker/容器状态，不能直接替换有业务的运行时。

在新的 Rocky Linux 9 VM 中，按 [Rocky 官方 Docker Engine 安装流程](https://docs.rockylinux.org/zh/gemstones/containers/docker/)使用 Docker 的 RHEL RPM 仓库。先核对 `/etc/os-release` 和 `uname -m`；既有 Podman/Docker 服务与冲突包须先确认用途，不能直接卸载正在承载业务的运行时。

```bash
cat /etc/os-release
uname -m
sudo dnf install -y dnf-plugins-core ca-certificates curl git python3 python3-pip container-selinux policycoreutils-python-utils audit firewalld
sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
getenforce
sestatus
free -h
df -h /
```

**保持 SELinux Enforcing**。若镜像报告 Permissive/Disabled，先按 Rocky 的策略恢复与启动流程使其持久 Enforcing，再继续；不通过 `setenforce 0`、修改为 disabled、`label:disable` 或 privileged 绕过文件访问问题。Host Enforcing 也不自动证明 Docker 已启用 SELinux 隔离：检查 `systemctl cat docker`，根据 [Docker daemon 配置](https://docs.docker.com/engine/daemon/)和 [selinux-enabled 选项](https://docs.docker.com/reference/cli/dockerd/#options)，在 `/etc/docker/daemon.json` 合并 `"selinux-enabled": true`。保留现有配置，若启动参数已设置同一选项，不重复写入 JSON；只在新 VM/无业务容器时执行下面启动步骤：

```bash
# 先用编辑器合并 daemon.json，再验证；不输出其完整内容。
sudo dockerd --validate --config-file /etc/docker/daemon.json
sudo systemctl enable --now firewalld
sudo systemctl enable --now docker
sudo docker run --rm hello-world
docker compose version
docker info --format '{{json .SecurityOptions}}'
getenforce
```

构建环境的通过判据是 Rocky 9、x86_64、Compose ≥2.23.1、`getenforce` 为 Enforcing，Docker `SecurityOptions` 含 `name=selinux`，内存门满足且磁盘充足。若 Docker 已在修改配置前启动，须在空机上重启并重新核对。后端镜像自带 JDK 21 / Maven，容器内 Ubuntu/JDK 基础镜像与 Rocky 主机不冲突；需能访问 Maven Central、npm、PyPI 和镜像仓库。此处 Docker 运行验证服务于镜像构建，不启动本次业务栈。

2026-10-06 已通过严格 SSH 实查：**Rocky Linux 9.8、x86_64、MemTotal 约 23 GiB、空机可用内存约 22 GiB、根文件系统约 99 GB / 可用 97 GB、SELinux Enforcing**。内存门满足，磁盘是首次检查的空机数值，不代表构建后余量。**Docker 29.8.2 / Compose 5.6.0 已安装并启动，hello-world 成功，Docker SecurityOptions 已验证含 SELinux，firewalld active**。Rocky 主机默认 Python 3.9 用于部署工具和 bcrypt venv，业务 Python 3.10 位于镜像内，不在宿主机安装完整 AI requirements。K3s 安装、控制面和容器运行尚待验证，不能以 Docker 成功代替。

firewalld 保持启用，仅授权 HTTP/HTTPS 与可信管理来源的 SSH；收窄 SSH 前保留已验证连接和控制台恢复通道。构建用 Docker 仍会建立 bridge/转发规则，不能把主机 INPUT 当作所有容器端口的保护，也不要关闭其 iptables/NAT，见 [Docker firewalld 集成](https://docs.docker.com/engine/network/packet-filtering-firewalls/#integration-with-firewalld)。正式应用走 K3s 网络，仅前端占 80/443；上线后须从 VM 外部实际检查数据库/MCP/Java/Kubernetes 管理端口的边界。

按 [K3s README](../../k8s/README.md)安装固定且已核验的 K3s 版本，使用默认 containerd，禁用 Traefik 和 ServiceLB，保留 CoreDNS、local-storage 和 metrics-server。Rocky 的 [K3s SELinux 策略和 `selinux: true`](https://docs.k3s.io/advanced#selinux-support)须单独配置，不能沿用 Docker daemon 的开关推定 K3s 已开启。保持 firewalld；API 6443 仅供受控管理，8472/10250 等集群端口不任意暴露公网。镜像层和两个运行时共用这块 100 GB 磁盘，导入前后均核对余量。

K3s 的 SQL 初始化目录和静态字体为只读 hostPath，需按 README 设置**持久的 `container_file_t:s0` 标签**；不存在 Compose 的 `Z` 属性。启动门检查目录、字体与标签，禁止关闭 SELinux 或递归改整个系统目录。Caddyfile 通过 ConfigMap，Milvus 凭据通过 `0600` Secret 挂载；须实际核对容器 UID/读取权限及标签，不打印秘密。Compose 的 legacy `ro,Z` / `configs.content` 仅属于 §9 备选路径。

## 5. SSH 传输、镜像导入和 K3s 私网启动

先在已验证 VM 中创建只供运维访问的目录：

```bash
sudo install -d -m 0700 /opt/resource-profile
sudo install -d -m 0700 /opt/resource-profile/.local-run/kamatera
sudo install -d -m 0700 /opt/resource-profile/.local-run/kamatera/fonts
sudo install -d -m 0700 /opt/resource-profile/docker
```

在本机使用同一已核验主机与部署密钥，单独传输源码包和两个私有配置；绝不上传 SSH 私钥：

```bash
scp -i .local-run/kamatera/ssh/id_ed25519 -o IdentitiesOnly=yes -o IdentityAgent=none -o UserKnownHostsFile=.local-run/kamatera/ssh/known_hosts -o StrictHostKeyChecking=yes .local-run/kamatera/source-k3s-reviewed.tar.gz "root@$KAMATERA_IP:/opt/resource-profile/source-k3s-reviewed.tar.gz"
scp -i .local-run/kamatera/ssh/id_ed25519 -o IdentitiesOnly=yes -o IdentityAgent=none -o UserKnownHostsFile=.local-run/kamatera/ssh/known_hosts -o StrictHostKeyChecking=yes docker/.env.kamatera "root@$KAMATERA_IP:/opt/resource-profile/docker/.env.kamatera"
scp -i .local-run/kamatera/ssh/id_ed25519 -o IdentitiesOnly=yes -o IdentityAgent=none -o UserKnownHostsFile=.local-run/kamatera/ssh/known_hosts -o StrictHostKeyChecking=yes .local-run/kamatera/seed-credentials.json "root@$KAMATERA_IP:/opt/resource-profile/.local-run/kamatera/seed-credentials.json"
scp -i .local-run/kamatera/ssh/id_ed25519 -o IdentitiesOnly=yes -o IdentityAgent=none -o UserKnownHostsFile=.local-run/kamatera/ssh/known_hosts -o StrictHostKeyChecking=yes .local-run/kamatera/fonts/NotoSansSC-Regular.ttf "root@$KAMATERA_IP:/opt/resource-profile/.local-run/kamatera/fonts/NotoSansSC-Regular.ttf"
scp -i .local-run/kamatera/ssh/id_ed25519 -o IdentitiesOnly=yes -o IdentityAgent=none -o UserKnownHostsFile=.local-run/kamatera/ssh/known_hosts -o StrictHostKeyChecking=yes .local-run/kamatera/fonts/OFL.txt "root@$KAMATERA_IP:/opt/resource-profile/.local-run/kamatera/fonts/OFL.txt"
```

字体需先在本机准备，并连同 OFL 许可证传输：使用官方 Noto/Google Fonts 的 Noto Sans SC **真正的 TrueType**，可变 TTF 需固定为 Regular 400 后再部署。字体 README 的旧 OTF 改名建议已纠正；不能仅凭 `.ttf` 扩展名证明 PDFBox 可用，也不复制本机商业系统字体或以符号链接代替。记录来源、许可证、SHA-256 和固定字重的处理步骤；启动门核对 TrueType header、`glyf` 且无 `fvar`，真实中文 PDF 渲染仍需验收。

本机已准备 `.local-run/kamatera/fonts/NotoSansSC-Regular.ttf`，从 [Google Fonts 固定提交](https://github.com/google/fonts/tree/2894aab31764f10f29c421bdfd2340d3b382d384/ofl/notosanssc)的 variable TTF 实例化为 `wght=400` 静态 Regular，SHA-256 `eeb06b8a64fd04a2744d95579db1571b51027cda61ed78c62e4b730791525461`，旁存 `OFL.txt`。使用项目实际 OpenHTMLToPDF 1.0.10 / PDFBox 2.0.24 和 JDK 21 已渲染并查看两张 PNG，中文视觉通过；提取文本的“理”会映射到兼容字 U+F9E4，NFKC 后完整，不承诺原始 Unicode 码点逐字一致。真实云端报告生成/下载仍待验收。

K3s 将字体以只读 hostPath 绑定到 agent-service 的 `/opt/educare-fonts/NotoSansSC-Regular.ttf`，显式设置 `EDUCARE_FONT_PATH=file:/opt/educare-fonts/NotoSansSC-Regular.ttf`。启动门拒绝缺文件、错误 TTF 和错误 SELinux 标签；不能让运行时自动补目录。字体会物化到导出 PVC 中的 `.fonts` 缓存。更换字体时需处理旧缓存后重启并重新验证 PDF，不能仅替换绑定文件就宣称生效。

新的完整 K3s 包审查及摘要核验通过后，在 VM 解压、核对权限并安装部署工具环境；目前新包摘要待补录，不能拿旧包直接执行 K3s 发布：

```bash
cd /opt/resource-profile
chmod 600 source-k3s-reviewed.tar.gz docker/.env.kamatera .local-run/kamatera/seed-credentials.json
chmod 644 .local-run/kamatera/fonts/NotoSansSC-Regular.ttf .local-run/kamatera/fonts/OFL.txt
tar -xzf source-k3s-reviewed.tar.gz
python3 -m venv .local-run/kamatera/venv
.local-run/kamatera/venv/bin/python -m pip install -r scripts/requirements-cloud.txt
```

首次安装 K3s 完成后，域名/模型仍未配置时可先启动五个基础设施工作负载并改初始化账号密码。此路径仅验证五个数据库/Redis/MinIO 秘密，不渲染模型占位值，不创建 HPA、业务工作负载或前端；SQL 标签须正确，完整应用的字体门仍保留：

```bash
python3 scripts/k3s-deploy.py apply-infra
python3 scripts/k3s-deploy.py apply-business  # 可先启动内部业务/HPA，尚不含AI或公网
.local-run/kamatera/venv/bin/python scripts/k3s-deploy.py bootstrap-seeds
python3 scripts/k3s-deploy.py status
```

源码包没有 Git 元数据是正常的；宿主仍须安装 git 供部署工具检查私有文件。`build` 只串行构建镜像，不调用 Compose `up`；它的共享配置门仍要求真实域名/API。配置尚未齐备时，可独立执行已审查的各 Dockerfile 构建并使用 `k8s/workloads.json` 的确切 tag，不填 dummy 绕过启动门。构建后按 [K3s README](../../k8s/README.md)导入 K3s containerd，并核对全部自建镜像 tag；基础镜像由 K3s 拉取。导入完成、K3s Node Ready / metrics-server 可用、SQL/字体标签正确后，才执行以下正式流程：

```bash
python3 scripts/k3s-deploy.py check
# 如尚未完成应用镜像构建，真实 env 齐备后可用此纯构建入口。
python3 scripts/kamatera-deploy.py build
# 先完成 K3s README 的镜像导入与标签核验，再继续 apply-private。
python3 scripts/k3s-deploy.py apply-private
.local-run/kamatera/venv/bin/python scripts/k3s-deploy.py bootstrap-seeds
python3 scripts/k3s-deploy.py init-rag
python3 scripts/k3s-deploy.py probe-models
python3 scripts/k3s-deploy.py status
```

`apply-private` 先移除前端，再按存储 → Milvus → 领域服务 → 双 MCP/Python → agent → gateway 等待就绪；会有维护窗口。SQL 只在全新 MySQL PVC 执行，PVC 不会因 Deployment 重启自动清空。`bootstrap-seeds` 更换初始化账号密码，也识别重新 bcrypt 后仍为用户名的默认密码，不覆盖已迁移的非默认密码。实际登录密码仅从私有 seed 文件取回。不要输出生成的完整 Secret/清单或 `kubectl get secrets -o yaml`。

`init-rag` 只创建集合，不灌知识、不证明 RAG 成功。`probe-models` 使用合成文本向真实聊天和 embedding 供应商发请求，核对返回内容与维度；会使用供应商额度，不把 key 或模型回复打印到 stdout。确认授权的非敏感知识源后，再执行既有灌库和检索验收；不同模型/维度须计划重嵌入。管理员 RAG upsert 需要 `EDUCARE_ADMIN_TOKEN`，远端 HTTP 注册及单写/幂等仍需验证。不开公开 MySQL、Milvus、MCP 或管理端口来方便排错。

按 README 用受控的 data-service 业务负载验证 HPA：先观察 1 副本及可读 CPU 指标，再在压力下观察 2 副本 Ready/业务成功，最后停止压力并经过稳定窗口确认回到 1。需要同时记录 Pod 数、CPU、延迟/错误率和数据库连接，不能只把 YAML 中 `maxReplicas: 2` 计作验收成功。不增加节点，不更改其他服务的固定副本；若资源不足先停止负载排查，不自动扩容付费 VM。

## 6. DNS、HTTPS 和发布验收

自有域名优先：A 记录直接指向 `CLOUD_PUBLIC_IP`，当前发布门不支持 CDN 代理地址。删除错误 AAAA，或确实配置相应 IPv6。没有域名时，可用 [nip.io](https://nip.io/) 的 `公网IP用连字符连接.nip.io` 临时演示域名，例如把真实 IP 的点替换为连字符；设置 `CLOUD_SITE_ADDRESS` 后实际核对 DNS。其第三方 DNS 和域名控制不适合长期/敏感生产；自有域名仍是正式部署前提。无论哪种域名，都需实际验证 80/443 可达和受信任 TLS，不能通过 HTTP 或 `curl -k` 绕过。

2026-10-07 / GROQ-PROBE-UA-20261007：该临时域名已实际解析到45.130.165.168；当前public区未声明http/https服务且前端未启动，80/443未监听。发布前应准备HTTP/HTTPS入站规则并实测K3s hostPort与证书，不仅检查INPUT列表；此轮只读核对未修改防火墙。

真实配置、随机登录密码、DNS 和私有模型/业务探针通过后，在 VM 使用安装了 bcrypt 的环境发布：

```bash
.local-run/kamatera/venv/bin/python scripts/k3s-deploy.py publish
python3 scripts/k3s-deploy.py status
```

K3s `publish` 再检查实际数据库默认密码、域名 A / 公网 IPv4、真实模型及 embedding、私网 Deployment 就绪，再创建前端；不重新应用 data 的 HPA 副本数。Caddy 申请证书后执行受信任 HTTPS `/healthz` 检查，失败会撤下前端。还须检查 `/login`、匿名业务 401、公网内部/actuator 403，数据库/MCP/Kubernetes 管理端口不可任意公网访问。随后验证三种角色、真实 AgentLoop、双 MCP、dense RAG、SSE、HPA、持久化及导出、可观测 trace。探针、静态解析、模拟测试和空库 fallback 不能代替这些验收。

## 7. 排错、回滚和试用到期停用

- 内存门失败/OOM：核对实际 RAM、容器限制和构建峰值，不缩掉 Milvus 或关权限来宣称完整上线。磁盘不足应检查镜像和卷，不能未经授权扩容。
- API 401/403/429：分别查 key/模型权限和额度，embedding 的独立 key 与维度也需核对；不静默改用收费供应商。既有 Groq 返回输出格式/截断错误时核对 §3 的三项选项。
- Pod 未就绪：先 K3s `status`、Pod events / logs，核对导入镜像 tag、PVC、探针及依赖就绪；分享前移除 Authorization、key、token、个人信息和完整 Secret/清单。新库初始化失败先核对 PVC 状态，不能清库重试。
- Rocky 文件访问拒绝：检查 `getenforce`、K3s `selinux: true` / 策略 RPM、SQL/字体的 `ls -Z` 和 Pod UID / 实际读取；必要时在本机查看 `sudo ausearch -m AVC,USER_AVC -ts recent`。Milvus Secret 文件须为 0600 且运行 UID 可读，不打印凭证。Compose 备选另查 Docker SecurityOptions / ProcessLabel / MountLabel。通过标签/路径/策略定位，不关闭 SELinux，也不直接把全部 audit 日志生成宽泛 allow 规则。
- HPA `<unknown>` / 未扩容：先确认 metrics-server 和 `kubectl top`、CPU requests、目标 Deployment 及压力是否持续；Pod Pending 时查 allocatable / requests / events。JVM 启动峰值需用 startup/readiness 与稳定窗口排除。只准 data 1–2，不通过新增收费节点解决本轮演示。
- TLS：查 A/AAAA、80/443、主机/Docker 入站规则、Caddy 日志和 ACME 限流；nip.io 的解析/证书也需实际验证。
- 默认账号改密：迁移库非默认密码不同会拒绝覆盖，应通过已有账户管理流程处理。
- 数据与导出：MySQL、MinIO/etcd/Milvus 需一致备份恢复；单机持久卷不等于备份。中文导出字体、导出文件持久化和恢复均待真实验证。

撤下公网入口并保留私网工作负载和 PVC：

```bash
python3 scripts/k3s-deploy.py stop-public
```

此命令只删除前端 Deployment，**不停止私网工作负载，也不会停止 Kamatera 计费**。禁止删除业务 PVC/namespace 或使用 Compose `down -v` 作日常回滚。回滚使用曾验证的镜像及兼容配置，再私网应用、验收和发布；不回用历史泄露凭据，不自动关闭既有 Render 环境。若切回 §9 的 Compose，先明确数据备份/迁移及 K3s 私网停用方案；两种运行时的数据卷不自动互通。

从账户中记录真实试用开始和到期时间，至少提前 48 小时准备退出；本文件没有创建后台提醒或自动删除任务。若不继续付费，先下载并验证必需备份，再按[官方终止服务器流程](https://www.kamatera.com/faq/answer/terminate-server/)在 Console → My Cloud → Servers 选择服务器，Power Off 后 **Terminate Server** 并确认。仅关机仍可能有[存储和 IP 费用](https://www.kamatera.com/faq/answer/will-i-be-charged-for-my-hourly-server-even-when-its-powered-off/)。核对服务器确已终止、无额外磁盘/IP/服务残留，保存确认和账单状态；月度订单的[账期规则](https://www.kamatera.com/faq/answer/why-was-i-charged-after-i-terminated-my-services/)也需检查。终止会破坏服务器数据，须完成备份恢复检查后由已授权操作执行；本地脚本不自动终止云资源。

## 8. 验证记录（2026-10-06）

| 检查 | 结果与边界 |
|---|---|
| 部署工具回归 | 14 项通过：此前 13 项加 SQL/Caddy bind 源存在/类型/符号链接门；真实本机 SQL/Caddy/静态字体来源门通过，不调用云资源 |
| 共享 Compose 与 OCI include | 合成测试值解析通过：各 17 服务、仅 frontend 80/443、17.25 GiB；Groq 默认 TEXT/空；3 个只读 private Z bind 和 Milvus grant 0600。未创建容器，真实 SELinux relabel 待验证 |
| Caddy | 官方 v2.11.6 本地 validate 通过；未监听、未申请真实证书 |
| Python 业务回归 | 主任务完成最小依赖环境 32 项通过，含独立 embedding key 和实际 create_app 的管理路由；ChatOpenAI stub，未安装真实 FastMCP，不证明模型/MCP 运行 |
| 安全源码包解压构建 | 新K3s包350文件/307,631字节及摘要已核对，347个旧运行文件一致。此前 JDK 21 全后端 `mvn package -DskipTests`、AI 开启/同源前端 build、Python factory/import/prompt 与 upsert 未授权 401 已通过（可选 SDK mock）。11个Linux应用镜像已在Rocky原生构建（包括完整pip与npm ci）并导入K3s；构建-DskipTests不计作Java单测 |
| 中文字体 | 官方固定来源、OFL 及静态字重已准备；真实项目 PDF 引擎本地渲染与双 PNG 视觉检查通过，NFKC 后文本完整；原始理字兼容码点限制已记录，云端导出待验证 |
| 押金/账号/VM | US$10 押金到账，创建前 30 天 trial 显示；`resource-profile-sg` 已手动创建并开启，Singapore/Type A 4 CPU/24,576 MB/100 GB；创建向导月付 US$92 含 1,000 GB。应用部署、域名和 API 配置尚未完成 |
| SSH 主机核验 | 专用 root 公钥登录可用；独立候选连接只发送公开 nonce/主机指纹，通过可信 Kamatera VNC 画面匹配后晋升专用 known_hosts，后续严格核验。Python O_NOCTTY 写 tty0/tty1 成功；普通重定向及 console/ttyS0 的 EIO 未当作核验成功 |
| Rocky / Docker / SELinux / firewalld | SSH 实查 Rocky 9.8/x86_64、约 23 GiB 总内存/22 GiB 空机可用、根文件系统 99 GB/可用 97 GB、主机 Enforcing；Docker 29.8.2 / Compose 5.6.0、hello-world 成功、引擎 SecurityOptions SELinux 与 firewalld active 已核验。五基础设施/12私有工作负载已Ready；从外部22可达、6443/10250/3306不可达，公网前端尚未创建 |
| K3s / data HPA | 正式方案为单节点 K3s，只有 data CPU70% / 1–2 副本，其余固定1；13项离线边界测试通过；真实Node Ready/metrics、5PVC/Enforcing和受控CPU负载1→2通过，300秒稳定窗口后自动回缩1已验 |
| 远端业务与恢复 | 12私有工作负载Ready、三角色登录/权限/退出撤销20项通过；MySQL合成数据快照在隔离数据库还原，20表行数及种子密码hash匹配，私有备份0600，测试数据库已清理。完整AI/TLS/模型/RAG/trace/向量及跨主机恢复待验；R-5/R-6保持未完成 |

## 9. Compose 备选（本次不启动）

共享 `docker-compose.cloud.yml` 及 OCI include 仍保留 17 服务的原单机启动能力，作为明确选择后的备选；Compose 本身不提供本次 data HPA。K3s 和 Compose 不能同时占用 80/443，不应同时启动两套有默认账号的数据库；切换必须处理卷备份/迁移及各自私网工作负载的停止方案。Docker 构建镜像可以继续使用，不代表已经选择 Compose 运行路径。

仅在确实切到备选并完成配置和数据安排后，才用以下入口；不要把这些启动命令混入 §5 正式流程：

```bash
python3 scripts/kamatera-deploy.py start-private
.local-run/kamatera/venv/bin/python scripts/kamatera-deploy.py bootstrap-seeds
python3 scripts/kamatera-deploy.py init-rag
.local-run/kamatera/venv/bin/python scripts/kamatera-deploy.py publish
python3 scripts/kamatera-deploy.py status
# 仅停止 Compose 容器，保留 named volumes；仍会产生平台费用。
python3 scripts/kamatera-deploy.py stop
```

备选的 SQL/Caddyfile/字体使用只读 `Z` legacy bind 和 `create_host_path:true`，来源门在 `up` 前检查存在、类型及字体。该选型由固定版本 [Compose bind 实现](https://github.com/docker/compose/blob/v5.5.0/pkg/compose/create.go#L880-L985)和 [Mount API 实现](https://github.com/docker/compose/blob/v5.5.0/pkg/compose/create.go#L1279-L1296)核对；不凭已关闭的 Issue 13396 推定所有版本状态。Milvus `configs.content` 经内存 tar 复制，grant 为 0600，代码见 [Compose secrets](https://github.com/docker/compose/blob/v5.5.0/pkg/compose/secrets.go#L42-L130)。这些不是 Kubernetes 挂载规则；备选真实 relabel、UID 与读取仍需验证。

## root密码状态补充（2026-10-06 / SSH-PASSWORD-STATE-20261006）

实机passwd -S root返回PS（Password Set）；当前连接条件下sshd -T显示PermitRootLogin/PubkeyAuthentication/PasswordAuthentication=yes、PermitEmptyPasswords=no。因此不能根据创建时未输入或部署未保存密码，判断root是空密码。密码明文未由本次部署保存，来源未知，检查没有读取密码哈希，也未修改密码/sshd。继续用已固定主机身份的专用SSH密钥登录即可；若需root密码，在自己的SSH终端执行passwd设置新密码。邮件的通用提示不是原密码记录，Linux密码hash不能直接还原明文。

2026-10-07 / LANGFUSE-OTLP-20261007 最终运行快照：17/17 Deployment Ready，frontend Ready；data HPA当前/目标均1、上限2，指标可用且ScalingActive=true。只读快照未施加新负载，不替代既有1→2→1扩缩容证据。

2026-10-07 / LANGFUSE-OTLP-20261007 私网最终复验：修正版 `--stage private-ai` 全部8项通过，Agent触发次数0；三库固定合成文档持久化、1024维有效向量、非fallback检索、双MCP鉴权/工具及Python生成有效。既有upsert按固定doc前缀替换同批旧合成chunk，行数每库由2变1；此前全部行已核验为相同公开合成文本，没有删未知资料或清Redis。VM私有结果 `k3s-ai-acceptance-20261007-private-ai.json` 为0600；该阶段不代表完整Agent通过。
