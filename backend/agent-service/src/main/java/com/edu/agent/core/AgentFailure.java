package com.edu.agent.core;

import com.edu.agent.config.LlmCallException;
import java.util.Set;

/** Allowlisted diagnostic contract; free-form exception messages cannot reach storage, UI or tracing. */
public record AgentFailure(String code, String stage, Integer httpStatus) {
    public static final Set<String> CODES = Set.of("INPUT_BUDGET", "OUTPUT_BUDGET", "QUEUE_FULL",
            "COOLDOWN_UNAVAILABLE", "DEADLINE_EXCEEDED", "PROVIDER_RATE_LIMIT", "PROVIDER_JSON_SCHEMA",
            "PROVIDER_AUTH", "PROVIDER_ERROR", "NETWORK_ERROR", "OUTPUT_INVALID", "TOOL_ERROR", "MCP_UNAVAILABLE", "ITERATION_LIMIT", "UNKNOWN");
    public static final Set<String> STAGES = Set.of("LLM_REQUEST", "TOOL_EXECUTION", "FINAL_VALIDATION", "COMPLIANCE_CHECKING", "UNKNOWN");
    public AgentFailure {
        if (code == null || !CODES.contains(code)) code = "UNKNOWN";
        if (stage == null || !STAGES.contains(stage)) stage = "UNKNOWN";
        if (httpStatus != null && (httpStatus < 400 || httpStatus > 599)) httpStatus = null;
    }
    public static AgentFailure from(Throwable error, String stage) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof AgentExecutionException typed) return typed.failure();
            if (cause instanceof LlmCallException typed) return new AgentFailure(typed.code(), stage, typed.httpStatus());
            if (cause instanceof java.io.IOException || cause instanceof org.springframework.web.client.ResourceAccessException)
                return new AgentFailure("NETWORK_ERROR", stage, null);
        }
        return new AgentFailure("UNKNOWN", stage, null);
    }
    public static AgentFailure forStatus(AgentLoopStatus status) {
        return switch (status) {
            case TOOL_ERROR -> new AgentFailure("TOOL_ERROR", "TOOL_EXECUTION", null);
            case PARSE_ERROR, VALIDATION_ERROR -> new AgentFailure("OUTPUT_INVALID", "FINAL_VALIDATION", null);
            case MAX_ITERATIONS -> new AgentFailure("ITERATION_LIMIT", "LLM_REQUEST", null);
            case DEADLINE_EXCEEDED -> new AgentFailure("DEADLINE_EXCEEDED", "LLM_REQUEST", null);
            default -> new AgentFailure("UNKNOWN", "UNKNOWN", null);
        };
    }
}
