package com.edu.agent.service;

import com.edu.agent.dto.FeedbackRequest;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.entity.InterventionFeedback;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.mapper.InterventionFeedbackMapper;
import com.edu.agent.service.impl.InterventionFeedbackServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * I-5：干预反馈 service 校验 + CSV 导出单测（纯 Mockito）。
 */
class InterventionFeedbackServiceTest {

    private final InterventionFeedbackMapper feedbackMapper = mock(InterventionFeedbackMapper.class);
    private final AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
    private final InterventionFeedbackServiceImpl service =
            new InterventionFeedbackServiceImpl(feedbackMapper, taskMapper);

    private FeedbackRequest req(Long taskId, Integer score, String outcome) {
        FeedbackRequest r = new FeedbackRequest();
        r.setTaskId(taskId);
        r.setScore(score);
        r.setOutcome(outcome);
        return r;
    }

    @Test
    void rejectsNullTaskId() {
        assertThatThrownBy(() -> service.submit(req(null, 3, null), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsScoreOutOfRange() {
        assertThatThrownBy(() -> service.submit(req(1L, 0, null), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(req(1L, 6, null), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidOutcome() {
        assertThatThrownBy(() -> service.submit(req(1L, 3, "great"), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingTask() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(req(99L, 3, "improved"), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitHappyFillsStudentIdFromTask() {
        AgentTask task = new AgentTask();
        task.setId(5L);
        task.setStudentId(42L);
        task.setStatus(TaskStatus.COMPLETED);
        task.setInterventionPlan("{\"immediate_actions\":[{\"action\":\"合成辅导\",\"owner\":\"导师\",\"deadline\":\"一周\"}]}");
        when(taskMapper.selectById(5L)).thenReturn(task);
        when(feedbackMapper.insert(any(InterventionFeedback.class))).thenAnswer(inv -> {
            ((InterventionFeedback) inv.getArgument(0)).setId(100L);
            return 1;
        });
        Long id = service.submit(req(5L, 4, "improved"), 7L);
        assertThat(id).isEqualTo(100L);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"PENDING", "RISK_ANALYZING", "KNOWLEDGE_RETRIEVING", "PLAN_GENERATING", "COMPLIANCE_CHECKING", "FAILED"})
    void rejectsNonFinalOrFailedTasksEvenWithPlan(TaskStatus status) {
        var task = new AgentTask(); task.setId(5L); task.setStatus(status);
        task.setInterventionPlan("{\"immediate_actions\":[{\"action\":\"合成辅导\"}]}");
        when(taskMapper.selectById(5L)).thenReturn(task);
        assertThatThrownBy(() -> service.submit(req(5L, 4, "improved"), 7L)).hasMessageContaining("终态");
        verify(feedbackMapper, never()).insert(any(InterventionFeedback.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"{}", "[]", "bad", "{\"summary\":\"LLM不可用转人工\",\"talk_outline\":\"待补全\"}"})
    void rejectsEmptyOrPlaceholderPlanAtTerminalStatus(String plan) {
        var task = new AgentTask(); task.setId(5L); task.setStatus(TaskStatus.COMPLETED); task.setInterventionPlan(plan);
        when(taskMapper.selectById(5L)).thenReturn(task);
        assertThatThrownBy(() -> service.submit(req(5L, 4, "improved"), 7L)).hasMessageContaining("有效干预方案");
        verify(feedbackMapper, never()).insert(any(InterventionFeedback.class));
    }

    @Test
    void rejectedButRealPlanCanReceiveHumanFollowUpAndCommentsAreBounded() {
        var task = new AgentTask(); task.setId(5L); task.setStatus(TaskStatus.REJECTED);
        task.setInterventionPlan("{\"long_term_plan\":[{\"phase\":\"1月\",\"goals\":[\"合成人工跟进\"]}]}");
        when(taskMapper.selectById(5L)).thenReturn(task);
        service.submit(req(5L, 4, "escalated"), 7L);
        var request = req(5L, 4, "escalated"); request.setComment("a".repeat(1001));
        assertThatThrownBy(() -> service.submit(request, 7L)).hasMessageContaining("1000");
    }

    @Test
    void rejectsBadMonthFormat() {
        assertThatThrownBy(() -> service.monthlyReport("2026/06"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportCsvEscapesCommaAndQuote() {
        InterventionFeedback f = new InterventionFeedback();
        f.setId(1L);
        f.setTaskId(5L);
        f.setStudentId(42L);
        f.setScore(4);
        f.setOutcome("improved");
        f.setComment("有进步, 但仍需\"持续\"关注");
        when(feedbackMapper.selectList(any())).thenReturn(List.of(f));

        String csv = service.exportCsv("2026-06");
        assertThat(csv).startsWith("id,task_id,student_id,counselor_id,score,outcome,comment,created_at\n");
        // 含逗号/引号的 comment 被双引号包裹且内部引号转义
        assertThat(csv).contains("\"有进步, 但仍需\"\"持续\"\"关注\"");
    }
}
