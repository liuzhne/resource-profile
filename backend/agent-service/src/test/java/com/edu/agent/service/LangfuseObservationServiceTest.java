package com.edu.agent.service;

import com.edu.agent.dto.LangfuseObservationPage;
import com.edu.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic loopback HTTP only. Never loads production credentials or calls a model/Langfuse deployment. */
class LangfuseObservationServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SECRET_MARKER = "sk-fixture-do-not-disclose";
    private static final String PRIVATE_MARKER = "SYNTHETIC_PRIVATE_NOTES";
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final AtomicReference<Response> response = new AtomicReference<>(new Response(200, "{\"data\":[],\"meta\":{}}", 0, false));
    private HttpServer server;
    private ExecutorService executor;
    private URI endpoint;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Authorization")));
                Response reply = response.get();
                if (!reply.trickle() && reply.delayMillis() > 0) Thread.sleep(reply.delayMillis());
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (reply.status() == 302) exchange.getResponseHeaders().add("Location", endpoint.toString() + "?redirected=1");
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(reply.status(), reply.trickle() ? 0 : bytes.length);
                if (reply.trickle()) {
                    exchange.getResponseBody().write(bytes, 0, 1);
                    exchange.getResponseBody().flush();
                    Thread.sleep(reply.delayMillis());
                    exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                } else exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/public/v2/observations");
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    private LangfuseObservationService service(int timeout) {
        return new LangfuseObservationService(MAPPER, CLOCK, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout)).followRedirects(HttpClient.Redirect.NEVER).build(),
                "pk-fixture", SECRET_MARKER, "https://jp.cloud.langfuse.com", true, timeout, endpoint);
    }

    private LangfuseObservationService service() { return service(1500); }
    private Request request() throws Exception {
        Request request = requests.poll(2, TimeUnit.SECONDS); assertNotNull(request); return request;
    }
    private static Map<String, String> query(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            String[] pieces = pair.split("=", 2);
            query.put(URLDecoder.decode(pieces[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pieces[1], StandardCharsets.UTF_8));
        }
        return query;
    }

    private static Map<String, Object> row(String id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("traceId", "trace-123"); row.put("name", "llm.chat");
        row.put("type", "GENERATION"); row.put("startTime", NOW.minusSeconds(5).toString());
        row.put("endTime", NOW.minusSeconds(2).toString()); row.put("model", "openai/gpt-oss-120b");
        row.put("usageDetails", Map.of("input", 12, "output", 8, "total", 20));
        row.put("latency", 2.625); row.put("level", "DEFAULT");
        return row;
    }
    private void page(List<Map<String, Object>> rows, Map<String, Object> meta) throws Exception {
        response.set(new Response(200, MAPPER.writeValueAsString(Map.of("data", rows, "meta", meta)), 0, false));
    }
    private static void errorCode(int code, org.junit.jupiter.api.function.Executable executable) {
        BusinessException error = assertThrows(BusinessException.class, executable);
        assertEquals(code, error.getCode());
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains(SECRET_MARKER));
        assertFalse(error.getMessage().contains(PRIVATE_MARKER));
        assertFalse(error.getMessage().contains("https://"));
    }

    @Test
    void defaultsToBoundedLastDayAndRequestsOnlyOperationalFields() throws Exception {
        LangfuseObservationPage result = service().observations(null, null, null, null);
        Request request = request();
        assertEquals("GET", request.method());
        assertEquals("/api/public/v2/observations", request.path());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(("pk-fixture:" + SECRET_MARKER)
                .getBytes(StandardCharsets.UTF_8)), request.authorization());
        assertEquals(Map.of("fields", "core,basic,model,usage,metrics,metadata", "fromStartTime", "2026-10-07T02:00:00Z",
                "toStartTime", NOW.toString(), "limit", "20"), query(request.query()));
        assertEquals("2026-10-07T02:00:00Z", result.from()); assertEquals(NOW.toString(), result.to());
        assertTrue(result.records().isEmpty()); assertFalse(result.hasMore()); assertNull(result.nextCursor());
        assertNull(requests.poll(50, TimeUnit.MILLISECONDS));
    }

    @Test
    void readsUsageDetailsAndLatencySecondsAndReturnsOpaqueCursorWithoutCorruptingPlus() throws Exception {
        page(List.of(row("obs-1")), Map.of("cursor", "YWJj+/=="));
        LangfuseObservationPage result = service().observations("2026-10-07T03:00:00Z", NOW.toString(), "50", "eHl6+/==");
        assertEquals("eHl6+/==", query(request().query()).get("cursor"));
        assertEquals("YWJj+/==", result.nextCursor()); assertTrue(result.hasMore());
        var first = result.records().getFirst();
        assertEquals("obs-1", first.id()); assertEquals("trace-123", first.traceId());
        assertEquals("llm.chat", first.name()); assertEquals("GENERATION", first.type());
        assertEquals("openai/gpt-oss-120b", first.model());
        assertEquals(12L, first.inputTokens()); assertEquals(8L, first.outputTokens()); assertEquals(20L, first.totalTokens());
        assertEquals(2625d, first.latencyMs()); assertEquals("DEFAULT", first.level());
    }

    @Test
    void projectionExcludesPromptsRepliesMetadataErrorsIdentityAndModelParameters() throws Exception {
        var row = row("obs-safe");
        for (String field : List.of("input", "output", "metadata", "statusMessage", "userId", "sessionId", "modelParameters",
                "projectId", "traceName", "promptName", "environment")) row.put(field, PRIVATE_MARKER);
        row.put("name", PRIVATE_MARKER); row.put("model", "private human notes with spaces " + PRIVATE_MARKER);
        page(List.of(row), Map.of("upstreamNote", PRIVATE_MARKER));
        String serialized = MAPPER.writeValueAsString(service().observations(null, null, null, null));
        request();
        assertFalse(serialized.contains(PRIVATE_MARKER)); assertFalse(serialized.contains(SECRET_MARKER));
        assertFalse(serialized.contains("statusMessage")); assertFalse(serialized.contains("modelParameters"));
        assertTrue(serialized.contains("观测记录")); assertTrue(serialized.contains("\"model\":null"));
    }

    @Test
    void credentialsAccidentallyReflectedInAllowedStringFieldsAreNeverReturned() throws Exception {
        var row = row("obs-safe"); row.put("model", SECRET_MARKER); row.put("traceId", "pk-fixture");
        page(List.of(row), Map.of());
        var result = service().observations(null, null, null, null); request();
        assertNull(result.records().getFirst().model()); assertNull(result.records().getFirst().traceId());
        String json = MAPPER.writeValueAsString(result); assertFalse(json.contains(SECRET_MARKER)); assertFalse(json.contains("pk-fixture"));
        row.put("id", SECRET_MARKER); page(List.of(row), Map.of());
        errorCode(502, () -> service().observations(null, null, null, null)); request();
        page(List.of(), Map.of("cursor", SECRET_MARKER));
        errorCode(502, () -> service().observations(null, null, null, null)); request();
    }

    @Test
    void missingOptionalFieldsRemainNullWhileRealZeroIsPreserved() throws Exception {
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("id", "obs-empty"); missing.put("startTime", NOW.minusSeconds(1).toString());
        var zero = row("obs-zero"); zero.put("usageDetails", Map.of("input", 0, "output", 0, "total", 0)); zero.put("latency", 0);
        page(List.of(missing, zero), Map.of());
        var result = service().observations(null, null, null, null); request();
        var empty = result.records().getFirst();
        assertNull(empty.model()); assertNull(empty.inputTokens()); assertNull(empty.outputTokens()); assertNull(empty.totalTokens());
        assertNull(empty.latencyMs()); assertNull(empty.endTime()); assertNull(empty.level()); assertNull(empty.type());
        assertEquals(0L, result.records().get(1).inputTokens()); assertEquals(0d, result.records().get(1).latencyMs());
    }

    @Test
    void agentLoopArtificialUsageIsUnknownWhileStatusIsExplicit() throws Exception {
        var row = row("obs-loop"); row.put("name", "agent.loop"); row.put("model", "unknown");
        row.put("usageDetails", Map.of("input", 0, "output", 0, "total", 0));
        row.put("metadata", Map.of("status", "COMPLETED"));
        page(List.of(row), Map.of());
        var result = service().observations(null, null, null, null); request(); var first = result.records().getFirst();
        assertNull(first.model()); assertNull(first.inputTokens()); assertNull(first.outputTokens()); assertNull(first.totalTokens());
        assertEquals("DEFAULT", first.level());
        assertEquals("COMPLETED", first.agentStatus());
        assertNull(first.iterations());
    }

    @Test
    void onlyFixedAgentDiagnosticMetadataIsProjectedAndTraceQueryRemainsBounded() throws Exception {
        var row = row("obs-loop"); row.put("name", "agent.loop");
        row.put("metadata", Map.of("status", "LLM_ERROR", "iterations", 1,
                "errorCode", "PROVIDER_JSON_SCHEMA", "errorStage", "LLM_REQUEST", "httpStatus", 400,
                "privateNotes", PRIVATE_MARKER, "studentId", 175, "traces_summary", PRIVATE_MARKER));
        page(List.of(row), Map.of());
        var result = service().observations(null, null, null, null, "trace-123");
        var query = query(request().query());
        assertEquals("trace-123", query.get("traceId"));
        assertNotNull(query.get("fromStartTime")); assertNotNull(query.get("toStartTime"));
        var first = result.records().getFirst();
        assertEquals("LLM_ERROR", first.agentStatus()); assertEquals(1, first.iterations());
        assertEquals("PROVIDER_JSON_SCHEMA", first.errorCode()); assertEquals("LLM_REQUEST", first.errorStage());
        assertEquals(400, first.httpStatus());
        var json = MAPPER.writeValueAsString(result);
        assertFalse(json.contains(PRIVATE_MARKER)); assertFalse(json.contains("studentId"));
        assertFalse(json.contains("traces_summary")); assertFalse(json.contains("metadata"));
    }

    @Test
    void unsafeMetadataAndTraceIdentifiersAreRejectedWithoutFreeTextDisclosure() throws Exception {
        var row = row("obs-loop"); row.put("name", "agent.loop");
        row.put("metadata", Map.of("status", PRIVATE_MARKER, "iterations", 1.5,
                "errorCode", PRIVATE_MARKER, "errorStage", PRIVATE_MARKER, "httpStatus", "400"));
        page(List.of(row), Map.of());
        var first = service().observations(null, null, null, null).records().getFirst(); request();
        assertNull(first.agentStatus()); assertNull(first.iterations()); assertNull(first.errorCode());
        assertNull(first.errorStage()); assertNull(first.httpStatus());
        for (String id : List.of("", "a&fields=input", "<script>", "a".repeat(129), SECRET_MARKER))
            errorCode(400, () -> service().observations(null, null, null, null, id));
        assertNull(requests.poll(25, TimeUnit.MILLISECONDS));
        errorCode(502, () -> service().observations(null, null, null, null, "another-trace")); request();
    }

    @Test
    void invalidOptionalNumbersAndLabelsAreUnknownRatherThanCoerced() throws Exception {
        var row = row("obs-bad-option"); row.put("traceId", "<script>bad</script>"); row.put("level", "SUCCESS");
        row.put("type", "SECRET_TEXT"); row.put("endTime", "private non-date"); row.put("latency", -1);
        row.put("usageDetails", Map.of("input", -1, "output", "123", "total", 12.5));
        page(List.of(row), Map.of());
        var first = service().observations(null, null, null, null).records().getFirst(); request();
        assertNull(first.traceId()); assertNull(first.type()); assertNull(first.level()); assertNull(first.endTime());
        assertNull(first.latencyMs()); assertNull(first.inputTokens()); assertNull(first.outputTokens()); assertNull(first.totalTokens());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("not-a-date", null, null, null), Arguments.of("x".repeat(65), null, null, null),
                Arguments.of("2026-10-08T02:00:00Z", "2026-10-08T02:00:00Z", null, null),
                Arguments.of("2026-10-08T02:00:00Z", "2026-10-07T02:00:00Z", null, null),
                Arguments.of("2026-09-06T02:00:00Z", "2026-10-08T02:00:00Z", null, null),
                Arguments.of(null, null, "0", null), Arguments.of(null, null, "51", null),
                Arguments.of(null, null, "20.0", null), Arguments.of(null, null, "SECRET_TEXT", null),
                Arguments.of(null, null, null, "with space"), Arguments.of(null, null, null, "a&fields=io"),
                Arguments.of(null, null, null, "a".repeat(4097)), Arguments.of(null, null, null, " ".repeat(4097)),
                Arguments.of(null, null, null, " "));
    }

    @ParameterizedTest @MethodSource("invalidQueries")
    void invalidQueryFailsBeforeAnyHttpRequest(String from, String to, String limit, String cursor) throws Exception {
        errorCode(400, () -> service().observations(from, to, limit, cursor));
        assertNull(requests.poll(25, TimeUnit.MILLISECONDS));
    }

    @Test
    void acceptsExactly31DaysAndConvertsExplicitOffsetBoundsToUtc() throws Exception {
        var page = service().observations("2026-09-07T10:00:00+08:00", "2026-10-08T10:00:00+08:00", "1", null);
        request(); assertEquals("2026-09-07T02:00:00Z", page.from()); assertEquals(NOW.toString(), page.to());
    }

    @Test
    void disabledOrIncompleteConfigurationReturns503WithoutNetwork() throws Exception {
        var missing = new LangfuseObservationService(MAPPER, CLOCK, HttpClient.newHttpClient(), "pk-fixture", "", "", true, 100, endpoint);
        var disabled = new LangfuseObservationService(MAPPER, CLOCK, HttpClient.newHttpClient(), "pk-fixture", SECRET_MARKER,
                "https://jp.cloud.langfuse.com", false, 100, endpoint);
        errorCode(503, () -> missing.observations(null, null, null, null));
        errorCode(503, () -> disabled.observations(null, null, null, null));
        assertNull(requests.poll(25, TimeUnit.MILLISECONDS));
    }

    @ParameterizedTest @ValueSource(strings = {"http://jp.cloud.langfuse.com", "https://evil.example", "https://jp.cloud.langfuse.com.evil.example",
            "https://pk:sk@jp.cloud.langfuse.com", "https://jp.cloud.langfuse.com/api/public/observations",
            "https://jp.cloud.langfuse.com?extra=1", "https://jp.cloud.langfuse.com#fragment", "https://jp.cloud.langfuse.com:8080",
            "https://127.0.0.1", "not a URI"})
    void productionConstructorRejectsUnsupportedHostsWithoutAnyRequest(String host) throws Exception {
        var configured = new LangfuseObservationService(MAPPER, "pk-fixture", SECRET_MARKER, host, true, 100, 100);
        errorCode(502, () -> configured.observations(null, null, null, null));
        assertNull(requests.poll(25, TimeUnit.MILLISECONDS));
    }

    static Stream<Arguments> failureStatuses() {
        return Stream.of(Arguments.of(401, 502), Arguments.of(403, 502), Arguments.of(429, 429),
                Arguments.of(408, 504), Arguments.of(504, 504), Arguments.of(500, 502), Arguments.of(302, 502));
    }

    @ParameterizedTest @MethodSource("failureStatuses")
    void upstreamErrorsNeverDiscloseBodyFollowRedirectOrRetry(int status, int expected) throws Exception {
        response.set(new Response(status, "{\"message\":\"" + PRIVATE_MARKER + SECRET_MARKER + "\"}", 0, false));
        errorCode(expected, () -> service().observations(null, null, null, null)); request();
        assertNull(requests.poll(50, TimeUnit.MILLISECONDS));
    }

    @ParameterizedTest @ValueSource(strings = {"not-json", "[]", "{}", "{\"data\":{},\"meta\":{}}",
            "{\"data\":[],\"meta\":null}", "{\"data\":[null],\"meta\":{}}", "{\"data\":[{}],\"meta\":{}}",
            "{\"data\":[{\"id\":\"<script>bad</script>\",\"startTime\":\"2026-10-08T02:00:00Z\"}],\"meta\":{}}",
            "{\"data\":[],\"meta\":{\"cursor\":123}}", "{\"data\":[],\"meta\":{\"cursor\":\"bad&cursor\"}}",
            "{\"data\":[],\"meta\":{}} {\"extra\":\"data\"}"})
    void malformedSuccessfulResponsesAre502AndNeverExposeBody(String body) throws Exception {
        response.set(new Response(200, body, 0, false));
        errorCode(502, () -> service().observations(null, null, null, null)); request();
    }

    @Test
    void rejectsTooManyRecordsAndRepeatedOrOversizedUpstreamCursor() throws Exception {
        page(List.of(row("first"), row("second")), Map.of());
        errorCode(502, () -> service().observations(null, null, "1", null)); request();
        page(List.of(), Map.of("cursor", "again"));
        errorCode(502, () -> service().observations(null, null, null, "again")); request();
        page(List.of(), Map.of("cursor", "a".repeat(4097)));
        errorCode(502, () -> service().observations(null, null, null, null)); request();
    }

    @Test
    void nullCursorIsLastPage() throws Exception {
        var meta = new LinkedHashMap<String, Object>(); meta.put("cursor", null); page(List.of(), meta);
        var result = service().observations(null, null, null, null); request();
        assertNull(result.nextCursor()); assertFalse(result.hasMore());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void oversizedResponseIsRejectedBeforeUnboundedBuffering(boolean chunked) throws Exception {
        response.set(new Response(200, "x".repeat(LangfuseObservationService.MAX_BODY_BYTES + 1), 0, chunked));
        errorCode(502, () -> service().observations(null, null, null, null)); request();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void timeoutIncludesSlowResponseBodyAfterHeaders(boolean trickle) throws Exception {
        response.set(new Response(200, "{\"data\":[],\"meta\":{}}", 900, trickle));
        long start = System.nanoTime(); errorCode(504, () -> service(150).observations(null, null, null, null));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 800); request();
    }

    @Test
    void malformedConfigurationKeysCannotBecomeHeadersOrErrorMessages() throws Exception {
        var configured = new LangfuseObservationService(MAPPER, "pk:\nprivate", SECRET_MARKER, "https://jp.cloud.langfuse.com", true, 100, 100);
        errorCode(502, () -> configured.observations(null, null, null, null));
        assertNull(requests.poll(25, TimeUnit.MILLISECONDS));
    }

    private record Request(String method, String path, String query, String authorization) { }
    private record Response(int status, String body, long delayMillis, boolean trickle) { }
}
