package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 服务入口鉴权门单测：纯 Mockito + MockHttpServletRequest，不起 Spring 上下文。
 * 覆盖匿名直连下游公网 URL 被拒、合法 JWT / 内部凭证放行、健康检查豁免不可被借道。
 */
class ServiceAuthFilterTest {

    private static final String SECRET = "internal-secret-at-least-32-chars-0001";

    private JwtUtil jwtUtil;
    private FilterChain chain;
    private ServiceAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        chain = mock(FilterChain.class);
        filter = new ServiceAuthFilter(jwtUtil, new InternalCallCredential(SECRET),
                new ObjectMapper().findAndRegisterModules());
    }

    /** DispatcherServlet 映射在 "/"，servletPath 即容器规范化后的完整路径。 */
    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setServletPath(path);
        return req;
    }

    private MockHttpServletResponse run(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test
    void anonymousDirectCall_returns401WithResultBody() throws Exception {
        // 复现漏洞场景：不带任何凭证直连下游公网 URL
        MockHttpServletResponse res = run(get("/data/dashboard/statistics"));

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("\"code\":401");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validJwt_passes() throws Exception {
        when(jwtUtil.validateToken("good")).thenReturn(true);
        MockHttpServletRequest req = get("/mental/overview");
        req.addHeader("Authorization", "Bearer good");

        MockHttpServletResponse res = run(req);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void invalidJwt_returns401_evenWithValidInternalCredential() throws Exception {
        // 带 token 即按端用户判定，不因同时带了内部凭证而放行（与 AccessGuard 同序）
        when(jwtUtil.validateToken("bad")).thenReturn(false);
        MockHttpServletRequest req = get("/student/1");
        req.addHeader("Authorization", "Bearer bad");
        req.addHeader(InternalCallCredential.HEADER, SECRET);

        assertThat(run(req).getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validInternalCredential_passesWithoutJwt() throws Exception {
        MockHttpServletRequest req = get("/student/1/academic");
        req.addHeader(InternalCallCredential.HEADER, SECRET);

        MockHttpServletResponse res = run(req);

        verify(chain).doFilter(req, res);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void wrongInternalCredential_returns401() throws Exception {
        MockHttpServletRequest req = get("/mental/student/assessments");
        req.addHeader(InternalCallCredential.HEADER, "guessed-value");

        assertThat(run(req).getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void unconfiguredCredential_rejectsTokenlessEvenWithEmptyHeader() throws Exception {
        // 普通 ObjectMapper 不认 LocalDateTime → 同时覆盖拒绝响应的兜底体
        filter = new ServiceAuthFilter(jwtUtil, new InternalCallCredential(""), new ObjectMapper());
        MockHttpServletRequest req = get("/student/ids");
        req.addHeader(InternalCallCredential.HEADER, "");

        MockHttpServletResponse res = run(req);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("\"code\":401");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void healthCheck_isExempt() throws Exception {
        MockHttpServletRequest health = get("/actuator/health");
        MockHttpServletResponse healthRes = run(health);
        verify(chain).doFilter(health, healthRes);

        MockHttpServletRequest probe = get("/actuator/health/liveness");
        MockHttpServletResponse probeRes = run(probe);
        verify(chain).doFilter(probe, probeRes);
    }

    @Test
    void healthExemption_cannotBeBorrowedViaDotSegments() throws Exception {
        // 原始 URI 以健康检查开头，但容器规范化后指向业务端点 → 不得豁免
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health/../../mental/overview");
        req.setServletPath("/mental/overview");

        assertThat(run(req).getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void otherActuatorEndpoints_requireCredentials() throws Exception {
        assertThat(run(get("/actuator/prometheus")).getStatus()).isEqualTo(401);
        assertThat(run(get("/actuator/healthz")).getStatus()).isEqualTo(401);  // 前缀相似但不是健康检查
        verify(chain, never()).doFilter(any(), any());
    }
}
