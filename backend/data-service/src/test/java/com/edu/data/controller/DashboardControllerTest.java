package com.edu.data.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.InternalCallCredential;
import com.edu.common.util.JwtUtil;
import com.edu.data.service.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardController 越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 全校聚合看板仅教职工或已验证的内部调用可见；学生越权 / 匿名直连 403 且不触达 service。
 */
class DashboardControllerTest {

    private static final String AUTH = "Bearer tok";
    private static final String INTERNAL_TOKEN = "internal-secret-at-least-32-chars-0001";

    private DashboardService dashboardService;
    private JwtUtil jwtUtil;
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new DashboardController(dashboardService,
                new AccessGuard(jwtUtil, new InternalCallCredential(INTERNAL_TOKEN)));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 模拟 agent-service 的内部 Feign 调用：不带 token，只带合法 X-Internal-Token。 */
    private static void asInternalCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalCallCredential.HEADER, INTERNAL_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
    void statistics_anonymous_forbidden_andNoServiceCall() {
        // 漏洞回归（2026-09-14 线上实测）：不带 token 直连 data-service 该端点曾返回 200
        Result<Map<String, Object>> r = controller.statistics(null);

        assertThat(r.getCode()).isEqualTo(403);
        verify(dashboardService, never()).getStatistics();
    }

    @Test
    void trend_internalCredential_ok() {
        asInternalCall();
        when(dashboardService.getTrend("week")).thenReturn(Map.of("k", "v"));

        Result<Map<String, Object>> r = controller.trend("week", null);

        assertThat(r.getCode()).isEqualTo(200);
        verify(dashboardService).getTrend("week");
    }
}
