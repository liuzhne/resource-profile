package com.edu.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.edu.agent.config.LangfuseClient;
import com.edu.agent.config.LlmCallException;
import com.edu.agent.core.AgentFailure;
import com.edu.agent.core.AgentLoop;
import com.edu.agent.core.SubAgentRegistry;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.feign.AiInferenceClient;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.service.StudentPortraitAggregator;
import com.edu.agent.skill.SkillLoader;
import com.edu.agent.sse.WarningPublisher;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Drives the real task/AgentLoop boundary; the in-memory row emulates guarded mapper updates, no inference. */
class AgentTaskTerminalTest {
    private static final List<TaskStatus> TERMINAL = List.of(TaskStatus.COMPLETED, TaskStatus.REJECTED, TaskStatus.FAILED);
    private AgentTask row;
    private AgentTaskMapper mapper;
    private WarningPublisher warnings;
    private ChatClient chat;
    private LangfuseClient tracing;
    private ToolCallback tool;
    private AiInferenceClient inference;
    private com.edu.agent.service.AgentGenerationService generation;
    private AgentTaskServiceImpl service;
    private final List<LambdaUpdateWrapper<AgentTask>> failureUpdates = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "terminal-test"), AgentTask.class);
        failureUpdates.clear();
        row = new AgentTask();
        row.setId(42L); row.setStudentId(7L); row.setStatus(TaskStatus.PENDING); row.setDeleted(0);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        mapper = mock(AgentTaskMapper.class);
        when(mapper.selectById(42L)).thenAnswer(call -> { var read = new AgentTask(); BeanUtils.copyProperties(row, read); return read; });
        when(mapper.updateById(any(AgentTask.class))).thenAnswer(call -> {
            AgentTask update = call.getArgument(0);
            if (update.getTraceId() != null) row.setTraceId(update.getTraceId());
            if (update.getFailureCode() != null) {
                row.setFailureCode(update.getFailureCode());
                row.setFailureStage(update.getFailureStage());
                row.setFailureHttpStatus(update.getFailureHttpStatus());
            }
            return 1;
        });
        when(mapper.updateStatus(eq(42L), anyString(), anyString())).thenAnswer(call -> {
            TaskStatus expected = TaskStatus.valueOf(call.getArgument(1));
            if (row.getStatus() != expected || row.getDeleted() != 0) return 0;
            row.setStatus(TaskStatus.valueOf(call.getArgument(2)));
            row.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            if (TERMINAL.contains(row.getStatus()) && row.getCompletedAt() == null) row.setCompletedAt(row.getUpdatedAt());
            return 1;
        });
        when(mapper.markTerminalTime(42L)).thenAnswer(call -> {
            if (row.getDeleted() != 0 || !TERMINAL.contains(row.getStatus()) || row.getCompletedAt() != null) return 0;
            row.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC)); return 1;
        });
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
            LambdaUpdateWrapper<AgentTask> update = call.getArgument(1);
            String sets = update.getSqlSet();
            update.getSqlSegment(); // Expand real MyBatis lambda conditions/parameter metadata before inspecting.
            if (sets.contains("failure_code=")) {
                failureUpdates.add(update);
                if (TERMINAL.contains(row.getStatus()) || row.getDeleted() != 0) return 0;
                row.setStatus((TaskStatus) assigned(update, "status"));
                if (row.getCompletedAt() == null) row.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
                row.setFailureCode((String) assigned(update, "failure_code"));
                row.setFailureStage((String) assigned(update, "failure_stage"));
                row.setFailureHttpStatus((Integer) assigned(update, "failure_http_status"));
            } else if (sets.contains("retrieved_knowledge=")) {
                row.setRetrievedKnowledge((String) assigned(update, "retrieved_knowledge"));
            } else if (sets.contains("compliance_audit=")) {
                row.setComplianceAudit((String) assigned(update, "compliance_audit"));
            }
            return 1;
        });
        warnings = mock(WarningPublisher.class);
        chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        tracing = mock(LangfuseClient.class);
        AgentLoop loop = new AgentLoop(chat, tracing);
        tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(ToolDefinition.builder().name("get_student_profile")
                .description("synthetic fixture").inputSchema("{}").build());
        ObjectProvider<ToolCallbackProvider> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(() -> new ToolCallback[]{tool});
        SkillLoader skills = mock(SkillLoader.class); when(skills.composeActiveSkillsPrompt()).thenReturn("");
        SubAgentRegistry subagents = mock(SubAgentRegistry.class); when(subagents.subAgentTools(any())).thenReturn(List.of());
        inference = mock(AiInferenceClient.class);
        generation = mock(com.edu.agent.service.AgentGenerationService.class);
        StudentPortraitAggregator portraits = mock(StudentPortraitAggregator.class);
        when(portraits.buildMaskedProfile("7")).thenReturn("{}");
        service = new AgentTaskServiceImpl(mapper, generation, inference, portraits, null, null, warnings,
                loop, providers, skills, subagents, Runnable::run);
        ReflectionTestUtils.setField(service, "agentLoopSystemPrompt", "synthetic task test protocol");
    }

    private static Object assigned(LambdaUpdateWrapper<AgentTask> update, String column) {
        var match = Pattern.compile(Pattern.quote(column) + "=\\#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}")
                .matcher(update.getSqlSet());
        assertThat(match.find()).as("assignment for %s in %s", column, update.getSqlSet()).isTrue();
        return update.getParamNameValuePairs().get(match.group(1));
    }

    @ParameterizedTest
    @CsvSource({"PROVIDER_JSON_SCHEMA,400", "PROVIDER_RATE_LIMIT,429", "PROVIDER_AUTH,403"})
    @SuppressWarnings("unchecked")
    void llmFailurePersistsExactSafeCategoryHttpUtcCompletionAndLinkedTrace(String code, int httpStatus) {
        when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenThrow(new LlmCallException(code, httpStatus));
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        ReflectionTestUtils.invokeMethod(service, "doExecuteAgentLoop", 42L);
        assertThat(row.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(row.getFailureCode()).isEqualTo(code);
        assertThat(row.getFailureStage()).isEqualTo("LLM_REQUEST");
        assertThat(row.getFailureHttpStatus()).isEqualTo(httpStatus);
        assertThat(row.getCompletedAt()).isBetween(start, LocalDateTime.now(ZoneOffset.UTC));
        assertThat(row.getTraceId()).matches("[0-9a-f]{32}");
        assertThat(row.getRetrievedKnowledge()).contains("\"executed\":false");
        assertThat(row.getRiskAnalysisResult()).isNull(); assertThat(row.getInterventionPlan()).isNull();
        verify(tool, never()).call(anyString());
        verify(warnings).publishTerminal(42L, "FAILED", null, 7L);
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(tracing).traceGeneration(eq("agent.loop"), eq("unknown"), any(), isNull(), eq(0L), eq(0L),
                metadata.capture(), any(Instant.class), any(Instant.class));
        assertThat(metadata.getValue()).containsEntry("traceId", row.getTraceId()).containsEntry("status", "LLM_ERROR")
                .containsEntry("errorCode", code).containsEntry("errorStage", "LLM_REQUEST").containsEntry("httpStatus", httpStatus);
    }

    @Test void unknownFailureDoesNotStoreRawProviderExceptionOrInventHttpStatus() {
        when(chat.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new NonTransientAiException("private-prompt-or-provider-response"));
        ReflectionTestUtils.invokeMethod(service, "doExecuteAgentLoop", 42L);
        assertThat(row.getFailureCode()).isEqualTo("UNKNOWN");
        assertThat(row.getFailureStage()).isEqualTo("LLM_REQUEST");
        assertThat(row.getFailureHttpStatus()).isNull();
        assertThat(row.toString()).doesNotContain("private-prompt-or-provider-response");
    }

    @Test void javaAuditProviderFailureRejectsTaskAndKeepsTypedDiagnosticsWithoutCallingPython() {
        row.setStatus(TaskStatus.PLAN_GENERATING); row.setInterventionPlan("{}");
        when(generation.complianceAudit(anyMap())).thenThrow(new LlmCallException("PROVIDER_RATE_LIMIT",429));
        ReflectionTestUtils.invokeMethod(service, "doExecuteAgentLoop", 42L);
        assertThat(row.getStatus()).isEqualTo(TaskStatus.REJECTED);
        assertThat(row.getFailureCode()).isEqualTo("PROVIDER_RATE_LIMIT");
        assertThat(row.getFailureHttpStatus()).isEqualTo(429);
        assertThat(row.getFailureStage()).isEqualTo("COMPLIANCE_CHECKING");
        verifyNoInteractions(inference);
    }

    @Test void genuineAuditRejectionSetsUtcEndTimeWithoutPretendingSystemFailure() {
        row.setStatus(TaskStatus.PLAN_GENERATING); row.setInterventionPlan("{}");
        when(generation.complianceAudit(anyMap())).thenReturn("{\"audit_passed\":false}");
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        ReflectionTestUtils.invokeMethod(service, "doExecuteAgentLoop", 42L);
        assertThat(row.getStatus()).isEqualTo(TaskStatus.REJECTED);
        assertThat(row.getCompletedAt()).isBetween(start, LocalDateTime.now(ZoneOffset.UTC));
        assertThat(row.getFailureCode()).isNull();
        verify(warnings).publishTerminal(42L, "REJECTED", null, 7L);
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"audit_passed\":\"true\"}", "{}", "not-json"})
    void malformedAuditCannotPassAndPersistsFixedValidationDiagnostic(String response) {
        row.setStatus(TaskStatus.PLAN_GENERATING); row.setInterventionPlan("{}");
        when(generation.complianceAudit(anyMap())).thenReturn(response);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        ReflectionTestUtils.invokeMethod(service, "doExecuteAgentLoop", 42L);
        assertThat(row.getStatus()).isEqualTo(TaskStatus.REJECTED);
        assertThat(row.getCompletedAt()).isBetween(start, LocalDateTime.now(ZoneOffset.UTC));
        assertThat(row.getFailureCode()).isEqualTo("OUTPUT_INVALID");
        assertThat(row.getFailureStage()).isEqualTo("COMPLIANCE_CHECKING");
        assertThat(row.getFailureHttpStatus()).isNull();
        assertThat(row.getComplianceAudit()).contains("\"audit_passed\":false", "\"manual_review_required\":true")
                .doesNotContain("not-json");
        verify(warnings).publishTerminal(42L, "REJECTED", null, 7L);
        verifyNoInteractions(chat);
    }

    @Test void repeatedTerminalDeliveryCannotRewriteTimeOutcomeOrFirstFailure() {
        LocalDateTime firstEnd = LocalDateTime.of(2026, 10, 1, 1, 2, 3);
        for (TaskStatus status : TERMINAL) {
            row.setStatus(status); row.setCompletedAt(firstEnd); row.setFailureCode("PROVIDER_JSON_SCHEMA");
            ReflectionTestUtils.invokeMethod(service, "completeTask", 42L);
            ReflectionTestUtils.invokeMethod(service, "failTask", 42L, new AgentFailure("PROVIDER_RATE_LIMIT", "LLM_REQUEST", 429));
            assertThat(row.getCompletedAt()).as(status.name()).isEqualTo(firstEnd);
            assertThat(row.getStatus()).isEqualTo(status);
            assertThat(row.getFailureCode()).isEqualTo("PROVIDER_JSON_SCHEMA");
        }
        for (var update : failureUpdates) {
            assertThat(update.getSqlSegment()).contains("status NOT IN", "deleted =", "id =");
            assertThat(update.getParamNameValuePairs().values()).contains(TaskStatus.COMPLETED, TaskStatus.REJECTED, TaskStatus.FAILED);
            assertThat(update.getSqlSet()).contains("completed_at=COALESCE(completed_at,UTC_TIMESTAMP())");
        }
    }

    @Test void completionRepairDoesNotTimestampActiveOrDeletedTasks() {
        ReflectionTestUtils.invokeMethod(service, "completeTask", 42L);
        assertThat(row.getCompletedAt()).isNull();
        row.setStatus(TaskStatus.REJECTED); row.setDeleted(1);
        ReflectionTestUtils.invokeMethod(service, "completeTask", 42L);
        assertThat(row.getCompletedAt()).isNull();
    }

    @Test void annotatedMapperMakesTerminalStatusAndCompletionAtomicAndRepairConditional() throws Exception {
        String transition = String.join(" ", AgentTaskMapper.class.getMethod("updateStatus", Long.class, String.class, String.class)
                .getAnnotation(Update.class).value());
        assertThat(transition).contains("status = #{newStatus}", "completed_at = CASE", "'COMPLETED','FAILED','REJECTED'",
                "COALESCE(completed_at,UTC_TIMESTAMP())", "status = #{oldStatus}", "deleted = 0");
        String repair = String.join(" ", AgentTaskMapper.class.getMethod("markTerminalTime", Long.class).getAnnotation(Update.class).value());
        assertThat(repair).contains("UTC_TIMESTAMP()", "status IN ('COMPLETED','REJECTED','FAILED')", "completed_at IS NULL", "deleted=0");
    }
}
