#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VALID_ENV="$(mktemp /tmp/educare-preflight.XXXXXX)"
DEV_DEFAULT_ENV="$(mktemp /tmp/educare-preflight.XXXXXX)"
trap 'rm -f "$VALID_ENV" "$DEV_DEFAULT_ENV"' EXIT

if ENV_FILE="$ROOT/docker/.env.example" "$ROOT/scripts/preflight-prod.sh" >/dev/null 2>&1; then
  echo "expected docker/.env.example to fail preflight" >&2
  exit 1
fi

cat >"$VALID_ENV" <<'EOF'
MYSQL_ROOT_PASSWORD=strong-mysql-root-password-000001
MYSQL_PASSWORD=strong-mysql-user-password-000001
NACOS_PASSWORD=strong-nacos-password-00000000001
NACOS_AUTH_TOKEN=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFB
MINIO_ACCESS_KEY=strong-minio-access-000000000001
MINIO_SECRET_KEY=strong-minio-secret-000000000001
JWT_SECRET=strong-jwt-secret-at-least-32-bytes-0001
REDIS_PASSWORD=strong-redis-password-at-least-32-0001
EDUCARE_MCP_TOKEN=strong-mcp-token-at-least-32-bytes-0001
EDUCARE_INTERNAL_TOKEN=strong-internal-token-at-least-32-bytes-01
EOF

ENV_FILE="$VALID_ENV" "$ROOT/scripts/preflight-prod.sh" >/dev/null

# 内部调用凭证仍是 compose 开发默认值 → 必须 fail
sed 's/^EDUCARE_INTERNAL_TOKEN=.*/EDUCARE_INTERNAL_TOKEN=edu-portrait-dev-internal-token-change-in-prod-0123456789/' \
  "$VALID_ENV" >"$DEV_DEFAULT_ENV"
if ENV_FILE="$DEV_DEFAULT_ENV" "$ROOT/scripts/preflight-prod.sh" >/dev/null 2>&1; then
  echo "expected dev-default EDUCARE_INTERNAL_TOKEN to fail preflight" >&2
  exit 1
fi

echo "preflight-prod regression: PASS"
