package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * G-2.2-c：请求入口处把 JWT 的 {@code roles} claim 灌进 {@link RequestContext}，
 * 供 {@link FieldPermissionAdvice} 在响应序列化时决策。
 *
 * <p>放在 {@code common} 模块；由 G-2.2-e 的 auto-configuration 通过
 * {@code FilterRegistrationBean} 注册到每个 service 的 servlet 容器（默认不启用，
 * 由 {@code educare.field-permission.enabled} 控制）。
 *
 * <p><b>语义</b>：
 * <ul>
 *   <li>非 {@code Authorization: Bearer ...} 请求 → 不灌任何角色（advice 端会按"无角色"降级）。</li>
 *   <li>Token 过期/非法 → 同上；本 filter 不负责拒绝请求（auth 由 auth-service 的 Spring Security 链路负责）。</li>
 *   <li>无论是否成功，{@code finally} 中清 ThreadLocal，避免 Tomcat 线程复用导致跨请求串角色。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class RoleContextFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(AUTH_HEADER);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                // 端用户请求（经网关，必带合法 token）：标记已认证，advice 据此按角色脱敏。
                RequestContext.setAuthenticated(true);
                String token = header.substring(BEARER_PREFIX.length());
                Set<String> roles = jwtUtil.parseRoles(token);
                if (!roles.isEmpty()) {
                    RequestContext.setRoles(roles);
                }
            }
            // 无 Bearer = 内网 Feign 匿名直调：不标记已认证，advice 放行不脱敏（保 AI 取数链路完整）。
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}
