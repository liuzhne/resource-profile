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
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 登录链安全单测（纯 Mockito，不起 Spring 上下文）。
 * 覆盖安全关键分支：
 *   ① 用户不存在 / 密码错误返回「同一条」401（防用户名枚举）；
 *   ② 禁用账号 403；
 *   ③ 登录成功写 Redis 会话白名单 token:{id}（网关 check-session 依据，登出即吊销）；
 *   ④ JWT claims 携带 roles（驱动 FieldPermissionAdvice 字段级权限）；
 *   ⑤ logout 删白名单 key（令牌吊销）。
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
        when(jwtUtil.generateRefreshToken("42")).thenReturn("refresh-tok");

        LoginResponse resp = authService.login(req("alice", "pw"));

        assertThat(resp.getToken()).isEqualTo("access-tok");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-tok");
        assertThat(resp.getExpiresIn()).isEqualTo(24 * 60 * 60L);
        // 会话白名单：key=token:{id}、TTL 24h，是网关 check-session 与登出吊销的依据
        verify(valueOps).set("token:42", "access-tok", 24L, TimeUnit.HOURS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void login_claimsCarryRolesForFieldPermission() {
        when(userMapper.selectByUsername("alice")).thenReturn(activeUser());
        when(passwordEncoder.matches("pw", "$2a$10$hash")).thenReturn(true);
        when(roleMapper.selectRolesByUserId(42L))
                .thenReturn(List.of(role("counselor"), role("teacher")));
        when(jwtUtil.generateAccessToken(eq("42"), any())).thenReturn("access-tok");

        authService.login(req("alice", "pw"));

        ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generateAccessToken(eq("42"), claims.capture());
        assertThat(claims.getValue()).containsEntry("username", "alice");
        assertThat((List<String>) claims.getValue().get("roles"))
                .containsExactly("counselor", "teacher");
    }

    @Test
    void logout_deletesRedisWhitelistKey() {
        authService.logout(42L);
        verify(redisTemplate).delete("token:42");
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
        when(jwtUtil.getSubject("refresh-old")).thenReturn("42");
        when(userMapper.selectById(42L)).thenReturn(activeUser());
        when(roleMapper.selectRolesByUserId(42L)).thenReturn(List.of(role("counselor")));
        when(jwtUtil.generateAccessToken(eq("42"), any())).thenReturn("access-new");
        when(jwtUtil.generateRefreshToken("42")).thenReturn("refresh-new");

        LoginResponse resp = authService.refreshToken("refresh-old");

        assertThat(resp.getToken()).isEqualTo("access-new");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-new");
        // 刷新同样刷新会话白名单，旧 access token 被新值覆盖
        verify(valueOps).set("token:42", "access-new", 24L, TimeUnit.HOURS);
    }
}
