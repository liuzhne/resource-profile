package com.edu.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Provider errors can contain failed_generation with personal data; retain only fixed error markers. */
@Slf4j
final class LlmResponseErrorHandler implements ResponseErrorHandler {
    private static final String SCHEMA_REJECTION_PREFIX = "invalid JSON schema for response_format:";
    private static final Set<String> CODES = Set.of("json_validate_failed", "rate_limit_exceeded", "invalid_request_error",
            "context_length_exceeded", "invalid_api_key");
    private static final ObjectMapper GENERATION_JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    @Override public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }
    @Override public void handleError(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String code = "provider_error";
        boolean schemaRejected = false;
        JsonNode failedGeneration = null;
        try {
            var error = new ObjectMapper().readTree(response.getBody().readNBytes(65536)).path("error");
            String found = error.path("code").asText();
            if (CODES.contains(found)) code = found;
            // Groq may reject schema compilation with code=null; inspect only this fixed prefix,
            // and never include its schema name, path, or other provider content in diagnostics.
            schemaRejected = status == 400 && error.path("message").asText().startsWith(SCHEMA_REJECTION_PREFIX);
            if (status == 400 && "json_validate_failed".equals(code)) failedGeneration = error.path("failed_generation");
        } catch (Exception ignored) { }
        String category = status == 429 ? "PROVIDER_RATE_LIMIT"
                : status == 401 || status == 403 ? "PROVIDER_AUTH"
                : schemaRejected || "json_validate_failed".equals(code) ? "PROVIDER_JSON_SCHEMA"
                : "context_length_exceeded".equals(code) ? "INPUT_BUDGET" : "PROVIDER_ERROR";
        String reason = status != 400 ? "UNKNOWN"
                : schemaRejected ? "SCHEMA_COMPILATION"
                : "json_validate_failed".equals(code) ? "JSON_GENERATION" : "UNKNOWN";
        if ("JSON_GENERATION".equals(reason)) logGenerationShape(failedGeneration);
        throw new LlmCallException(category, status, reason);
    }

    /** In-memory, bounded structural diagnosis; no keys or values from provider content reach logs. */
    private static void logGenerationShape(JsonNode failedGeneration) {
        JsonNode parsed = null;
        try {
            if (failedGeneration != null && failedGeneration.isTextual()) {
                String text = failedGeneration.textValue();
                if (text.length() <= 65536 && text.getBytes(StandardCharsets.UTF_8).length <= 65536) {
                    parsed = GENERATION_JSON.readTree(text);
                }
            }
        } catch (Exception ignored) { }
        boolean hasThought = parsed != null && parsed.isObject() && parsed.has("thought");
        boolean hasPlan = parsed != null && parsed.isObject() && parsed.has("plan");
        boolean hasAction = parsed != null && parsed.isObject() && parsed.has("action");
        boolean hasFinal = parsed != null && parsed.isObject() && parsed.has("final_answer");
        String finalType = hasFinal ? finalShape(parsed.path("final_answer")) : "OTHER";
        String actionType = hasAction ? actionShape(parsed.path("action")) : "OTHER";
        log.warn("LLM_REJECTION_SHAPE json_parsed={} thought_present={} plan_present={} action_present={} final_present={} final_type={} action_type={}",
                parsed != null, hasThought, hasPlan, hasAction, hasFinal, finalType, actionType);
    }

    private static String finalShape(JsonNode node) {
        if (node.isNull()) return "NULL";
        if (node.isObject()) return "OBJECT";
        if (node.isTextual()) return "STRING";
        return "OTHER";
    }

    private static String actionShape(JsonNode node) {
        if (node.isNull()) return "NULL";
        if (node.isObject()) return "OBJECT";
        if (node.isArray()) return "ARRAY";
        return "OTHER";
    }
}
