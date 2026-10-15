package com.edu.agent.config;

import com.edu.agent.core.AgentDeadline;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.List;

/** Single-instance chat admission and HTTP retry policy. Does not occupy HTTP/MCP executors. */
@Component
@Slf4j
public class LlmRequestPolicy implements ClientHttpRequestInterceptor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final boolean enabled;
    private final Semaphore admission;
    private final Semaphore active = new Semaphore(1, true);
    private final long maxWaitMs, minIntervalMs, maxRetryAfterMs;
    private final int maxAttempts, maxOutputTokens, maxInputCharacters;
    private volatile long nextRequestAt;
    @Autowired(required = false)
    private StringRedisTemplate redis;
    @Value("${educare.llm.request-policy.shared-cooldown-enabled:true}")
    private boolean sharedCooldownEnabled = true;
    // Redis TIME keeps Java/Python clocks consistent. This key contains no credentials or user identifiers.
    private static final String COOLDOWN_KEY = "edu:llm:provider-cooldown";
    private static final DefaultRedisScript<Long> CLAIM = new DefaultRedisScript<>(
            "local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000); "
                    + "local at=tonumber(redis.call('GET',KEYS[1]) or '0'); "
                    + "if at>now then return at-now end; "
                    + "local delay=tonumber(ARGV[1]); if delay>0 then redis.call('PSETEX',KEYS[1],delay,now+delay) end; return 0", Long.class);
    private static final DefaultRedisScript<Long> DEFER = new DefaultRedisScript<>(
            "local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000); "
                    + "local at=math.max(tonumber(redis.call('GET',KEYS[1]) or '0'),now+tonumber(ARGV[1])); "
                    + "if at>now then redis.call('PSETEX',KEYS[1],at-now,at) end; return 0", Long.class);

    public LlmRequestPolicy(
            @Value("${educare.llm.request-policy.enabled:false}") boolean enabled,
            @Value("${educare.llm.request-policy.queue-capacity:2}") int queueCapacity,
            @Value("${educare.llm.request-policy.max-wait-ms:180000}") long maxWaitMs,
            @Value("${educare.llm.request-policy.min-interval-ms:60000}") long minIntervalMs,
            @Value("${educare.llm.request-policy.max-attempts:3}") int maxAttempts,
            @Value("${educare.llm.request-policy.max-retry-after-ms:120000}") long maxRetryAfterMs,
            @Value("${educare.llm.request-policy.max-output-tokens:2400}") int maxOutputTokens,
            @Value("${educare.llm.request-policy.max-input-characters:18000}") int maxInputCharacters) {
        if (queueCapacity < 0 || queueCapacity > 20 || maxWaitMs < 1 || maxWaitMs > 300000
                || minIntervalMs < 0 || minIntervalMs > 120000 || maxAttempts < 1 || maxAttempts > 3
                || maxRetryAfterMs < 0 || maxRetryAfterMs > 180000 || maxOutputTokens < 1
                || maxOutputTokens > 8192 || maxInputCharacters < 1 || maxInputCharacters > 64000) {
            throw new IllegalArgumentException("Invalid bounded LLM request policy");
        }
        this.enabled = enabled;
        this.admission = new Semaphore(queueCapacity + 1, true);
        this.maxWaitMs = maxWaitMs;
        this.minIntervalMs = minIntervalMs;
        this.maxAttempts = maxAttempts;
        this.maxRetryAfterMs = maxRetryAfterMs;
        this.maxOutputTokens = maxOutputTokens;
        this.maxInputCharacters = maxInputCharacters;
    }

    public boolean enabled() { return enabled; }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!enabled || !request.getURI().getPath().endsWith("/chat/completions")) {
            return execution.execute(request, body);
        }
        var payload = JSON.readTree(body);
        int outputTokens = payload.path("max_completion_tokens").asInt(payload.path("max_tokens").asInt(0));
        if (outputTokens < 1 || outputTokens > maxOutputTokens) {
            throw new LlmCallException("OUTPUT_BUDGET", null);
        }
        if (payload.path("messages").toString().length() > maxInputCharacters) {
            throw new LlmCallException("INPUT_BUDGET", null);
        }
        if (!admission.tryAcquire()) throw new LlmCallException("QUEUE_FULL", null);
        boolean acquired = false;
        long deadline = System.currentTimeMillis() + Math.min(maxWaitMs, AgentDeadline.remainingMillis());
        try {
            acquired = active.tryAcquire(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            if (!acquired) throw new LlmCallException("DEADLINE_EXCEEDED", null);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                await(nextRequestAt - System.currentTimeMillis(), deadline);
                awaitSharedSlot(deadline);
                if (System.currentTimeMillis() >= deadline) throw new LlmCallException("DEADLINE_EXCEEDED", null);
                nextRequestAt = System.currentTimeMillis() + minIntervalMs;
                logRequestShape(payload, outputTokens);
                ClientHttpResponse response = execution.execute(request, body);
                if (response.getStatusCode().value() != 429) return response;
                long delay = retryAfterMillis(response.getHeaders().getFirst("Retry-After"), Instant.now(),
                        1000L << (attempt - 1));
                deferSharedSlot(delay);
                if (attempt == maxAttempts || delay > maxRetryAfterMs || delay >= deadline - System.currentTimeMillis()) return response;
                response.close();
                nextRequestAt = Math.max(nextRequestAt, System.currentTimeMillis() + delay);
            }
            throw new IllegalStateException("Unreachable retry state");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("DEADLINE_EXCEEDED", null);
        } finally {
            if (acquired) active.release();
            admission.release();
        }
    }

    /** Only fixed protocol shape at the transport handoff; never log request contents or schema. */
    private static void logRequestShape(JsonNode payload, int outputTokens) {
        JsonNode format = payload.path("response_format");
        String type = switch (format.path("type").asText("")) {
            case "text" -> "TEXT";
            case "json_object" -> "JSON_OBJECT";
            case "json_schema" -> "JSON_SCHEMA";
            default -> "UNKNOWN";
        };
        JsonNode strict = format.path("json_schema").path("strict");
        String strictFlag = strict.isBoolean() ? (strict.booleanValue() ? "TRUE" : "FALSE") : "ABSENT";
        boolean knownSchema = "educare_react_turn".equals(format.path("json_schema").path("name").asText(""));
        boolean tools = payload.has("tools") && !payload.path("tools").isNull();
        boolean stream = payload.path("stream").isBoolean() && payload.path("stream").booleanValue();
        log.info("LLM_REQUEST_CONTRACT response_format={} strict={} react_schema={} tools={} stream={} output_tokens={}",
                type, strictFlag, knownSchema, tools, stream, outputTokens);
    }

    private void awaitSharedSlot(long deadline) throws InterruptedException {
        if (!sharedCooldownEnabled) return;
        if (redis == null) throw new LlmCallException("COOLDOWN_UNAVAILABLE", null);
        try {
            while (true) {
                Long delay = redis.execute(CLAIM, List.of(COOLDOWN_KEY), String.valueOf(minIntervalMs));
                if (delay == null) throw new IllegalStateException();
                if (delay == 0) return;
                await(delay, deadline);
            }
        } catch (InterruptedException e) { throw e; }
        catch (LlmCallException e) { throw e; }
        catch (RuntimeException e) { throw new LlmCallException("COOLDOWN_UNAVAILABLE", null); }
    }

    private void deferSharedSlot(long delay) {
        if (!sharedCooldownEnabled || delay <= 0) return;
        try {
            if (redis == null || redis.execute(DEFER, List.of(COOLDOWN_KEY), String.valueOf(delay)) == null) {
                throw new IllegalStateException();
            }
        } catch (LlmCallException e) { throw e; }
        catch (RuntimeException e) { throw new LlmCallException("COOLDOWN_UNAVAILABLE", null); }
    }

    private static void await(long delay, long deadline) throws InterruptedException {
        if (delay <= 0) return;
        if (delay >= deadline - System.currentTimeMillis()) {
            throw new LlmCallException("DEADLINE_EXCEEDED", null);
        }
        Thread.sleep(delay);
    }

    static long retryAfterMillis(String header, Instant now, long fallback) {
        if (header == null || header.isBlank()) return fallback;
        try {
            double seconds = Double.parseDouble(header);
            if (Double.isFinite(seconds) && seconds >= 0) return (long) Math.ceil(seconds * 1000);
        } catch (NumberFormatException ignored) { }
        try {
            return Math.max(0, ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                    - now.toEpochMilli());
        } catch (RuntimeException ignored) { return fallback; }
    }
}
