package com.edu.auth.service.impl;

import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.LoginResponse;
import com.edu.auth.dto.UserInfoResponse;
import com.edu.auth.entity.Role;
import com.edu.auth.entity.User;
import com.edu.auth.mapper.RoleMapper;
import com.edu.auth.mapper.UserMapper;
import com.edu.common.exception.BusinessException;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 登录链安全单测（纯 Mockito，不起 Spring 上下文）。
 * 覆盖安全关键分支：
 *   ① 用户不存在 / 密码错误返回「同一条」401（防用户名枚举）；
 *   ② 禁用账号 403；
 *   ③ 登录成功写 Redis 会话白名单 token:{id}（网关 check-session 依据，登出即吊销）；
 *   ④ JWT claims 携带 roles（驱动 FieldPermissionAdvice 字段级权限）；
 *   ⑤ logout 原子比较并删除本次令牌的会话，不撤销较新的登录。
 */
class AuthServiceImplTest {

    private UserMapper userMapper;
    private RoleMapper roleMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private AuthServiceImpl authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        roleMapper = mock(RoleMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(JwtUtil.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        authService = new AuthServiceImpl(userMapper, roleMapper, redisTemplate, passwordEncoder, jwtUtil);
        when(roleMapper.lockIdentityAdminRole()).thenReturn(List.of(1L));
        when(userMapper.selectByIdForUpdate(42L)).thenReturn(activeUser());
    }

    private User activeUser() {
        User u = new User();
        u.setId(42L);
        u.setUsername("alice");
        u.setPassword("$2a$10$hash");
        u.setUserType(1);
        u.setStatus(1);
        return u;
    }

    private LoginRequest req(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        r.setRemember(true);
        return r;
    }

    private Role role(String code) {
        Role r = new Role();
        r.setCode(code);
        return r;
    }

    @Test
    void login_userNotFound_throws401Generic() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThatThrownBy(() -> authService.login(req("ghost", "x")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(401);
                    assertThat(e.getMessage()).isEqualTo("用户名或密码错误");
                });

        // 不存在的用户不应触发密码校验或写 Redis
        verify(passwordEncoder, never()).matches(any(), any());
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void login_wrongPassword_throwsSame401AsUserNotFound() {
        when(userMapper.selectByUsername("alice")).thenReturn(activeUser());
        when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req("alice", "wrong")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(401);
                    // 必须与「用户不存在」完全一致，杜绝通过响应差异枚举用户名
                    assertThat(e.getMessage()).isEqualTo("用户名或密码错误");
                });
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void login_disabledAccount_throws403() {
        User disabled = activeUser();
        disabled.setStatus(0);
        when(userMapper.selectByUsername("alice")).thenReturn(disabled);
        when(passwordEncoder.matches("pw", "$2a$10$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req("alice", "pw")))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(403));
        // 账号禁用不应签发 token
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void login_success_writesRedisWhitelistAndReturnsToken() {
        when(userMapper.selectByUsername("alice")).thenReturn(activeUser());
        when(passwordEncoder.matches("pw", "$2a$10$hash")).thenReturn(true);
        when(roleMapper.selectRolesByUserId(42L)).thenReturn(List.of(role("counselor")));
        when(jwtUtil.generateAccessToken(eq("42"), any())).thenReturn("access-tok");
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("token:42", "refresh:42")), eq("access-tok"), any())).thenReturn(1L);
        when(jwtUtil.generateRefreshToken("42")).thenReturn("refresh-tok");

        LoginResponse resp = authService.login(req("alice", "pw"));

        assertThat(resp.getToken()).isEqualTo("access-tok");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-tok");
        assertThat(resp.getExpiresIn()).isEqualTo(24 * 60 * 60L);
        // 会话白名单：key=token:{id}、TTL 24h，是网关 check-session 与登出吊销的依据
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("token:42", "refresh:42")), eq("access-tok"), eq("refresh-tok"));
        verify(userMapper).recordLogin(42L, "alice", "counselor", "unknown", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void login_claimsCarryRolesForFieldPermission() {
        when(userMapper.selectByUsername("alice")).thenReturn(activeUser());
        when(passwordEncoder.matches("pw", "$2a$10$hash")).thenReturn(true);
        when(roleMapper.selectRolesByUserId(42L))
                .thenReturn(List.of(role("counselor"), role("teacher")));
        when(jwtUtil.generateAccessToken(eq("42"), any())).thenReturn("access-tok");
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("token:42", "refresh:42")), eq("access-tok"), any())).thenReturn(1L);

        authService.login(req("alice", "pw"));

        ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generateAccessToken(eq("42"), claims.capture());
        assertThat(claims.getValue()).containsEntry("username", "alice");
        assertThat((List<String>) claims.getValue().get("roles"))
                .containsExactly("counselor", "teacher");
    }

    @Test
    @SuppressWarnings("unchecked")
    void logout_executesAtomicComparisonWithThePresentedToken() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("token:42", "refresh:42")), eq("access-tok")))
                .thenReturn(1L);

        authService.logout(42L, "access-tok");

        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(script.capture(), eq(List.of("token:42", "refresh:42")), eq("access-tok"));
        assertThat(script.getValue().getResultType()).isEqualTo(Long.class);
        assertThat(script.getValue().getScriptAsString()).contains(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then",
                "return redis.call('DEL', KEYS[1])", "return 0");
        // The single Lua invocation is the only Redis operation; no racy GET + DEL.
        verifyNoMoreInteractions(redisTemplate);
        verifyNoInteractions(valueOps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void logout_alreadyRevokedOrReplacedSessionIsAnIdempotentSuccess() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("token:42", "refresh:42")), eq("old-access")))
                .thenReturn(0L);

        authService.logout(42L, "old-access");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("token:42", "refresh:42")), eq("old-access"));
        verifyNoMoreInteractions(redisTemplate);
        verifyNoInteractions(valueOps);
    }

    @Test
    void logout_missingUserOrTokenCannotRevokeAnySession() {
        for (Long userId : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> authService.logout(userId, "access-tok"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(401));
        }
        for (String token : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> authService.logout(42L, token))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(401));
        }
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void logout_missingRedisConfirmationCannotReportSuccess() {
        assertThatThrownBy(() -> authService.logout(42L, "access-tok"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(503));
    }

    @Test
    @SuppressWarnings("unchecked")
    void logout_redisFailurePropagatesSoTheClientCanRetry() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("token:42", "refresh:42")), eq("access-tok")))
                .thenThrow(new IllegalStateException("test redis unavailable"));

        assertThatThrownBy(() -> authService.logout(42L, "access-tok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test redis unavailable");
    }

    @Test
    void getUserInfo_notFound_throws404() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> authService.getUserInfo(99L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(404));
    }

    @Test
    void getUserInfo_success_returnsProfileWithRoles() {
        User u = activeUser();
        u.setNickname("Alice");
        u.setEmail("a@edu.cn");
        when(userMapper.selectById(42L)).thenReturn(u);
        when(roleMapper.selectRolesByUserId(42L)).thenReturn(List.of(role("counselor")));

        UserInfoResponse info = authService.getUserInfo(42L);

        assertThat(info.getId()).isEqualTo(42L);
        assertThat(info.getUsername()).isEqualTo("alice");
        assertThat(info.getNickname()).isEqualTo("Alice");
        assertThat(info.getRoles()).containsExactly("counselor");
    }

    @Test
    void refreshToken_issuesNewTokenAndUpdatesRedisWhitelist() {
        when(jwtUtil.validateToken("refresh-old")).thenReturn(true);
        when(jwtUtil.getClaim("refresh-old", "tokenUse")).thenReturn("refresh");
        when(jwtUtil.getSubject("refresh-old")).thenReturn("42");
        when(redisTemplate.getExpire("refresh:42", TimeUnit.SECONDS)).thenReturn(3600L);
        when(jwtUtil.getExpiration("refresh-old")).thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000L));
        when(userMapper.selectById(42L)).thenReturn(activeUser());
        when(roleMapper.selectRolesByUserId(42L)).thenReturn(List.of(role("counselor")));
        when(jwtUtil.generateAccessToken(eq("42"), any())).thenReturn("access-new");
        when(jwtUtil.generateRefreshTokenUntil(eq("42"), any(java.util.Date.class))).thenReturn("refresh-new");
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("refresh:42", "token:42")), eq("refresh-old"), eq("refresh-new"), eq("access-new"))).thenReturn(1L);

        LoginResponse resp = authService.refreshToken("refresh-old");

        assertThat(resp.getToken()).isEqualTo("access-new");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-new");
        // 刷新同样刷新会话白名单，旧 access token 被新值覆盖
        assertThat(resp.getExpiresIn()).isBetween(3598L, 3600L);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("refresh:42", "token:42")), eq("refresh-old"), eq("refresh-new"), eq("access-new"));
    }
}
