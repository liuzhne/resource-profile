# 生产部署 Runbook

师生资源画像系统生产部署步骤。公网仅暴露 nginx（80/443），网关与所有数据存储绑 `127.0.0.1`，
不直接对外。AI 模型（LLM/embedding/reranker）仍在宿主 llama.cpp，经 `host.docker.internal` 访问。

## 网络拓扑

```
公网 ──443/80──> nginx(frontend 容器) ──/api──> gateway:8080 ──> auth/user/.../agent
                       │                              │
                  TLS 终止 + SPA               edu-network（容器内网）
                                                      └──> mysql / redis / nacos / milvus（仅 127.0.0.1）
```

## 一、前置准备

### 1. 生产密钥（一次性）

```bash
cp docker/.env.example docker/.env
# 编辑 docker/.env，把每个 change-me 换成强随机值（文件内有 openssl 生成命令）
vim docker/.env

# 密钥体检：任何项留空 / 仍是 dev 默认 / JWT<32 即 fail，禁止继续
scripts/preflight-prod.sh
```

### 2. TLS 证书

把证书放到 `docker/certs/`（文件名固定，被 .gitignore 忽略）：

- `docker/certs/fullchain.pem` —— 证书链
- `docker/certs/privkey.pem` —— 私钥

正式证书用 Let's Encrypt（certbot http-01，挑战目录已挂载 `docker/certbot/www`）或购买的证书。
**仅测试**可自签：

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout docker/certs/privkey.pem -out docker/certs/fullchain.pem \
  -subj "/CN=your-domain.example.com"
```

## 二、起栈

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

首次会构建：gateway / agent-service / mcp-student-data（Spring Boot）、ai-inference / knowledge-rag（Python）、
frontend（Node 构建 Vue → nginx）。

## 三、数据库默认账号改密（全新库首次初始化后）

`sql/init/01_init.sql` 种子账号 admin/teacher/student 出厂密码等于用户名，**必须改**：

```bash
# 1) 生成 bcrypt 并填入 sql/prod/01_rotate_default_passwords.sql 的占位符
python3 -c "import bcrypt;print(bcrypt.hashpw(b'强密码', bcrypt.gensalt(rounds=10)).decode())"
# 2) 执行
docker exec -i edu-portrait-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" edu_portrait \
  < ../sql/prod/01_rotate_default_passwords.sql
```

## 四、验证

```bash
# 容器健康
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# 网关经 nginx 可达（自签证书加 -k）
curl -ksS https://localhost/api/auth/login -X POST \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"<新密码>"}'

# 确认数据端口未对公网暴露（应只在 127.0.0.1）
docker compose -f docker-compose.yml -f docker-compose.prod.yml config | grep -A1 '3306\|6379\|8080'
```

## 五、运维提示

- **证书续期**：certbot 续期后 `docker compose ... restart frontend` 重载（nginx 配置/证书是挂载，不必重建镜像）。
- **网关复验**：鉴权默认开（`educare.gateway.auth.enabled` / `check-session`）。如需临时排障关闭，经 Nacos `gateway.yml` 改，勿改镜像。
- **可观测**：
  - 指标/告警：`docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring up -d`
    → Grafana http://localhost:3000（「EduCare 总览」看板已自动加载）、Prometheus http://localhost:9090（均仅 127.0.0.1）。
    监控 gateway/agent-service/mcp-student-data 的 HTTP 速率/错误率/P95 时延 + JVM 堆；告警规则见 `docker/monitoring/prometheus/alert-rules.yml`。
  - LLM trace：Langfuse 用 `--profile langfuse` 起，并在 `docker/.env` 配 `LANGFUSE_*`。
- **内部服务凭据**：`EDUCARE_MCP_TOKEN` 与 `REDIS_PASSWORD` 均为生产硬门，必须设置至少 32 字符强值；agent-service 与两个 MCP server 通过前者互验，Redis 通过后者启用认证。

## 六、备份与压测

**数据备份**（MySQL）：

```bash
scripts/backup-mysql.sh                       # dump edu_portrait → backups/，gzip + 保留 7 天
RETAIN_DAYS=30 scripts/backup-mysql.sh        # 自定义保留
# cron 每天 02:30：
#   30 2 * * * /path/to/Resource-Profile/scripts/backup-mysql.sh >> /var/log/edu-backup.log 2>&1

scripts/restore-mysql.sh backups/edu_portrait-YYYYMMDD-HHMMSS.sql.gz   # 恢复（会确认）
```

> Milvus 向量数据可重建（重新 embedding），故未纳入 DB 备份；如需冷备，停 milvus 后打包 `docker/milvus/`。
> Redis 为缓存/会话，丢失只需重登，无需备份。

**压测**：

```bash
# 业务链（登录 + student/data/auth 端点）
k6 run scripts/load-test.js                   # 默认 20 VU 爬坡，P95<800ms / 失败率<1% 为门
BASE_URL=https://<域名>/api USER=admin PASS=<密码> k6 run scripts/load-test.js

# Agent 链（并发触发 + 时延分位 + Redis 命中率）
N=20 bash scripts/bench_agent.sh
```
