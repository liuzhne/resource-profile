package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 统一访问控制助手：从请求的 {@code Authorization: Bearer} 解析当前登录者身份（userId / 角色），
 * 提供「本人或授权角色」「拥有任一角色」判定，供各业务 controller 做端点级越权(IDOR)校验。
 *
 * <p>背景：网关 {@code JwtAuthGlobalFilter} 只做"是否登录"的准入校验，<b>不做</b>"被查资源是否属于当前用户"
 * 的水平授权。各 controller 需自行用本助手判定，否则任意登录者可用 {@code ?userId=}/路径 id 越权读他人数据。
 *
 * <p>token 缺失/非法一律 fail-closed（{@link #currentUserId} 返回 {@code null}、判定返回 false）。
 * 解析签名 JWT（签名即防伪），不信任任何明文身份头。
 *
 * <p>装配：随 common 的 {@code AutoConfiguration.imports} 注册（同 {@link JwtUtil}），各服务依赖 common 即得。
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    /** 当前登录者 userId（JWT subject）；无/非法 token 返回 {@code null}。 */
    public Long currentUserId(String authHeader) {
        String token = bearer(authHeader);
        if (token == null) {
            return null;
        }
        try {
            String subject = jwtUtil.getSubject(token);
            return subject == null ? null : Long.parseLong(subject);
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前登录者角色集合；无/非法 token 返回空集。 */
    public Set<String> currentRoles(String authHeader) {
        String token = bearer(authHeader);
        return token == null ? Set.of() : jwtUtil.parseRoles(token);
    }

    /**
     * 是否「本人」（{@code ownerUserId} == 当前 userId）或拥有任一 {@code privilegedRoles}。
     * fail-closed：未登录 / token 非法 → false。
     */
    public boolean isSelfOrAnyRole(String authHeader, Long ownerUserId, String... privilegedRoles) {
        Long me = currentUserId(authHeader);
        if (me == null) {
            return false;
        }
        if (ownerUserId != null && ownerUserId.equals(me)) {
            return true;
        }
        return hasAnyRole(authHeader, privilegedRoles);
    }

    /**
     * 端点级越权判定，<b>兼容内部服务匿名直调</b>：
     * <ul>
     *   <li><b>无 Authorization 头 → 放行</b>：外部流量必经网关，而网关 {@code JwtAuthGlobalFilter} 已强制带合法 token；
     *       故"无 token"只可能来自内网直连 Feign（student/mental 等服务端口未公网发布，仅集群内可达）。视为可信内部调用。</li>
     *   <li><b>带 token → 必须本人</b>（{@code ownerUserId}==当前 userId）<b>或拥有任一</b> {@code privilegedRoles}，否则拒绝。</li>
     * </ul>
     * 这是"内网可信 + 网关为边界"的标准模型。若需纵深防御，可改为内部服务携带正向凭证（共享密钥头）再放行。
     */
    public boolean allowSelfRoleOrInternal(String authHeader, Long ownerUserId, String... privilegedRoles) {
        if (bearer(authHeader) == null) {
            return true;
        }
        return isSelfOrAnyRole(authHeader, ownerUserId, privilegedRoles);
    }

    /** 是否拥有任一指定角色（纯管理操作用，无"本人"语义）。 */
    public boolean hasAnyRole(String authHeader, String... anyRoles) {
        if (anyRoles == null || anyRoles.length == 0) {
            return false;
        }
        Set<String> roles = currentRoles(authHeader);
        for (String r : anyRoles) {
            if (roles.contains(r)) {
                return true;
            }
        }
        return false;
    }

    private String bearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }
}
