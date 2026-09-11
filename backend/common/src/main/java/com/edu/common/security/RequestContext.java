package com.edu.common.security;

import java.util.Collections;
import java.util.Set;

/**
 * 请求级别上下文：携带当前调用者的角色集合，供 {@link FieldPermissionAdvice} 决策。
 *
 * <p>由 G-2.2-c 的 {@code RoleContextFilter}（来自 gateway / 各 service）在请求入口
 * 调用 {@link #setRoles(Set)}，并在 finally 中调用 {@link #clear()} 防 ThreadLocal
 * 泄漏（Tomcat 线程复用）。
 *
 * <p>设计选择：用 ThreadLocal 而非 Spring `SecurityContextHolder` —— Spring Security
 * 不是本项目的鉴权主链路（auth-service 自己用 JWT + Redis），引入 SecurityContextHolder
 * 反而要装配整套 SecurityFilterChain。
 */
public final class RequestContext {

    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();
    /** 本请求是否携带 Bearer token（端用户经网关）。false=内网 Feign 匿名直调（可信，不脱敏）。 */
    private static final ThreadLocal<Boolean> AUTHENTICATED = new ThreadLocal<>();

    private RequestContext() {}

    public static void setRoles(Set<String> roles) {
        ROLES.set(roles == null ? Collections.emptySet() : Set.copyOf(roles));
    }

    public static Set<String> getRoles() {
        Set<String> roles = ROLES.get();
        return roles == null ? Collections.emptySet() : roles;
    }

    public static void setAuthenticated(boolean authenticated) {
        AUTHENTICATED.set(authenticated);
    }

    /** 是否端用户请求（带 token）。{@link FieldPermissionAdvice} 据此区分「端用户→按角色脱敏」与「内网→放行」。 */
    public static boolean isAuthenticated() {
        return Boolean.TRUE.equals(AUTHENTICATED.get());
    }

    public static void clear() {
        ROLES.remove();
        AUTHENTICATED.remove();
    }
}
