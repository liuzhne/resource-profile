#!/usr/bin/env bash
# 本机测试环境一键起停：Docker 起基础设施 + Python/MCP，9 个 Java 服务与前端在宿主后台运行。
#
# 为什么不用 docker compose 起全栈：docker-compose.yml 没有 auth/user/teacher/student/mental/data
# 六个 service 定义（见 RUNBOOK.md §4.1），业务服务只能在宿主跑。
# 为什么 java -jar 而非 mvn spring-boot:run：9 个 mvn 进程太重，且 fat jar 便于 pidfile 管理。
#
# 用法：
#   bash scripts/local_dev.sh up          # 起全栈（首次会自动构建）
#   bash scripts/local_dev.sh up --core   # 只起 CRUD 链（gateway+auth+user+teacher+student+mental+data+前端）
#   bash scripts/local_dev.sh up --build  # 强制重新 mvn install
#   bash scripts/local_dev.sh down        # 停宿主进程（默认保留容器与数据卷）
#   bash scripts/local_dev.sh down --all  # 同时 docker compose stop
#   bash scripts/local_dev.sh status      # 端口/健康总览
#   bash scripts/local_dev.sh logs agent-service
#   bash scripts/local_dev.sh smoke       # 登录 + 健康 + MCP 冒烟
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
RUNDIR="$ROOT/.local-run"; LOGDIR="$RUNDIR/logs"; PIDDIR="$RUNDIR/pids"
mkdir -p "$LOGDIR" "$PIDDIR"

# JDK 17 固定路径：本机默认无 java / 或为高版本，Enforcer 会拒绝构建
JAVA17="${JAVA17:-/opt/homebrew/opt/openjdk@17}"
export JAVA_HOME="$JAVA17"
export PATH="$JAVA_HOME/bin:$PATH"

# 宿主服务连容器里的中间件，都走 localhost 映射端口
export MYSQL_HOST=localhost MYSQL_USER=edu MYSQL_PASSWORD=edu123456
export REDIS_HOST=localhost REDIS_PORT=6379
export NACOS_SERVER=localhost:8848
export JWT_SECRET="${JWT_SECRET:-edu-portrait-dev-jwt-secret-change-in-prod-0123456789}"
LLM_PORT=8091

# service:port —— 顺序即启动顺序（auth 先起，gateway 最后，agent 依赖 8094）
CORE_SVCS=(auth-service:8081 user-service:8082 teacher-service:8083 student-service:8084 mental-service:8085 data-service:8086)
AI_SVCS=(mcp-student-data:8094 agent-service:8087)
GW_SVCS=(gateway:8080)

c()  { printf '\033[%sm%s\033[0m\n' "$1" "$2"; }
ok() { c '0;32' "  ✓ $1"; }
wn() { c '0;33' "  ! $1"; }
er() { c '0;31' "  ✗ $1"; }
hd() { printf '\n'; c '1;36' "== $1 =="; }

need() { command -v "$1" >/dev/null 2>&1 || { er "缺少 $1"; exit 1; }; }

wait_http() { # url name timeout_s
  local url=$1 name=$2 t=${3:-90} i=0
  while [ $i -lt "$t" ]; do
    curl -fsS -m 2 "$url" >/dev/null 2>&1 && { ok "$name 就绪"; return 0; }
    i=$((i+1)); sleep 1
  done
  er "$name 超时未就绪（$url）"; return 1
}

preflight() {
  hd "0. 环境体检"
  need docker; need curl; need node; need npm
  [ -x "$JAVA_HOME/bin/java" ] || { er "JDK 17 不在 $JAVA_HOME（用 JAVA17=... 覆盖，或 brew install openjdk@17）"; exit 1; }
  ok "JDK $("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | awk '{print $3}' | tr -d '\"')"
  ok "Node $(node -v)"
  docker info >/dev/null 2>&1 || { er "Docker daemon 未运行，先启动 Docker Desktop"; exit 1; }
  ok "Docker 可用"
}

infra_up() {
  hd "1. 起基础设施（Docker）"
  local svcs="mysql redis nacos"
  [ "$CORE_ONLY" = false ] && svcs="$svcs etcd minio milvus-standalone attu ai-inference-service knowledge-rag-mcp"
  (cd docker && docker compose up -d $svcs) || { er "docker compose 失败"; exit 1; }
  wait_http "http://localhost:8848/nacos" Nacos 120 || wn "Nacos 未确认（首次启动较慢，可继续）"
  for i in $(seq 1 60); do
    docker exec edu-portrait-mysql mysqladmin ping -uroot -proot >/dev/null 2>&1 && { ok "MySQL 就绪"; break; }
    sleep 2
  done
  docker exec edu-portrait-redis redis-cli ping >/dev/null 2>&1 && ok "Redis 就绪"
  if [ "$CORE_ONLY" = false ]; then
    wait_http "http://localhost:8090/health" ai-inference-service 120 || wn "ai-inference 未就绪（AI 链会降级）"
  fi
}

seed_sql() {
  hd "2. 灌初始化 SQL（幂等，缺表才补）"
  # 卷已存在时 docker-entrypoint 不会重放 sql/init，这里显式补齐
  local n
  n=$(docker exec edu-portrait-mysql mysql -uroot -proot -N -B edu_portrait \
        -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='edu_portrait'" 2>/dev/null || echo 0)
  if [ "${n:-0}" -ge 10 ]; then ok "已有 $n 张表，跳过"; return; fi
  for f in 01_init 03_agent_init 04_student_extras 05_intervention_feedback; do
    [ -f "sql/init/$f.sql" ] || continue
    docker exec -i edu-portrait-mysql mysql -uroot -proot edu_portrait < "sql/init/$f.sql" \
      2>>"$LOGDIR/sql.err" && ok "loaded $f" || wn "$f 执行有告警，见 $LOGDIR/sql.err"
  done
}

build() {
  hd "3. 构建后端"
  local jars; jars=$(ls backend/*/target/*.jar 2>/dev/null | wc -l | tr -d ' ')
  if [ "$FORCE_BUILD" = false ] && [ "$jars" -ge 9 ]; then ok "已有 $jars 个 jar，跳过（--build 强制重建）"; return; fi
  c '0;33' "  mvn clean install -DskipTests（首次约几分钟）..."
  (cd backend && mvn -B -ntp clean install -DskipTests) >"$LOGDIR/mvn.log" 2>&1 \
    || { er "构建失败，见 $LOGDIR/mvn.log"; tail -30 "$LOGDIR/mvn.log"; exit 1; }
  ok "构建完成"
}

llm_check() {
  [ "$CORE_ONLY" = true ] && return 0
  hd "4. 检查生成 LLM (:$LLM_PORT)"
  if curl -fsS -m 2 "http://localhost:$LLM_PORT/v1/models" >/dev/null 2>&1; then
    ok "LLM 已在 :$LLM_PORT"
  elif [ "$MOCK_LLM" = true ]; then
    pkill -f mock_llm_server.py 2>/dev/null
    python3 scripts/mock_llm_server.py "$LLM_PORT" >"$LOGDIR/mock-llm.log" 2>&1 &
    echo $! >"$PIDDIR/mock-llm.pid"
    sleep 2
    curl -fsS -m 2 "http://localhost:$LLM_PORT/v1/models" >/dev/null 2>&1 \
      && ok "桩 LLM 已起（仅冒烟用，非真实推理）" || wn "桩 LLM 启动失败"
  else
    wn "未检测到 :$LLM_PORT 的 LLM —— AgentLoop 不可用。宿主起 llama.cpp/vLLM，或加 --mock-llm 用桩"
  fi
}

start_java() { # module port
  local mod=$1 port=$2 jar pid
  jar=$(ls "backend/$mod/target/$mod"-*.jar 2>/dev/null | grep -v sources | head -1)
  [ -z "$jar" ] && { er "$mod 未找到 jar"; return 1; }
  if curl -fsS -m 1 "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
    ok "$mod 已在 :$port，跳过"; return 0
  fi
  local extra=()
  # agent-service 在无 MCP/无 LLM 时关掉 MCP client，避免拉工具列表失败导致启动挂掉
  if [ "$mod" = agent-service ] && ! curl -fsS -m 1 "http://localhost:$LLM_PORT/v1/models" >/dev/null 2>&1; then
    extra+=(--spring.ai.mcp.client.enabled=false --educare.agent.loop.enabled=false)
    wn "agent-service 降级启动（MCP client + AgentLoop off）"
  fi
  nohup "$JAVA_HOME/bin/java" -jar "$jar" \
      --spring.ai.openai.base-url="http://localhost:$LLM_PORT" \
      ${extra[@]+"${extra[@]}"} >"$LOGDIR/$mod.log" 2>&1 &
  pid=$!; echo "$pid" >"$PIDDIR/$mod.pid"
  printf '  %-20s PID=%-7s :%s ' "$mod" "$pid" "$port"
  for i in $(seq 1 90); do
    curl -fsS -m 2 "http://localhost:$port/actuator/health" >/dev/null 2>&1 && { c '0;32' 'UP'; return 0; }
    kill -0 "$pid" 2>/dev/null || { c '0;31' "进程退出，见 $LOGDIR/$mod.log"; tail -15 "$LOGDIR/$mod.log"; return 1; }
    printf '.'; sleep 2
  done
  c '0;31' "超时，见 $LOGDIR/$mod.log"; return 1
}

java_up() {
  hd "5. 起 Java 服务"
  local list=("${CORE_SVCS[@]}")
  [ "$CORE_ONLY" = false ] && list+=("${AI_SVCS[@]}")
  list+=("${GW_SVCS[@]}")
  local failed=0
  for e in "${list[@]}"; do start_java "${e%%:*}" "${e##*:}" || failed=$((failed+1)); done
  [ "$failed" -gt 0 ] && wn "$failed 个服务未起来（其余可用）"
  return 0
}

front_up() {
  hd "6. 起前端 (:5173)"
  [ -d frontend/node_modules ] || { c '0;33' "  npm ci ..."; (cd frontend && npm ci) >"$LOGDIR/npm.log" 2>&1 || { er "npm ci 失败，见 $LOGDIR/npm.log"; return 1; }; }
  if curl -fsS -m 1 http://localhost:5173 >/dev/null 2>&1; then ok "前端已在 :5173"; return 0; fi
  # 注意：pid 必须在子 shell 外捕获，否则 $! 拿到的是子 shell 而非 vite
  ( cd frontend && exec nohup npm run dev >"$LOGDIR/frontend.log" 2>&1 ) &
  echo $! >"$PIDDIR/frontend.pid"
  wait_http http://localhost:5173 前端 60
}

summary() {
  hd "就绪"
  c '1;32' "  前端  http://localhost:5173   （admin/admin · teacher/teacher · student/student）"
  c '0;36' "  网关  http://localhost:8080/actuator/health"
  [ "$CORE_ONLY" = false ] && c '0;36' "  Attu  http://localhost:8000    Nacos http://localhost:8848/nacos (nacos/nacos)"
  echo "  日志  $LOGDIR/    停止  bash scripts/local_dev.sh down"
}

cmd_status() {
  hd "端口健康"
  local all=("${CORE_SVCS[@]}" "${AI_SVCS[@]}" "${GW_SVCS[@]}")
  for e in "${all[@]}"; do
    local m=${e%%:*} p=${e##*:}
    if curl -fsS -m 2 "http://localhost:$p/actuator/health" >/dev/null 2>&1; then ok "$(printf '%-20s :%s' "$m" "$p")"
    else er "$(printf '%-20s :%s' "$m" "$p")"; fi
  done
  for e in 前端:5173 ai-inference:8090 LLM:8091; do
    # 分开赋值：local 一行内会先展开所有右值，$p 会取到上一个循环的残留值
    local n=${e%%:*} p=${e##*:}
    local u="http://localhost:$p/"
    [ "$p" = 8090 ] && u="http://localhost:8090/health"
    [ "$p" = 8091 ] && u="http://localhost:8091/v1/models"
    curl -fsS -m 2 "$u" >/dev/null 2>&1 && ok "$(printf '%-20s :%s' "$n" "$p")" || er "$(printf '%-20s :%s' "$n" "$p")"
  done
  hd "容器"; (cd docker && docker compose ps --format '  {{.Name}}\t{{.Status}}' 2>/dev/null)
}

cmd_down() {
  hd "停宿主进程"
  for f in "$PIDDIR"/*.pid; do
    [ -e "$f" ] || continue
    local n p; n=$(basename "$f" .pid); p=$(cat "$f")
    if kill -0 "$p" 2>/dev/null; then kill "$p" 2>/dev/null; ok "停 $n (PID=$p)"; else wn "$n 已不在"; fi
    rm -f "$f"
  done
  pkill -f "vite" 2>/dev/null
  if [ "$DOWN_ALL" = true ]; then
    hd "停容器（保留数据卷）"
    (cd docker && docker compose stop) && ok "容器已停"
  else
    echo "  容器仍在运行（--all 一并停）"
  fi
}

cmd_smoke() {
  hd "冒烟"
  curl -fsS -m 5 http://localhost:8080/actuator/health >/dev/null 2>&1 && ok "gateway health" || { er "gateway 不可用"; return 1; }
  local tok
  tok=$(curl -fsS -m 10 -X POST http://localhost:8080/auth/login \
        -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}' \
        | python3 -c 'import sys,json; print((json.load(sys.stdin).get("data") or {}).get("token",""))' 2>/dev/null)
  [ -n "$tok" ] && ok "admin 登录成功（token ${#tok} 字符）" || { er "登录失败"; return 1; }
  curl -fsS -m 10 -H "Authorization: Bearer $tok" "http://localhost:8080/student/list?pageNum=1&pageSize=1" >/dev/null 2>&1 \
    && ok "带 token 访问 student 通过" || wn "student 接口未通过"
  curl -fsS -m 10 "http://localhost:8080/student/list" >/dev/null 2>&1 \
    && er "无 token 也能访问 student —— 网关鉴权未生效" || ok "无 token 被网关拦截（预期 401）"
  if [ -x scripts/mcp_smoke_test.sh ] && curl -fsS -m 2 http://localhost:8094/actuator/health >/dev/null 2>&1; then
    bash scripts/mcp_smoke_test.sh && ok "MCP 冒烟通过" || wn "MCP 冒烟未全绿"
  fi
}

CORE_ONLY=false; FORCE_BUILD=false; MOCK_LLM=false; DOWN_ALL=false
CMD="${1:-up}"; shift || true
for a in "$@"; do case "$a" in
  --core) CORE_ONLY=true;; --build) FORCE_BUILD=true;; --mock-llm) MOCK_LLM=true;;
  --all) DOWN_ALL=true;; *) TARGET="$a";; esac; done

case "$CMD" in
  up)     preflight; infra_up; seed_sql; build; llm_check; java_up; front_up; summary;;
  down)   cmd_down;;
  status) cmd_status;;
  smoke)  cmd_smoke;;
  logs)   f="$LOGDIR/${TARGET:-gateway}.log"; [ -f "$f" ] && tail -f "$f" || { er "无日志 $f"; ls "$LOGDIR"; };;
  *) sed -n '2,20p' "$0"; exit 1;;
esac
