package com.edu.agent.security;

import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentSelfAuthFilter 单测：纯 Mockito + MockHttpServlet*。
 * 直连 8087 业务端点须带合法 token；豁免 actuator/_internal；可整体关闭。
 */
class AgentSelfAuthFilterTest {

    private JwtUtil jwtUtil;
    private AgentSelfAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        // 与 Spring 托管 ObjectMapper 对齐：注册 jsr310，使 Result.timestamp(LocalDateTime) 可序列化
        ObjectMapper om = new ObjectMapper();
        om.findAndRegisterModules();
        filter = new AgentSelfAuthFilter(jwtUtil, om);
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    private MockHttpServletResponse run(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, resp, chain);
        return resp;
    }

    private boolean passedThrough(MockHttpServletRequest req) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null; // chain 被调用即放行
    }

    @Test
    void businessPath_noToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/agent/api/v1/task/list");
        MockHttpServletResponse resp = run(req);
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void businessPath_invalidToken_returns401() throws Exception {
        when(jwtUtil.validateToken("bad")).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/agent/api/v1/task/list");
        req.addHeader("Authorization", "Bearer bad");
        assertThat(run(req).getStatus()).isEqualTo(401);
    }

    @Test
    void businessPath_validBearer_passes() throws Exception {
        when(jwtUtil.validateToken("good")).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/agent/api/v1/task/trigger/1");
        req.addHeader("Authorization", "Bearer good");
        assertThat(passedThrough(req)).isTrue();
    }

    @Test
    void businessPath_validTokenParam_passes_forSse() throws Exception {
        when(jwtUtil.validateToken("good")).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/agent/api/v1/warning/stream");
        req.addParameter("token", "good");
        assertThat(passedThrough(req)).isTrue();
    }

    @Test
    void actuator_exempt_passesWithoutToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(passedThrough(req)).isTrue();
    }

    @Test
    void internal_exempt_passesWithoutToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/agent/api/v1/_internal/loop/dry-run");
        assertThat(passedThrough(req)).isTrue();
    }

    @Test
    void disabled_passesEverything() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/agent/api/v1/task/list");
        assertThat(passedThrough(req)).isTrue();
    }
}
