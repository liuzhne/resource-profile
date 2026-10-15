package com.edu.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCallExceptionTest {
    @Test void twoArgumentConstructorPreservesExistingMessageAndDefaultsToUnknown() {
        LlmCallException failure = new LlmCallException("PROVIDER_JSON_SCHEMA", 400);
        assertThat(failure.reason()).isEqualTo("UNKNOWN");
        assertThat(failure.getMessage()).isEqualTo("LLM failure: PROVIDER_JSON_SCHEMA; HTTP 400");
        assertThat(failure.getCause()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCHEMA_COMPILATION", "JSON_GENERATION", "UNKNOWN"})
    void fixedReasonDoesNotAlterExistingExceptionMessage(String reason) {
        LlmCallException failure = new LlmCallException("PROVIDER_JSON_SCHEMA", 400, reason);
        assertThat(failure.reason()).isEqualTo(reason);
        assertThat(failure.getMessage()).isEqualTo("LLM failure: PROVIDER_JSON_SCHEMA; HTTP 400");
        assertThat(failure.getCause()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"private-provider-body", "schema_compilation", " SCHEMA_COMPILATION", "JSON_GENERATION\nprivate-model-reply"})
    void unapprovedReasonIsNeitherRetainedNorIncludedInMessage(String reason) {
        LlmCallException failure = new LlmCallException("PROVIDER_JSON_SCHEMA", 400, reason);
        assertThat(failure.reason()).isEqualTo("UNKNOWN");
        assertThat(failure.getMessage()).isEqualTo("LLM failure: PROVIDER_JSON_SCHEMA; HTTP 400");
        assertThat(failure.getCause()).isNull();
    }

    @Test void boundedCauseChainFindsOnlyTypedReason() {
        Throwable wrapped = new LlmCallException("PROVIDER_JSON_SCHEMA", 400, "JSON_GENERATION");
        for (int depth = 0; depth < 11; depth++) wrapped = new CompletionException("private-wrapper-detail", wrapped);
        assertThat(LlmCallException.reasonFrom(wrapped)).isEqualTo("JSON_GENERATION");
        assertThat(LlmCallException.reasonFrom(new CompletionException(wrapped))).isEqualTo("UNKNOWN");
    }

    @Test void nullUntrustedMessagesAndCyclicCausesRemainUnknown() {
        assertThat(LlmCallException.reasonFrom(null)).isEqualTo("UNKNOWN");
        assertThat(LlmCallException.reasonFrom(new RuntimeException("SCHEMA_COMPILATION json_validate_failed private-body")))
                .isEqualTo("UNKNOWN");
        RuntimeException first = new RuntimeException("private-first");
        RuntimeException second = new RuntimeException("private-second", first);
        first.initCause(second);
        assertThat(LlmCallException.reasonFrom(first)).isEqualTo("UNKNOWN");
    }
}
