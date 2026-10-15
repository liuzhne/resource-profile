package com.edu.auth.controller;

import com.edu.auth.service.AuthService;
import com.edu.common.config.JwtProperties;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLogoutControllerTest {

    private AuthService authService;
    private JwtUtil jwtUtil;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        JwtProperties properties = new JwtProperties();
        properties.setSecret("logout-test-secret-at-least-32-characters");
        jwtUtil = new JwtUtil(properties);
        jwtUtil.init();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, jwtUtil)).build();
    }

    @Test
    void validSignedTokenPassesTheExactTokenForConditionalRevocation() throws Exception {
        String token = jwtUtil.generateAccessToken("42", Map.of("userId", 42L));

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        verify(authService).logout(42L, token);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Bearer ", "Bearer", "Basic test", "Bearer invalid"})
    void missingOrInvalidAuthorizationReturns401WithoutRevocation(String authorization) throws Exception {
        MockHttpServletRequestBuilder request = post("/auth/logout");
        if (authorization != null) request.header("Authorization", authorization);

        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(authService);
    }

    @Test
    void expiredTokenReturns401WithoutRevocation() throws Exception {
        JwtProperties expiredProperties = new JwtProperties();
        expiredProperties.setSecret("logout-test-secret-at-least-32-characters");
        expiredProperties.setAccessTokenExpire(-1000);
        JwtUtil expired = new JwtUtil(expiredProperties);
        expired.init();
        String token = expired.generateAccessToken("42", Map.of("userId", 42L));

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(authService);
    }

    @Test
    void tokenWithoutUserIdCannotChooseARedisSession() throws Exception {
        String token = jwtUtil.generateAccessToken("42", Map.of());

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void invalidUserIdCannotChooseARedisSession(long userId) throws Exception {
        String token = jwtUtil.generateAccessToken("42", Map.of("userId", userId));

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(authService);
    }
}
