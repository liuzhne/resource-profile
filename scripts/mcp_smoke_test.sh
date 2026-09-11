#!/usr/bin/env bash
# H-1.4：两个 MCP server 的 Streamable HTTP happy-path 冒烟
#   - student-data   (Java,   端口 8094)：4 个 tool
#   - knowledge-rag  (Python, 端口 8095)：3 个 search tool
# 用法：
#   bash scripts/mcp_smoke_test.sh
#   STUDENT_ID=1 STUDENT_DATA_URL=http://localhost:8094 \
#       KNOWLEDGE_RAG_URL=http://localhost:8095 bash scripts/mcp_smoke_test.sh
#
# 依赖：bash >= 4（mac 默认 3.2，可 `brew install bash` 或显式 `/opt/homebrew/bin/bash`）+ curl + jq
set -e

if [[ ${BASH_VERSINFO[0]:-0} -lt 4 ]]; then
    echo "✗ 需要 bash >= 4（mac 默认 3.x）。请 brew install bash，并用 /opt/homebrew/bin/bash $0 重跑"
    exit 1
fi

STUDENT_DATA_URL=${STUDENT_DATA_URL:-http://localhost:8094}
KNOWLEDGE_RAG_URL=${KNOWLEDGE_RAG_URL:-http://localhost:8095}
MCP_PATH=${MCP_PATH:-/mcp}
STUDENT_ID=${STUDENT_ID:-1}
PROTOCOL_VERSION=${PROTOCOL_VERSION:-2025-03-26}

require() { command -v "$1" >/dev/null 2>&1 || { echo "✗ 缺少命令 $1"; exit 1; }; }
require curl
require jq

FAIL=0
declare -A SESSION_ID  # server_name → Mcp-Session-Id

# JSON-RPC 调用；session id 自动注入（若已捕获）。响应 body 写入 $4，响应头写入临时文件后解析 session id。
rpc() {
    local server_name="$1"
    local base_url="$2"
    local payload_file="$3"
    local out_body="$4"
    local sid="${SESSION_ID[$server_name]:-}"
    local hdr_file
    hdr_file=$(mktemp)

    local -a args=(
        -sS -X POST
        -H 'Content-Type: application/json'
        -H 'Accept: application/json, text/event-stream'
        -D "$hdr_file"
        -o "$out_body"
        --data-binary "@$payload_file"
    )
    if [[ -n "$sid" ]]; then
        args+=( -H "Mcp-Session-Id: $sid" )
    fi

    curl "${args[@]}" "$base_url$MCP_PATH" || true

    # Streamable HTTP 服务器可能以 SSE 帧回包（id:...\nevent:message\ndata:{...}）。
    # 归一化成纯 JSON 再落盘，jq 才能解析；单请求单帧场景取全部 data: 行拼接即完整 JSON。
    if grep -q '^data:' "$out_body" 2>/dev/null; then
        local norm
        norm=$(mktemp)
        grep '^data:' "$out_body" | sed 's/^data:[[:space:]]*//' > "$norm"
        [[ -s "$norm" ]] && mv "$norm" "$out_body" || rm -f "$norm"
    fi

    # 抽取 Mcp-Session-Id（首次 initialize 才会回）
    local new_sid
    new_sid=$(grep -i '^Mcp-Session-Id:' "$hdr_file" 2>/dev/null | tail -n1 | awk '{print $2}' | tr -d '\r')
    if [[ -n "$new_sid" && -z "$sid" ]]; then
        SESSION_ID[$server_name]="$new_sid"
    fi
    rm -f "$hdr_file"
}

# 握手：initialize + notifications/initialized
handshake() {
    local name="$1"
    local url="$2"
    echo "  ▸ $name 握手..."
    local req body
    req=$(mktemp); body=$(mktemp)
    cat > "$req" <<JSON
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$PROTOCOL_VERSION","capabilities":{},"clientInfo":{"name":"mcp-smoke","version":"0.1"}}}
JSON
    rpc "$name" "$url" "$req" "$body"
    if ! jq -e '.result.serverInfo' "$body" >/dev/null 2>&1; then
        echo "  ✗ $name initialize 失败：$(head -c 200 "$body")"
        FAIL=1
        rm -f "$req" "$body"
        return 1
    fi
    local server_info
    server_info=$(jq -c '.result.serverInfo' "$body")
    echo "  ✓ $name initialize 通过 session=${SESSION_ID[$name]:-<none>} serverInfo=$server_info"

    cat > "$req" <<'JSON'
{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
JSON
    rpc "$name" "$url" "$req" "$body"   # 通知无返回体，rpc 内已 || true
    rm -f "$req" "$body"
}

# tools/list 校验：响应必须包含给定 tool 名
list_tools() {
    local name="$1"
    local url="$2"
    shift 2
    local -a expected=( "$@" )
    local req body
    req=$(mktemp); body=$(mktemp)
    cat > "$req" <<'JSON'
{"jsonrpc":"2.0","id":10,"method":"tools/list"}
JSON
    rpc "$name" "$url" "$req" "$body"
    local got
    got=$(jq -r '.result.tools[].name' "$body" 2>/dev/null | sort | tr '\n' ' ')
    if [[ -z "$got" ]]; then
        echo "  ✗ $name tools/list 失败或返回空：$(head -c 200 "$body")"
        FAIL=1
        rm -f "$req" "$body"
        return 1
    fi
    echo "  ▸ $name tools/list → $got"
    local t
    for t in "${expected[@]}"; do
        if ! echo " $got " | grep -q " $t "; then
            echo "  ✗ $name 缺少 tool: $t"
            FAIL=1
        fi
    done
    rm -f "$req" "$body"
}

# tools/call：调一个 tool，校验无 error 字段
call_tool() {
    local name="$1"
    local url="$2"
    local tool="$3"
    local args_json="$4"
    local req body
    req=$(mktemp); body=$(mktemp)
    cat > "$req" <<JSON
{"jsonrpc":"2.0","id":100,"method":"tools/call","params":{"name":"$tool","arguments":$args_json}}
JSON
    rpc "$name" "$url" "$req" "$body"

    if jq -e '.error' "$body" >/dev/null 2>&1; then
        echo "  ✗ $tool 报错：$(jq -c '.error' "$body")"
        FAIL=1
    else
        local text
        text=$(jq -r '.result.content[0].text // empty' "$body" 2>/dev/null)
        if [[ -z "$text" ]]; then
            echo "  ⚠ $tool 返回 content 空（server 可能未起 / 下游服务降级）"
        else
            local preview="${text:0:120}"
            local suffix=""
            if [[ ${#text} -gt 120 ]]; then suffix="..."; fi
            echo "  ✓ $tool → ${preview}${suffix}"
        fi
    fi
    rm -f "$req" "$body"
}

echo "============================================================"
echo " H-1.4：MCP server 双端 happy-path 冒烟"
echo "  student-data : $STUDENT_DATA_URL$MCP_PATH"
echo "  knowledge-rag: $KNOWLEDGE_RAG_URL$MCP_PATH"
echo "  student_id   : $STUDENT_ID  (可用 STUDENT_ID=<n> 覆盖)"
echo "  protocol     : $PROTOCOL_VERSION"
echo "============================================================"

# ---- [1/2] student-data ----
echo
echo "[1/2] student-data (端口 8094, Java)"
if handshake "student-data" "$STUDENT_DATA_URL"; then
    list_tools "student-data" "$STUDENT_DATA_URL" \
        get_student_profile get_academic_history get_mental_indicators get_attendance
    call_tool "student-data" "$STUDENT_DATA_URL" get_student_profile   "{\"studentId\":$STUDENT_ID}"
    call_tool "student-data" "$STUDENT_DATA_URL" get_academic_history  "{\"studentId\":$STUDENT_ID}"
    call_tool "student-data" "$STUDENT_DATA_URL" get_mental_indicators "{\"studentId\":$STUDENT_ID}"
    call_tool "student-data" "$STUDENT_DATA_URL" get_attendance        "{\"studentId\":$STUDENT_ID}"
fi

# ---- [2/2] knowledge-rag ----
echo
echo "[2/2] knowledge-rag (端口 8095, Python FastMCP)"
if handshake "knowledge-rag" "$KNOWLEDGE_RAG_URL"; then
    list_tools "knowledge-rag" "$KNOWLEDGE_RAG_URL" \
        search_cases search_policies search_psychology
    call_tool "knowledge-rag" "$KNOWLEDGE_RAG_URL" search_cases      '{"query":"高一学生上学期挂科 3 门","top_k":3}'
    call_tool "knowledge-rag" "$KNOWLEDGE_RAG_URL" search_policies   '{"query":"学籍异动处理流程","top_k":3}'
    call_tool "knowledge-rag" "$KNOWLEDGE_RAG_URL" search_psychology '{"query":"青少年抑郁倾向识别","top_k":3}'
fi

echo
echo "============================================================"
if [[ $FAIL -eq 0 ]]; then
    echo "✓ H-1.4 smoke 全部通过"
    exit 0
else
    echo "✗ H-1.4 smoke 有失败项，见上文 ✗ 标记"
    exit 1
fi
