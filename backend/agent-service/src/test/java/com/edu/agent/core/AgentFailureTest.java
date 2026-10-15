package com.edu.agent.core;

import com.edu.agent.config.LlmCallException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AgentFailureTest {
    @Test void nullAndUnapprovedDiagnosticsBecomeUnknownWithoutRetainingFreeText() {
        assertThat(new AgentFailure(null, null, 200)).isEqualTo(new AgentFailure("UNKNOWN", "UNKNOWN", null));
        AgentFailure untrusted = new AgentFailure("private-provider-response", "private-prompt", 900);
        assertThat(untrusted).isEqualTo(new AgentFailure("UNKNOWN", "UNKNOWN", null));
        assertThat(untrusted.toString()).doesNotContain("private-provider-response", "private-prompt");
    }

    @Test void validCategoriesKeepOnlyApprovedHttpErrorRange() {
        assertThat(new AgentFailure("PROVIDER_AUTH", "LLM_REQUEST", 403).httpStatus()).isEqualTo(403);
        assertThat(new AgentFailure("PROVIDER_ERROR", "LLM_REQUEST", 599).httpStatus()).isEqualTo(599);
        assertThat(new AgentFailure("PROVIDER_ERROR", "LLM_REQUEST", 600).httpStatus()).isNull();
        assertThat(new AgentFailure("PROVIDER_ERROR", "LLM_REQUEST", 399).httpStatus()).isNull();
    }

    @Test void wrappedTypedLlmErrorSurvivesWithoutRawWrapperMessage() {
        Throwable wrapped = new IllegalStateException("private-wrapper-message",
                new java.util.concurrent.CompletionException(new LlmCallException("PROVIDER_JSON_SCHEMA", 400)));
        AgentFailure failure = AgentFailure.from(wrapped, "LLM_REQUEST");
        assertThat(failure).isEqualTo(new AgentFailure("PROVIDER_JSON_SCHEMA", "LLM_REQUEST", 400));
        assertThat(failure.toString()).doesNotContain("private-wrapper-message");
    }

    @Test void stageFailureKeepsItsOriginalStageAcrossAsyncWrappers() {
        var original = new AgentFailure("MCP_UNAVAILABLE", "TOOL_EXECUTION", null);
        assertThat(AgentFailure.from(new RuntimeException("private", new AgentExecutionException(original)), "UNKNOWN"))
                .isEqualTo(original);
    }

    @Test void networkFailuresAreDistinctFromProviderHttpRefusal() {
        assertThat(AgentFailure.from(new IOException("private-network-detail"), "LLM_REQUEST"))
                .isEqualTo(new AgentFailure("NETWORK_ERROR", "LLM_REQUEST", null));
        assertThat(AgentFailure.from(new RuntimeException(new ResourceAccessException("private-url")), "COMPLIANCE_CHECKING"))
                .isEqualTo(new AgentFailure("NETWORK_ERROR", "COMPLIANCE_CHECKING", null));
    }

    @Test void unknownOrMissingCauseCannotInventProviderStatusOrToolFailure() {
        assertThat(AgentFailure.from(new RuntimeException("HTTP 400 private model body"), "LLM_REQUEST"))
                .isEqualTo(new AgentFailure("UNKNOWN", "LLM_REQUEST", null));
        assertThat(AgentFailure.from(null, null)).isEqualTo(new AgentFailure("UNKNOWN", "UNKNOWN", null));
    }

    @Test void iterationLimitAndTimeDeadlineHaveDifferentDiagnostics() {
        assertThat(AgentFailure.forStatus(AgentLoopStatus.MAX_ITERATIONS))
                .isEqualTo(new AgentFailure("ITERATION_LIMIT", "LLM_REQUEST", null));
        assertThat(AgentFailure.forStatus(AgentLoopStatus.DEADLINE_EXCEEDED))
                .isEqualTo(new AgentFailure("DEADLINE_EXCEEDED", "LLM_REQUEST", null));
        assertThat(AgentFailure.forStatus(AgentLoopStatus.TOOL_ERROR).stage()).isEqualTo("TOOL_EXECUTION");
        assertThat(AgentFailure.forStatus(AgentLoopStatus.VALIDATION_ERROR).stage()).isEqualTo("FINAL_VALIDATION");
    }
}
