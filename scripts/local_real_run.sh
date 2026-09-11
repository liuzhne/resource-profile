#!/usr/bin/env bash
# 无 GPU/无 Docker 环境下的"最小真跑"：用桩 LLM 替代 14B，真实启动 agent-service，
# 真 curl 触发一个任务，验证 ReAct 循环跑完 + final_answer 解析落库 + status=COMPLETED。
#
# 依赖：本机 redis + mysql（brew）+ 已 mvn package 的 agent-service jar + scripts/mock_llm_server.py。
# 关掉 Nacos 注册与 MCP client（桩 LLM 直接给 final_answer，低风险短路，无需 MCP/Python）。
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LOGDIR=/tmp/edu-realrun; mkdir -p "$LOGDIR"

echo "== 1. 起 redis =="
redis-server --daemonize yes --port 6379 >/dev/null 2>&1 || true
redis-cli ping || { echo "redis 未就绪"; exit 1; }

echo "== 2. 起 mysql + 建库/用户/灌 schema =="
mysql.server start >/dev/null 2>&1 || /opt/homebrew/opt/mysql/bin/mysqld_safe --datadir=/opt/homebrew/var/mysql >/dev/null 2>&1 &
for i in $(seq 1 30); do mysqladmin ping -uroot >/dev/null 2>&1 && break; sleep 1; done
mysqladmin ping -uroot >/dev/null 2>&1 || { echo "mysql 未就绪"; exit 1; }
mysql -uroot <<'SQL'
CREATE DATABASE IF NOT EXISTS edu_portrait DEFAULT CHARSET utf8mb4;
CREATE USER IF NOT EXISTS 'edu'@'localhost' IDENTIFIED BY 'edu123456';
ALTER USER 'edu'@'localhost' IDENTIFIED WITH mysql_native_password BY 'edu123456';
GRANT ALL PRIVILEGES ON edu_portrait.* TO 'edu'@'localhost';
FLUSH PRIVILEGES;
SQL
for f in 01_init 03_agent_init 04_student_extras 05_intervention_feedback; do
  [ -f "sql/init/$f.sql" ] && mysql -uroot edu_portrait < "sql/init/$f.sql" 2>>"$LOGDIR/sql.err" && echo "  loaded $f"
done

echo "== 3. 起桩 LLM (:8091) =="
pkill -f mock_llm_server.py 2>/dev/null || true
python3 scripts/mock_llm_server.py 8091 >"$LOGDIR/mockllm.log" 2>&1 &
sleep 1; curl -sf http://localhost:8091/v1/models >/dev/null && echo "  mock LLM ok"

echo "== 4. 起 agent-service (loop on, nacos/mcp off) =="
JAR=$(ls backend/agent-service/target/agent-service-*.jar 2>/dev/null | head -1)
[ -z "$JAR" ] && { echo "未找到 jar，先 mvn -pl agent-service -am package -DskipTests"; exit 1; }
pkill -f "agent-service-.*.jar" 2>/dev/null || true
MYSQL_USER=edu MYSQL_PASSWORD=edu123456 MYSQL_HOST=localhost REDIS_HOST=localhost \
java -jar "$JAR" \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.discovery.register-enabled=false \
  --spring.ai.mcp.client.enabled=false \
  --educare.agent.loop.enabled=true \
  --spring.ai.openai.base-url=http://localhost:8091 \
  --educare.model.local.base-url=http://localhost:8091 \
  >"$LOGDIR/agent.log" 2>&1 &
AGENT_PID=$!
echo "  agent-service PID=$AGENT_PID，等待 /actuator/health ..."
for i in $(seq 1 60); do
  curl -sf http://localhost:8087/actuator/health >/dev/null 2>&1 && { echo "  health UP"; break; }
  sleep 2
done
curl -sf http://localhost:8087/actuator/health >/dev/null 2>&1 || { echo "agent-service 未起来，看 $LOGDIR/agent.log"; tail -40 "$LOGDIR/agent.log"; exit 1; }

echo "== 5. 触发任务并轮询 =="
TASK=$(curl -s -XPOST http://localhost:8087/agent/api/v1/task/trigger/1 | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',''))")
echo "  taskId=$TASK"
for i in $(seq 1 40); do
  S=$(curl -s http://localhost:8087/agent/api/v1/task/$TASK | python3 -c "import sys,json;print((json.load(sys.stdin).get('data') or {}).get('status',''))")
  echo "  [$i] status=$S"
  case "$S" in COMPLETED|REJECTED|FAILED) break;; esac
  sleep 2
done

echo "== 6. 结果（DB 落库证据）=="
curl -s http://localhost:8087/agent/api/v1/task/$TASK | python3 -m json.tool
echo "--- DB 直查 ---"
mysql -uroot edu_portrait -e "SELECT id,student_id,status,risk_level, LEFT(risk_analysis_result,40) r, LEFT(intervention_plan,40) p FROM agent_task WHERE id=$TASK\G"
