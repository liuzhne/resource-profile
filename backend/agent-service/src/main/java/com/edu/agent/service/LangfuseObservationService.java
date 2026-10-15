package com.edu.agent.service;

import com.edu.agent.dto.LangfuseObservationPage;
import com.edu.agent.core.AgentFailure;
import com.edu.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads the official v2 observations endpoint, using only server credentials.
 * A page is not a complete Agent call tree, and observation level is not business status.
 */
@Service
public class LangfuseObservationService {
    static final int MAX_BODY_BYTES = 256 * 1024;
    static final int MAX_CURSOR_LENGTH = 4096;
    private static final Set<String> CLOUD_HOSTS =
            Set.of("cloud.langfuse.com", "us.cloud.langfuse.com", "jp.cloud.langfuse.com");
    private static final Set<String> OPERATIONS =
            Set.of("llm.chat", "llm.risk", "llm.plan", "llm.audit", "llm.generation", "agent.loop");
    private static final Set<String> TYPES = Set.of("GENERATION", "SPAN", "EVENT", "AGENT", "TOOL", "CHAIN",
            "RETRIEVER", "EVALUATOR", "EMBEDDING", "GUARDRAIL");
    private static final Set<String> LEVELS = Set.of("DEBUG", "DEFAULT", "WARNING", "ERROR");
    private static final Set<String> AGENT_STATUSES = Set.of("COMPLETED", "MAX_ITERATIONS", "TOOL_ERROR", "LLM_ERROR",
            "PARSE_ERROR", "VALIDATION_ERROR", "DEADLINE_EXCEEDED");

    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient client;
    private final String publicKey;
    private final String secretKey;
    private final String host;
    private final boolean enabled;
    private final int readTimeoutMs;
    private final URI fixtureEndpoint;

    @Autowired
    public LangfuseObservationService(
            ObjectMapper mapper,
            @Value("${langfuse.public-key:}") String publicKey,
            @Value("${langfuse.secret-key:}") String secretKey,
            @Value("${langfuse.host:}") String host,
            @Value("${langfuse.enabled:true}") boolean enabled,
            @Value("${langfuse.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${langfuse.read-timeout-ms:5000}") int readTimeoutMs) {
        this(mapper, Clock.systemUTC(), HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(boundedTimeout(connectTimeoutMs, 2000)))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                publicKey, secretKey, host, enabled, boundedTimeout(readTimeoutMs, 5000), null);
    }

    // Package-local loopback fixture injection. Production construction always validates the Cloud host.
    LangfuseObservationService(ObjectMapper mapper, Clock clock, HttpClient client, String publicKey,
                               String secretKey, String host, boolean enabled, int readTimeoutMs, URI fixtureEndpoint) {
        this.mapper = mapper;
        this.clock = clock;
        this.client = client;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.host = host;
        this.enabled = enabled;
        this.readTimeoutMs = readTimeoutMs;
        this.fixtureEndpoint = fixtureEndpoint;
    }

    public LangfuseObservationPage observations(String from, String to, String limit, String cursor) {
        return observations(from, to, limit, cursor, null);
    }

    public LangfuseObservationPage observations(String from, String to, String limit, String cursor, String traceId) {
        Query query = validateQuery(from, to, limit, cursor);
        if (traceId != null && (traceId.isBlank() || safeId(mapper.getNodeFactory().textNode(traceId)) == null))
            throw new BusinessException(400, "追踪标识无效");
        URI endpoint = configuredEndpoint();
        StringBuilder url = new StringBuilder(endpoint.toString())
                .append("?fields=core%2Cbasic%2Cmodel%2Cusage%2Cmetrics%2Cmetadata")
                .append("&fromStartTime=").append(encode(query.from().toString()))
                .append("&toStartTime=").append(encode(query.to().toString()))
                .append("&limit=").append(query.limit());
        if (query.cursor() != null) url.append("&cursor=").append(encode(query.cursor()));
        if (traceId != null) url.append("&traceId=").append(encode(traceId));
        String basic = Base64.getEncoder().encodeToString(
                (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Authorization", "Basic " + basic)
                .header("Accept", "application/json")
                .GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            pending = client.sendAsync(request, info -> {
                if (info.statusCode() != 200) return new BoundedBodySubscriber(0);
                long length = info.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (length > MAX_BODY_BYTES) throw malformed();
                return new BoundedBodySubscriber(MAX_BODY_BYTES);
            });
            // Bound the entire exchange, including a slow/trickling response body.
            HttpResponse<byte[]> response = pending.get(readTimeoutMs, TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status == 429) throw new BusinessException(429, "追踪服务请求频率受限，请稍后重试");
            if (status == 408 || status == 504) throw timeout();
            if (status == 401 || status == 403) throw new BusinessException(502, "追踪服务凭证或读取权限配置有误");
            if (status != 200) throw new BusinessException(502, "追踪服务暂时无法读取");
            LangfuseObservationPage page = project(response.body(), query);
            if (traceId != null && page.records().stream().anyMatch(row -> !traceId.equals(row.traceId())))
                throw malformed();
            return page;
        } catch (BusinessException e) {
            throw e;
        } catch (TimeoutException e) {
            throw timeout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw timeout();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof java.net.http.HttpTimeoutException) throw timeout();
                if (cause instanceof BusinessException business) throw business;
                cause = cause.getCause();
            }
            throw new BusinessException(502, "追踪服务暂时无法读取");
        } catch (Exception e) {
            // Do not attach causes or log exception text: it can contain credentials, URL or response body.
            throw malformed();
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private URI configuredEndpoint() {
        if (!enabled || blank(publicKey) || blank(secretKey) || blank(host))
            throw new BusinessException(503, "追踪服务尚未配置或已关闭");
        if (!publicKey.matches("[A-Za-z0-9_-]{1,256}") || !secretKey.matches("[A-Za-z0-9_-]{1,1024}"))
            throw new BusinessException(502, "追踪服务配置有误");
        if (fixtureEndpoint != null) return fixtureEndpoint;
        try {
            URI uri = URI.create(host);
            String hostname = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !CLOUD_HOSTS.contains(hostname)
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw new IllegalArgumentException();
            return URI.create("https://" + hostname + "/api/public/v2/observations");
        } catch (Exception e) {
            throw new BusinessException(502, "追踪服务地址配置有误");
        }
    }

    private Query validateQuery(String from, String to, String limit, String cursor) {
        try {
            if ((from != null && from.length() > 64) || (to != null && to.length() > 64)
                    || (cursor != null && cursor.length() > MAX_CURSOR_LENGTH)) throw new IllegalArgumentException();
            Instant end = blank(to) ? clock.instant() : parseBoundedInstant(to);
            Instant start = blank(from) ? end.minus(Duration.ofHours(24)) : parseBoundedInstant(from);
            if (!start.isBefore(end) || Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0)
                throw new IllegalArgumentException();
            int size = limit == null ? 20 : (limit.matches("[0-9]{1,2}") ? Integer.parseInt(limit) : -1);
            if (size < 1 || size > 50) throw new IllegalArgumentException();
            String next = cursor == null || cursor.isEmpty() ? null : cursor;
            if (next != null && !validCursor(next)) throw new IllegalArgumentException();
            return new Query(start, end, size, next);
        } catch (Exception e) {
            throw new BusinessException(400, "查询参数无效：时间范围须在31天内，每页1至50条");
        }
    }

    private LangfuseObservationPage project(byte[] bytes, Query query) throws Exception {
        if (bytes.length == 0) throw malformed();
        JsonNode root;
        try (JsonParser parser = mapper.getFactory().createParser(bytes)) {
            root = mapper.readTree(parser);
            if (parser.nextToken() != null) throw malformed();
        }
        if (root == null || !root.isObject() || !root.path("data").isArray()
                || !root.path("meta").isObject() || root.path("data").size() > query.limit()) throw malformed();
        JsonNode cursorNode = root.path("meta").path("cursor");
        String nextCursor = null;
        if (!cursorNode.isMissingNode() && !cursorNode.isNull()) {
            if (!cursorNode.isTextual() || !validCursor(cursorNode.textValue())
                    || containsCredential(cursorNode.textValue())) throw malformed();
            nextCursor = cursorNode.textValue();
            if (nextCursor.equals(query.cursor())) throw malformed();
        }
        List<LangfuseObservationPage.Observation> records = new ArrayList<>();
        for (JsonNode row : root.path("data")) {
            if (!row.isObject()) throw malformed();
            String id = safeId(row.path("id"));
            String startTime = safeTime(row.path("startTime"));
            if (id == null || startTime == null) throw malformed();
            String rawName = text(row.path("name"));
            String name = rawName != null && OPERATIONS.contains(rawName) ? rawName : "观测记录";
            String model = safeModel(row.path("model"));
            JsonNode metadata = row.path("metadata");
            JsonNode usage = row.path("usageDetails");
            Long input = token(usage.path("input"));
            Long output = token(usage.path("output"));
            Long total = token(usage.path("total"));
            // AgentLoop currently exports an unknown model with placeholder zero usage, not measured tokens.
            if ("agent.loop".equals(name) && model == null && zeroOrNull(input) && zeroOrNull(output) && zeroOrNull(total)) {
                input = null; output = null; total = null;
            }
            records.add(new LangfuseObservationPage.Observation(id, safeId(row.path("traceId")), name,
                    known(row.path("type"), TYPES), startTime, safeTime(row.path("endTime")), model,
                    input, output, total, latencyMs(row.path("latency")), known(row.path("level"), LEVELS),
                    "agent.loop".equals(name) ? known(metadata.path("status"), AGENT_STATUSES) : null,
                    "agent.loop".equals(name) ? boundedInteger(metadata.path("iterations"), 0, 1000) : null,
                    known(metadata.path("errorCode"), AgentFailure.CODES), known(metadata.path("errorStage"), AgentFailure.STAGES),
                    boundedInteger(metadata.path("httpStatus"), 400, 599)));
        }
        return new LangfuseObservationPage(List.copyOf(records), nextCursor, nextCursor != null,
                query.from().toString(), query.to().toString());
    }

    private String safeId(JsonNode value) {
        String text = text(value);
        return text != null && !containsCredential(text) && text.matches("[A-Za-z0-9_-]{1,128}") ? text : null;
    }

    private String safeModel(JsonNode value) {
        String text = text(value);
        return text != null && !containsCredential(text)
                && !text.startsWith("sk-lf-") && !text.startsWith("pk-lf-")
                && !Set.of("unknown", "none", "null", "n/a").contains(text.toLowerCase(Locale.ROOT))
                && text.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,127}") ? text : null;
    }

    private boolean containsCredential(String value) {
        return (!blank(publicKey) && value.contains(publicKey)) || (!blank(secretKey) && value.contains(secretKey));
    }

    private static String safeTime(JsonNode value) {
        String text = text(value);
        try { return text == null ? null : parseBoundedInstant(text).toString(); }
        catch (Exception e) { return null; }
    }

    private static Instant parseBoundedInstant(String value) {
        if (value.length() > 64) throw new IllegalArgumentException();
        Instant instant = Instant.parse(value);
        if (instant.isBefore(Instant.parse("1970-01-01T00:00:00Z"))
                || instant.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) throw new IllegalArgumentException();
        return instant;
    }

    private static Long token(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null;
    }

    private static Integer boundedInteger(JsonNode value, int minimum, int maximum) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= minimum
                && value.intValue() <= maximum ? value.intValue() : null;
    }

    private static Double latencyMs(JsonNode value) {
        if (!value.isNumber()) return null;
        double milliseconds = value.doubleValue() * 1000;
        return Double.isFinite(milliseconds) && milliseconds >= 0 ? milliseconds : null;
    }

    private static String known(JsonNode value, Set<String> known) {
        String text = text(value);
        return text != null && known.contains(text) ? text : null;
    }
    private static String text(JsonNode value) { return value.isTextual() ? value.textValue() : null; }
    private static boolean zeroOrNull(Long value) { return value == null || value == 0; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean validCursor(String value) {
        return value.length() <= MAX_CURSOR_LENGTH && value.matches("[A-Za-z0-9_+/-]+={0,2}");
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static int boundedTimeout(int configured, int fallback) { return configured <= 0 ? fallback : Math.min(configured, 15_000); }
    private static BusinessException malformed() { return new BusinessException(502, "追踪服务返回的数据不可用"); }
    private static BusinessException timeout() { return new BusinessException(504, "追踪服务读取超时，请稍后重试"); }
    private record Query(Instant from, Instant to, int limit, String cursor) { }

    /** Bounds bytes before buffering; error responses are cancelled without reading their free text. */
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final int maximum;
        private Flow.Subscription subscription;

        private BoundedBodySubscriber(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (maximum == 0) { subscription.cancel(); result.complete(new byte[0]); }
            else subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                if (remaining > maximum - output.size()) {
                    subscription.cancel(); result.completeExceptionally(malformed()); return;
                }
                byte[] part = new byte[remaining]; buffer.get(part); output.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(output.toByteArray()); }
    }
}
