#!/usr/bin/env bash
# G-1.6 验收脚本：连续两次相同 /api/v1/agent/risk 调用，对比第二次的
# 增量 cached_n / prompt_n 是否 ≥ THRESHOLD（默认 0.8）。
#
# 依赖：
#   - ai-inference-service 已启动 (默认 :8090)
#   - 宿主 llama.cpp 已启动且开启了 prompt slot cache（默认行为）
#   - jq, curl
#
# 用法：
#   bash scripts/verify_prompt_cache.sh
#   AI_INFERENCE=http://localhost:8090 THRESHOLD=0.8 bash scripts/verify_prompt_cache.sh
set -e

AI_INFERENCE=${AI_INFERENCE:-http://localhost:8090}
THRESHOLD=${THRESHOLD:-0.8}
ROUTE=${ROUTE:-risk}

require() { command -v "$1" >/dev/null || { echo "✗ 缺少命令: $1"; exit 1; }; }
require curl
require jq

echo "============================================================"
echo " G-1.6 验收：llama.cpp slot cache 命中率"
echo "  目标：route=$ROUTE 第二次调用 cached_tokens/prompt_tokens ≥ $THRESHOLD"
echo "  服务：$AI_INFERENCE"
echo "============================================================"

snap_route() {
    curl -s "$AI_INFERENCE/api/v1/diagnostics/llm-metrics" | \
        jq --arg r "$ROUTE" '
            .by_route[$r] // {calls: 0, prompt_tokens: 0, cached_tokens: 0}
        '
}

PAYLOAD=$(cat <<'JSON'
{
  "student_profile": {
    "studentId": "S00001",
    "studentName": "测试同学",
    "grade": "大三",
    "major": "软件工程",
    "gpaDetail": "GPA: 2.1 / 4.0；近 2 学期下滑约 0.6",
    "failedCourses": ["数据结构", "操作系统"],
    "attendanceRate": 0.72,
    "mentalHealthLevel": "medium",
    "familyEconomicLevel": "中等"
  }
}
JSON
)

echo
echo "[0/3] 基线 snapshot..."
BEFORE=$(snap_route)
echo "$BEFORE" | jq .

echo
echo "[1/3] 第一次调用 /api/v1/agent/risk..."
curl -s -X POST -H 'Content-Type: application/json' -d "$PAYLOAD" \
    "$AI_INFERENCE/api/v1/agent/$ROUTE" > /dev/null
AFTER1=$(snap_route)
echo "$AFTER1" | jq .

echo
echo "[2/3] 第二次调用（相同 payload，期望命中 prefix cache）..."
curl -s -X POST -H 'Content-Type: application/json' -d "$PAYLOAD" \
    "$AI_INFERENCE/api/v1/agent/$ROUTE" > /dev/null
AFTER2=$(snap_route)
echo "$AFTER2" | jq .

echo
echo "[3/3] 计算第二次调用的增量命中率..."
PROMPT_DELTA=$(jq -n --argjson a "$AFTER2" --argjson b "$AFTER1" \
    '$a.prompt_tokens - $b.prompt_tokens')
CACHED_DELTA=$(jq -n --argjson a "$AFTER2" --argjson b "$AFTER1" \
    '$a.cached_tokens - $b.cached_tokens')

if [[ "$PROMPT_DELTA" == "0" || -z "$PROMPT_DELTA" ]]; then
    echo "✗ 第二次调用的 prompt_tokens 增量为 0；检查 ai-inference 是否真的有调到 LLM"
    exit 1
fi

RATIO=$(python3 -c "print($CACHED_DELTA / $PROMPT_DELTA)")
echo "  PROMPT_DELTA=$PROMPT_DELTA  CACHED_DELTA=$CACHED_DELTA  RATIO=$RATIO"

PASS=$(python3 -c "print(1 if $RATIO >= $THRESHOLD else 0)")
if [[ "$PASS" == "1" ]]; then
    echo
    echo "✓ 验收通过：第二次调用命中率 $RATIO ≥ $THRESHOLD"
    exit 0
fi

echo
echo "✗ 验收失败：第二次调用命中率 $RATIO < $THRESHOLD"
echo "  排查："
echo "  1) llama.cpp 启动是否带 --slots（必需）+ 默认 slot 数 ≥1"
echo "  2) Python 侧请求体是否含 cache_prompt:true（查 ai-inference 日志的 chat_completion_raw 入口）"
echo "  3) Prompt 字节是否稳定（system prompt 文件第二次没有改动；user 同一 payload 序列化一致）"
exit 1
