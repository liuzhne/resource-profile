package com.edu.agent.config;

import org.springframework.ai.retry.NonTransientAiException;

/** Fixed diagnostics only; never retain provider response, prompts, credentials or a raw cause. */
public final class LlmCallException extends NonTransientAiException {
    private enum Reason { SCHEMA_COMPILATION, JSON_GENERATION, UNKNOWN }
    private final String code;
    private final Integer httpStatus;
    private final Reason reason;
    public LlmCallException(String code, Integer httpStatus) {
        this(code, httpStatus, "UNKNOWN");
    }
    public LlmCallException(String code, Integer httpStatus, String reason) {
        super("LLM failure: " + code + (httpStatus == null ? "" : "; HTTP " + httpStatus));
        this.code = code;
        this.httpStatus = httpStatus;
        this.reason = reason == null ? Reason.UNKNOWN : switch (reason) {
            case "SCHEMA_COMPILATION" -> Reason.SCHEMA_COMPILATION;
            case "JSON_GENERATION" -> Reason.JSON_GENERATION;
            default -> Reason.UNKNOWN;
        };
    }
    public String code() { return code; }
    public Integer httpStatus() { return httpStatus; }
    public String reason() { return reason.name(); }

    /** Read only our typed marker; never classify free-form exception messages or retain causes. */
    public static String reasonFrom(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof LlmCallException typed) return typed.reason();
        }
        return Reason.UNKNOWN.name();
    }
}
