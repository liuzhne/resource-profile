package com.edu.common.security;

import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 服务入口鉴权门：下游业务服务的每个请求都必须出示「合法 JWT」或「合法内部凭证」，否则 401。
 *
 * <p><b>为何需要</b>：Render 部署下每个下游服务都有自己的公网 URL，网关 {@code JwtAuthGlobalFilter}
 * 不再是唯一入口。只靠端点里的 {@link AccessGuard} 不够 —— 有端点根本没做端点级校验（如 mental-service
 * {@code MentalController} 的概览/预警名单/问卷增删改、student-service {@code /student/ids}）。
 * 本 filter 在服务入口统一拦截，匿名直连公网 URL 的请求到不了任何 controller。
 *
 * <p>判定顺序与 {@link AccessGuard} / {@link RoleContextFilter} 一致（带 token 优先按端用户处理）：
 * <ol>
 *   <li>{@code /actuator/health}（及 liveness/readiness 子路径）豁免，供平台健康检查。原始 URI 与容器规范化后的
 *       路径必须同时是健康检查路径，防 {@code /actuator/health/../..} 之类借豁免绕过。</li>
 *   <li>带 {@code Authorization: Bearer} → 校验 JWT 签名 + 未过期（与 {@code AgentSelfAuthFilter} 同口径），
 *       非法即 401，不再看内部凭证。</li>
 *   <li>不带 token → {@link InternalCallCredential#matches} 通过（常量时间比较）才放行。</li>
 *   <li>其余一律 401。</li>
 * </ol>
 *
 * <p>本门只管准入：对象级授权仍由 {@link AccessGuard}，字段级由 {@link FieldPermissionAdvice} 负责。
 * 不查 Redis 会话白名单（多数下游服务不连 Redis），故已登出但未过期的 token 仍能直连下游 ——
 * 残余风险见 docs/educare/FIELD_PERMISSION.md §11。
 *
 * <p>装配见 {@link ServiceAuthAutoConfiguration}，开关 {@code educare.service-auth.enabled}（默认开）。
 */
@Slf4j
@RequiredArgsConstructor
public class ServiceAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEALTH_PATH = "/actuator/health";
    private static final String DENY_MESSAGE = "未登录或令牌无效";

    private final JwtUtil jwtUtil;
    private final InternalCallCredential internalCredential;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isHealthCheck(request) || isAuthenticated(request)) {
            chain.doFilter(request, response);
            return;
        }
        log.debug("服务入口鉴权拒绝 method={} uri={}", request.getMethod(), request.getRequestURI());
        deny(response);
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return jwtUtil.validateToken(header.substring(BEARER_PREFIX.length()));
        }
        return internalCredential.matches(request.getHeader(InternalCallCredential.HEADER));
    }

    private static boolean isHealthCheck(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        String normalized = request.getServletPath() + (pathInfo == null ? "" : pathInfo);
        return isHealthPath(request.getRequestURI()) && isHealthPath(normalized);
    }

    private static boolean isHealthPath(String path) {
        return HEALTH_PATH.equals(path) || path.startsWith(HEALTH_PATH + "/");
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body;
        try {
            body = objectMapper.writeValueAsString(Result.error(401, DENY_MESSAGE));
        } catch (Exception e) {
            // ObjectMapper 异常兜底，保证拒绝响应始终有体
            body = "{\"code\":401,\"message\":\"" + DENY_MESSAGE + "\"}";
        }
        response.getWriter().write(body);
    }
}
