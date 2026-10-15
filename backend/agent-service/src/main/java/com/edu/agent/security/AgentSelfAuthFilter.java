package com.edu.agent.security;

import com.edu.common.result.Result;
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
 * <p><b>为何需要</b>：直连业务服务可能绕过网关，原签名校验不能撤销登出、改密或角色变更后的令牌。
 * 本 filter 对所有业务请求验证 JWT 和 Redis 会话白名单；会话不存在或被替换时 401，Redis 不可用时 503。
 * 角色/资源授权仍由各 controller 执行，长连接还须在推送期间重验会话。
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

    private final AgentSessionVerifier sessions;
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
        AgentSessionVerifier.Verification verification = sessions.verify(token);
        if (verification.status() != AgentSessionVerifier.Status.VALID) {
            deny(response, verification.status() == AgentSessionVerifier.Status.UNAVAILABLE ? 503 : 401);
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

    private void deny(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body;
        try {
            body = objectMapper.writeValueAsString(Result.error(status,
                    status == 503 ? "鉴权服务暂不可用" : "未登录或令牌无效"));
        } catch (Exception e) {
            // ObjectMapper 异常兜底，保证拒绝响应始终有体
            body = "{\"code\":" + status + ",\"message\":\"鉴权失败\"}";
        }
        response.getWriter().write(body);
    }
}
