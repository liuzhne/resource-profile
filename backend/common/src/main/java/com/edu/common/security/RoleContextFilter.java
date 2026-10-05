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
 * G-2.2-c：请求入口处给调用方归类并写入 {@link RequestContext}，
 * 供 {@link FieldPermissionAdvice} 在响应序列化时决策。
 *
 * <p>放在 {@code common} 模块；由 G-2.2-e 的 auto-configuration 通过
 * {@code FilterRegistrationBean} 注册到每个 service 的 servlet 容器（由 {@code educare.field-permission.enabled}
 * 控制，默认开）。
 *
 * <p><b>语义</b>（与 {@link AccessGuard}、{@link ServiceAuthFilter} 同序：带 token 优先）：
 * <ul>
 *   <li>{@code Authorization: Bearer ...} → 端用户：灌 JWT {@code roles}，advice 按角色脱敏。Token 过期/非法时
 *       解析不出角色，按"无角色"只留 PUBLIC；本 filter 不负责拒绝请求（准入由 {@link ServiceAuthFilter} 负责）。</li>
 *   <li>无 token 且 {@link InternalCallCredential#HEADER} 比对通过 → 已验证的内部调用，advice 放行不脱敏
 *       （保 AI 取数链路完整）。</li>
 *   <li>其余（匿名、凭证错误、本服务未配置密钥）→ 既无角色也非内部，advice 只留 PUBLIC（fail-closed）。</li>
 *   <li>无论是否成功，{@code finally} 中清 ThreadLocal，避免 Tomcat 线程复用导致跨请求串角色。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class RoleContextFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final InternalCallCredential internalCredential;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(AUTH_HEADER);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                Set<String> roles = jwtUtil.parseRoles(header.substring(BEARER_PREFIX.length()));
                if (!roles.isEmpty()) {
                    RequestContext.setRoles(roles);
                }
            } else if (internalCredential.matches(request.getHeader(InternalCallCredential.HEADER))) {
                RequestContext.setInternal(true);
            }
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}
