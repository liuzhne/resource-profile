#!/usr/bin/env bash
# ============================================================
# MySQL 数据备份：mysqldump edu_portrait（容器内）→ gzip + 时间戳 + 保留 N 天。
# cron 友好，例（每天 02:30）：
#   30 2 * * * /path/to/Resource-Profile/scripts/backup-mysql.sh >> /var/log/edu-backup.log 2>&1
#
# 环境变量（默认读 docker/.env）：
#   MYSQL_CONTAINER   默认 edu-portrait-mysql
#   MYSQL_DATABASE    默认 edu_portrait
#   MYSQL_ROOT_PASSWORD  root 密码（备份用）
#   BACKUP_DIR        默认 <repo>/backups
#   RETAIN_DAYS       保留天数，默认 7
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/docker/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a; # shellcheck disable=SC1090
  source "$ENV_FILE"; set +a
fi

CONTAINER="${MYSQL_CONTAINER:-edu-portrait-mysql}"
DB="${MYSQL_DATABASE:-edu_portrait}"
ROOT_PW="${MYSQL_ROOT_PASSWORD:-root}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
RETAIN_DAYS="${RETAIN_DAYS:-7}"

mkdir -p "$BACKUP_DIR"
ts="$(date +%Y%m%d-%H%M%S)"
out="$BACKUP_DIR/${DB}-${ts}.sql.gz"

echo "[backup] $(date '+%F %T') dump $DB from $CONTAINER -> $out"
# --single-transaction：InnoDB 一致性快照，不锁表；MYSQL_PWD 避免密码出现在 ps
docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
  mysqldump -uroot --single-transaction --quick --routines --triggers --databases "$DB" \
  | gzip > "$out"

# 产物非空校验（dump 失败时 gzip 会产出空/极小文件）
if [[ ! -s "$out" ]] || [[ "$(gzip -dc "$out" | head -c 16 | wc -c)" -lt 1 ]]; then
  echo "[backup] 失败：产物为空，删除并退出" >&2
  rm -f "$out"
  exit 1
fi
echo "[backup] 完成，大小 $(du -h "$out" | cut -f1)"

# 保留策略
deleted="$(find "$BACKUP_DIR" -name "${DB}-*.sql.gz" -mtime +"$RETAIN_DAYS" -print -delete | wc -l | tr -d ' ')"
echo "[backup] 已清理 >${RETAIN_DAYS} 天旧备份：$deleted 个"
