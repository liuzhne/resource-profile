package com.edu.mcp.student.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MCP 内部预共享 token 校验（A3 余项纵深防御）。
 *
 * <p>student-data MCP（8094）的 4 个 tool 直吐学生画像/学业/心理/出勤等敏感数据。端口虽已绑
 * {@code 127.0.0.1}，本 filter 在其上加应用层凭证：{@code educare.mcp.token} 非空时，{@code /mcp}
 * 请求必须带匹配的 {@code X-MCP-Token} 头（由 agent-service 的 McpClientConfig 附上），否则 401。
 *
 * <p>token 为空（默认）→ 放行，仅靠网络隔离，demo 默认链路零改动。
 */
@Slf4j
@Component
public class McpTokenFilter extends OncePerRequestFilter {

    @Value("${educare.mcp.token:}")
    private String mcpToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = mcpToken == null ? "" : mcpToken.strip();
        if (!token.isEmpty() && request.getRequestURI().startsWith("/mcp")) {
            if (!token.equals(request.getHeader("X-MCP-Token"))) {
                log.debug("MCP token 校验失败 uri={}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
