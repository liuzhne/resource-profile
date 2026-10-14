package com.edu.mcp.student.security;

import com.edu.common.config.JwtProperties;
import com.edu.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AUTHZ-MCP-20261013：把 MCP 请求上的 {@code Authorization: Bearer} 解析成 {@link CallerIdentity}。
 *
 * <p>校验顺序：Bearer 存在 → 签名 + 未过期（common {@link JwtUtil}）→ {@code aud} 必须含
 * {@value JwtProperties#AUDIENCE_MCP_STUDENT_DATA}。任一失败抛 {@link McpAccessDeniedException}（fail-closed）。
 *
 * <p>兼容开关 {@code educare.mcp.auth.allow-missing-audience}（默认 false）：仅用于升级窗口内接受
 * <b>不带 aud</b> 的旧令牌（auth-service 升级前签发、最长 24h）。带了 aud 但不含本服务的令牌任何时候都拒绝。
 */
@Slf4j
@Component
public class McpCallerAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final boolean allowMissingAudience;

    public McpCallerAuthenticator(JwtUtil jwtUtil,
                                  @Value("${educare.mcp.auth.allow-missing-audience:false}") boolean allowMissingAudience) {
        this.jwtUtil = jwtUtil;
        this.allowMissingAudience = allowMissingAudience;
    }

    public CallerIdentity authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new McpAccessDeniedException("缺少调用者身份（Authorization: Bearer）");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).strip();
        if (token.isEmpty()) {
            throw new McpAccessDeniedException("缺少调用者身份（Authorization: Bearer）");
        }
        Claims claims;
        try {
            claims = jwtUtil.parseTokenForAudience(token, JwtProperties.AUDIENCE_MCP_STUDENT_DATA, allowMissingAudience);
        } catch (Exception e) {
            log.warn("MCP 调用者令牌校验失败: {}", e.getClass().getSimpleName());
            throw new McpAccessDeniedException("调用者令牌无效、已过期或受众不符");
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new McpAccessDeniedException("调用者令牌缺少 subject");
        }
        if (JwtUtil.TYPE_SYSTEM.equals(claims.get(JwtUtil.CLAIM_TYPE))
                && subject.startsWith(JwtUtil.SYSTEM_SUBJECT_PREFIX)) {
            return new CallerIdentity(CallerIdentity.Kind.SYSTEM, null, Set.of(),
                    toStrings(claims.get(JwtUtil.CLAIM_SCOPE)).stream().toList(),
                    toLong(claims.get(JwtUtil.CLAIM_STUDENT_ID)),
                    String.valueOf(claims.get(JwtUtil.CLAIM_INITIATOR)), subject);
        }
        Long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new McpAccessDeniedException("调用者令牌 subject 非法");
        }
        return new CallerIdentity(CallerIdentity.Kind.USER, userId, toStrings(claims.get("roles")),
                List.of(), null, subject, subject);
    }

    private static Set<String> toStrings(Object raw) {
        if (raw instanceof Collection<?> c) {
            return c.stream().filter(java.util.Objects::nonNull).map(Object::toString)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
