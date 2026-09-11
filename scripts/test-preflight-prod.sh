#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VALID_ENV="$(mktemp /tmp/educare-preflight.XXXXXX)"
trap 'rm -f "$VALID_ENV"' EXIT

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
EOF

ENV_FILE="$VALID_ENV" "$ROOT/scripts/preflight-prod.sh" >/dev/null
echo "preflight-prod regression: PASS"
