package com.edu.agent.controller;

import com.edu.agent.entity.AgentTask;
import com.edu.agent.service.AgentTaskService;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTaskController 越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 教职工面向端点（触发分析 / 看任务产物），学生越权 403 且不触达 service。
 */
class AgentTaskControllerTest {

    private static final String AUTH = "Bearer tok";

    private AgentTaskService agentTaskService;
    private JwtUtil jwtUtil;
    private AgentTaskController controller;

    @BeforeEach
    void setUp() {
        agentTaskService = mock(AgentTaskService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new AgentTaskController(agentTaskService, new AccessGuard(jwtUtil));
    }

    @Test
    void trigger_staff_ok() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("counselor"));
        when(agentTaskService.triggerTask(5L)).thenReturn(100L);

        Result<Long> r = controller.trigger(5L, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo(100L);
    }

    @Test
    void trigger_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<Long> r = controller.trigger(5L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(agentTaskService, never()).triggerTask(anyLong());
    }

    @Test
    void detail_student_forbidden() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<AgentTask> r = controller.detail(7L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(agentTaskService, never()).getTaskDetail(anyLong());
    }

    @Test
    void trigger_internalNoToken_ok() {
        when(agentTaskService.triggerTask(5L)).thenReturn(100L);

        Result<Long> r = controller.trigger(5L, null);

        assertThat(r.getCode()).isEqualTo(200);
        verify(agentTaskService).triggerTask(5L);
    }
}
