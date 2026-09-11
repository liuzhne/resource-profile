#!/usr/bin/env bash
# 第 5 件：agent 路径 vs legacy 4 阶段路径的等级一致性对比（合格性证据）。
#
# 对同一组学生（eval/agent_vs_legacy_cohort.jsonl，含 ground truth expected_level），
# 分别在 legacy（EDUCARE_AGENT_LOOP_ENABLED=false）与 agent（=true, canary=100）两种模式下
# 触发 agent-service 真实任务、轮询到终态、读回 riskLevel，然后比对：
#   - legacy 对 ground truth 的等级一致率
#   - agent  对 ground truth 的等级一致率
#   - agent vs legacy 的一致率
# 退出码：agent 一致率 ≥ legacy → 0（不回退，合格）；否则 1。
#
# 依赖：curl / jq；需要 agent-service + 两个 MCP server + 宿主 llama.cpp + 已灌库在线（见 E2E_RUNBOOK.md）。
# 模式切换经 Nacos agent-canary.yml（@RefreshScope 热生效）。
set -u

GATEWAY=${GATEWAY:-http://localhost:8080}
NACOS_ADDR=${NACOS_ADDR:-localhost:8848}
NACOS_USER=${NACOS_USER:-nacos}
NACOS_PASS=${NACOS_PASS:-nacos}
NACOS_GROUP=${NACOS_GROUP:-DEFAULT_GROUP}
NACOS_DATA_ID=${NACOS_DATA_ID:-agent-canary.yml}
INPUT=${INPUT:-eval/agent_vs_legacy_cohort.jsonl}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-180}
INTERVAL=${INTERVAL:-3}
REFRESH_WAIT=${REFRESH_WAIT:-6}
IDEM_WAIT=${IDEM_WAIT:-35}   # 跨模式间隔，避开 trigger 幂等窗口（默认 30s）

require() { command -v "$1" >/dev/null 2>&1 || { echo "✗ 缺少命令: $1"; exit 1; }; }
require curl
require jq

NACOS_TOKEN=""
nacos_login() {
  local r; r=$(curl -s -X POST "http://$NACOS_ADDR/nacos/v1/auth/login" \
    --data-urlencode "username=$NACOS_USER" --data-urlencode "password=$NACOS_PASS" 2>/dev/null)
  NACOS_TOKEN=$(echo "$r" | jq -r '.accessToken // empty' 2>/dev/null)
}
set_mode() {  # $1=enabled $2=percent
  local content; content=$(printf 'educare:\n  agent:\n    loop:\n      enabled: %s\n      canary-percent: %s\n' "$1" "$2")
  local args=(-s -X POST "http://$NACOS_ADDR/nacos/v1/cs/configs"
    --data-urlencode "dataId=$NACOS_DATA_ID" --data-urlencode "group=$NACOS_GROUP"
    --data-urlencode "type=yaml" --data-urlencode "content=$content")
  [[ -n "$NACOS_TOKEN" ]] && args+=(--data-urlencode "accessToken=$NACOS_TOKEN")
  local out; out=$(curl "${args[@]}")
  [[ "$out" == "true" ]] && echo "  ✓ 模式 enabled=$1 percent=$2" || { echo "  ✗ Nacos 推送失败: $out"; return 1; }
}

# 触发一个学生任务并轮询到终态，回显 riskLevel（小写）
run_one() {
  local sid=$1 resp tid status elapsed=0
  resp=$(curl -s -X POST "$GATEWAY/agent/api/v1/task/trigger/$sid")
  tid=$(echo "$resp" | jq -r '.data // empty')
  [[ -z "$tid" || "$tid" == "null" ]] && { echo "-"; return; }
  while [[ $elapsed -lt $TIMEOUT_SECONDS ]]; do
    status=$(curl -s "$GATEWAY/agent/api/v1/task/$tid" | jq -r '.data.status // empty')
    case "$status" in
      COMPLETED|REJECTED|FAILED)
        curl -s "$GATEWAY/agent/api/v1/task/$tid" | jq -r '(.data.riskLevel // "-") | ascii_downcase'
        return ;;
    esac
    sleep "$INTERVAL"; elapsed=$((elapsed+INTERVAL))
  done
  echo "timeout"
}

mapfile -t COHORT < <(grep -v '^[[:space:]]*$' "$INPUT")
echo "载入 ${#COHORT[@]} 个学生，gateway=$GATEWAY"
nacos_login

declare -a SIDS EXP LEG AGT
for line in "${COHORT[@]}"; do
  SIDS+=("$(echo "$line" | jq -r '.student_id')")
  EXP+=("$(echo "$line" | jq -r '.expected_level | ascii_downcase')")
done

echo; echo ">> 模式 1：legacy（enabled=false）"
set_mode false 0 || exit 1
sleep "$REFRESH_WAIT"
for i in "${!SIDS[@]}"; do
  r=$(run_one "${SIDS[$i]}"); LEG+=("$r")
  printf "  legacy student=%s → %s (expected %s)\n" "${SIDS[$i]}" "$r" "${EXP[$i]}"
done

echo; echo ">> 等待 ${IDEM_WAIT}s 避开 trigger 幂等窗口..."; sleep "$IDEM_WAIT"

echo; echo ">> 模式 2：agent（enabled=true, canary=100）"
set_mode true 100 || exit 1
sleep "$REFRESH_WAIT"
for i in "${!SIDS[@]}"; do
  r=$(run_one "${SIDS[$i]}"); AGT+=("$r")
  printf "  agent  student=%s → %s (expected %s)\n" "${SIDS[$i]}" "$r" "${EXP[$i]}"
done

# 回滚到 legacy
set_mode false 0 >/dev/null

# 统计
leg_hit=0; agt_hit=0; agree=0; n=${#SIDS[@]}
for i in "${!SIDS[@]}"; do
  [[ "${LEG[$i]}" == "${EXP[$i]}" ]] && leg_hit=$((leg_hit+1))
  [[ "${AGT[$i]}" == "${EXP[$i]}" ]] && agt_hit=$((agt_hit+1))
  [[ "${LEG[$i]}" == "${AGT[$i]}" ]] && agree=$((agree+1))
done

echo; echo "============================================================"
printf "对 ground truth 等级一致率：legacy %d/%d   agent %d/%d\n" "$leg_hit" "$n" "$agt_hit" "$n"
printf "agent vs legacy 一致率：%d/%d\n" "$agree" "$n"
echo "============================================================"
if [[ $agt_hit -ge $leg_hit ]]; then
  echo "✓ agent 路径不比 legacy 差（合格）"; exit 0
else
  echo "✗ agent 路径相比 legacy 回退，需排查 prompt / 模型 / 工具"; exit 1
fi
