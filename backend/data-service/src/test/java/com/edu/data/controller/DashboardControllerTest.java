package com.edu.data.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import com.edu.data.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardController 越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 全校聚合看板仅教职工或内网可见；学生越权 403 且不触达 service。
 */
class DashboardControllerTest {

    private static final String AUTH = "Bearer tok";

    private DashboardService dashboardService;
    private JwtUtil jwtUtil;
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new DashboardController(dashboardService, new AccessGuard(jwtUtil));
    }

    @Test
    void statistics_staff_ok() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("teacher"));
        when(dashboardService.getStatistics()).thenReturn(Map.of("k", "v"));

        Result<Map<String, Object>> r = controller.statistics(AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        verify(dashboardService).getStatistics();
    }

    @Test
    void statistics_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<Map<String, Object>> r = controller.statistics(AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(dashboardService, never()).getStatistics();
    }

    @Test
    void trend_internalNoToken_ok() {
        when(dashboardService.getTrend("week")).thenReturn(Map.of("k", "v"));

        Result<Map<String, Object>> r = controller.trend("week", null);

        assertThat(r.getCode()).isEqualTo(200);
        verify(dashboardService).getTrend("week");
    }
}
