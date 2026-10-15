package com.edu.agent.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmProtocolShapeDiagnosticsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({"text,ABSENT,false,false,false,TEXT", "json_object,FALSE,false,true,false,JSON_OBJECT",
            "json_schema,TRUE,true,false,false,JSON_SCHEMA", "json_schema,FALSE,false,true,true,JSON_SCHEMA",
            "private-format,STRING,false,true,false,UNKNOWN", "json_schema,ABSENT,false,false,false,JSON_SCHEMA"})
    void transportHandoffLogsOnlyFixedShapeAndForwardsUnchangedBytes(String type, String strict, boolean knownSchema,
            boolean tools, boolean stream, String expectedType) throws Throwable {
        var payload = JSON.createObjectNode();
        payload.put("max_tokens", 32);
        payload.putArray("messages").addObject().put("role", "user").put("content", "private-student-profile");
        var format = payload.putObject("response_format"); format.put("type", type);
        var schema = format.putObject("json_schema");
        schema.put("name", knownSchema ? "educare_react_turn" : "private-schema-name");
        schema.putObject("schema").put("private-schema-key", "private-schema-value");
        if ("TRUE".equals(strict)) schema.put("strict", true);
        else if ("FALSE".equals(strict)) schema.put("strict", false);
        else if ("STRING".equals(strict)) schema.put("strict", "private-strict-value");
        if (tools) payload.putArray("tools").addObject().put("name", "private-tool-name");
        payload.put("stream", stream);
        byte[] body = JSON.writeValueAsBytes(payload);
        LlmRequestPolicy policy = new LlmRequestPolicy(true, 2, 1000, 0, 1, 1000, 64, 1024);
        ReflectionTestUtils.setField(policy, "sharedCooldownEnabled", false);
        AtomicInteger calls = new AtomicInteger();
        List<ILoggingEvent> logs = capture(LlmRequestPolicy.class, () -> policy.intercept(
                new MockClientHttpRequest(HttpMethod.POST, URI.create("https://synthetic.invalid/v1/chat/completions")),
                body, (request, forwarded) -> {
                    assertArrayEquals(body, forwarded); calls.incrementAndGet();
                    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                }));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(logs).hasSize(1);
        String message = logs.get(0).getFormattedMessage();
        assertThat(message).startsWith("LLM_REQUEST_CONTRACT ").contains("response_format=" + expectedType,
                "strict=" + ("STRING".equals(strict) ? "ABSENT" : strict), "react_schema=" + knownSchema,
                "tools=" + tools, "stream=" + stream, "output_tokens=32");
        assertPrivateContentAbsent(logs);
    }

    @ParameterizedTest
    @CsvSource({"OBJECT,true,true,OBJECT,NULL", "STRING,true,true,STRING,OBJECT", "ARRAY,true,true,NULL,ARRAY",
            "OTHER,true,true,OTHER,OTHER", "UNKNOWN_KEYS,true,false,OTHER,OTHER",
            "INVALID,false,false,OTHER,OTHER", "DUPLICATE,false,false,OTHER,OTHER", "TRAILING,false,false,OTHER,OTHER",
            "NON_TEXT,false,false,OTHER,OTHER", "MISSING,false,false,OTHER,OTHER"})
    void generationDiagnosisContainsOnlyParsePresenceAndFixedTypes(String kind, boolean parsed, boolean fields,
            String finalType, String actionType) throws Throwable {
        String generation = switch (kind) {
            case "OBJECT" -> "{\"thought\":\"private-thought\",\"plan\":[\"private-plan\"],\"action\":null,\"final_answer\":{\"private-key\":\"private-value\"}}";
            case "STRING" -> "{\"thought\":\"private-thought\",\"plan\":null,\"action\":{\"tool\":\"private-tool\"},\"final_answer\":\"private-reply\"}";
            case "ARRAY" -> "{\"thought\":\"private-thought\",\"plan\":null,\"action\":[{\"private-key\":\"private-value\"}],\"final_answer\":null}";
            case "OTHER" -> "{\"thought\":\"private-thought\",\"plan\":null,\"action\":17,\"final_answer\":[\"private-value\"]}";
            case "UNKNOWN_KEYS" -> "{\"private-key\":\"private-value\"}";
            case "DUPLICATE" -> "{\"thought\":\"private-first\",\"thought\":\"private-second\"}";
            case "TRAILING" -> "{\"thought\":\"private-thought\"} {\"private-key\":\"private-value\"}";
            default -> "{\"private-incomplete";
        };
        var body = JSON.createObjectNode();
        var error = body.putObject("error");
        error.put("code", "json_validate_failed"); error.put("message", "private-provider-error");
        if ("NON_TEXT".equals(kind)) error.putObject("failed_generation").put("private-key", "private-value");
        else if (!"MISSING".equals(kind)) error.put("failed_generation", generation);
        var response = new MockClientHttpResponse(JSON.writeValueAsBytes(body), HttpStatus.BAD_REQUEST);
        List<ILoggingEvent> logs = capture(LlmResponseErrorHandler.class, () -> {
            LlmCallException failure = assertThrows(LlmCallException.class,
                    () -> new LlmResponseErrorHandler().handleError(response));
            assertThat(failure.code()).isEqualTo("PROVIDER_JSON_SCHEMA");
            assertThat(failure.reason()).isEqualTo("JSON_GENERATION");
            assertThat(failure.getCause()).isNull();
        });
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFormattedMessage()).startsWith("LLM_REJECTION_SHAPE ").contains("json_parsed=" + parsed,
                "thought_present=" + fields, "plan_present=" + fields, "action_present=" + fields,
                "final_present=" + fields, "final_type=" + finalType, "action_type=" + actionType);
        assertPrivateContentAbsent(logs);
    }

    @ParameterizedTest
    @CsvSource({"401", "403", "429", "500"})
    void non400DoesNotInspectOrLogGenerationShape(int status) throws Throwable {
        String body = "{\"error\":{\"code\":\"json_validate_failed\",\"failed_generation\":\"private-provider-content\"}}";
        var response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.valueOf(status));
        List<ILoggingEvent> logs = capture(LlmResponseErrorHandler.class, () -> {
            LlmCallException failure = assertThrows(LlmCallException.class,
                    () -> new LlmResponseErrorHandler().handleError(response));
            assertThat(failure.reason()).isEqualTo("UNKNOWN");
        });
        assertThat(logs).isEmpty();
    }

    @Test void oversizedOuterBodyDoesNotTrustPartialErrorOrLogItsContent() throws Throwable {
        var body = JSON.createObjectNode();
        var error = body.putObject("error"); error.put("code", "json_validate_failed");
        error.put("failed_generation", "private-provider-content".repeat(4000));
        var response = new MockClientHttpResponse(JSON.writeValueAsBytes(body), HttpStatus.BAD_REQUEST);
        List<ILoggingEvent> logs = capture(LlmResponseErrorHandler.class, () -> {
            LlmCallException failure = assertThrows(LlmCallException.class,
                    () -> new LlmResponseErrorHandler().handleError(response));
            assertThat(failure.code()).isEqualTo("PROVIDER_ERROR");
            assertThat(failure.reason()).isEqualTo("UNKNOWN");
        });
        assertThat(logs).isEmpty();
    }

    private static void assertPrivateContentAbsent(List<ILoggingEvent> logs) {
        for (ILoggingEvent event : logs) {
            assertThat(event.getFormattedMessage()).doesNotContain("private-", "failed_generation\"", "Authorization", "Bearer");
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    private static List<ILoggingEvent> capture(Class<?> type, Executable action) throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start(); logger.addAppender(captured);
        try {
            action.execute();
            return List.copyOf(captured.list);
        } finally {
            logger.detachAppender(captured); captured.stop();
        }
    }
}
