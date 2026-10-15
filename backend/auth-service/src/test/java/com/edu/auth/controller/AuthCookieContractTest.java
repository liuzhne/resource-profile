package com.edu.auth.controller;

import com.edu.auth.dto.LoginResponse;
import com.edu.auth.service.AuthService;
import com.edu.common.config.JwtProperties;
import com.edu.common.exception.BusinessException;
import com.edu.common.exception.GlobalExceptionHandler;
import com.edu.common.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthCookieContractTest {
    private AuthService service;
    private JwtUtil jwt;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(AuthService.class);
        var properties = new JwtProperties(); properties.setSecret("synthetic-cookie-contract-key-0123456789abcdef");
        jwt = new JwtUtil(properties); jwt.init();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service, jwt)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private LoginResponse response(String refresh) {
        var result = new LoginResponse(); result.setToken("synthetic-access"); result.setExpiresIn(86400L); result.setRefreshToken(refresh); return result;
    }

    @Test
    void httpsRememberLoginUsesHttpOnlySecureStrictCookieAndNoRefreshInJson() throws Exception {
        String refresh = jwt.generateRefreshToken("42"); when(service.login(any())).thenReturn(response(refresh));
        var result = mvc.perform(post("/auth/login").secure(true).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"synthetic\",\"password\":\"synthetic-password\",\"remember\":true}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.token").value("synthetic-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn();
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("educare_refresh=" + refresh, "HttpOnly", "Secure", "SameSite=Strict", "Path=/");
        long maxAge = Long.parseLong(cookie.split("Max-Age=")[1].split(";")[0]);
        assertThat(maxAge).isBetween(604798L, 604800L);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(refresh);
    }

    @Test
    void forwardedHttpsAlsoSetsSecureWhileLocalHttpRemainsUsable() throws Exception {
        when(service.login(any())).thenAnswer(inv -> response(jwt.generateRefreshToken("42")));
        var secure = mvc.perform(post("/auth/login").header("X-Forwarded-Proto", "https").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"synthetic\",\"password\":\"synthetic-password\"}")).andReturn();
        assertThat(secure.getResponse().getHeader("Set-Cookie")).contains("; Secure");
        var local = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"synthetic\",\"password\":\"synthetic-password\"}")).andReturn();
        assertThat(local.getResponse().getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Strict").doesNotContain("; Secure");
    }

    @Test
    void unrememberedLoginClearsOldCookieAndResponseModelCannotSerializeRefreshEvenWithoutController() throws Exception {
        when(service.login(any())).thenReturn(response(null));
        var result = mvc.perform(post("/auth/login").secure(true).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"synthetic\",\"password\":\"synthetic-password\",\"remember\":false}"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("educare_refresh=;", "Max-Age=0", "HttpOnly", "Secure");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response("synthetic-refresh-secret")))
                .doesNotContain("refreshToken", "synthetic-refresh-secret");
    }

    @Test
    void refreshReadsCookieAndRotatesOnlyToOriginalAbsoluteDeadline() throws Exception {
        Date expiry = new Date((System.currentTimeMillis() / 1000 + 300) * 1000);
        String old = jwt.generateRefreshTokenUntil("42", expiry), rotated = jwt.generateRefreshTokenUntil("42", expiry);
        when(service.refreshToken(old)).thenReturn(response(rotated));
        var result = mvc.perform(post("/auth/refresh").secure(true).cookie(new Cookie("educare_refresh", old)))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn();
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("educare_refresh=" + rotated, "HttpOnly", "SameSite=Strict", "Secure");
        long maxAge = Long.parseLong(cookie.split("Max-Age=")[1].split(";")[0]);
        assertThat(maxAge).isBetween(298L, 300L);
        verify(service).refreshToken(old);
    }

    @Test
    void legacyExplicitRefreshHeaderTakesPrecedenceWithoutLeakingEitherToken() throws Exception {
        when(service.refreshToken("synthetic-header")).thenReturn(response(jwt.generateRefreshToken("42")));
        mvc.perform(post("/auth/refresh").header("X-Refresh-Token", "synthetic-header").cookie(new Cookie("educare_refresh", "synthetic-cookie")))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        verify(service).refreshToken("synthetic-header"); verify(service, never()).refreshToken("synthetic-cookie");
    }

    @Test
    void failedRefreshDoesNotReplaceCookieOrReturnToken() throws Exception {
        when(service.refreshToken(null)).thenThrow(new BusinessException(401, "刷新会话无效"));
        mvc.perform(post("/auth/refresh")).andExpect(jsonPath("$.code").value(401))
                .andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void logoutOnlyClearsCookieForCurrentSessionAndOldLogoutPreservesNewRememberSession() throws Exception {
        String access = jwt.generateAccessToken("42", Map.of("userId", 42L));
        when(service.requireSession(access)).thenReturn(42L);
        var current = mvc.perform(post("/auth/logout").secure(true).header("Authorization", "Bearer " + access)).andReturn();
        assertThat(current.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");
        when(service.requireSession(access)).thenThrow(new BusinessException(401, "旧会话"));
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.code").value(200)).andExpect(header().doesNotExist("Set-Cookie"));
        verify(service, times(2)).logout(42L, access);
    }
}
