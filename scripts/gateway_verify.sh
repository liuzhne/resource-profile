#!/usr/bin/env bash
# ============================================================
# 网关安全门复验：对「真实起栈」断言鉴权链是否成立。
# 互补 scripts/smoke_test_agent.sh（那个是 Agent 功能冒烟，不验安全门）。
#
# 覆盖（可自动断言）：
#   1) 公开路径 /auth/login 放行并取 token
#   2) 准入门：无 token 访问受保护端点 → 401（JwtAuthGlobalFilter）
#   3) 有效 token → 200
#   4) 无效 token → 401
#   5) /_internal/ → 403（内网手测端点不经公网网关，filter 鉴权前一律拦）
#   6) 登出吊销（A6 会话白名单）：同一 token 登出前 200 → 登出后 401
# 角色相关项（需多账号，列为手动复验，见末尾与 E2E_RUNBOOK）：字段脱敏 / IDOR。
#
# 前置：已起栈（gateway 8080 + auth + 一个业务服务 + redis；鉴权默认开）。
# 用法：
#   bash scripts/gateway_verify.sh
#   GATEWAY=https://<域名>/api ADMIN_USER=admin ADMIN_PASS=<pw> bash scripts/gateway_verify.sh
# 退出码：0=全过；1=有失败项。
# ============================================================
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin}"
PROBE="${PROBE:-/student/list}"                          # 需鉴权的受保护 GET
INTERNAL_PROBE="${INTERNAL_PROBE:-/agent/api/v1/_internal/ping}"  # 任意 /_internal/ 路径即可

RED='\033[0;31m'; GREEN='\033[0;32m'; YEL='\033[0;33m'; NC='\033[0m'
pass=0; fail=0
require(){ command -v "$1" >/dev/null 2>&1 || { echo "✗ 缺少命令 $1"; exit 1; }; }
require curl; require jq
ok(){ echo -e "${GREEN}✓ $1${NC}"; pass=$((pass+1)); }
bad(){ echo -e "${RED}✗ $1${NC}"; fail=$((fail+1)); }

# 取 HTTP 状态码（带超时；连接失败返回 000）
code(){ curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@"; }

echo "============================================================"
echo " 网关安全门复验 @ $GATEWAY"
echo "============================================================"

# 连通性预检：连接失败（curl 非零）即网关未起
if ! curl -s -o /dev/null --max-time 5 "$GATEWAY/auth/login" -XPOST -H 'Content-Type: application/json' -d '{}'; then
  echo -e "${RED}✗ 网关不可达 $GATEWAY —— 先起栈（docker compose up）再跑${NC}"
  exit 1
fi

# 1) 公开路径放行 + 取 token
login_resp="$(curl -s --max-time 10 -XPOST "$GATEWAY/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")"
TOKEN="$(echo "$login_resp" | jq -r '.data.token // empty')"
if [[ -n "$TOKEN" ]]; then
  ok "公开路径 /auth/login 放行且取到 token"
else
  bad "/auth/login 未取到 token（账号/密码或服务异常）：$login_resp"
fi

# 2) 准入门：无 token → 401
c="$(code "$GATEWAY$PROBE")"
[[ "$c" == "401" ]] && ok "无 token 访问 $PROBE → 401（准入门生效）" \
  || bad "无 token 访问 $PROBE 期望 401，实得 $c（000=网关不可达）"

# 3) 有效 token → 200
if [[ -n "$TOKEN" ]]; then
  c="$(code -H "Authorization: Bearer $TOKEN" "$GATEWAY$PROBE")"
  [[ "$c" == "200" ]] && ok "有效 token 访问 $PROBE → 200" \
    || bad "有效 token 访问 $PROBE 期望 200，实得 $c"
fi

# 4) 无效 token → 401
c="$(code -H "Authorization: Bearer invalid.jwt.token" "$GATEWAY$PROBE")"
[[ "$c" == "401" ]] && ok "无效 token → 401" || bad "无效 token 期望 401，实得 $c"

# 5) /_internal/ → 403（与是否存在 controller 无关，filter 一律拦）
c="$(code -H "Authorization: Bearer ${TOKEN:-x}" "$GATEWAY$INTERNAL_PROBE")"
[[ "$c" == "403" ]] && ok "/_internal/ → 403（内网手测端点不经公网网关）" \
  || bad "$INTERNAL_PROBE 期望 403，实得 $c"

# 6) 登出吊销（A6 会话白名单）：用一枚新 token，登出后应 401
rl="$(curl -s --max-time 10 -XPOST "$GATEWAY/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")"
RT="$(echo "$rl" | jq -r '.data.token // empty')"
if [[ -n "$RT" ]]; then
  pre="$(code -H "Authorization: Bearer $RT" "$GATEWAY$PROBE")"
  curl -s -o /dev/null --max-time 10 -XPOST "$GATEWAY/auth/logout" -H "Authorization: Bearer $RT"
  post="$(code -H "Authorization: Bearer $RT" "$GATEWAY$PROBE")"
  if [[ "$pre" == "200" && "$post" == "401" ]]; then
    ok "登出吊销：同一 token 登出前 200 → 登出后 401（会话白名单生效）"
  else
    bad "登出吊销异常：登出前 $pre / 登出后 $post（期望 200→401；若仍 200 检查 educare.gateway.auth.check-session 是否开）"
  fi
else
  bad "登出吊销用例：二次登录未取到 token，跳过"
fi

echo ""
echo -e "${YEL}=== 角色相关项（需多账号，手动复验，见 E2E_RUNBOOK §6.5）===${NC}"
echo "  - 字段脱敏：admin vs student token 取同一学生，敏感字段（心理/经济）应对 student 脱敏"
echo "  - IDOR：student A 的 token 取 student B 详情应 403（AccessGuard）"

echo ""
if [[ "$fail" -gt 0 ]]; then
  echo -e "${RED}网关复验未通过：$pass 过 / $fail 败${NC}"
  exit 1
fi
echo -e "${GREEN}网关复验通过：$pass 项全过${NC}"
exit 0
