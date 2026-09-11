package com.edu.agent.controller;

import com.edu.agent.dto.FeedbackRequest;
import com.edu.agent.service.InterventionFeedbackService;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InterventionFeedbackController 越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 干预反馈教职工面向；学生越权 403 且不触达 service。
 */
class InterventionFeedbackControllerTest {

    private static final String AUTH = "Bearer tok";

    private InterventionFeedbackService feedbackService;
    private JwtUtil jwtUtil;
    private InterventionFeedbackController controller;

    @BeforeEach
    void setUp() {
        feedbackService = mock(InterventionFeedbackService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new InterventionFeedbackController(feedbackService, new AccessGuard(jwtUtil));
    }

    @Test
    void submit_staff_ok() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("counselor"));
        when(feedbackService.submit(any(FeedbackRequest.class), anyLong())).thenReturn(1L);

        Result<Long> r = controller.submit(new FeedbackRequest(), AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo(1L);
    }

    @Test
    void submit_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<Long> r = controller.submit(new FeedbackRequest(), AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(feedbackService, never()).submit(any(), anyLong());
    }

    @Test
    void report_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<?> r = controller.report("2026-06", AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(feedbackService, never()).monthlyReport(anyString());
    }
}
