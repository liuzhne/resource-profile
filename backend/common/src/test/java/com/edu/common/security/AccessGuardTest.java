package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AccessGuard 纯逻辑单测（Mockito mock JwtUtil，无 Spring 上下文）。
 */
class AccessGuardTest {

    private static final String AUTH = "Bearer tok";

    private JwtUtil jwtUtil;
    private AccessGuard guard;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        guard = new AccessGuard(jwtUtil);
    }

    @Test
    void currentUserId_parsesSubject() {
        when(jwtUtil.getSubject("tok")).thenReturn("42");
        assertThat(guard.currentUserId(AUTH)).isEqualTo(42L);
    }

    @Test
    void currentUserId_noBearer_null() {
        assertThat(guard.currentUserId(null)).isNull();
        assertThat(guard.currentUserId("Basic xxx")).isNull();
    }

    @Test
    void currentUserId_badToken_null() {
        when(jwtUtil.getSubject("tok")).thenThrow(new RuntimeException("invalid"));
        assertThat(guard.currentUserId(AUTH)).isNull();
    }

    @Test
    void isSelfOrAnyRole_self_true() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        assertThat(guard.isSelfOrAnyRole(AUTH, 7L, Roles.STAFF_VIEW)).isTrue();
    }

    @Test
    void isSelfOrAnyRole_otherButPrivileged_true() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("teacher"));
        assertThat(guard.isSelfOrAnyRole(AUTH, 99L, Roles.STAFF_VIEW)).isTrue();
    }

    @Test
    void isSelfOrAnyRole_otherAndStudent_false() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));
        assertThat(guard.isSelfOrAnyRole(AUTH, 99L, Roles.STAFF_VIEW)).isFalse();
    }

    @Test
    void isSelfOrAnyRole_noToken_false() {
        assertThat(guard.isSelfOrAnyRole(null, 7L, Roles.STAFF_VIEW)).isFalse();
    }

    @Test
    void hasAnyRole_matchesOnlyListed() {
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("admin"));
        assertThat(guard.hasAnyRole(AUTH, Roles.STUDENT_WRITE)).isTrue();   // admin ∈ {admin,teacher}
        assertThat(guard.hasAnyRole(AUTH, "teacher")).isFalse();           // 只有 admin
    }

    // —— allowSelfRoleOrInternal：兼容内网匿名直调 ——

    @Test
    void allowSelfRoleOrInternal_noToken_allowedAsInternal() {
        // 内网 Feign 匿名直调（无 Authorization）→ 放行，不触碰 jwtUtil
        assertThat(guard.allowSelfRoleOrInternal(null, 7L, Roles.STAFF_VIEW)).isTrue();
    }

    @Test
    void allowSelfRoleOrInternal_tokenSelf_allowed() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        assertThat(guard.allowSelfRoleOrInternal(AUTH, 7L, Roles.STAFF_VIEW)).isTrue();
    }

    @Test
    void allowSelfRoleOrInternal_tokenOtherStudent_denied() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));
        assertThat(guard.allowSelfRoleOrInternal(AUTH, 99L, Roles.STAFF_VIEW)).isFalse();
    }

    @Test
    void allowSelfRoleOrInternal_tokenOtherStaff_allowed() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("counselor"));
        assertThat(guard.allowSelfRoleOrInternal(AUTH, 99L, Roles.STAFF_VIEW)).isTrue();
    }
}
