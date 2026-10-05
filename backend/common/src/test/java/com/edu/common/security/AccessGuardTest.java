package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AccessGuard 纯逻辑单测（Mockito mock JwtUtil，无 Spring 上下文；内部凭证经 RequestContextHolder 模拟当前请求）。
 */
class AccessGuardTest {

    private static final String AUTH = "Bearer tok";
    private static final String SECRET = "internal-secret-at-least-32-chars-0001";

    private JwtUtil jwtUtil;
    private AccessGuard guard;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        guard = new AccessGuard(jwtUtil, new InternalCallCredential(SECRET));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 模拟当前请求；{@code internalHeader} 为 null 表示请求存在但不带内部凭证头。 */
    private static void currentRequest(String internalHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (internalHeader != null) {
            request.addHeader(InternalCallCredential.HEADER, internalHeader);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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

    // —— allowSelfRoleOrInternal：内部调用必须出示正向凭证 ——

    @Test
    void allowSelfRoleOrInternal_noToken_noCredential_denied() {
        // 漏洞回归：Render 上下游服务公网可达，匿名直连（既无 Authorization 也无内部凭证）必须拒绝
        currentRequest(null);
        assertThat(guard.allowSelfRoleOrInternal(null, 7L, Roles.STAFF_VIEW)).isFalse();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void allowSelfRoleOrInternal_noToken_noRequestContext_denied() {
        // 没有请求上下文的线程拿不到凭证 → fail-closed
        assertThat(guard.allowSelfRoleOrInternal(null, 7L, Roles.STAFF_VIEW)).isFalse();
    }

    @Test
    void allowSelfRoleOrInternal_noToken_validCredential_allowedAsInternal() {
        currentRequest(SECRET);
        assertThat(guard.allowSelfRoleOrInternal(null, 7L, Roles.STAFF_VIEW)).isTrue();
        assertThat(guard.isVerifiedInternalCall()).isTrue();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void allowSelfRoleOrInternal_noToken_wrongCredential_denied() {
        currentRequest("guessed-value");
        assertThat(guard.allowSelfRoleOrInternal(null, 7L, Roles.STAFF_VIEW)).isFalse();
    }

    @Test
    void allowSelfRoleOrInternal_credentialNotConfigured_deniedEvenWithEmptyHeader() {
        // 本服务没配密钥时，空头不能与"空密钥"匹配成内部调用
        guard = new AccessGuard(jwtUtil, new InternalCallCredential(""));
        currentRequest("");
        assertThat(guard.allowSelfRoleOrInternal(null, 7L, Roles.STAFF_VIEW)).isFalse();
    }

    @Test
    void allowSelfRoleOrInternal_tokenPresent_credentialDoesNotElevate() {
        // 带 token 一律按端用户判定：学生即使附上合法内部凭证也不能越权读他人
        currentRequest(SECRET);
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));
        assertThat(guard.allowSelfRoleOrInternal(AUTH, 99L, Roles.STAFF_VIEW)).isFalse();
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
