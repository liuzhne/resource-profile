package com.edu.auth.controller;

import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.LoginResponse;
import com.edu.auth.dto.UserInfoResponse;
import com.edu.auth.service.AuthService;
import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import com.edu.common.exception.BusinessException;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest http, HttpServletResponse response) {
        log.info("请求登录！");
        LoginResponse result = authService.login(request);
        refreshCookie(http, response, result.getRefreshToken());
        result.setRefreshToken(null);
        return Result.success(result);
    }

    @GetMapping("/userInfo")
    public Result<UserInfoResponse> getUserInfo(@RequestHeader("Authorization") String token) {
        // 解析 token 获取用户ID
        Long userId = authService.requireSession(accessToken(token));

        UserInfoResponse response = authService.getUserInfo(userId);
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token,
                              HttpServletRequest http, HttpServletResponse response) {
        if (token == null || !token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Result.error(401, "登录状态无效");
        }
        String jwt = token.substring(7).trim();
        if (jwt.isEmpty()) {
            return Result.error(401, "登录状态无效");
        }
        Long userId;
        try {
            Claims claims = jwtUtil.parseToken(jwt);
            Object rawUserId = claims.get("userId");
            if (rawUserId == null) {
                return Result.error(401, "登录状态无效");
            }
            userId = Long.valueOf(rawUserId.toString());
            if (userId <= 0) {
                return Result.error(401, "登录状态无效");
            }
        } catch (JwtException | IllegalArgumentException exception) {
            return Result.error(401, "登录状态无效");
        }

        boolean current = false;
        try { current = userId.equals(authService.requireSession(jwt)); }
        catch (BusinessException e) { if (e.getCode() != 401) throw e; }
        authService.logout(userId, jwt);
        if (current) refreshCookie(http, response, null);
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestHeader(value = "X-Refresh-Token", required = false) String header,
            @CookieValue(value = "educare_refresh", required = false) String cookie,
            HttpServletRequest http, HttpServletResponse response) {
        LoginResponse result = authService.refreshToken(header == null ? cookie : header);
        refreshCookie(http, response, result.getRefreshToken());
        result.setRefreshToken(null);
        return Result.success(result);
    }

    static String accessToken(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7) || header.substring(7).isBlank())
            throw new BusinessException(401, "登录状态无效");
        return header.substring(7).trim();
    }

    static void refreshCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        long ttl = 0;
        if (token != null) {
            // JWT is issuer-generated. Use its remaining expiry when rotating, never restart the seven-day clock.
            try {
                String body = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
                ttl = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("exp").asLong() - java.time.Instant.now().getEpochSecond();
            } catch (Exception e) { throw new IllegalStateException("刷新令牌有效期不可读取", e); }
        }
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        response.addHeader("Set-Cookie", ResponseCookie.from("educare_refresh", token == null ? "" : token)
                .httpOnly(true).secure(secure).sameSite("Strict").path("/").maxAge(Math.max(0, ttl)).build().toString());
    }
}
