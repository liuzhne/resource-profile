package com.edu.agent.security;

import com.edu.agent.config.McpClientConfig;
import com.edu.common.config.JwtProperties;
import com.edu.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AUTHZ-MCP-20261013：agent-service 每次 MCP 出站请求都携带调用者身份。 */
class McpCallerIdentityPropagationTest {

    private JwtUtil jwtUtil;
    private McpCallerTokenResolver resolver;

    @BeforeEach
    void setUp() {
        JwtProperties p = new JwtProperties();
        p.setSecret("unit-test-secret-at-least-32-bytes-long!!");
        jwtUtil = new JwtUtil(p);
        jwtUtil.init();
        resolver = new McpCallerTokenResolver(jwtUtil);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void userCaller_forwardsOriginalJwt() {
        String header = "Bearer user-jwt";
        String got = McpCallerContext.callAs(McpCaller.user(header, 11L, "9"), resolver::currentAuthorization);
        assertThat(got).isEqualTo(header);
    }

    @Test
    void systemCaller_mintsScopedTokenBoundToStudent() {
        String got = McpCallerContext.callAs(McpCaller.system(11L, "scheduler"), resolver::currentAuthorization);
        assertThat(got).startsWith("Bearer ");
        Claims c = jwtUtil.parseTokenForAudience(got.substring(7), JwtProperties.AUDIENCE_MCP_STUDENT_DATA, false);
        assertThat(c.getSubject()).isEqualTo("system:agent-task");
        assertThat(c.get(JwtUtil.CLAIM_SCOPE).toString()).isEqualTo("[student-data:read]");
        assertThat(((Number) c.get(JwtUtil.CLAIM_STUDENT_ID)).longValue()).isEqualTo(11L);
        assertThat(c.get(JwtUtil.CLAIM_INITIATOR)).isEqualTo("scheduler");
        assertThat(c.getAudience()).containsExactly(JwtProperties.AUDIENCE_MCP_STUDENT_DATA);
    }

    @Test
    void noCaller_inHttpRequest_forwardsRequestBearer_otherwiseNone() {
        assertThat(resolver.currentAuthorization()).isNull();
        assertThat(resolver.currentTransportContext()).isSameAs(McpTransportContext.EMPTY);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer req-jwt");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        assertThat(resolver.currentAuthorization()).isEqualTo("Bearer req-jwt");
        assertThat(resolver.currentTransportContext().get(McpCallerTokenResolver.CONTEXT_KEY)).isEqualTo("Bearer req-jwt");
    }

    @Test
    void callAs_restoresPreviousCaller() {
        McpCaller outer = McpCaller.system(1L, "a");
        McpCallerContext.runAs(outer, () -> {
            McpCallerContext.runAs(McpCaller.system(2L, "b"), () ->
                    assertThat(McpCallerContext.current().studentId()).isEqualTo(2L));
            assertThat(McpCallerContext.current()).isSameAs(outer);
        });
        assertThat(McpCallerContext.current()).isNull();
        assertThat(outer.toString()).doesNotContain("Bearer");
    }

    @Test
    void httpCustomizer_attachesAuthorization_fromTransportContext_orThread() {
        McpClientConfig config = new McpClientConfig();
        ReflectionTestUtils.setField(config, "mcpToken", "");
        McpSyncHttpClientRequestCustomizer customizer = config.mcpTokenRequestCustomizer(resolver);

        HttpRequest.Builder b1 = HttpRequest.newBuilder(URI.create("http://x/mcp"));
        customizer.customize(b1, "POST", URI.create("http://x/mcp"), "{}",
                McpTransportContext.create(Map.of(McpCallerTokenResolver.CONTEXT_KEY, "Bearer ctx-jwt")));
        assertThat(b1.build().headers().firstValue("Authorization")).contains("Bearer ctx-jwt");

        HttpRequest.Builder b2 = HttpRequest.newBuilder(URI.create("http://x/mcp"));
        McpCallerContext.runAs(McpCaller.user("Bearer thread-jwt", 1L, "9"), () ->
                customizer.customize(b2, "POST", URI.create("http://x/mcp"), "{}", McpTransportContext.EMPTY));
        assertThat(b2.build().headers().firstValue("Authorization")).contains("Bearer thread-jwt");

        HttpRequest.Builder b3 = HttpRequest.newBuilder(URI.create("http://x/mcp"));
        customizer.customize(b3, "POST", URI.create("http://x/mcp"), "{}", McpTransportContext.EMPTY);
        assertThat(b3.build().headers().firstValue("Authorization")).isEmpty();
    }
}
