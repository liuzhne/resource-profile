package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RoleContextFilter 单测：只有「不带 token 且出示合法内部凭证」的请求才被标为内部调用（字段不脱敏）。
 */
class RoleContextFilterTest {

    private static final String SECRET = "internal-secret-at-least-32-chars-0001";

    private final AtomicBoolean seenInternal = new AtomicBoolean();
    private final AtomicReference<Set<String>> seenRoles = new AtomicReference<>();
    /** 在 chain 内部抓取 filter 写入的上下文（finally 会清掉，事后读不到）。 */
    private final FilterChain chain = (req, res) -> {
        seenInternal.set(RequestContext.isInternal());
        seenRoles.set(RequestContext.getRoles());
    };

    private JwtUtil jwtUtil;
    private RoleContextFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        filter = new RoleContextFilter(jwtUtil, new InternalCallCredential(SECRET));
    }

    private void run(MockHttpServletRequest req) throws Exception {
        filter.doFilter(req, new MockHttpServletResponse(), chain);
    }

    @Test
    void bearer_setsRoles_notInternal() throws Exception {
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("teacher"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/student/1");
        req.addHeader("Authorization", "Bearer tok");

        run(req);

        assertThat(seenRoles.get()).containsExactly("teacher");
        assertThat(seenInternal.get()).isFalse();
    }

    @Test
    void validInternalCredential_withoutToken_marksInternal() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/student/1");
        req.addHeader(InternalCallCredential.HEADER, SECRET);

        run(req);

        assertThat(seenInternal.get()).isTrue();
        assertThat(seenRoles.get()).isEmpty();
    }

    @Test
    void bearerWins_overInternalCredential() throws Exception {
        // 端用户请求照常按角色脱敏，附上内部凭证也不能换来不脱敏
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/student/1");
        req.addHeader("Authorization", "Bearer tok");
        req.addHeader(InternalCallCredential.HEADER, SECRET);

        run(req);

        assertThat(seenInternal.get()).isFalse();
        assertThat(seenRoles.get()).containsExactly("student");
    }

    @Test
    void anonymousOrWrongCredential_isNeitherInternalNorPrivileged() throws Exception {
        run(new MockHttpServletRequest("GET", "/student/1"));
        assertThat(seenInternal.get()).isFalse();
        assertThat(seenRoles.get()).isEmpty();

        MockHttpServletRequest wrong = new MockHttpServletRequest("GET", "/student/1");
        wrong.addHeader(InternalCallCredential.HEADER, "guessed-value");
        run(wrong);
        assertThat(seenInternal.get()).isFalse();
        assertThat(seenRoles.get()).isEmpty();
    }

    @Test
    void contextIsClearedAfterRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/student/1");
        req.addHeader(InternalCallCredential.HEADER, SECRET);

        run(req);

        assertThat(RequestContext.isInternal()).isFalse();
        assertThat(RequestContext.getRoles()).isEmpty();
    }
}
