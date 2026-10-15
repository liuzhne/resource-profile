package com.edu.agent.core;

/** Carries only fixed diagnostics across task stages. */
public final class AgentExecutionException extends RuntimeException {
    private final AgentFailure failure;
    public AgentExecutionException(AgentFailure failure) {
        super("Agent stage failure: " + failure.code());
        this.failure = failure;
    }
    public AgentFailure failure() { return failure; }
}
