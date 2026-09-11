package com.edu.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G-5.3：Langfuse self-hosted v2 ingestion 客户端。
 *
 * <p>直接 POST 到 `${langfuse.host}/api/public/ingestion`，绕过 unofficial Java SDK。
 * 所有调用走 `@Async("agentExecutor")` 火-忘，不阻塞 LLM 主流程；任何失败只 debug 日志，
 * 不影响业务。
 *
 * <p>三个配置任一为空 → 所有 trace 调用静默 no-op（fail-soft 与 G-5.2 Python 侧一致）。
 *
 * <p>事件格式（v2 ingestion）：单批两条 —— 一条 trace-create + 一条 generation-create。
 */
@Slf4j
@Component
public class LangfuseClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${langfuse.public-key:}")
    private String publicKey;

    @Value("${langfuse.secret-key:}")
    private String secretKey;

    @Value("${langfuse.host:}")
    private String host;

    private RestClient restClient;
    private boolean enabled;

    @PostConstruct
    void init() {
        enabled = !publicKey.isBlank() && !secretKey.isBlank() && !host.isBlank();
        if (!enabled) {
            log.info("Langfuse 未配置（host/public/secret 任一为空），trace 上报关闭");
            return;
        }
        String basic = Base64.getEncoder().encodeToString(
                (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        restClient = RestClient.builder()
                .baseUrl(host.replaceAll("/+$", ""))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("Langfuse trace 已启用 host={}", host);
    }

    /**
     * 异步上报一次 LLM generation。trace 与 generation 共用 traceId / 时间戳。
     * 任何参数为 null 时按合理默认值传 Langfuse。
     */
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
            String traceId = UUID.randomUUID().toString();
            String genId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            String startIso = (startTime == null ? now : startTime).toString();
            String endIso = (endTime == null ? now : endTime).toString();

            Map<String, Object> traceBody = new LinkedHashMap<>();
            traceBody.put("id", traceId);
            traceBody.put("name", name);
            traceBody.put("timestamp", startIso);
            traceBody.put("metadata", metadata == null ? Map.of() : metadata);

            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("input", inputTokens);
            usage.put("output", outputTokens);
            usage.put("unit", "TOKENS");

            Map<String, Object> genBody = new LinkedHashMap<>();
            genBody.put("id", genId);
            genBody.put("traceId", traceId);
            genBody.put("name", name);
            genBody.put("model", model);
            genBody.put("input", input);
            genBody.put("output", output);
            genBody.put("usage", usage);
            genBody.put("metadata", metadata == null ? Map.of() : metadata);
            genBody.put("startTime", startIso);
            genBody.put("endTime", endIso);

            Map<String, Object> traceEvent = Map.of(
                    "id", UUID.randomUUID().toString(),
                    "type", "trace-create",
                    "timestamp", now.toString(),
                    "body", traceBody);
            Map<String, Object> genEvent = Map.of(
                    "id", UUID.randomUUID().toString(),
                    "type", "generation-create",
                    "timestamp", now.toString(),
                    "body", genBody);

            Map<String, Object> payload = Map.of("batch", List.of(traceEvent, genEvent));
            String json = MAPPER.writeValueAsString(payload);

            restClient.post()
                    .uri("/api/public/ingestion")
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("Langfuse trace 上报失败（已忽略）: {}", e.getMessage());
        }
    }
}
