#!/usr/bin/env bash
# ============================================================
# MySQL 数据恢复：从 backup-mysql.sh 产出的 .sql.gz 恢复 edu_portrait。
# 危险操作——会覆盖现有库数据，需显式确认（或 --yes）。
#
# 用法：
#   scripts/restore-mysql.sh backups/edu_portrait-20260625-023000.sql.gz
#   scripts/restore-mysql.sh --yes backups/xxx.sql.gz        # 跳过确认（CI/自动化）
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/docker/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a; # shellcheck disable=SC1090
  source "$ENV_FILE"; set +a
fi

CONTAINER="${MYSQL_CONTAINER:-edu-portrait-mysql}"
ROOT_PW="${MYSQL_ROOT_PASSWORD:-root}"

assume_yes=0
dump=""
for arg in "$@"; do
  case "$arg" in
    --yes|-y) assume_yes=1 ;;
    *) dump="$arg" ;;
  esac
done

if [[ -z "$dump" ]]; then
  echo "用法：$0 [--yes] <backup.sql.gz>" >&2
  exit 1
fi
if [[ ! -f "$dump" ]]; then
  echo "找不到备份文件：$dump" >&2
  exit 1
fi

if [[ "$assume_yes" -ne 1 ]]; then
  echo "将把 $dump 恢复进容器 $CONTAINER —— 这会覆盖现有库数据。"
  read -r -p "确认继续？输入 yes：" ans
  [[ "$ans" == "yes" ]] || { echo "已取消"; exit 1; }
fi

echo "[restore] $(date '+%F %T') restoring $dump -> $CONTAINER"
gzip -dc "$dump" | docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot
echo "[restore] 完成"
