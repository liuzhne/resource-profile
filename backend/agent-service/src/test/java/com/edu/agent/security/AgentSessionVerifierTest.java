package com.edu.agent.security;

import com.edu.common.config.JwtProperties;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentSessionVerifierTest {
    private JwtProperties properties;
    private JwtUtil jwt;
    private ValueOperations<String, String> values;
    private AgentSessionVerifier sessions;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("test-only-session-key-with-at-least-32-bytes");
        properties.setAccessTokenExpire(60_000L);
        jwt = new JwtUtil(properties);
        jwt.init();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        sessions = new AgentSessionVerifier(jwt, redis);
    }

    private String live(String role, List<String> permissions) {
        String token = jwt.generateAccessToken("7", permissions == null
                ? Map.of("roles", List.of(role)) : Map.of("roles", List.of(role), "permissions", permissions));
        when(values.get("token:7")).thenReturn(token);
        return token;
    }

    @Test void signedStudentIsAuthenticatedButCannotReceiveWarnings() {
        var result = sessions.verify(live("student", List.of("agent:read")));
        assertThat(result.status()).isEqualTo(AgentSessionVerifier.Status.VALID);
        assertThat(result.session().warningsAllowed()).isFalse();
        assertThat(sessions.isActiveWarningSession(result.session())).isFalse();
    }

    @Test void teacherNeedsAgentReadPermission() {
        assertThat(sessions.verify(live("teacher", List.of("agent:read"))).session().warningsAllowed()).isTrue();
        assertThat(sessions.verify(live("teacher", List.of("student:read"))).session().warningsAllowed()).isFalse();
    }

    @Test void legacyStaffAndAdminMatchOrdinaryTaskBoundary() {
        assertThat(sessions.verify(live("teacher", null)).session().warningsAllowed()).isTrue();
        assertThat(sessions.verify(live("admin", List.of())).session().warningsAllowed()).isTrue();
    }

    @Test void allCanonicalStaffRolesAreAllowedWithReadPermission() {
        for (String role : com.edu.common.security.Roles.STAFF_VIEW) {
            assertThat(sessions.verify(live(role, List.of("agent:read"))).session().warningsAllowed()).as(role).isTrue();
        }
    }

    @Test void revokedAndReplacedTokensAreRejected() {
        String token = live("teacher", List.of("agent:read"));
        var connected = sessions.verify(token).session();
        when(values.get("token:7")).thenReturn(null);
        assertThat(sessions.verify(token).status()).isEqualTo(AgentSessionVerifier.Status.UNAUTHORIZED);
        assertThat(sessions.isActiveWarningSession(connected)).isFalse();
        when(values.get("token:7")).thenReturn("another-issued-session");
        assertThat(sessions.verify(token).status()).isEqualTo(AgentSessionVerifier.Status.UNAUTHORIZED);
    }

    @Test void expiredAndTamperedJwtDoNotReadRedis() {
        properties.setAccessTokenExpire(0L);
        String expired = jwt.generateAccessToken("7", Map.of("roles", List.of("admin")));
        assertThat(sessions.verify(expired).status()).isEqualTo(AgentSessionVerifier.Status.UNAUTHORIZED);
        assertThat(sessions.verify("invalid").status()).isEqualTo(AgentSessionVerifier.Status.UNAUTHORIZED);
        verifyNoInteractions(values);
    }

    @Test void refreshTokenCannotAuthenticateEvenIfWhitelistIsMisconfigured() {
        String refresh = jwt.generateRefreshToken("7");
        when(values.get("token:7")).thenReturn(refresh);
        assertThat(sessions.verify(refresh).status()).isEqualTo(AgentSessionVerifier.Status.UNAUTHORIZED);
        verifyNoInteractions(values);
    }

    @Test void redisFailureIsUnavailableAndClosesAuthorizedSession() {
        String token = live("teacher", List.of("agent:read"));
        var connected = sessions.verify(token).session();
        when(values.get("token:7")).thenThrow(new IllegalStateException("private-infrastructure-message"));
        assertThat(sessions.verify(token).status()).isEqualTo(AgentSessionVerifier.Status.UNAVAILABLE);
        assertThat(sessions.isActiveWarningSession(connected)).isFalse();
    }

    @Test void unknownRoleMissingSubjectAndMalformedPermissionsFailClosed() {
        assertThat(sessions.verify(live("custom-role", List.of("agent:read"))).session().warningsAllowed()).isFalse();
        String invalidSubject = jwt.generateAccessToken("invalid", Map.of("roles", List.of("admin")));
        assertThat(sessions.verify(invalidSubject).status()).isEqualTo(AgentSessionVerifier.Status.UNAUTHORIZED);
        String malformed = jwt.generateAccessToken("7", Map.of("roles", List.of("teacher"), "permissions", "agent:read"));
        when(values.get("token:7")).thenReturn(malformed);
        assertThat(sessions.verify(malformed).session().warningsAllowed()).isFalse();
    }
}
