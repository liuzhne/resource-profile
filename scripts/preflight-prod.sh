#!/usr/bin/env bash
# ============================================================
# 生产起栈前密钥体检（fail-fast）
#   用法：scripts/preflight-prod.sh            # 读 docker/.env
#        ENV_FILE=/path/to/.env scripts/preflight-prod.sh
# 退出码：0=全部通过可起栈；1=存在未覆盖/弱密钥，禁止上生产。
#
# 检查项：关键密钥是否仍为 dev 默认 / 留空 / 强度不足。
# 与 docker-compose.yml 的 ${VAR:-devdefault} 默认值一一对应。
# 兼容 bash 3.2（macOS 自带），故不用关联数组。
# ============================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/docker/.env}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
fail_count=0
warn_count=0

err()  { echo -e "${RED}✗ $1${NC}"; fail_count=$((fail_count+1)); }
warn() { echo -e "${YELLOW}! $1${NC}"; warn_count=$((warn_count+1)); }
ok()   { echo -e "${GREEN}✓ $1${NC}"; }

# 与 docker-compose.yml 内联默认一致的「dev 默认值」黑名单（空=该变量无黑名单默认）
dev_default() {
  case "$1" in
    MYSQL_ROOT_PASSWORD)        echo "root" ;;
    MYSQL_PASSWORD)             echo "edu123456" ;;
    NACOS_PASSWORD)             echo "nacos" ;;
    NACOS_AUTH_TOKEN)           echo "SecretKey012345678901234567890123456789012345678901234567890123456789" ;;
    NACOS_AUTH_IDENTITY_VALUE)  echo "security" ;;
    MINIO_ACCESS_KEY)           echo "minioadmin" ;;
    MINIO_SECRET_KEY)           echo "minioadmin" ;;
    JWT_SECRET)                 echo "edu-portrait-dev-jwt-secret-change-in-prod-0123456789" ;;
    *)                          echo "" ;;
  esac
}

# 间接取值（bash 3.2 安全）：未设置返回空串
getval() { eval "printf '%s' \"\${$1:-}\""; }

if [[ ! -f "$ENV_FILE" ]]; then
  echo -e "${RED}未找到 $ENV_FILE${NC}"
  echo "  请先：cp docker/.env.example docker/.env 并填入强随机值。"
  exit 1
fi

# 载入 env（操作者自有文件，source 可接受）
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "=== 体检 $ENV_FILE ==="

# 必须覆盖（留空 / 等于 dev 默认 / 仍含 change-me 即 fail）
REQUIRED="MYSQL_ROOT_PASSWORD MYSQL_PASSWORD NACOS_PASSWORD NACOS_AUTH_TOKEN MINIO_ACCESS_KEY MINIO_SECRET_KEY JWT_SECRET REDIS_PASSWORD EDUCARE_MCP_TOKEN"

for var in $REQUIRED; do
  val="$(getval "$var")"
  ddef="$(dev_default "$var")"
  if [[ -z "$val" ]]; then
    err "$var 未设置或为空"
  elif [[ -n "$ddef" && "$val" == "$ddef" ]]; then
    err "$var 仍为 dev 默认值，生产必须覆盖"
  elif [[ "$val" == *change-me* ]]; then
    err "$var 仍是模板占位（含 change-me）"
  else
    ok "$var 已覆盖"
  fi
done

# JWT_SECRET 强度：HS256 要求 ≥ 32 字节（与 JwtUtil fail-fast 对齐）
jwt="$(getval JWT_SECRET)"
if [[ -n "$jwt" && "$jwt" != *change-me* ]]; then
  len=${#jwt}
  if (( len < 32 )); then
    err "JWT_SECRET 长度 $len < 32，HS256 启动会 fail-fast"
  else
    ok "JWT_SECRET 长度 $len ≥ 32"
  fi
fi

# NACOS_AUTH_TOKEN 须 Base64 解码后 ≥ 32 字节
tok="$(getval NACOS_AUTH_TOKEN)"
if [[ -n "$tok" && "$tok" != *change-me* && "$tok" != "$(dev_default NACOS_AUTH_TOKEN)" ]]; then
  decoded_len=$(printf '%s' "$tok" | base64 -d 2>/dev/null | wc -c | tr -d ' ')
  if [[ -z "$decoded_len" || "$decoded_len" -lt 32 ]]; then
    err "NACOS_AUTH_TOKEN 非合法 Base64 或解码后 < 32 字节（openssl rand -base64 48）"
  else
    ok "NACOS_AUTH_TOKEN Base64 解码 $decoded_len 字节 ≥ 32"
  fi
fi

# 内部服务凭据强度：均为生产硬门。
mcp="$(getval EDUCARE_MCP_TOKEN)"
if [[ -n "$mcp" && "$mcp" != *change-me* ]]; then
  mcp_len=${#mcp}
  if (( mcp_len < 32 )); then
    err "EDUCARE_MCP_TOKEN 长度 $mcp_len < 32"
  else
    ok "EDUCARE_MCP_TOKEN 长度 $mcp_len ≥ 32"
  fi
fi

redis_password="$(getval REDIS_PASSWORD)"
if [[ -n "$redis_password" && "$redis_password" != *change-me* ]]; then
  redis_len=${#redis_password}
  if (( redis_len < 32 )); then
    err "REDIS_PASSWORD 长度 $redis_len < 32"
  else
    ok "REDIS_PASSWORD 长度 $redis_len ≥ 32"
  fi
fi

echo ""
echo "=== 提醒：SQL 默认账号 ==="
echo "  sql/init/01_init.sql 种子 admin/teacher/student（密码=用户名）。"
echo "  全新生产库首次初始化后，务必跑 sql/prod/01_rotate_default_passwords.sql 改密。"

echo ""
if (( fail_count > 0 )); then
  echo -e "${RED}体检未通过：$fail_count 项 fail，$warn_count 项 warn —— 禁止上生产。${NC}"
  exit 1
fi
if (( warn_count > 0 )); then
  echo -e "${YELLOW}体检通过（含 $warn_count 项建议）。${NC}"
else
  echo -e "${GREEN}体检全部通过，可起栈。${NC}"
fi
exit 0
