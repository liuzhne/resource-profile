package com.edu.common.security;

import java.util.Collections;
import java.util.Set;

/**
 * 请求级别上下文：携带当前调用者的角色集合与「是否已验证的内部调用」，供 {@link FieldPermissionAdvice} 决策。
 *
 * <p>由 G-2.2-c 的 {@code RoleContextFilter}（来自 gateway / 各 service）在请求入口
 * 调用 {@link #setRoles(Set)} / {@link #setInternal(boolean)}，并在 finally 中调用 {@link #clear()} 防 ThreadLocal
 * 泄漏（Tomcat 线程复用）。
 *
 * <p>设计选择：用 ThreadLocal 而非 Spring `SecurityContextHolder` —— Spring Security
 * 不是本项目的鉴权主链路（auth-service 自己用 JWT + Redis），引入 SecurityContextHolder
 * 反而要装配整套 SecurityFilterChain。
 */
public final class RequestContext {

    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();
    /**
     * 本请求是否为已验证的内部调用：不带 token 且出示了合法 {@link InternalCallCredential#HEADER}。
     * 只有它为 true 时 advice 才放行不脱敏；未设置（匿名、端用户、上下文缺失）一律按角色脱敏。
     */
    private static final ThreadLocal<Boolean> INTERNAL = new ThreadLocal<>();

    private RequestContext() {}

    public static void setRoles(Set<String> roles) {
        ROLES.set(roles == null ? Collections.emptySet() : Set.copyOf(roles));
    }

    public static Set<String> getRoles() {
        Set<String> roles = ROLES.get();
        return roles == null ? Collections.emptySet() : roles;
    }

    public static void setInternal(boolean internal) {
        INTERNAL.set(internal);
    }

    /** 是否已验证的内部调用。{@link FieldPermissionAdvice} 据此区分「内部调用→放行」与「其余→按角色脱敏」。 */
    public static boolean isInternal() {
        return Boolean.TRUE.equals(INTERNAL.get());
    }

    public static void clear() {
        ROLES.remove();
        INTERNAL.remove();
    }
}
