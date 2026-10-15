package com.edu.agent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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

/** Exercises the HTTP boundary against a loopback JDK server; no Langfuse deployment or credentials needed. */
class LangfuseClientTransportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant START = Instant.parse("2026-10-07T01:02:03.123456789Z");
    private static final Instant END = START.plusMillis(725);
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final AtomicReference<Response> response = new AtomicReference<>(new Response(200, "{}", 0));
    private HttpServer server;
    private ExecutorService executor;
    private String host;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                Response reply = response.get();
                if (reply.delayMillis() > 0) Thread.sleep(reply.delayMillis());
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        host = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void exportsCompleteOtlpGenerationWithExplicitSyntheticContentCapture() throws Exception {
        LangfuseClient client = client(true);
        Map<String, Object> input = Map.of("messages", List.of(Map.of("role", "user", "content", "synthetic prompt")));
        Map<String, Object> output = Map.of("answer", "synthetic reply");
        Map<String, Object> metadata = Map.of("iterations", 3, "status", "COMPLETED", "cached_n", 4L,
                "prefill_ms", 12.5, "cache_hit_rate", 0.5, "traces_summary", "synthetic reasoning must stay local");
        assertDoesNotThrow(() -> client.traceGeneration("llm.chat", "fixture-model", input, output,
                12, 7, metadata, START, END));

        Request request = nextRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/public/otel/v1/traces", request.path());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("pk-fixture:sk-fixture".getBytes(StandardCharsets.UTF_8)),
                request.authorization());
        assertEquals("application/json", request.contentType());
        assertEquals("4", request.ingestionVersion());
        JsonNode payload = MAPPER.readTree(request.body());
        assertFalse(payload.has("batch"));
        JsonNode resources = payload.path("resourceSpans");
        assertEquals(1, resources.size());
        assertEquals("agent-service", attributes(resources.get(0).path("resource")).get("service.name"));
        JsonNode scopes = resources.get(0).path("scopeSpans");
        assertEquals(1, scopes.size());
        assertEquals("com.edu.agent.langfuse", scopes.get(0).path("scope").path("name").asText());
        assertEquals(1, scopes.get(0).path("spans").size());
        JsonNode span = span(payload);
        assertTrue(span.path("traceId").asText().matches("[0-9a-f]{32}"));
        assertTrue(span.path("spanId").asText().matches("[0-9a-f]{16}"));
        assertFalse(span.has("parentSpanId"));
        assertEquals(1, span.path("kind").asInt());
        assertEquals("llm.chat", span.path("name").asText());
        assertTrue(span.path("startTimeUnixNano").isTextual());
        assertEquals(Long.toString(START.getEpochSecond() * 1_000_000_000L + START.getNano()),
                span.path("startTimeUnixNano").asText());
        assertEquals(Long.toString(END.getEpochSecond() * 1_000_000_000L + END.getNano()),
                span.path("endTimeUnixNano").asText());

        Map<String, String> attributes = attributes(span);
        assertEquals("llm.chat", attributes.get("langfuse.trace.name"));
        assertEquals("generation", attributes.get("langfuse.observation.type"));
        assertEquals("fixture-model", attributes.get("langfuse.observation.model.name"));
        assertEquals(MAPPER.valueToTree(input), MAPPER.readTree(attributes.get("langfuse.observation.input")));
        assertEquals(MAPPER.valueToTree(output), MAPPER.readTree(attributes.get("langfuse.observation.output")));
        assertEquals(MAPPER.valueToTree(Map.of("input", 12, "output", 7, "total", 19)),
                MAPPER.readTree(attributes.get("langfuse.observation.usage_details")));
        assertEquals("3", attributes.get("langfuse.observation.metadata.iterations"));
        assertEquals("\"COMPLETED\"", attributes.get("langfuse.observation.metadata.status"));
        assertEquals("4", attributes.get("langfuse.observation.metadata.cached_n"));
        assertEquals("12.5", attributes.get("langfuse.observation.metadata.prefill_ms"));
        assertEquals("0.5", attributes.get("langfuse.observation.metadata.cache_hit_rate"));
        assertFalse(request.body().contains("synthetic reasoning"));
        assertFalse(attributes.containsKey("langfuse.trace.input"));
        assertFalse(attributes.containsKey("langfuse.trace.output"));
    }

    @Test
    void defaultsToNoContentOrFreeTextMetadataAndDoesNotInventCorrelation() throws Exception {
        LangfuseClient client = client(null); // Leave the field's production default unchanged.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("iterations", 2);
        metadata.put("status", "COMPLETED");
        metadata.put("task_tag", "synthetic-student-id-123");
        metadata.put("traces_summary", "synthetic private reasoning");
        metadata.put("other", Map.of("nested", "synthetic private notes"));
        metadata.put("prefill_ms", Double.NaN);
        metadata.put("cached_n", -1);
        metadata.put("cache_hit_rate", "synthetic private string");
        client.traceGeneration("agent.loop", "unknown", "synthetic private prompt", "synthetic private output",
                0, 0, metadata, START, END);
        Request first = nextRequest();
        Map<String, String> attrs = attributes(span(MAPPER.readTree(first.body())));
        assertFalse(attrs.containsKey("langfuse.observation.input"));
        assertFalse(attrs.containsKey("langfuse.observation.output"));
        assertEquals("2", attrs.get("langfuse.observation.metadata.iterations"));
        assertEquals("\"COMPLETED\"", attrs.get("langfuse.observation.metadata.status"));
        assertFalse(first.body().contains("synthetic private"));
        assertFalse(first.body().contains("synthetic-student"));
        assertFalse(attrs.keySet().stream().anyMatch(key -> key.endsWith("prefill_ms") || key.endsWith("cached_n")
                || key.endsWith("cache_hit_rate") || key.endsWith("traces_summary") || key.endsWith("task_tag") || key.endsWith("other")));

        client.traceGeneration("llm.chat", "fixture-model", null, null, 0, 0, null, START, END);
        JsonNode firstSpan = span(MAPPER.readTree(first.body()));
        JsonNode nextSpan = span(MAPPER.readTree(nextRequest().body()));
        assertNotEquals(firstSpan.path("traceId"), nextSpan.path("traceId"));
        assertNotEquals(firstSpan.path("spanId"), nextSpan.path("spanId"));
        assertFalse(nextSpan.has("parentSpanId"));
    }

    @Test
    void missingCredentialsOrExplicitDisableAreNoOp() throws Exception {
        LangfuseClient missing = configured(null);
        ReflectionTestUtils.setField(missing, "secretKey", "");
        missing.init();
        assertDoesNotThrow(() -> trace(missing));
        LangfuseClient disabled = configured(true);
        ReflectionTestUtils.setField(disabled, "reportingEnabled", false);
        disabled.init();
        assertDoesNotThrow(() -> trace(disabled));
        assertNull(requests.poll(100, TimeUnit.MILLISECONDS));
    }

    static Stream<Arguments> failSoftResponses() {
        return Stream.of(
                Arguments.of(400, "{\"message\":\"synthetic confidential error body\"}"),
                Arguments.of(500, "{\"message\":\"synthetic confidential error body\"}"),
                Arguments.of(200, "{\"partialSuccess\":{\"rejectedSpans\":\"1\",\"errorMessage\":\"synthetic private reason\"}}"),
                Arguments.of(200, "{\"partialSuccess\":{\"rejectedSpans\":\"0\",\"errorMessage\":\"synthetic warning\"}}"),
                Arguments.of(200, "malformed-response")
        );
    }

    @ParameterizedTest
    @MethodSource("failSoftResponses")
    void httpErrorsAndPartialSuccessDoNotThrowOrRetry(int status, String body) throws Exception {
        LangfuseClient client = client(false);
        response.set(new Response(status, body, 0));
        assertDoesNotThrow(() -> trace(client));
        nextRequest();
        assertNull(requests.poll(100, TimeUnit.MILLISECONDS));
        response.set(new Response(200, "{}", 0));
        assertDoesNotThrow(() -> trace(client));
        nextRequest(); // A failed export does not disable or corrupt later business telemetry calls.
    }

    @Test
    void finiteReadTimeoutKeepsSlowExporterFromHoldingTheWorker() throws Exception {
        LangfuseClient client = configured(false);
        ReflectionTestUtils.setField(client, "connectTimeoutMs", 100);
        ReflectionTestUtils.setField(client, "readTimeoutMs", 100);
        client.init();
        response.set(new Response(200, "{}", 1000));
        assertTimeout(Duration.ofMillis(800), () -> assertDoesNotThrow(() -> trace(client)));
        nextRequest();
    }

    private LangfuseClient configured(Boolean capture) {
        LangfuseClient client = new LangfuseClient();
        ReflectionTestUtils.setField(client, "publicKey", "pk-fixture");
        ReflectionTestUtils.setField(client, "secretKey", "sk-fixture");
        ReflectionTestUtils.setField(client, "host", host);
        if (capture != null) ReflectionTestUtils.setField(client, "captureContent", capture);
        return client;
    }

    private LangfuseClient client(Boolean capture) {
        LangfuseClient client = configured(capture);
        client.init();
        return client;
    }

    private void trace(LangfuseClient client) {
        client.traceGeneration("llm.chat", "fixture-model", "synthetic input", "synthetic output", 1, 2,
                Map.of("cached_n", 1), START, END);
    }

    private Request nextRequest() throws InterruptedException {
        Request request = requests.poll(2, TimeUnit.SECONDS);
        assertNotNull(request, "Expected one OTLP request to the local collector");
        return request;
    }

    private static JsonNode span(JsonNode payload) {
        return payload.path("resourceSpans").path(0).path("scopeSpans").path(0).path("spans").path(0);
    }

    private static Map<String, String> attributes(JsonNode holder) {
        Map<String, String> attrs = new LinkedHashMap<>();
        for (JsonNode attribute : holder.path("attributes")) {
            assertEquals(1, attribute.path("value").size());
            assertTrue(attribute.path("value").path("stringValue").isTextual());
            attrs.put(attribute.path("key").asText(), attribute.path("value").path("stringValue").asText());
        }
        return attrs;
    }

    private record Request(String method, String path, String authorization, String contentType,
                           String ingestionVersion, String body) {}
    private record Response(int status, String body, int delayMillis) {}
}
