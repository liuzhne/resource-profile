package com.edu.agent.ai;

/**
 * JAVA-RISK-PLAN-20261013：Java 侧 LLM 结构化调用的显式失败。
 * {@link Reason#MALFORMED_OUTPUT}：重试 {@code max-attempts} 次后仍无法解析/校验；
 * {@link Reason#MODEL_ERROR}：模型调用本身抛错（超时、HTTP 错误等）。
 */
public class AiStructuredOutputException extends RuntimeException {

    public enum Reason { MALFORMED_OUTPUT, MODEL_ERROR }

    private final Reason reason;
    private final String route;
    private final int attempts;

    public AiStructuredOutputException(Reason reason, String route, int attempts, String message, Throwable cause) {
        super("[" + route + "] " + message, cause);
        this.reason = reason;
        this.route = route;
        this.attempts = attempts;
    }

    public Reason reason() { return reason; }
    public String route() { return route; }
    public int attempts() { return attempts; }
}
