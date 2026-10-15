package com.edu.agent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Langfuse v4 OTLP HTTP/JSON exporter; one completed root generation span per call.
 *
 * <p>The existing call sites do not share trace context, so this client does not invent
 * parent/child relationships between AgentLoop, Java LLM and Python operations.
 * Export runs on {@code agentExecutor}; missing configuration and transport failures
 * never affect the business result. Completed spans are exported once, without retries.
 *
 * <p>Content capture is opt-in. Metadata always uses an allowlist of operational
 * metrics/status: prompts, student identifiers and reasoning summaries are not metadata.
 */
@Slf4j
@Component
public class LangfuseClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> NUMERIC_METADATA =
            Set.of("iterations", "cached_n", "prefill_ms", "cache_hit_rate");
    private static final Set<String> SAFE_STATUSES =
            Set.of("COMPLETED", "MAX_ITERATIONS", "TOOL_ERROR", "LLM_ERROR", "PARSE_ERROR", "VALIDATION_ERROR", "DEADLINE_EXCEEDED");

    @Value("${langfuse.public-key:}")
    private String publicKey = "";
    @Value("${langfuse.secret-key:}")
    private String secretKey = "";
    @Value("${langfuse.host:}")
    private String host = "";
    @Value("${langfuse.enabled:true}")
    private boolean reportingEnabled = true;
    @Value("${langfuse.capture-content:false}")
    private boolean captureContent;
    @Value("${langfuse.connect-timeout-ms:2000}")
    private int connectTimeoutMs = 2000;
    @Value("${langfuse.read-timeout-ms:5000}")
    private int readTimeoutMs = 5000;

    private RestClient restClient;
    private boolean enabled;

    @PostConstruct
    void init() {
        enabled = false;
        if (!reportingEnabled || publicKey.isBlank() || secretKey.isBlank() || host.isBlank()) {
            log.info("Langfuse trace 上报关闭（禁用或配置不完整）");
            return;
        }
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            // Never use the factory's infinite timeout, even if configuration is zero/negative.
            factory.setConnectTimeout(boundedTimeout(connectTimeoutMs, 2000));
            factory.setReadTimeout(boundedTimeout(readTimeoutMs, 5000));
            restClient = RestClient.builder()
                    .requestFactory(factory)
                    .baseUrl(host.replaceAll("/+$", ""))
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader("x-langfuse-ingestion-version", "4")
                    .build();
            enabled = true;
            log.info("Langfuse OTLP trace 已启用（capture-content={}）", captureContent);
        } catch (Exception e) {
            // Exception messages can contain URLs, response content or credentials.
            log.debug("Langfuse 初始化失败（已关闭）: {}", e.getClass().getSimpleName());
        }
    }

    @Async("agentExecutor")
    public void traceGeneration(
            String name,
            String model,
            Object input,
            Object output,
            long inputTokens,
            long outputTokens,
            Map<String, Object> metadata,
            Instant startTime,
            Instant endTime
    ) {
        if (!enabled) return;
        try {
            Instant now = Instant.now();
            Instant start = startTime == null ? now : startTime;
            Instant end = endTime == null ? now : endTime;
            if (end.isBefore(start)) end = start;
            String operation = name == null || name.isBlank() ? "llm.generation" : name;

            List<Map<String, Object>> attributes = new ArrayList<>();
            attributes.add(attribute("langfuse.trace.name", operation));
            attributes.add(attribute("langfuse.observation.type", "generation"));
            if (model != null && !model.isBlank()) {
                attributes.add(attribute("langfuse.observation.model.name", model));
            }
            long in = Math.max(0, inputTokens);
            long out = Math.max(0, outputTokens);
            attributes.add(attribute("langfuse.observation.usage_details",
                    MAPPER.writeValueAsString(Map.of("input", in, "output", out, "total", Math.addExact(in, out)))));
            if (captureContent) {
                if (input != null) attributes.add(attribute("langfuse.observation.input", MAPPER.writeValueAsString(input)));
                if (output != null) attributes.add(attribute("langfuse.observation.output", MAPPER.writeValueAsString(output)));
            }
            appendSafeMetadata(attributes, metadata);

            Map<String, Object> span = new LinkedHashMap<>();
            Object suppliedTraceId = metadata == null ? null : metadata.get("traceId");
            span.put("traceId", suppliedTraceId instanceof String id && id.matches("[0-9a-f]{32}")
                    ? id : UUID.randomUUID().toString().replace("-", ""));
            span.put("spanId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            span.put("name", operation);
            span.put("kind", 1); // SPAN_KIND_INTERNAL, enums use integers in OTLP JSON.
            span.put("startTimeUnixNano", unixNanos(start));
            span.put("endTimeUnixNano", unixNanos(end));
            span.put("attributes", attributes);

            Map<String, Object> payload = Map.of("resourceSpans", List.of(Map.of(
                    "resource", Map.of("attributes", List.of(attribute("service.name", "agent-service"))),
                    "scopeSpans", List.of(Map.of("scope", Map.of("name", "com.edu.agent.langfuse"),
                            "spans", List.of(span))))));
            restClient.post()
                    .uri("/api/public/otel/v1/traces")
                    .body(MAPPER.writeValueAsString(payload))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.debug("Langfuse OTLP HTTP {}（已忽略）", response.getStatusCode().value());
                            return null;
                        }
                        // OTLP partial success is not a retry signal. Never log its free-text message.
                        byte[] body = response.getBody().readNBytes(16 * 1024);
                        if (body.length != 0) {
                            JsonNode result = MAPPER.readTree(body);
                            JsonNode partial = result.path("partialSuccess");
                            if (partial.path("rejectedSpans").asLong() != 0
                                    || !partial.path("errorMessage").asText("").isBlank()) {
                                log.debug("Langfuse OTLP partialSuccess（已忽略，不重试）");
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.debug("Langfuse trace 上报失败（已忽略）: {}", e.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> attribute(String key, String value) {
        return Map.of("key", key, "value", Map.of("stringValue", value));
    }

    private static String unixNanos(Instant instant) {
        return Long.toString(Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano()));
    }

    private static int boundedTimeout(int configured, int fallback) {
        return configured <= 0 ? fallback : Math.min(configured, 30_000);
    }

    private static void appendSafeMetadata(List<Map<String, Object>> attributes, Map<String, Object> metadata)
            throws Exception {
        if (metadata == null) return;
        for (String key : NUMERIC_METADATA) {
            Object value = metadata.get(key);
            if (value instanceof Number number && Double.isFinite(number.doubleValue())
                    && number.doubleValue() >= 0
                    && (!"cache_hit_rate".equals(key) || number.doubleValue() <= 1)) {
                attributes.add(attribute("langfuse.observation.metadata." + key, MAPPER.writeValueAsString(value)));
            }
        }
        for (String key : List.of("errorCode", "errorStage")) {
            Object value = metadata.get(key);
            if (value instanceof String text && (key.equals("errorCode")
                    ? com.edu.agent.core.AgentFailure.CODES.contains(text) : com.edu.agent.core.AgentFailure.STAGES.contains(text))) {
                attributes.add(attribute("langfuse.observation.metadata." + key, MAPPER.writeValueAsString(text)));
            }
        }
        Object httpStatus = metadata.get("httpStatus");
        if (httpStatus instanceof Integer statusCode && statusCode >= 400 && statusCode <= 599) {
            attributes.add(attribute("langfuse.observation.metadata.httpStatus", MAPPER.writeValueAsString(statusCode)));
        }
        Object status = metadata.get("status");
        if (status instanceof String value && SAFE_STATUSES.contains(value)) {
            attributes.add(attribute("langfuse.observation.metadata.status", MAPPER.writeValueAsString(value)));
        }
    }
}
