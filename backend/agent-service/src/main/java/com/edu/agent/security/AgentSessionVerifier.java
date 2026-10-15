package com.edu.agent.security;

import com.edu.common.security.Roles;
import com.edu.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/** Shared direct-service and long-lived SSE gate. A signed JWT alone is not a live session. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSessionVerifier {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public enum Status { VALID, UNAUTHORIZED, UNAVAILABLE }

    public record Verification(Status status, Session session) { }

    /** Keeps the credential private and never includes it in a generated toString/log entry. */
    public static final class Session {
        private final Long userId;
        private final String token;
        private final boolean warningsAllowed;

        private Session(Long userId, String token, boolean warningsAllowed) {
            this.userId = userId;
            this.token = token;
            this.warningsAllowed = warningsAllowed;
        }

        public Long userId() { return userId; }
        public boolean warningsAllowed() { return warningsAllowed; }
    }

    public Verification verify(String token) {
        if (token == null || token.isBlank()) return denied(Status.UNAUTHORIZED);
        Claims claims;
        Long userId;
        try {
            claims = jwtUtil.parseToken(token); // signature and absolute expiry, on every check
            userId = Long.valueOf(claims.getSubject());
            if (userId <= 0 || (claims.get("tokenUse") != null && !"access".equals(claims.get("tokenUse")))) {
                return denied(Status.UNAUTHORIZED);
            }
        } catch (Exception ignored) {
            return denied(Status.UNAUTHORIZED);
        }
        try {
            if (!token.equals(redis.opsForValue().get("token:" + userId))) return denied(Status.UNAUTHORIZED);
        } catch (Exception e) {
            // Exception messages may contain infrastructure details; only the type is logged.
            log.warn("Agent session verification unavailable, fail closed: {}", e.getClass().getSimpleName());
            return denied(Status.UNAVAILABLE);
        }
        return new Verification(Status.VALID, new Session(userId, token, permitsWarnings(claims)));
    }

    /** Role/permission edits revoke the Redis session; logout/rotation/expiry all close existing streams. */
    public boolean isActiveWarningSession(Session session) {
        if (session == null || !session.warningsAllowed()) return false;
        Verification current = verify(session.token);
        return current.status() == Status.VALID && current.session().warningsAllowed();
    }

    private boolean permitsWarnings(Claims claims) {
        Object rawRoles = claims.get("roles");
        if (!(rawRoles instanceof Collection<?> roles)) return false;
        boolean staff = Set.of(Roles.STAFF_VIEW).stream().anyMatch(roles::contains);
        if (!staff) return false;
        if (roles.contains(Roles.ADMIN)) return true;
        Object permissions = claims.get("permissions");
        // Match AccessGuard: pre-migration sessions retain their canonical role boundary.
        return permissions == null || (permissions instanceof Collection<?> codes && codes.contains("agent:read"));
    }

    private Verification denied(Status status) { return new Verification(status, null); }
}
