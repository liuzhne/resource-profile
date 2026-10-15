# Kamatera 单节点 K3s

此部署面将公开的 [Cloud Compose](../docker/docker-compose.cloud.yml) 环境、内部地址、命令和内存预算转为 [workloads.json](./workloads.json)，由 [k3s-deploy.py](../scripts/k3s-deploy.py) 生成 Kubernetes JSON 清单。模板只含占位符，真实值通过 Kubernetes Secret 引用；Milvus 的 MinIO 凭据同样用 Secret 文件（0600），不会写入 ConfigMap。命令不打印解析后的清单、密钥、SQL 哈希或模型响应。

17 个应用/基础设施工作负载中，16 个先在集群内启动。只有通过发布门的 frontend 使用 hostPort TCP 80/443；其他 Service 全为 ClusterIP。沿用同名服务 DNS，禁用 Nacos，不创建公网数据库/MCP 端口或公开 Kubernetes API。

## 单节点和弹性边界

- Rocky Linux 9、4 核 / 24GB / 100GB；K3s 使用 containerd，Docker 仅构建应用镜像，镜像必须导入 K3s 的 `k8s.io` containerd namespace。
- K3s 保留 CoreDNS、metrics-server、local-path provisioner，关闭 Traefik 和 ServiceLB；API 可在节点地址监听以供 kubernetes Service / CoreDNS / metrics-server 使用，但 firewalld 必须禁止公网 TCP 6443，只放行已核验的 Pod/Service CIDR、节点自身及 loopback；UDP 8472 和 TCP 10250 同样不得对公网开放。管理经SSH本机执行，kubeconfig保持0600。安装命令、内网CIDR与版本由真实服务器部署记录确定，禁止为排错关闭 SELinux。
- 本轮仅 data-service 启用 autoscaling/v2 HPA，CPU requests=100m、目标利用率70%、min=1/max=2、缩容稳定窗口300秒；其余工作负载固定1。HPA 不创建 Kamatera 服务器，且不提供节点故障高可用。metrics-server 未就绪时 HPA 不会自动扩大副本。
- 全栈基线 limits=17.25GiB，data-service 第2副本额外512MiB，最大17.75GiB；基础 requests=1.8 CPU / 9.1875GiB（含 frontend），HPA第2副本再请求100m / 384MiB。limits 不等于实际峰值验收，系统及 K3s 仍需余量。
- Stateful/RWO 与当前异步/SSE 单例部署使用 Recreate；升级会有短暂停机。单节点PVC采用local-path，8卷申请总计46GiB。`stop-public` 不删除 PVC；删除 PVC/namespace/服务器可能丢失数据，务必先备份。

## SELinux 和文件前提

SQL初始化目录及静态 TTF 字体通过只读 hostPath 挂载。hostPath 不会执行 Docker 的 `:Z` 操作；实际管理员应使用 `semanage fcontext` + `restorecon` 为这两个源设置持久的 `container_file_t:s0` 上下文。脚本检查 `getenforce=Enforcing` 和目录/SQL/字体标签，在缺失时停止；不得使用 privileged、SELinux disable 或不限定路径的大范围重标记。K3s 必须安装其官方 `k3s-selinux` 策略、启动 `selinux: true`；PVC真实写入仍须验收。

私有 env、seed JSON、静态字体已单独传到 `/opt/resource-profile` 的受限路径；此目录下需保留 `sql/init`、`docker/caddy/Caddyfile.cloud` 及运维脚本。绝不传历史 `.env.audit`、访谈、真实师生/心理数据、SSH私钥或整个本地工作目录。镜像部署使用合成初始化数据。

## 本次主机安装与镜像导入记录（2026-10-06）

官方 stable channel 当日返回 `v1.36.5+k3s1`；安装脚本下载后保存审查，SHA-256 为 `e5cc3b3d9dfc1662c2d9be6da5abc9a4cd317d6abc3a5ffc02e3dd3248207fee`。安装器另核对下载二进制摘要，并安装签名校验的 `k3s-selinux-1.6-1.el9` RPM。当前 K3s containerd 为 `2.3.4-k3s1.36`。真实节点/三个系统组件 Ready，metrics API Available=True、Secret Encryption Enabled、kubeconfig600、Enforcing均已检查；不要重复初始化正在运行的集群。

`/etc/rancher/k3s/config.yaml`（0600）采用以下非秘密设置：

```yaml
node-name: resource-profile-sg
selinux: true
secrets-encryption: true
write-kubeconfig-mode: "0600"
disable:
  - traefik
  - servicelb
```

保留firewalld，通过默认Pod/Service CIDR信任规则支持集群通信，未向public zone放行6443/10250/8472。从外部实查22可达、6443/10250/3306不可达；单机节点API需要供内部kubernetes Service访问，因此不强制绑定127.0.0.1。SQL目录0755、SQL文件0644（合成初始化数据），字体0644；精确标签执行后，MySQL与五个基础设施PVC启动/写入已通过：

```bash
semanage fcontext -a -t container_file_t -r s0 '/opt/resource-profile/sql/init(/.*)?'
semanage fcontext -a -t container_file_t -r s0 '/opt/resource-profile/\.local-run/kamatera/fonts/NotoSansSC-Regular\.ttf'
restorecon -Rv /opt/resource-profile/sql/init /opt/resource-profile/.local-run/kamatera/fonts/NotoSansSC-Regular.ttf
```

以上规则已存在时先检查并使用 `semanage fcontext -m` 更新既有精确规则，不扩展到整个 `/opt`。应用镜像11个均已在VM原生构建（Python完整依赖、9Java JDK21、前端npm ci）并一次导入；后续新版本须重复构建/导入，再显式重建受影响Pod，不能以本机Docker缓存当作已更新运行镜像：

```bash
images=(edu-portrait-ai-inference:cloud)
for service in auth-service user-service teacher-service student-service mental-service data-service mcp-student-data agent-service gateway frontend; do
  images+=("edu-portrait-kamatera-${service}:latest")
done
docker save "${images[@]}" | k3s ctr --namespace k8s.io images import -
k3s crictl images
```

默认IfNotPresent与latest标签仅供此单机首次迁移；正式持续部署应固定镜像版本/digest并记录回滚版本，避免旧tag缓存误判为新代码。

## 命令顺序

在服务器中从项目根目录运行；运维 Python 环境安装 [requirements-cloud.txt](../scripts/requirements-cloud.txt) 的 bcrypt，脚本兼容 Rocky 的 Python 3.9。env 文件和 seed 文件均为未追踪的0600文件。模型接口/域名待填时可独立启动五个基础设施服务并改种子密码，严格验证数据库、Redis、MinIO的五个随机密码，清单仅包含对应数据Secret、五个PVC和内部Service，不读取/渲染模型占位值。

```bash
python3 scripts/k3s-deploy.py apply-infra
python3 scripts/k3s-deploy.py bootstrap-seeds
```

如果模型/域名仍未提供，可继续准备基础业务域及HPA：

```bash
python3 scripts/k3s-deploy.py apply-business
```

此阶段使用已有五个基础设施和 gateway/auth/user/teacher/student/mental/data 共12个工作负载，额外严格校验新 JWT/内部凭据及全组密钥互异；同一个 Secret 保留12服务的全部所需字段，暂不注入无关MCP令牌，不渲染任何模型密钥、agent、双MCP、Python或frontend。全组仍只有内网ClusterIP，data HPA为1–2；业务段 limits=13.875GiB/max14.375GiB，requests=1.3CPU/7.5GiB。此阶段可以验证登录/域API及CPU指标与HPA，gateway的AI路由尚未有目标服务，不视为AI验收。已进入更宽阶段后，脚本拒绝降回较小阶段，避免server-side apply删除更宽阶段的Secret字段；需要重新部署时使用对应相同阶段或完整`apply-private`。

填好域名和两个真实HTTPS模型接口后继续全栈：

```bash
python3 scripts/k3s-deploy.py check
python3 scripts/k3s-deploy.py render --output .local-run/kamatera/k3s-private-reviewed.json
python3 scripts/k3s-deploy.py apply-private
python3 scripts/k3s-deploy.py bootstrap-seeds
python3 scripts/k3s-deploy.py init-rag
python3 scripts/k3s-deploy.py probe-models
python3 scripts/k3s-deploy.py publish
python3 scripts/k3s-deploy.py status
```

`render` 不覆盖已有文件，也不在终端输出秘密。`apply-private` 先移除旧 frontend，按数据库→Milvus→域服务→双MCP/Python→agent→gateway 依次等待实际Ready；Kubernetes API通过 stdin接收Secret，使用server-side apply避免客户端last-applied注解复制秘密。Secret使用标准`data`字段以避免官方说明的`stringData`/server-side apply限制；base64只编码、不提供保密性，解析清单仍必须保持0600。真实env变化会通过模板哈希触发Pod重建。

`bootstrap-seeds` 对当前数据库执行真实 bcrypt 校验；即便默认密码重新加盐也会阻止发布，并拒绝覆盖未知已迁移密码。`init-rag` 只建立集合，不等于灌库/检索验收。`probe-models` 用合成文本执行真实chat及embedding调用并核对向量维度，可能消耗供应商额度，供应商成本不包含在VM免费试用中。

`publish` 再次校验容量/SELinux/字体、默认密码、DNS→VM IPv4、真实模型接口以及全部私有工作负载Ready，才创建 frontend；随后执行受信HTTPS健康检查，失败时移除公共 frontend。最终仍要完成登录、注销撤销、SSE、报告中文PDF、合成RAG入库/检索、metrics/HPA扩缩容、备份还原与重启验收；本地静态通过不能写成真实部署成功或R-5/R-6完成。

出现私有服务失败，在服务器私下检查 `k3s kubectl -n educare describe pod ...` 与日志（可能含凭据/业务数据，禁止复制到聊天）。若metrics未知，检查 `k3s kubectl -n kube-system rollout status deployment/metrics-server`、`k3s kubectl top pods -n educare`、HPA Conditions 与 CPU requests。缩容可能等待300秒稳定窗口，最大副本2不会因CPU继续高而无限增长。回退公共入口：

```bash
python3 scripts/k3s-deploy.py stop-public
```

此命令只删除 frontend Deployment。停止容器不会终止Kamatera计费；试用期结束需要用户管理平台资源/费用。

官方依据：[K3s SELinux 与 containerd](https://docs.k3s.io/advanced)、[Kubernetes HPA](https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/)、[Kubernetes Secret](https://kubernetes.io/docs/concepts/configuration/secret/)。

## 实机阶段验收（2026-10-06 / K3S-HPA-DEPLOY-20261006）

当时已启动12项私有工作负载（5基础设施+7Java），11应用镜像构建/导入，完整16私有服务及前端尚未发布；最新状态见下方2026-10-07记录。当时三角色登录、dashboard权限、内部路径封锁、退出会话撤销及默认密码拒绝共20项通过。

HPA使用现有data容器内150秒受控CPU负载，并未手工scale：UTC09:24:22初始1副本，09:24:55控制器扩至2，09:25:58两Pod均Ready；09:26:52停止负载，09:32:31自动回到1个Ready Pod。缩容稳定窗口保持300秒，最终CPU为request的3%，ScalingActive可计算指标。它证明CPU指标与HPA控制链工作，不是业务QPS、故障HA或完整AI性能验收。

MySQL合成数据备份0600，在新建隔离数据库还原后20表行数和种子hash匹配，测试数据库已清理；备份留在VM私有目录。复制到本地被自动审批拒绝，原因是数据库内容导出到该目的地未有明确授权；未经用户批准不重试，也不走其他通道。完整向量/应用与跨主机恢复待验证。

当时临时域名resource-profile.45-130-165-168.nip.io已解析，尚未监听或取得证书、模型配置未齐；2026-10-07后续已发布HTTPS并验证实际供应商，见下方最新记录。

## Cloud 可观测迁移阶段（2026-10-07 / LANGFUSE-OTLP-20261007）

Java已改OTLP HTTP/JSON v4，Python固定Langfuse4.13.1、HOST显式base_url及退出shutdown；默认内容关闭。Java20/Python41真实SDK/部署27项通过，两个镜像重建导入、16私有服务及Milvus初始化通过。原VM三供应商配置同步，内部九项密钥相等、0600回滚保留。frontend发布及外部受信TLSv1.3、登录200/匿名401/内部403与端口边界通过；三库合成RAG、双MCP和实际Python/Java Cloud记录均已验证。公开SQL全行/Milvus全内容核验后用户明确授权唯一示例Agent：约20秒FAILED，ReAct第2轮出现供应商JSON校验和限流错误；实际 `llm.chat`/`agent.loop`记录通过且I/O为空。只完成R-5.1，完整生成/质量及R-6待验。旧12服务阶段为历史记录；缓存命中旧docID与新写入成功需分开核对，临时脚本校准不改生产缓存行为。

2026-10-07 / LANGFUSE-OTLP-20261007 最终运行快照：17/17 Deployment Ready，frontend Ready；data HPA当前/目标均1、上限2，指标可用且ScalingActive=true。只读快照未施加新负载，不替代既有1→2→1扩缩容证据。

2026-10-07 / LANGFUSE-OTLP-20261007 私网最终复验：修正版 `--stage private-ai` 全部8项通过，Agent触发次数0；三库固定合成文档持久化、1024维有效向量、非fallback检索、双MCP鉴权/工具及Python生成有效。既有upsert按固定doc前缀替换同批旧合成chunk，行数每库由2变1；此前全部行已核验为相同公开合成文本，没有删未知资料或清Redis。VM私有结果 `k3s-ai-acceptance-20261007-private-ai.json` 为0600；该阶段不代表完整Agent通过。
