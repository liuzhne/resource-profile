package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import com.edu.agent.config.LlmCallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Provider JSON-generation repairs use real subsequent turns and never fabricate or replay tools. */
class AgentLoopGenerationRepairTest {
    private static final String ACTION = """
            {"thought":"取基础档案","plan":null,"action":{"tool":"get_student_profile","args":{"studentId":750}},"final_answer":null}
            """;
    private static final String FINAL = """
            {"thought":"依据测试工具返回","plan":null,"action":null,"final_answer":{
              "risk_analysis":{"risk_level":"low","risk_score":20,"primary_risk_type":"学业滑坡",
                "root_cause_analysis":"依据合成工具数据","key_indicators":["测试GPA为2.4"],
                "recommended_intervention_types":["学业辅导"],"urgency_reason":"常规跟进"},
              "intervention_plan":{"report_title":"合成验收方案","summary":"进行常规学业支持",
                "immediate_actions":[{"action":"沟通学习困难","owner":"学业导师","deadline":"1周内"}],
                "long_term_plan":[],"talk_outline":"了解学习困难","resources":[],"references":[]}}}
            """;

    private static RuntimeException generationFailure() {
        return new CompletionException("private-provider-detail",
                new LlmCallException("PROVIDER_JSON_SCHEMA", 400, "JSON_GENERATION"));
    }
    private static AgentLoop loop(ChatClient client) {
        AgentLoop loop = new AgentLoop(client, mock(LangfuseClient.class));
        ReflectionTestUtils.setField(loop, "structuredOutputEnabled", true);
        return loop;
    }
    private static ToolCallback profile(AtomicInteger calls) {
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("get_student_profile").description("Synthetic profile fixture")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"studentId\":{\"type\":\"integer\"}},\"required\":[\"studentId\"]}").build();
            }
            @Override public String call(String input) {
                assertThat(input).contains("\"studentId\":750");
                calls.incrementAndGet();
                return "{\"studentId\":750,\"gpa\":2.4}";
            }
        };
    }
    private static FinalAnswerValidator strictFinal() {
        return answer -> AgentLoopOutputSchema.parseFinalAnswer(answer) != null
                ? FinalAnswerValidator.Result.ok() : FinalAnswerValidator.Result.invalid("Complete final objects required");
    }

    @Test
    void singleGeneration400ThenActualToolAndValidatedFinalCompletes() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(generationFailure()).thenReturn(ACTION, FINAL);
        AtomicInteger toolCalls = new AtomicInteger();
        AgentLoopResult result = loop(client).run(new AgentLoopRequest("Strict task schema", "Synthetic test",
                List.of(profile(toolCalls)), 4, "generation-test"), strictFinal());

        assertThat(result.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(result.iterations()).isEqualTo(3);
        assertThat(result.finalAnswer()).isNotNull();
        assertThat(AgentLoopOutputSchema.parseFinalAnswer(result.finalAnswer())).isNotNull();
        assertThat(toolCalls).hasValue(1);
        AgentTrace failedTurn = result.traces().get(0);
        assertThat(failedTurn.rawLlmOutput()).isEmpty();
        assertThat(failedTurn.toolName()).isNull();
        assertThat(failedTurn.toolResult()).contains("JSON_GENERATION_INVALID", "不重复已成功执行的工具")
                .doesNotContain("private-provider-detail", "750", "2.4");
        assertThat(HistoryCompactor.compose("Synthetic test", List.of(failedTurn))).contains("Observation: JSON_GENERATION_INVALID");
        assertThat(result.traces().get(1).toolName()).isEqualTo("get_student_profile");
    }

    @Test
    void secondGeneration400FailsWithoutAnotherRepair() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AtomicInteger modelCalls = new AtomicInteger();
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenAnswer(invocation -> { modelCalls.incrementAndGet(); throw generationFailure(); });
        AgentLoopResult result = loop(client).run(new AgentLoopRequest("s", "u", List.of(), 8, "double-generation"));
        assertThat(result.status()).isEqualTo(AgentLoopStatus.LLM_ERROR);
        assertThat(result.failure()).isEqualTo(new AgentFailure("PROVIDER_JSON_SCHEMA", "LLM_REQUEST", 400));
        assertThat(result.finalAnswer()).isNull();
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void laterGenerationRepairDoesNotReplayPreviouslyExecutedTool() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(ACTION).thenThrow(generationFailure()).thenReturn(FINAL);
        AtomicInteger toolCalls = new AtomicInteger();
        AgentLoopResult result = loop(client).run(new AgentLoopRequest("s", "u", List.of(profile(toolCalls)),
                4, "later-generation"), strictFinal());
        assertThat(result.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(result.iterations()).isEqualTo(3);
        assertThat(toolCalls).hasValue(1);
    }

    @ParameterizedTest
    @CsvSource({"PROVIDER_AUTH,401,JSON_GENERATION", "PROVIDER_RATE_LIMIT,429,JSON_GENERATION",
            "PROVIDER_JSON_SCHEMA,400,SCHEMA_COMPILATION", "PROVIDER_JSON_SCHEMA,400,UNKNOWN",
            "PROVIDER_JSON_SCHEMA,500,JSON_GENERATION"})
    void otherErrorsKeepTheirActualFailureWithoutGenerationRepair(String code, int status, String reason) {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AtomicInteger modelCalls = new AtomicInteger();
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenAnswer(invocation -> { modelCalls.incrementAndGet(); throw new LlmCallException(code, status, reason); });
        AgentLoopResult result = loop(client).run(new AgentLoopRequest("s", "u", List.of(), 8, "other-error"));
        assertThat(result.status()).isEqualTo(AgentLoopStatus.LLM_ERROR);
        assertThat(result.failure()).isEqualTo(new AgentFailure(code, "LLM_REQUEST", status));
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void exhaustedIterationBudgetCannotAdmitAnExtraRepairCall() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AtomicInteger modelCalls = new AtomicInteger();
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenAnswer(invocation -> { modelCalls.incrementAndGet(); throw generationFailure(); });
        AgentLoopResult result = loop(client).run(new AgentLoopRequest("s", "u", List.of(), 1, "iteration-bound"));
        assertThat(result.status()).isEqualTo(AgentLoopStatus.LLM_ERROR);
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void expiredDeadlineCannotAdmitAnExtraRepairCall() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AtomicInteger modelCalls = new AtomicInteger();
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenAnswer(invocation -> {
                    modelCalls.incrementAndGet();
                    Thread.sleep(1100);
                    throw generationFailure();
                });
        try (var ignored = AgentDeadline.start(1000)) {
            AgentLoopResult result = loop(client).run(new AgentLoopRequest("s", "u", List.of(), 8, "deadline-bound"));
            assertThat(result.status()).isEqualTo(AgentLoopStatus.DEADLINE_EXCEEDED);
            assertThat(result.finalAnswer()).isNull();
        }
        assertThat(modelCalls).hasValue(1);
    }
}
