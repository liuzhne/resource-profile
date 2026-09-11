package com.edu.agent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * G-1.5 Java + G-3.3：拦截 llama.cpp /chat/completions 响应，
 * 从 root.timings 抽 prompt_n / cached_n / predicted_n / prompt_ms，喂 Micrometer。
 *
 * <p>响应体可重读依赖 {@link org.springframework.http.client.BufferingClientHttpRequestFactory}
 * 已在 {@link SpringAiConfig#openAiApi} 启用。
 *
 * <p>{@code cache_hit_rate} 是衍生量（cached_tokens / prompt_tokens），不直接写 gauge，
 * 在 Prometheus 端用 PromQL `sum(rate(educare_llm_cached_tokens_total[5m])) /
 * sum(rate(educare_llm_prompt_tokens_total[5m]))` 计算，避免 push 时的 race。
 *
 * <p>当前不打 route 标签（Spring AI 在 RestClient 层看不到调用方业务路由）；
 * 未来若 agent-service 出现多业务调用点，可通过 ThreadLocal `LlmRouteContext` 注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmMetricsInterceptor implements ClientHttpRequestInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MeterRegistry meterRegistry;
    private final LangfuseClient langfuseClient;

    private Counter promptTokens;
    private Counter cachedTokens;
    private Counter completionTokens;
    private Timer prefillTimer;
    private Counter callCounter;

    @PostConstruct
    void initMeters() {
        promptTokens = Counter.builder("educare.llm.prompt_tokens")
                .description("Total prompt tokens sent to LLM (llama.cpp timings.prompt_n)")
                .register(meterRegistry);
        cachedTokens = Counter.builder("educare.llm.cached_tokens")
                .description("Prompt tokens served from llama.cpp slot prefix cache (timings.cached_n)")
                .register(meterRegistry);
        completionTokens = Counter.builder("educare.llm.completion_tokens")
                .description("Total completion tokens generated (timings.predicted_n)")
                .register(meterRegistry);
        prefillTimer = Timer.builder("educare.llm.prefill")
                .description("Prefill duration reported by llama.cpp (timings.prompt_ms)")
                .register(meterRegistry);
        callCounter = Counter.builder("educare.llm.calls")
                .description("Total LLM chat/completions calls (response observed)")
                .register(meterRegistry);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Instant start = Instant.now();
        ClientHttpResponse response = execution.execute(request, body);
        String path = request.getURI().getPath();
        if (path == null || !path.contains("/chat/completions")) {
            return response;
        }
        try {
            // BufferingClientHttpResponseWrapper 缓存了 body，再次 getBody() 返回新的 ByteArrayInputStream
            byte[] bytes = StreamUtils.copyToByteArray(response.getBody());
            JsonNode root = MAPPER.readTree(bytes);
            JsonNode timings = root.path("timings");
            JsonNode usage = root.path("usage");

            long promptN = timings.path("prompt_n").asLong(usage.path("prompt_tokens").asLong(0));
            long cachedN = timings.path("cached_n").asLong(0);
            long predictedN = timings.path("predicted_n").asLong(usage.path("completion_tokens").asLong(0));
            double prefillMs = timings.path("prompt_ms").asDouble(0.0);

            if (promptN > 0) promptTokens.increment(promptN);
            if (cachedN > 0) cachedTokens.increment(cachedN);
            if (predictedN > 0) completionTokens.increment(predictedN);
            if (prefillMs > 0) prefillTimer.record((long) prefillMs, TimeUnit.MILLISECONDS);
            callCounter.increment();

            double hitRate = promptN > 0 ? (double) cachedN / promptN : 0.0;
            log.debug("llm.metric prompt_tokens={} cached_tokens={} hit_rate={} prefill_ms={}",
                    promptN, cachedN, String.format("%.1f%%", hitRate * 100), prefillMs);

            // G-5.3：异步上报 Langfuse trace（client 内部判 enabled，无配置时直接 return）
            JsonNode reqRoot = MAPPER.readTree(body);
            String model = reqRoot.path("model").asText("unknown");
            JsonNode messages = reqRoot.path("messages");
            String output = root.path("choices").path(0).path("message").path("content").asText("");
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("cached_n", cachedN);
            metadata.put("prefill_ms", prefillMs);
            metadata.put("cache_hit_rate", hitRate);
            langfuseClient.traceGeneration(
                    "llm.chat",
                    model,
                    messages,
                    output,
                    promptN,
                    predictedN,
                    metadata,
                    start,
                    Instant.now()
            );
        } catch (Exception e) {
            log.debug("解析 llama.cpp timings 失败（指标未记录）: {}", e.getMessage());
        }
        return response;
    }
}
