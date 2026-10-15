package com.edu.agent.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.edu.agent.config.LangfuseClient;
import com.edu.agent.config.LlmCallException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLoopFailureReasonTest {
    @ParameterizedTest
    @CsvSource({"react,SCHEMA_COMPILATION,SCHEMA_COMPILATION", "react,JSON_GENERATION,JSON_GENERATION",
            "react,private-provider-body,UNKNOWN", "native,SCHEMA_COMPILATION,SCHEMA_COMPILATION",
            "native,JSON_GENERATION,JSON_GENERATION", "native,private-provider-body,UNKNOWN"})
    void logsOnlyFixedReasonAndPreservesExistingFailureContract(String protocol, String suppliedReason, String expectedReason) {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        CompletionException failure = new CompletionException("private-wrapper-detail",
                new LlmCallException("PROVIDER_JSON_SCHEMA", 400, suppliedReason));
        if ("native".equals(protocol)) {
            when(client.prompt().system(anyString()).user(anyString()).toolCallbacks(anyList()).call().content()).thenThrow(failure);
        } else {
            when(client.prompt().system(anyString()).user(anyString()).call().content()).thenThrow(failure);
        }
        AgentLoop loop = new AgentLoop(client, mock(LangfuseClient.class));
        ReflectionTestUtils.setField(loop, "protocol", protocol);
        Logger logger = (Logger) LoggerFactory.getLogger(AgentLoop.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            AgentLoopResult result = loop.run(new AgentLoopRequest("private-system-prompt", "private-student-profile",
                    List.of(), 3, "reason-test"));
            assertThat(result.status()).isEqualTo(AgentLoopStatus.LLM_ERROR);
            assertThat(result.failure()).isEqualTo(new AgentFailure("PROVIDER_JSON_SCHEMA", "LLM_REQUEST", 400));
            assertThat(result.iterations()).isEqualTo(1);
            List<ILoggingEvent> diagnosticLogs = captured.list.stream()
                    .filter(event -> event.getFormattedMessage().contains("reason=")).toList();
            assertThat(diagnosticLogs).hasSize(1);
            assertThat(diagnosticLogs.get(0).getFormattedMessage())
                    .contains("code=PROVIDER_JSON_SCHEMA", "httpStatus=400", "reason=" + expectedReason)
                    .doesNotContain("private-provider-body", "private-wrapper-detail", "private-system-prompt", "private-student-profile");
            assertThat(diagnosticLogs.get(0).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }
}
