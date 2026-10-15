package com.edu.auth.service.impl;

import com.edu.auth.dto.LoginRequest;
import com.edu.auth.entity.User;
import com.edu.auth.entity.Role;
import com.edu.auth.mapper.UserMapper;
import com.edu.auth.mapper.RoleMapper;
import com.edu.common.config.JwtProperties;
import com.edu.common.exception.BusinessException;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthRememberRefreshContractTest {
    private UserMapper users; private RoleMapper roles; private StringRedisTemplate redis; private PasswordEncoder encoder; private JwtUtil jwt; private AuthServiceImpl service;
    @BeforeEach void setup() {
        users=mock(UserMapper.class);roles=mock(RoleMapper.class);redis=mock(StringRedisTemplate.class);encoder=mock(PasswordEncoder.class);jwt=mock(JwtUtil.class);
        service=new AuthServiceImpl(users,roles,redis,encoder,jwt);
        var user=activeUser();when(users.selectByUsername("synthetic")).thenReturn(user);when(users.selectById(42L)).thenReturn(user);
        when(users.selectByIdForUpdate(42L)).thenReturn(user);when(roles.lockIdentityAdminRole()).thenReturn(List.of(1L));
        when(encoder.matches("synthetic-password", "synthetic-hash")).thenReturn(true);
        when(roles.selectRolesByUserId(42L)).thenReturn(List.of());when(roles.selectPermissionsByUserId(42L)).thenReturn(List.of("synthetic:read"));
        when(jwt.generateAccessToken(eq("42"),any())).thenReturn("synthetic-access");
        when(redis.execute(any(RedisScript.class),eq(List.of("token:42","refresh:42")),eq("synthetic-access"),any())).thenReturn(1L);
    }
    private User activeUser() {var user=new User();user.setId(42L);user.setUsername("synthetic");user.setStatus(1);user.setPassword("synthetic-hash");return user;}
    private void validRefresh() {
        when(jwt.validateToken("synthetic-refresh")).thenReturn(true);when(jwt.getClaim("synthetic-refresh","tokenUse")).thenReturn("refresh");when(jwt.getSubject("synthetic-refresh")).thenReturn("42");
        when(jwt.getExpiration("synthetic-refresh")).thenReturn(new Date(System.currentTimeMillis()+3600000));
        when(redis.getExpire("refresh:42",TimeUnit.SECONDS)).thenReturn(3600L);
        when(jwt.generateRefreshTokenUntil(eq("42"),any(Date.class))).thenReturn("synthetic-rotated");
    }

    @ParameterizedTest @NullSource @ValueSource(booleans=false)
    void rememberFalseOrOmittedNeverSignsOrStoresARefreshToken(Boolean remember) {
        var request=new LoginRequest();request.setUsername("synthetic");request.setPassword("synthetic-password");request.setRemember(remember);
        var result=service.login(request);
        assertThat(result.getRefreshToken()).isNull();
        verify(jwt,never()).generateRefreshToken(anyString());
        verify(redis).execute(any(RedisScript.class),eq(List.of("token:42","refresh:42")),eq("synthetic-access"),eq(""));
    }

    private LoginRequest loginRequest() {
        var request=new LoginRequest();request.setUsername("synthetic");request.setPassword("synthetic-password");request.setRemember(false);return request;
    }

    @Test void loginCoordinatesAfterBcryptAndUsesTheCurrentLockedAccountForClaims() {
        var current=activeUser();current.setUsername("synthetic-current");current.setUserType(2);
        when(users.selectByIdForUpdate(42L)).thenReturn(current);
        service.login(loginRequest());
        var order=inOrder(encoder,roles,users,jwt);
        order.verify(users).selectByUsername("synthetic");
        order.verify(encoder).matches("synthetic-password","synthetic-hash");
        order.verify(roles).lockIdentityAdminRole();
        order.verify(users).selectByIdForUpdate(42L);
        order.verify(roles).selectRolesByUserId(42L);
        order.verify(roles).selectPermissionsByUserId(42L);
        var claims=org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        order.verify(jwt).generateAccessToken(eq("42"),claims.capture());
        assertThat(claims.getValue()).containsEntry("username","synthetic-current").containsEntry("userType",2);
    }

    @Test void staleMissingOrDisabledLockedAccountCannotIssueASession() {
        var changed=activeUser();changed.setPassword("synthetic-new-hash");
        for(User current:new User[]{null,changed}) {
            when(users.selectByIdForUpdate(42L)).thenReturn(current);
            assertThatThrownBy(() -> service.login(loginRequest())).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(401));
        }
        var disabled=activeUser();disabled.setStatus(0);when(users.selectByIdForUpdate(42L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.login(loginRequest())).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(403));
        verify(jwt,never()).generateAccessToken(anyString(),any());verifyNoInteractions(redis);
    }

    @Test void missingOrAmbiguousCoordinatorFailsClosedBeforeUserLockOrSigning() {
        for(List<Long> ids:java.util.Arrays.asList(null,List.<Long>of(),List.of(1L,2L))) {
            when(roles.lockIdentityAdminRole()).thenReturn(ids);
            assertThatThrownBy(() -> service.login(loginRequest())).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(503));
        }
        verify(users,never()).selectByIdForUpdate(anyLong());verify(jwt,never()).generateAccessToken(anyString(),any());verifyNoInteractions(redis);
    }

    @Test void accessTokenOrInvalidTokenCannotStartRefreshOrReadUserDatabase() {
        when(jwt.validateToken("synthetic-access")).thenReturn(true);when(jwt.getClaim("synthetic-access","tokenUse")).thenReturn("access");
        for(String token:new String[]{null,""," ","synthetic-access","invalid"}) {
            assertThatThrownBy(() -> service.refreshToken(token)).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(401));
        }
        verifyNoInteractions(redis);verify(users,never()).selectById(anyLong());
    }

    @Test void lostOrExpiredRefreshWhitelistCannotRotate() {
        validRefresh();
        for(Long ttl:new Long[]{null,0L,-2L}) {
            when(redis.getExpire("refresh:42",TimeUnit.SECONDS)).thenReturn(ttl);
            assertThatThrownBy(() -> service.refreshToken("synthetic-refresh")).hasMessageContaining("失效");
        }
        verify(users,never()).selectById(anyLong());
    }

    @Test void disabledAccountRevokesBothExistingSessionsAndDoesNotIssue() {
        validRefresh();var disabled=activeUser();disabled.setStatus(0);when(users.selectById(42L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.refreshToken("synthetic-refresh")).hasMessageContaining("账号不可用");
        verify(redis).delete(List.of("token:42","refresh:42"));verify(jwt,never()).generateAccessToken(anyString(),any());
    }

    @Test void replayedOrRevokedRefreshFailsEvenWithApparentlyLiveTtl() {
        validRefresh(); // ROTATE_SESSION returns zero when the presented token no longer matches.
        assertThatThrownBy(() -> service.refreshToken("synthetic-refresh")).hasMessageContaining("已使用或撤销");
    }

    @Test void slowRoleLookupCannotExtendOriginalJwtExpirationOrCookieBudget() {
        var properties=new JwtProperties();properties.setSecret("synthetic-refresh-clock-test-0123456789abcdef");
        var realJwt=new JwtUtil(properties);realJwt.init();
        String original=realJwt.generateRefreshToken("42",10000);
        Date deadline=realJwt.getExpiration(original);
        when(redis.getExpire("refresh:42",TimeUnit.SECONDS)).thenReturn(10L);
        when(roles.selectRolesByUserId(42L)).thenAnswer(inv -> {Thread.sleep(1250);var role=new Role();role.setCode("teacher");return List.of(role);});
        when(redis.execute(any(RedisScript.class),eq(List.of("refresh:42","token:42")),eq(original),anyString(),anyString())).thenReturn(1L);
        var actual=new AuthServiceImpl(users,roles,redis,encoder,realJwt).refreshToken(original);
        assertThat(realJwt.getExpiration(actual.getRefreshToken())).isEqualTo(deadline);
        assertThat(actual.getExpiresIn()).isBetween(6L,9L);
        assertThat(realJwt.parseRoles(actual.getToken())).containsExactly("teacher");
        assertThat((Object)realJwt.getClaim(actual.getToken(),"permissions")).isEqualTo(List.of("synthetic:read"));
    }
}
