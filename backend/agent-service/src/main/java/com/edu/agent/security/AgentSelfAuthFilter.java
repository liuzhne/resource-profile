package com.edu.agent.security;

import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * agent-service 自身鉴权门（A3 纵深防御）。
 *
 * <p><b>为何需要</b>：8087 虽已绑 {@code 127.0.0.1}（禁公网），但 {@code AccessGuard} 的越权判定对
 * 「无 token」走<b>内网信任放行</b> —— 即同宿主进程直连 8087 业务端点（绕过网关）可无凭证读 AI 任务/报告。
 * 本 filter 补上这层：直连 8087 的业务请求也必须带合法 JWT（签名+未过期），否则 401。网关转发的请求
 * 本就携带 token，验证通过照常放行，故对正常链路零影响。
 *
 * <p><b>豁免</b>：{@code /actuator/}（容器健康检查/指标）、{@code /_internal/}（本地手测端点，仅
 * 127.0.0.1 可达、网关侧已 403）。token 双轨：{@code Authorization: Bearer} 或 {@code ?token=}（SSE）。
 *
 * <p>开关 {@code educare.agent.self-auth.enabled}（默认开）；本地免登录联调可置 false。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AgentSelfAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${educare.agent.self-auth.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!enabled || isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            deny(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        return path.startsWith("/actuator/") || path.contains("/_internal/");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        String param = request.getParameter("token");
        return (param != null && !param.isBlank()) ? param : null;
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body;
        try {
            body = objectMapper.writeValueAsString(Result.error(401, "未登录或令牌无效"));
        } catch (Exception e) {
            // ObjectMapper 异常兜底，保证拒绝响应始终有体
            body = "{\"code\":401,\"message\":\"未登录或令牌无效\"}";
        }
        response.getWriter().write(body);
    }
}
