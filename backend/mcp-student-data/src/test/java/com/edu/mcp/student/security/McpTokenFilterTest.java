package com.edu.mcp.student.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class McpTokenFilterTest {

    private McpTokenFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new McpTokenFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    void configuredToken_rejectsMissingHeader() throws Exception {
        ReflectionTestUtils.setField(filter, "mcpToken", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void configuredToken_allowsMatchingHeader() throws Exception {
        ReflectionTestUtils.setField(filter, "mcpToken", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("X-MCP-Token", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void emptyConfiguration_allowsMcpForDevelopment() throws Exception {
        ReflectionTestUtils.setField(filter, "mcpToken", "  ");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenDoesNotGateHealthEndpoint() throws Exception {
        ReflectionTestUtils.setField(filter, "mcpToken", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
