package com.edu.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Distinguishes local admission failures from provider errors without any external HTTP or content capture. */
class LlmFailureDiagnosticsTest {
    private MockClientHttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.POST, URI.create("https://synthetic.invalid/v1/chat/completions"));
    }
    private LlmRequestPolicy policy(int output, int input, boolean shared) {
        var policy = new LlmRequestPolicy(true, 2, 1000, 0, 1, 1000, output, input);
        ReflectionTestUtils.setField(policy, "sharedCooldownEnabled", shared);
        return policy;
    }
    private byte[] payload(String tokenParameter, int tokens, String content) {
        return ("{\"" + tokenParameter + "\":" + tokens + ",\"messages\":[{\"role\":\"user\",\"content\":\"" + content + "\"}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @CsvSource({"max_tokens,0", "max_tokens,65", "max_completion_tokens,65"})
    void outputBudgetRefusalMakesZeroProviderRequests(String parameter, int tokens) {
        AtomicInteger calls = new AtomicInteger();
        LlmCallException failure = assertThrows(LlmCallException.class, () -> policy(64, 1000, false)
                .intercept(request(), payload(parameter, tokens, "synthetic"), (req, body) -> {
                    calls.incrementAndGet(); return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                }));
        assertThat(failure.code()).isEqualTo("OUTPUT_BUDGET");
        assertThat(failure.httpStatus()).isNull();
        assertThat(calls.get()).isZero();
    }

    @Test void inputBudgetRefusalMakesZeroProviderRequests() {
        AtomicInteger calls = new AtomicInteger();
        LlmCallException failure = assertThrows(LlmCallException.class, () -> policy(64, 80, false)
                .intercept(request(), payload("max_tokens", 32, "s".repeat(100)), (req, body) -> {
                    calls.incrementAndGet(); return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                }));
        assertThat(failure.code()).isEqualTo("INPUT_BUDGET");
        assertThat(failure.httpStatus()).isNull();
        assertThat(calls.get()).isZero();
    }

    @Test @SuppressWarnings("unchecked")
    void sharedCooldownBeyondDeadlineRetainsDeadlineCategoryRatherThanRedisFailure() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(10_000L);
        var policy = policy(64, 1000, true); ReflectionTestUtils.setField(policy, "redis", redis);
        AtomicInteger calls = new AtomicInteger();
        LlmCallException failure = assertThrows(LlmCallException.class, () -> policy.intercept(request(),
                payload("max_tokens", 32, "synthetic"), (req, body) -> {
                    calls.incrementAndGet(); return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                }));
        assertThat(failure.code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(failure.httpStatus()).isNull();
        assertThat(calls.get()).isZero();
        verify(redis).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test @SuppressWarnings("unchecked")
    void redisFailureIsDistinctAndDoesNotRetainInfrastructureMessage() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new IllegalStateException("private-redis-address-or-password"));
        var policy = policy(64, 1000, true); ReflectionTestUtils.setField(policy, "redis", redis);
        AtomicInteger calls = new AtomicInteger();
        LlmCallException failure = assertThrows(LlmCallException.class, () -> policy.intercept(request(),
                payload("max_tokens", 32, "synthetic"), (req, body) -> {
                    calls.incrementAndGet(); return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                }));
        assertThat(failure.code()).isEqualTo("COOLDOWN_UNAVAILABLE");
        assertThat(failure.getMessage()).doesNotContain("private-redis-address-or-password");
        assertThat(failure.getCause()).isNull();
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"400,json_validate_failed,PROVIDER_JSON_SCHEMA,JSON_GENERATION", "429,rate_limit_exceeded,PROVIDER_RATE_LIMIT,UNKNOWN",
            "401,invalid_api_key,PROVIDER_AUTH,UNKNOWN", "400,context_length_exceeded,INPUT_BUDGET,UNKNOWN",
            "500,unapproved-private-error,PROVIDER_ERROR,UNKNOWN", "500,json_validate_failed,PROVIDER_JSON_SCHEMA,UNKNOWN"})
    void providerErrorsKeepOnlyAllowlistedCategoryAndActualHttpStatus(int status, String code, String expected, String reason) {
        String privateBody = "{\"error\":{\"code\":\"" + code + "\",\"failed_generation\":\"private-model-reply\"}}";
        var response = new MockClientHttpResponse(privateBody.getBytes(StandardCharsets.UTF_8), HttpStatus.valueOf(status));
        LlmCallException failure = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertThat(failure.code()).isEqualTo(expected);
        assertThat(failure.httpStatus()).isEqualTo(status);
        assertThat(failure.reason()).isEqualTo(reason);
        assertThat(failure.getMessage()).doesNotContain("private-model-reply", "unapproved-private-error", "failed_generation");
        assertThat(failure.getCause()).isNull();
    }

    @Test void nullCodeSchemaCompilationRejectionHasSafeFixedDiagnostics() {
        String privateBody = """
                {"error":{"type":"invalid_request_error","code":null,
                "message":"invalid JSON schema for response_format: private-schema-name at private-schema-path"}}
                """;
        var response = new MockClientHttpResponse(privateBody.getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST);
        LlmCallException failure = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertThat(failure.code()).isEqualTo("PROVIDER_JSON_SCHEMA");
        assertThat(failure.httpStatus()).isEqualTo(400);
        assertThat(failure.reason()).isEqualTo("SCHEMA_COMPILATION");
        assertThat(failure.getMessage()).isEqualTo("LLM failure: PROVIDER_JSON_SCHEMA; HTTP 400");
        assertThat(failure.getCause()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"401,PROVIDER_AUTH", "403,PROVIDER_AUTH", "429,PROVIDER_RATE_LIMIT", "500,PROVIDER_ERROR"})
    void schemaMessageNeverOverridesAuthenticationRateLimitOrNon400Status(int status, String expected) {
        String privateBody = """
                {"error":{"type":"invalid_request_error","code":null,
                "message":"invalid JSON schema for response_format: private-schema-details"}}
                """;
        var response = new MockClientHttpResponse(privateBody.getBytes(StandardCharsets.UTF_8), HttpStatus.valueOf(status));
        LlmCallException failure = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertThat(failure.code()).isEqualTo(expected);
        assertThat(failure.httpStatus()).isEqualTo(status);
        assertThat(failure.reason()).isEqualTo("UNKNOWN");
        assertThat(failure.getMessage()).doesNotContain("private-schema-details", "invalid_request_error");
        assertThat(failure.getCause()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"Unsupported parameter", "Request references invalid JSON schema for response_format:",
            "invalid JSON schema for another_format:"})
    void otherInvalidRequestsAreNotMisreportedAsSchemaCompilationFailures(String message) {
        String privateBody = "{\"error\":{\"type\":\"invalid_request_error\",\"code\":null,\"message\":\"" + message + "\"}}";
        var response = new MockClientHttpResponse(privateBody.getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST);
        LlmCallException failure = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertThat(failure.code()).isEqualTo("PROVIDER_ERROR");
        assertThat(failure.httpStatus()).isEqualTo(400);
        assertThat(failure.reason()).isEqualTo("UNKNOWN");
        assertThat(failure.getCause()).isNull();
    }

    @Test void oversizedProviderErrorReadsAtMost64KiBAndNeverRetainsPrivateBody() throws Exception {
        String privateBody = "{\"error\":{\"code\":\"json_validate_failed\","
                + "\"message\":\"invalid JSON schema for response_format: private-schema-path\",\"failed_generation\":\""
                + "private-model-reply".repeat(6000) + "\"}}";
        CountingBody body = new CountingBody(privateBody.getBytes(StandardCharsets.UTF_8));
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(response.getBody()).thenReturn(body);
        LlmCallException failure = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertThat(body.readBytes).isEqualTo(65536);
        // Truncated JSON is not trusted for provider-code attribution.
        assertThat(failure.code()).isEqualTo("PROVIDER_ERROR");
        assertThat(failure.httpStatus()).isEqualTo(400);
        assertThat(failure.reason()).isEqualTo("UNKNOWN");
        assertThat(failure.getMessage()).doesNotContain("private-model-reply", "failed_generation");
        assertThat(failure.getCause()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"401,PROVIDER_AUTH", "403,PROVIDER_AUTH", "429,PROVIDER_RATE_LIMIT"})
    void jsonGenerationMarkerDoesNotOverrideAuthenticationOrRateLimit(int status, String expected) {
        String privateBody = """
                {"error":{"code":"json_validate_failed","failed_generation":"private-model-reply"}}
                """;
        var response = new MockClientHttpResponse(privateBody.getBytes(StandardCharsets.UTF_8), HttpStatus.valueOf(status));
        LlmCallException failure = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertThat(failure.code()).isEqualTo(expected);
        assertThat(failure.httpStatus()).isEqualTo(status);
        assertThat(failure.reason()).isEqualTo("UNKNOWN");
        assertThat(failure.getMessage()).doesNotContain("private-model-reply", "failed_generation");
        assertThat(failure.getCause()).isNull();
    }

    @Test void compilationMarkerWinsOverGenerationMarkerButKeepsOnlyFixedReason() {
        String privateBody = """
                {"error":{"code":"json_validate_failed",
                "message":"invalid JSON schema for response_format: private-schema-details"}}
                """;
        var response = new MockClientHttpResponse(privateBody.getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST);
        LlmCallException failure = assertThrows(LlmCallException.class, () -> new LlmResponseErrorHandler().handleError(response));
        assertThat(failure.code()).isEqualTo("PROVIDER_JSON_SCHEMA");
        assertThat(failure.reason()).isEqualTo("SCHEMA_COMPILATION");
        assertThat(failure.getMessage()).doesNotContain("private-schema-details", "SCHEMA_COMPILATION");
        assertThat(failure.getCause()).isNull();
    }

    private static final class CountingBody extends InputStream {
        private final ByteArrayInputStream delegate;
        private int readBytes;
        CountingBody(byte[] bytes) { delegate = new ByteArrayInputStream(bytes); }
        @Override public int read() {
            int value = delegate.read(); if (value >= 0) readBytes++; return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) {
            int read = delegate.read(bytes, offset, length); if (read > 0) readBytes += read; return read;
        }
    }
}
