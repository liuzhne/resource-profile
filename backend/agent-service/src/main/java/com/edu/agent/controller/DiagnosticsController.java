package com.edu.agent.controller;

import com.edu.common.result.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * G-1.6 Java：诊断端点，对称 Python 侧 `/api/v1/diagnostics/llm-metrics`。
 *
 * <p>Prometheus 端有 `/actuator/prometheus` 全量 exposition；这个 JSON 端点
 * 是给开发/运维做"快速人眼快照"用的（不依赖 Prom UI）。
 *
 * <p>关键字段：
 * <ul>
 *   <li>{@code cache_hit_rate} —— cached_tokens / prompt_tokens，0.0–1.0</li>
 *   <li>{@code prompt_tokens / cached_tokens / completion_tokens} —— Counter 累计</li>
 *   <li>{@code calls} —— Counter 累计</li>
 *   <li>{@code prefill_ms_sum / prefill_ms_count} —— Timer 累计 + 次数</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/diagnostics")
@RequiredArgsConstructor
public class DiagnosticsController {

    private final MeterRegistry meterRegistry;

    @GetMapping("/llm-metrics")
    public Result<Map<String, Object>> llmMetrics() {
        double promptTokens = counterValue("educare.llm.prompt_tokens");
        double cachedTokens = counterValue("educare.llm.cached_tokens");
        double completionTokens = counterValue("educare.llm.completion_tokens");
        double calls = counterValue("educare.llm.calls");

        Timer prefill = meterRegistry.find("educare.llm.prefill").timer();
        long prefillCount = prefill == null ? 0L : prefill.count();
        double prefillSumMs = prefill == null ? 0.0 : prefill.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("calls", (long) calls);
        snapshot.put("prompt_tokens", (long) promptTokens);
        snapshot.put("cached_tokens", (long) cachedTokens);
        snapshot.put("completion_tokens", (long) completionTokens);
        snapshot.put("cache_hit_rate", promptTokens > 0 ? cachedTokens / promptTokens : 0.0);
        snapshot.put("prefill_ms_count", prefillCount);
        snapshot.put("prefill_ms_sum", prefillSumMs);
        return Result.success(snapshot);
    }

    private double counterValue(String name) {
        Counter c = meterRegistry.find(name).counter();
        return c == null ? 0.0 : c.count();
    }
}
