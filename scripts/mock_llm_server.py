#!/usr/bin/env python3
"""最小桩 LLM（OpenAI 兼容）—— 替代 llama.cpp，用于无 GPU 环境真跑 AgentLoop HTTP 链路。

监听 :8091，对 POST /v1/chat/completions 返回一个 ReAct `final_answer` 轮次（双 JSON schema），
使 AgentLoop 一轮即收口、无需 MCP 工具，从而验证：
  Spring 启动 → 控制器 → ChatClient/拦截器链 → HTTP → 解析 final_answer → 落库 → COMPLETED。

不是真实推理，只为打通"活的 HTTP 端到端"代码路径（活模型质量验证见 E2E_RUNBOOK）。
用法：python3 scripts/mock_llm_server.py [port]
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FINAL_INNER = json.dumps({
    # 用 low：doExecuteAgentLoop 低/无风险短路直接 COMPLETED（risk/plan 仍落库），
    # 避开合规审核阶段对 Python ai-inference 的 Feign 调用，使最小真跑无需起 Python。
    "risk_analysis": {
        "risk_level": "low", "risk_score": 35, "primary_risk_type": "学业滑坡",
        "root_cause_analysis": "单科成绩波动，出勤略有下滑，暂未形成趋势",
        "key_indicators": ["GPA:2.6", "单科预警:高数 II"],
        "recommended_intervention_types": ["学业辅导"],
        "urgency_reason": "潜在风险，纳入常规关注",
    },
    "intervention_plan": {
        "report_title": "学业帮扶方案", "summary": "一对一补考辅导 + 学业规划",
        "immediate_actions": [{"action": "安排补考辅导", "owner": "学业导师", "deadline": "2周内"}],
        "long_term_plan": [], "talk_outline": "先共情再帮扶", "resources": [], "references": [],
    },
}, ensure_ascii=False)

REACT_TURN = json.dumps({"thought": "数据已足够，直接给出结论", "final_answer": FINAL_INNER}, ensure_ascii=False)

# legacy 风险识别端点期望的"普通 risk JSON"（非 ReAct 包裹）。低风险使 legacy 短路 COMPLETED，免 Python。
RISK_JSON = json.dumps({
    "risk_level": "low", "risk_score": 35, "primary_risk_type": "学业滑坡",
    "root_cause_analysis": "单科成绩波动，暂未成趋势", "key_indicators": ["GPA:2.6"],
    "recommended_intervention_types": ["学业辅导"], "urgency_reason": "潜在风险，常规关注",
}, ensure_ascii=False)


# 全局计数：让首个 react 请求返回"调用 get_student_profile"的 action（真正驱动一次 MCP 工具调用），
# 之后返回 final_answer。用于真实栈演示 ReAct 经 MCP 调真工具。设 MOCK_LLM_CALL_TOOL=0 可关。
_react_calls = {"n": 0}
_TOOL_TURN = json.dumps(
    {"thought": "先取该生画像", "action": {"tool": "get_student_profile", "args": {"studentId": 1}}},
    ensure_ascii=False)
_CALL_TOOL_FIRST = os.getenv("MOCK_LLM_CALL_TOOL", "1") == "1"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _embeddings(self, payload):
        """确定性桩向量：同文本同向量（sha256 播种），维度对齐 EMBEDDING_DIM（默认 1024）。
        仅供无 GPU 环境打通 RAG upsert/search 的机械链路；语义检索质量需真 embedding 模型。"""
        import hashlib
        import math
        dim = int(os.getenv("MOCK_EMBEDDING_DIM", "1024"))
        data = []
        for i, text in enumerate(payload.get("input", [])):
            seed = hashlib.sha256(str(text).encode("utf-8")).digest()
            vec = []
            for j in range(dim):
                h = hashlib.sha256(seed + j.to_bytes(4, "little")).digest()
                # 映射到 [-1,1) 并粗归一，避免全零/爆值
                v = (int.from_bytes(h[:4], "little") / 0xFFFFFFFF) * 2 - 1
                vec.append(round(v / math.sqrt(dim), 8))
            data.append({"object": "embedding", "index": i, "embedding": vec})
        sys.stderr.write(f"[mock-llm] embeddings x{len(data)} dim={dim}\n")
        self._send({"object": "list", "data": data, "model": os.getenv("EMBEDDING_MODEL", "mock-bge"),
                    "usage": {"prompt_tokens": sum(len(str(t)) for t in payload.get("input", [])),
                              "total_tokens": 0}})

    def do_GET(self):
        if self.path.endswith("/models"):
            self._send({"object": "list", "data": [{"id": "mock-qwen", "object": "model"}]})
        else:
            self._send({"status": "ok"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)  # 含 messages / system prompt
        if self.path.endswith("/embeddings"):
            try:
                self._embeddings(json.loads(body or b"{}"))
            except Exception as e:
                self._send({"error": {"message": str(e)}}, code=400)
            return
        # 双模：agent-loop 的 system prompt 含 "final_answer" → 返回 ReAct；否则是 legacy 风险识别 → 返回普通 risk JSON
        is_react = b"final_answer" in body
        # 诊断：把 system prompt 里出现的工具名 dump 出来（composeSystemPrompt 写 "- name: <tool>"）
        try:
            import re as _re
            names = _re.findall(r"- name: ([\w\-]+)", body.decode("utf-8", "ignore"))
            if names:
                sys.stderr.write("[mock-llm] 可用工具名: " + ", ".join(dict.fromkeys(names)) + "\n")
        except Exception:
            pass
        # 按是否已有 Observation 判断轮次：iter1（无 Observation）→ 调 get_student_profile 驱动真实 MCP；
        # iter2（已有 Observation）→ 给 final_answer。每个任务都会真调一次工具，不受并发干扰。
        has_observation = b"Observation:" in body
        if is_react and _CALL_TOOL_FIRST and not has_observation:
            content = _TOOL_TURN
            sys.stderr.write("[mock-llm] react iter1 -> 调 get_student_profile (driving real MCP)\n")
        elif is_react:
            content = REACT_TURN
            sys.stderr.write("[mock-llm] react iter2 -> final_answer\n")
        else:
            content = RISK_JSON
            sys.stderr.write("[mock-llm] mode=risk\n")
        self._send({
            "id": "mock-cmpl", "object": "chat.completion", "created": 0, "model": "mock-qwen",
            "choices": [{"index": 0, "message": {"role": "assistant", "content": content},
                         "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 50, "completion_tokens": 120, "total_tokens": 170},
            "timings": {"prompt_n": 50, "predicted_n": 120, "cached_n": 0, "prompt_ms": 12.0},
        })


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8091
    print(f"[mock-llm] listening on :{port}  (POST /v1/chat/completions -> ReAct final_answer)")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
