package com.edu.agent.config;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LlmRequestPolicyTest {
    private static final byte[] REQUEST = "{\"max_tokens\":128,\"messages\":[{\"role\":\"user\",\"content\":\"synthetic\"}]}".getBytes();
    private LlmRequestPolicy policy(int queue, long wait) {
        var policy = new LlmRequestPolicy(true, queue, wait, 0, 3, 1000, 2400, 18000);
        ReflectionTestUtils.setField(policy, "sharedCooldownEnabled", false);
        return policy;
    }
    private MockClientHttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.POST, URI.create("https://synthetic.invalid/v1/chat/completions"));
    }
    @Test void permanent400IsNotRetriedAndErrorDoesNotExposeProviderContent() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var response = policy(2, 1000).intercept(request(), REQUEST, (r,b) -> {
            calls.incrementAndGet();
            return new MockClientHttpResponse("{\"error\":{\"code\":\"json_validate_failed\",\"failed_generation\":\"private synthetic text\"}}".getBytes(), HttpStatus.BAD_REQUEST);
        });
        var error = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertEquals(1, calls.get());
        assertEquals("PROVIDER_JSON_SCHEMA", error.code());
        assertEquals(400, error.httpStatus());
        assertFalse(error.getMessage().contains("private synthetic text"));
    }
    @Test void retryAfterSecondsAndDateAreRespected() throws Exception {
        assertEquals(1250, LlmRequestPolicy.retryAfterMillis("1.25", Instant.EPOCH, 100));
        assertEquals(2000, LlmRequestPolicy.retryAfterMillis("Thu, 1 Jan 1970 00:00:02 GMT", Instant.EPOCH, 100));
        AtomicInteger calls = new AtomicInteger();
        long start = System.nanoTime();
        var response = policy(2, 1000).intercept(request(), REQUEST, (r,b) -> {
            var result = new MockClientHttpResponse(new byte[0], calls.incrementAndGet() == 1 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.OK);
            result.getHeaders().set("Retry-After", "0.02"); return result;
        });
        assertEquals(2, calls.get()); assertEquals(200, response.getStatusCode().value());
        assertTrue(System.nanoTime() - start >= 18000000);
    }
    @Test void retryAfterOutsideBudgetDoesNotRetryEarly() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var response = policy(2, 1000).intercept(request(), REQUEST, (r,b) -> {
            calls.incrementAndGet(); var result = new MockClientHttpResponse(new byte[0], HttpStatus.TOO_MANY_REQUESTS);
            result.getHeaders().set("Retry-After", "120"); return result;
        });
        assertEquals(1, calls.get()); assertEquals(429, response.getStatusCode().value());
    }
    @Test void fullQueueRejectsWithoutAnotherProviderRequest() throws Exception {
        var policy = policy(0, 1000); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> policy.intercept(request(), REQUEST, (r,b) -> {
                entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
            }));
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS));
            try {
                assertThrows(NonTransientAiException.class, () -> policy.intercept(request(), REQUEST, (r,b) -> { fail("must not execute"); return null; }));
            } finally { release.countDown(); }
            first.get();
        }
    }
    @Test void realRestClientRetryStillRecordsSuccessfulUsage() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = (calls.incrementAndGet() == 1 ? "{\"error\":{\"code\":\"rate_limit_exceeded\"}}"
                    : "{\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":2},\"choices\":[{\"message\":{\"content\":\"synthetic\"}}]}").getBytes();
            int status = calls.get() == 1 ? 429 : 200;
            exchange.getResponseHeaders().set("Retry-After", "0");
            exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new LlmMetricsInterceptor(registry, mock(LangfuseClient.class)); metrics.initMeters();
            var client = RestClient.builder().requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                    .requestInterceptor(metrics).requestInterceptor(policy(2, 1000)).defaultStatusHandler(new LlmResponseErrorHandler()).build();
            client.post().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions")
                    .body(REQUEST).retrieve().body(String.class);
            assertEquals(2, calls.get());
            assertEquals(1, registry.get("educare.llm.calls").counter().count());
            assertEquals(11, registry.get("educare.llm.prompt_tokens").counter().count());
            assertEquals(2, registry.get("educare.llm.completion_tokens").counter().count());
        } finally { registry.close(); server.stop(0); }
    }
}
