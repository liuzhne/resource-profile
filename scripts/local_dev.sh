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

java_major() {
  "$1/bin/java" -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print v[1]; exit }'
}

select_java() {
  # 优先尊重显式 JAVA21/JAVA_HOME；默认 JDK 版本不符合时再查本机的 JDK 21。
  local candidate="${JAVA21:-${JAVA_HOME:-}}"
  if [ -z "${JAVA21:-}" ] && { [ -z "$candidate" ] || [ "$(java_major "$candidate")" != 21 ]; }; then
    candidate=""
    if [ -x /usr/libexec/java_home ]; then
      candidate=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
      [ "$(java_major "$candidate")" = 21 ] || candidate=""
    fi
    if [ -z "$candidate" ]; then
      local path
      for path in /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home /usr/local/opt/openjdk@21; do
        if [ -x "$path/bin/java" ] && [ "$(java_major "$path")" = 21 ]; then candidate="$path"; break; fi
      done
    fi
  fi
  [ -n "$candidate" ] && [ -x "$candidate/bin/java" ] && [ "$(java_major "$candidate")" = 21 ] \
    || { er "需要 JDK 21；请用 JAVA21=/path/to/jdk21 或 JAVA_HOME 指定（父 POM 只接受 21.x）"; exit 1; }
  export JAVA_HOME="$candidate"
  export PATH="$JAVA_HOME/bin:$PATH"
}

configure_shared_env() {
  # Compose 负责解析 docker/.env（以及调用者的环境覆盖）；只提取宿主服务共用的指定项。
  # 临时文件权限为 600，既不 source .env，也不在日志中打印解析后的凭证。
  local config_file values_file key value
  config_file=$(mktemp "$RUNDIR/compose.XXXXXX") || exit 1
  values_file=$(mktemp "$RUNDIR/env.XXXXXX") || { rm -f "$config_file"; exit 1; }
  if ! (cd docker && docker compose config --format json) >"$config_file"; then
    rm -f "$config_file" "$values_file"; er "无法解析 Docker Compose 配置"; exit 1
  fi
  if ! python3 - "$config_file" >"$values_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    services = json.load(source)["services"]
fields = {
    "mysql": ("MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "MYSQL_DATABASE"),
    "gateway": ("JWT_SECRET", "REDIS_PASSWORD", "NACOS_USERNAME", "NACOS_PASSWORD"),
    "nacos": ("NACOS_AUTH_IDENTITY_KEY", "NACOS_AUTH_IDENTITY_VALUE"),
    "mcp-student-data": ("EDUCARE_INTERNAL_TOKEN", "EDUCARE_MCP_TOKEN"),
}
for service, names in fields.items():
    environment = services[service].get("environment", {})
    for name in names:
        value = environment.get(name) or ""
        sys.stdout.buffer.write(name.encode() + b"\0" + str(value).encode() + b"\0")
PY
  then
    rm -f "$config_file" "$values_file"; er "无法读取共享环境配置"; exit 1
  fi
  while IFS= read -r -d '' key && IFS= read -r -d '' value; do
    export "$key=$value"
  done <"$values_file"
  rm -f "$config_file" "$values_file"
  [ -n "$EDUCARE_INTERNAL_TOKEN" ] || { er "Compose 未配置 EDUCARE_INTERNAL_TOKEN，内部取数链无法启动"; exit 1; }
  # 宿主服务连容器的回环映射端口；凭证与容器使用同一份解析结果。
  export MYSQL_HOST=localhost MYSQL_PORT=3306 REDIS_HOST=localhost REDIS_PORT=6379 NACOS_SERVER=localhost:8848
}

record_pid() { # name pid；记录启动时间，防止 PID 被其他进程复用。
  local name=$1 pid=$2 start
  start=$(ps -p "$pid" -o lstart= 2>/dev/null)
  printf '%s\n%s\n' "$pid" "$start" >"$PIDDIR/$name.pid"
}

stop_recorded() { # pidfile；只能停止本工作区创建且身份仍一致的进程。
  local file=$1 name pid recorded_start actual_start command
  [ -f "$file" ] || return 0
  name=$(basename "$file" .pid)
  { IFS= read -r pid; IFS= read -r recorded_start || true; } <"$file"
  case "${pid:-}" in ''|*[!0-9]*) wn "$name PID 记录无效，跳过"; rm -f "$file"; return 0;; esac
  if [ "$pid" -le 1 ] || ! kill -0 "$pid" 2>/dev/null; then
    wn "$name 已不在"; rm -f "$file"; return 0
  fi
  actual_start=$(ps -p "$pid" -o lstart= 2>/dev/null)
  command=$(ps -p "$pid" -o command= 2>/dev/null)
  if [ -z "${recorded_start:-}" ] || [ "$recorded_start" != "$actual_start" ]; then
    wn "$name 缺少匹配的进程身份记录，跳过"; rm -f "$file"; return 0
  fi
  case "$name:$command" in
    mock-llm:*"$ROOT/scripts/mock_llm_server.py"*|frontend:*"$ROOT/frontend/node_modules/vite/bin/vite.js"*) ;;
    auth-service:*"$ROOT/backend/auth-service/target/auth-service-"*|user-service:*"$ROOT/backend/user-service/target/user-service-"*|teacher-service:*"$ROOT/backend/teacher-service/target/teacher-service-"*|student-service:*"$ROOT/backend/student-service/target/student-service-"*|mental-service:*"$ROOT/backend/mental-service/target/mental-service-"*|data-service:*"$ROOT/backend/data-service/target/data-service-"*|mcp-student-data:*"$ROOT/backend/mcp-student-data/target/mcp-student-data-"*|agent-service:*"$ROOT/backend/agent-service/target/agent-service-"*|gateway:*"$ROOT/backend/gateway/target/gateway-"*) ;;
    *) wn "$name 进程不属于当前工作区，跳过"; rm -f "$file"; return 0;;
  esac
  if kill "$pid" 2>/dev/null; then ok "停 $name (PID=$pid)"; else wn "$name 未能停止"; fi
  rm -f "$file"
}

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
  need docker; need curl; need node; need npm; need mvn; need python3
  select_java
  configure_shared_env
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
    docker exec edu-portrait-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -uroot' >/dev/null 2>&1 && { ok "MySQL 就绪"; break; }
    sleep 2
  done
  docker exec edu-portrait-redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping' >/dev/null 2>&1 && ok "Redis 就绪"
  if [ "$CORE_ONLY" = false ]; then
    wait_http "http://localhost:8090/health" ai-inference-service 120 || wn "ai-inference 未就绪（AI 链会降级）"
  fi
}

seed_sql() {
  hd "2. 灌初始化 SQL（幂等，缺表才补）"
  # 卷已存在时 docker-entrypoint 不会重放 sql/init，这里显式补齐
  local n
  n=$(docker exec edu-portrait-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B edu_portrait -e "$1"' sh \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='edu_portrait'" 2>/dev/null || echo 0)
  if [ "${n:-0}" -ge 10 ]; then ok "已有 $n 张表，跳过"; return; fi
  for f in 01_init 03_agent_init 04_student_extras 05_intervention_feedback; do
    [ -f "sql/init/$f.sql" ] || continue
    docker exec -i edu-portrait-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot edu_portrait' < "sql/init/$f.sql" \
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
    stop_recorded "$PIDDIR/mock-llm.pid"
    python3 "$ROOT/scripts/mock_llm_server.py" "$LLM_PORT" >"$LOGDIR/mock-llm.log" 2>&1 &
    record_pid mock-llm "$!"
    sleep 2
    curl -fsS -m 2 "http://localhost:$LLM_PORT/v1/models" >/dev/null 2>&1 \
      && ok "桩 LLM 已起（仅冒烟用，非真实推理）" || wn "桩 LLM 启动失败"
  else
    wn "未检测到 :$LLM_PORT 的 LLM —— AgentLoop 不可用。宿主起 llama.cpp/vLLM，或加 --mock-llm 用桩"
  fi
}

start_java() { # module port
  local mod=$1 port=$2 jar pid
  jar=$(ls "$ROOT/backend/$mod/target/$mod"-*.jar 2>/dev/null | grep -v sources | head -1)
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
  pid=$!; record_pid "$mod" "$pid"
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
  # 直接 exec 项目现有 dev 脚本的 Vite 入口，pidfile 对应实际进程，停止时无需全局 pkill。
  ( cd frontend && exec nohup node "$ROOT/frontend/node_modules/vite/bin/vite.js" >"$LOGDIR/frontend.log" 2>&1 ) &
  record_pid frontend "$!"
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
    stop_recorded "$f"
  done
  if [ "$DOWN_ALL" = true ]; then
    hd "停容器（保留数据卷）"
    (cd docker && docker compose stop) && ok "容器已停"
  else
    echo "  容器仍在运行（--all 一并停）"
  fi
}

cmd_smoke() {
  hd "冒烟"
  need docker; need python3
  configure_shared_env
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
    if [ -n "${EDUCARE_MCP_TOKEN:-}" ]; then
      wn "已配置 MCP token：现有 mcp_smoke_test.sh 不附鉴权头，请用 Agent 工具调用验证"
    else
      bash scripts/mcp_smoke_test.sh && ok "MCP 冒烟通过" || wn "MCP 冒烟未全绿"
    fi
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
