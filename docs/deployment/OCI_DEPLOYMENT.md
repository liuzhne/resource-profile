# OCI 单主机部署

> 日期 / 修复标识：2026-10-06 / OCI-DEPLOY-20261006
> 状态：部署配置与本地静态验证已准备；Oracle 注册、资源创建和实际上线未完成。
> 2026-10-06 / KAMATERA-DEPLOY-20261006：当前选用 [Kamatera 部署](KAMATERA_DEPLOYMENT.md)；本 OCI 文档保留为兼容参考，配置统一复用 cloud 文件。

## 1. 账号、资源与数据前提

注册入口是 [Oracle Cloud Free Tier](https://www.oracle.com/cloud/free/)。开票国家、姓名、邮箱应真实；邮箱验证、密码设置、付款验证和条款确认由账号所有者完成。Home Region 应根据实际访问者和资源供应选择，不能从电脑时区推定开票国家或部署区域。

2026-10-06 核对的 [Always Free 文档](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) 为 A1 每月 1,500 OCPU 小时 / 9,000 GB 小时，纯免费租户约 2 OCPU / 12 GB。200 GB boot + block volume 合计额度在 Home Region；空闲实例可能回收，也可能缺货。[价格表](https://www.oracle.com/cloud/price-list/) 则注明**付费租户** A1 每月前 3,000 OCPU 小时 / 18,000 GB 小时免费，约 4 OCPU / 24 GB，且 VM / 裸金属 / Container Instances 共享此额度。实际租户限额、已有资源和计费预估必须再次核对。

本配置完整保留 9 个 JVM、Python 双进程与 Milvus，容器内存上限合计 17.25 GiB；脚本要求 Linux `MemTotal` ≥20 GiB，目标为 24 GiB。它不适用于未经验证的 12 GB 免费实例。账户升级、试用额度消耗或超免费额度资源需要单独的费用授权；不得把短期 trial VM 宣称为长期免费，也不得静默删掉 RAG 来声称完整部署成功。Budget 是告警，不能当作硬性费用上限。

账号可用后，拟创建一台 Ubuntu ARM64 A1 VM，Home Region 中的免费额度内 boot volume（例如 150 GB，仍须核对已有占用）、公网 IPv4、VCN / public subnet / Internet Gateway。入站仅允许可信管理来源的 TCP 22、公开 TCP 80/443；443 UDP 是可选 HTTP/3。MySQL、Redis、Milvus、MCP、Java 端口不开放。OCI 安全列表与主机防火墙都需核对。不能假设 Docker 发布端口受 UFW 自动保护。

先确定使用全新演示数据库还是迁移现有业务库；迁移前备份、核验兼容性和权限，不复制已忽略的访谈目录或历史泄露凭据。生成与 embedding 端点均为外部 HTTPS API；API 用量和额度属于供应商，Oracle VM 免费额度不包含模型费用。真实学生及心理数据送外部模型前须明确处理范围。没有真实模型、知识灌库与 trace 验收，R-5/R-6 仍未完成。

## 2. 文件与调用链

| 文件 | 用途 |
|---|---|
| [`docker/docker-compose.oci.yml`](../../docker/docker-compose.oci.yml) | OCI 兼容 include；复用 docker-compose.cloud.yml 的 17 服务，前端 public profile |
| [`docker/Dockerfile.frontend.cloud`](../../docker/Dockerfile.frontend.cloud) | AI 页面开启、API/SSE `/api` 同源的前端镜像 |
| [`docker/caddy/Caddyfile.cloud`](../../docker/caddy/Caddyfile.cloud) | TLS 自动签发/续期、SPA、SSE 即时转发、内部/运维路径阻断 |
| [`docker/oci.env.example`](../../docker/oci.env.example) | 无真实秘密的配置模板 |
| [`scripts/oci-deploy.py`](../../scripts/oci-deploy.py) | OCI 薄入口；共用 cloud_deploy.py 配置检查、内存/发布门和默认账号改密 |
| [`scripts/requirements-oci.txt`](../../scripts/requirements-oci.txt) | seed 改密的 bcrypt 依赖 |

浏览器 → Caddy :443 → `/api` 去前缀 → gateway :8080 → 领域服务。服务、MySQL/Redis、Milvus/etcd/MinIO 与双 MCP 通过同一 `edu-network` 的服务名通信。JWT、Redis 会话、内部 token、MCP token 和角色/字段权限保持开启。只有生成模型和 embedding 请求走外网；可选 reranker 默认关闭。

Milvus 保留 v2.4.1（ARM64 manifest 已核验）；OCI MinIO 使用 Milvus 上游镜像 `milvusdb/minio:RELEASE.2024-12-18T13-15-44Z`，替代无法匿名拉取的旧 2023 tag。镜像清单兼容不等于完整镜像构建和容器运行已通过。

## 3. 私有配置

Linux VM 安装 [Docker Engine / Compose 官方发行版](https://docs.docker.com/engine/install/ubuntu/)，Compose ≥2.23.1（使用 `configs.content`）。构建需要 Maven Central、npm、PyPI、镜像仓库网络可用；后端镜像自带 JDK 21，不能用 JDK 17。

共享配置现要求独立上传静态 Noto Sans SC TrueType 字体，以 `CLOUD_EXPORT_FONT_FILE` 指定路径（相对路径从 docker/ 解析），只读挂载并由 `EDUCARE_FONT_PATH` 接入。较早的 OCI env 没有此项时须补充；`start-private` / `publish` 会阻止缺文件、OTF 改后缀或可变字体。详情见 [Kamatera 手册 §5](KAMATERA_DEPLOYMENT.md#5-ssh-传输和分阶段启动)。

在包含本次新文件的源码目录中执行：

```bash
python3 -m venv .local-run/oci/venv
.local-run/oci/venv/bin/python -m pip install -r scripts/requirements-oci.txt
python3 scripts/oci-deploy.py init-env
```

`init-env` 只用于尚无私有配置的新目录，拒绝覆盖已有文件。兼容入口仍读取 `.env.oci` / `.local-run/oci/seed-credentials.json`；新模板使用 `CLOUD_SITE_ADDRESS` / `CLOUD_PUBLIC_IP`，已有 OCI 同名变量也支持映射，项目名保持 `edu-portrait-oci`。当前本地已经生成 `docker/.env.oci` 和 `.local-run/oci/seed-credentials.json`，均为 0600 且被 Git 忽略；它们不应进入源码包。向已授权 VM 传输时单独通过 SSH/SCP 传输，并在目标核对权限，不向聊天、CI 输出或截图暴露内容。

在私有 env 中填写域名、公网 IPv4、OpenAI 兼容生成接口、模型、embedding 接口及其输出维度。Java 根 URL 不带 `/v1`，Python URL 为该根 + `/v1`；embedding URL 带 `/v1`。`EMBEDDING_API_KEY` 支持独立供应商；留空才复用 `LLM_API_KEY`。MinIO 与应用内部凭据已分别随机生成，不使用历史 `.env.audit` 中的值。旧平台和历史凭证的全量轮换 B-2 不因本次新密钥自动完成。

新域名 A 记录指向 `OCI_PUBLIC_IP`；本脚本直接核对 DNS，当前不支持 CDN 代理地址。删除不正确的 AAAA 记录或配置匹配的 IPv6，避免证书/浏览器连接到其他机器。无域名时保持私网状态，不能用 HTTP 明文登录代替 TLS 验收。

## 4. 分阶段部署

以下命令在已满足容量和费用条件的 **Linux OCI VM** 上运行。首次建库的 SQL 位于 [`sql/init`](../../sql/init)；已有卷不会再次执行初始化。

```bash
python3 scripts/oci-deploy.py check
python3 scripts/oci-deploy.py build
python3 scripts/oci-deploy.py start-private
.local-run/oci/venv/bin/python scripts/oci-deploy.py bootstrap-seeds
python3 scripts/oci-deploy.py init-rag
python3 scripts/oci-deploy.py status
```

`start-private` 先停止已有前端，再等待私有服务健康；更新时这会造成维护窗口。启动默认 16 个服务。`bootstrap-seeds` 只将 admin/teacher/student 默认密码换为私有文件中的随机密码；既识别初始化 SQL 的哈希，也校验重新 bcrypt 后仍为用户名的默认密码。已有非默认密码不覆盖，凭证不一致时拒绝继续。密码只从私有文件取回，不打印。`publish` 再次查询数据库并实际 bcrypt 校验，发现默认密码即拒绝开放；执行 `publish` 同样需要安装 bcrypt 的 Python 环境。

`init-rag` 只创建 4 个向量集合，不灌知识，不证明 retrieval 可用；确认 embedding 实际维度与集合 schema 一致后，以授权的非敏感知识源执行既有灌库流程。不得把空库 fallback 当作 RAG 成功。向量模型或维度变更须计划重建和重嵌入；现有集合不能直接复用不同模型向量。

在账户密码已替换、DNS 指向本机、模型/业务只读探针通过后发布：

```bash
.local-run/oci/venv/bin/python scripts/oci-deploy.py publish
python3 scripts/oci-deploy.py status
```

Caddy 将申请证书。验证 `/healthz`、`/login` 使用受信任 HTTPS；不能用 `curl -k` 或绕过浏览器证书警告充当成功。检查未登录访问业务路由返回 401，公网内部/actuator 路径 403，数据库/MCP 端口从公网不可达；再用私有随机密码登录验证管理/教师/学生角色，以及一次真实 AgentLoop、双 MCP、dense RAG、SSE、结果持久化/下载。可观测 trace 单独按执行计划验收。全部容器 healthy 只表示探针通过，不能代替这些业务验收。

## 5. 排错、备份与回滚

- `out of host capacity`：在 Home Region 尝试其他可用域或稍后重试；不能自动换付费 shape。
- 12 GB 或内存不足：脚本会阻止启动；核对实际额度和完整栈容量方案，不能关闭 Milvus 后宣称完整上线。
- 未知外部模型/embedding：保持模板门禁；401/403 查 key/模型权限，429 查额度，不降低业务授权或默默换收费供应商。
- 容器 unhealthy：`status` 后检查对应服务日志，避免分享 Authorization、模型 key、token 或完整解析后的 Compose。MySQL 初始化失败先查卷状态，不直接清卷。
- TLS 失败：核对 A/AAAA、80/443 的 OCI 与主机规则、域名控制权及 Caddy 日志；修复证书问题前不接受明文登录。
- 改密失败：确认是新建演示库；迁移库的非默认密码由原管理流程维护，脚本不会重置它。
- 备份：先验证业务库备份与恢复流程；部署状态至少涵盖 MySQL、MinIO/etcd/Milvus 一致恢复方案、导出文件和 TLS 卷。单机持久卷不等于备份。未实跑的恢复标为待验证。

紧急撤下公网入口、保留数据库与所有卷：

```bash
docker compose --env-file docker/.env.oci -f docker/docker-compose.oci.yml stop frontend
```

回滚使用先前验证过的源码/镜像与兼容配置，执行私网启动和业务验收后再发布。禁止 `down -v`、删除卷或回用历史泄露秘密。此配置为新平台准备，不能据此关闭既有 Render 服务或修改其数据层。

## 6. 验证记录（2026-10-06）

| 检查 | 结果与边界 |
|---|---|
| Python 最小测试环境 | 2026-10-06 主任务最小环境 32 项通过；含独立 embedding key / 共享 key 与实际 create_app 的路由；ChatOpenAI stub，不调用真实供应商/真实 MCP |
| 共享云部署安全测试 | 2026-10-06 新共享工具 14 项通过；包括 OCI 兼容、全 Java/runtime 资源、安全源码包、字体门和 SQL/Caddy bind 源门；原 7 项扩展而来。Rocky SELinux 接线的实际运行待验证 |
| Compose 解析 | 使用合成测试值解析通过；17 服务、仅 frontend 发布端口、public profile、Docker DNS 接线、17.25 GiB 上限核对通过 |
| OCI 前端生产构建 | 构建通过；不证明 ARM 镜像 npm ci / 构建已通过 |
| Caddy 配置 | 官方 v2.11.6 本地 validate/adapt 通过；未启动监听、未申请实际证书 |
| 私有配置存储 | 新随机密钥与 seed 文件 0600、Git 忽略通过；域名和供应商字段待填写 |
| ARM 镜像元数据 | 主要基础镜像 / Milvus / etcd / MinIO 清单已核验；完整构建与运行待验证 |
| Oracle 账号 / VM / 发布 | 待用户完成注册与资源授权；未创建云资源，未迁移业务数据 |
| Docker 全栈 / TLS / 真实模型 / RAG / 备份恢复 | 待验证；本机 Docker 启动未成功，不声明上线完成或 R-5/R-6 通过 |
