package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * 统一访问控制助手：从请求的 {@code Authorization: Bearer} 解析当前登录者身份（userId / 角色），
 * 提供「本人或授权角色」「拥有任一角色」判定，供各业务 controller 做端点级越权(IDOR)校验。
 *
 * <p>背景：网关 {@code JwtAuthGlobalFilter} 只做"是否登录"的准入校验，<b>不做</b>"被查资源是否属于当前用户"
 * 的水平授权。各 controller 需自行用本助手判定，否则任意登录者可用 {@code ?userId=}/路径 id 越权读他人数据。
 *
 * <p>token 缺失/非法一律 fail-closed（{@link #currentUserId} 返回 {@code null}、判定返回 false）。
 * 身份只取自签名 JWT（签名即防伪），不信任任何明文身份头；唯一接受的非 JWT 凭证是共享密钥
 * {@link InternalCallCredential#HEADER}，它只证明「调用方是持有密钥的内部服务」，不代表任何用户。
 *
 * <p>装配：随 common 的 {@code AutoConfiguration.imports} 注册（同 {@link JwtUtil}），各服务依赖 common 即得。
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final InternalCallCredential internalCredential;

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
     * 端点级越权判定：<b>本人、授权角色，或已验证的内部服务调用</b>。
     * <ul>
     *   <li><b>带 Bearer token → 必须本人</b>（{@code ownerUserId}==当前 userId）<b>或拥有任一</b> {@code privilegedRoles}，
     *       否则拒绝。带 token 即按端用户判定，即使同时带了内部凭证头也不提权。</li>
     *   <li><b>不带 token → 仅当当前请求出示合法内部凭证</b>（{@link InternalCallCredential#HEADER}，常量时间比较）
     *       才视为内部调用放行；凭证缺失 / 错误 / 本服务未配置密钥 → 拒绝（fail-closed）。</li>
     * </ul>
     *
     * <p><b>为何不再「无 token 即内网」</b>：旧实现假设外部流量必经网关、下游端口不对公网发布。Render 部署下
     * 该假设不成立 —— 每个下游服务都是独立的公网 Web Service（{@code https://edu-portrait-<name>.onrender.com}），
     * 免费实例也收不到私网流量，任何人不带 token 直连即可越过网关 {@code JwtAuthGlobalFilter}。
     * 服务入口的 {@link ServiceAuthFilter} 先拦掉「既无合法 JWT 也无内部凭证」的请求，本方法是端点级的第二道防线
     * （入口门被关闭时也不会退化成放行）。
     *
     * <p>内部凭证取自当前请求（{@link RequestContextHolder}），调用方签名不变；没有请求上下文的线程视为无凭证。
     */
    public boolean allowSelfRoleOrInternal(String authHeader, Long ownerUserId, String... privilegedRoles) {
        if (bearer(authHeader) == null) {
            return isVerifiedInternalCall();
        }
        return isSelfOrAnyRole(authHeader, ownerUserId, privilegedRoles);
    }

    /** 当前请求是否出示了合法内部凭证 {@link InternalCallCredential#HEADER}。 */
    public boolean isVerifiedInternalCall() {
        return internalCredential.matches(currentRequestHeader(InternalCallCredential.HEADER));
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

    private static String currentRequestHeader(String name) {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getHeader(name)
                : null;
    }
}
