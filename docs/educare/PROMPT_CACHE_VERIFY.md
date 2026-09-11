# Prompt Cache 验收手册（G-1.6）

> **覆盖范围**：当前只验证 Python 侧（`ai-inference-service`）的 llama.cpp slot prefix cache 命中率。Java 侧的 `Diagnostics` 端点 + Micrometer 接入待 G-1.5 Java 部分完成后补充。
>
> **依赖前置**：G-1.4 已透传 `cache_prompt:true`；G-1.5 Python 部分已在 `chat_completion_raw` 内调 `record_llm_response`。

---

## 1. 启动要求

宿主侧 llama.cpp 启动 **必须**：
- 带 `--slots` 启用 slot 管理（多数版本默认开启）
- prompt cache 不要被 `--no-cache-prompt` 关掉（默认 true）

服务侧：
```bash
# ai-inference-service（端口 8090）
cd ai-inference-service && uvicorn app.main:app --host 0.0.0.0 --port 8090
```

---

## 2. 一键验收

```bash
bash scripts/verify_prompt_cache.sh
# 自定义阈值与端点：
AI_INFERENCE=http://localhost:8090 THRESHOLD=0.8 ROUTE=risk \
  bash scripts/verify_prompt_cache.sh
```

脚本逻辑：
1. 读 `/api/v1/diagnostics/llm-metrics` 取 `by_route.risk` 基线
2. 用固定 payload 调 `/api/v1/agent/risk` 一次 → 再快照
3. 同 payload 再调一次 → 再快照
4. 算第二次调用的 **增量** `cached_tokens / prompt_tokens`
5. ≥ `THRESHOLD`（默认 0.8）即通过

---

## 3. 手动排查

```bash
# 1) 看累计快照
curl -s http://localhost:8090/api/v1/diagnostics/llm-metrics | jq

# 2) 看 ai-inference 日志中 llm.metric 行（每次调用打印 hit_rate）
docker logs ai-inference-service 2>&1 | grep llm.metric
```

期望日志格式：
```
llm.metric route=risk prompt_tokens=1234 cached_tokens=1110 hit_rate=90.0% prefill_ms=85.0
```

---

## 4. 命中率不达标常见原因

| 现象 | 原因 | 处理 |
|------|------|------|
| `cached_tokens=0` 全程 | llama.cpp 启动未开 slot cache | 加 `--slots`；查 llama-server 日志确认 |
| 命中率随机波动 | prompt 字节不稳定（YAML 转义、时间戳混入 system） | 检查 `app/prompts/*.system.md` 是否被改；user 序列化的 dict 顺序 |
| 第一次本就高命中 | 之前已有过同 prefix 的调用 | 正常 —— 脚本看 **增量**，不看绝对值 |
| `prompt_tokens` delta=0 | LLM 调用根本没发起（被前置 fallback 拦截） | 看 ai-inference 日志是否走到 `chat_completion_raw` |

---

## 5. 后续接入

- **Phase G-3.3**：把 `llm_metrics.snapshot()` 的数值接 Micrometer → Prometheus（gauge: `educare_llm_cache_hit_rate`，按 `route` 标签维度）
- **Phase G-5.2**：Langfuse `@observe` 包 `chat_completion_raw`，trace 里直接看每次调用的 cached_n
- **Java G-1.5 / G-1.6**：补 Spring AI RestClient response interceptor，把 llama.cpp `timings` 接到同一格式的指标，并在 `agent-service` 暴露 `/agent/api/v1/diagnostics/llm-metrics`
