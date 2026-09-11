package com.edu.common.util;

import com.edu.common.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 纯逻辑单测（真实 jjwt，无 Spring 上下文）。
 * 覆盖 A5 密钥 fail-fast、签发/解析往返、roles claim、过期与防篡改。
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef-0123456789abcdef";

    private JwtProperties props;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret(SECRET);
        props.setAccessTokenExpire(60_000L);
        props.setRefreshTokenExpire(120_000L);
        jwtUtil = utilWithSecret(SECRET);
    }

    private JwtUtil utilWithSecret(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        JwtUtil util = new JwtUtil(p);
        util.init();
        return util;
    }

    // ---- A5 fail-fast ----

    @Test
    void init_blankSecret_failsFast() {
        assertThatThrownBy(() -> utilWithSecret(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT 密钥未配置");
    }

    @Test
    void init_shortSecret_failsFast() {
        assertThatThrownBy(() -> utilWithSecret("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("密钥强度不足");
    }

    // ---- 签发/解析往返 ----

    @Test
    void accessToken_roundtrip_subjectAndClaims() {
        String token = jwtUtil.generateAccessToken("42", Map.of("roles", java.util.List.of("admin", "teacher")));
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getSubject(token)).isEqualTo("42");
        assertThat(jwtUtil.parseRoles(token)).containsExactlyInAnyOrder("admin", "teacher");
        Object roles = jwtUtil.getClaim(token, "roles");
        assertThat(roles).isNotNull();
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    void refreshToken_roundtrip_noRolesClaim() {
        String token = jwtUtil.generateRefreshToken("7");
        assertThat(jwtUtil.getSubject(token)).isEqualTo("7");
        assertThat(jwtUtil.parseRoles(token)).isEmpty();
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void parseRoles_nonCollectionClaim_returnsEmptySet() {
        String token = jwtUtil.generateAccessToken("1", Map.of("roles", "admin"));
        assertThat(jwtUtil.parseRoles(token)).isEmpty();
    }

    // ---- 失效与防篡改 ----

    @Test
    void expiredToken_validateFalse_parseThrowsExpired() {
        JwtProperties p = new JwtProperties();
        p.setSecret(SECRET);
        p.setAccessTokenExpire(-1_000L);
        JwtUtil shortLived = new JwtUtil(p);
        shortLived.init();
        String token = shortLived.generateAccessToken("1", null);
        assertThat(shortLived.validateToken(token)).isFalse();
        assertThatThrownBy(() -> shortLived.getSubject(token)).isInstanceOf(ExpiredJwtException.class);
        assertThat(shortLived.parseRoles(token)).isEmpty();
    }

    @Test
    void tamperedSignature_validateFalse() {
        String token = jwtUtil.generateAccessToken("1", null);
        String forged = token.substring(0, token.length() - 2) + "xx";
        assertThat(jwtUtil.validateToken(forged)).isFalse();
        assertThatThrownBy(() -> jwtUtil.getSubject(forged)).isInstanceOf(Exception.class);
    }

    @Test
    void differentSecret_tokenRejected() {
        String token = jwtUtil.generateAccessToken("1", null);
        JwtUtil other = utilWithSecret(SECRET.substring(0, 32) + "-other-secret-padding!");
        assertThat(other.validateToken(token)).isFalse();
    }
}
