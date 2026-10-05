#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VALID_ENV="$(mktemp /tmp/educare-preflight.XXXXXX)"
TRACKED_REPO="$(mktemp -d /tmp/educare-preflight-repo.XXXXXX)"
trap 'rm -rf "$VALID_ENV" "$TRACKED_REPO"' EXIT

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

# 正例失败时回显体检输出：若仓库又跟踪了 docker/.env.*（如旧分支合并带回），会在这里暴露
if ! out="$(ENV_FILE="$VALID_ENV" "$ROOT/scripts/preflight-prod.sh" 2>&1)"; then
  echo "$out" >&2
  echo "expected strong env to pass preflight" >&2
  exit 1
fi

# 同样的强密钥，只要 env 文件被 git 跟踪就必须 fail（ENV-AUDIT-LEAK-20260914）
git -C "$TRACKED_REPO" init -q
cp "$VALID_ENV" "$TRACKED_REPO/prod.env"
git -C "$TRACKED_REPO" add prod.env
if ENV_FILE="$TRACKED_REPO/prod.env" "$ROOT/scripts/preflight-prod.sh" >/dev/null 2>&1; then
  echo "expected git-tracked env file to fail preflight" >&2
  exit 1
fi

echo "preflight-prod regression: PASS"
