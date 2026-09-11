package com.edu.gateway.filter;

import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 网关全局鉴权门单测：纯 Mockito + MockServerWebExchange，不起 Spring 上下文。
 *
 * <p>前 7 例隔离签名/过期门（{@code checkSession=false}）；后 3 例验 A6 会话白名单门
 * （{@code checkSession=true} + mock 反应式 Redis）。
 */
class JwtAuthGlobalFilterTest {

    private JwtUtil jwtUtil;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private JwtAuthGlobalFilter filter;
    private AtomicBoolean chainCalled;
    private GatewayFilterChain chain;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        filter = new JwtAuthGlobalFilter(jwtUtil, new ObjectMapper(), redisTemplate);
        ReflectionTestUtils.setField(filter, "enabled", true);
        // 前 7 例隔离签名门；A6 会话门各自在用例内打开
        ReflectionTestUtils.setField(filter, "checkSession", false);
        chainCalled = new AtomicBoolean(false);
        chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };
    }

    @Test
    void publicAuthPath_passesThrough_withoutToken() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login"));
        filter.filter(ex, chain).block();
        assertThat(chainCalled).isTrue();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void missingToken_returns401() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/1"));
        filter.filter(ex, chain).block();
        assertThat(chainCalled).isFalse();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidToken_returns401() {
        when(jwtUtil.validateToken("bad")).thenReturn(false);
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/mental/score/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad"));
        filter.filter(ex, chain).block();
        assertThat(chainCalled).isFalse();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validToken_inHeader_passesThrough() {
        when(jwtUtil.validateToken("good")).thenReturn(true);
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good"));
        filter.filter(ex, chain).block();
        assertThat(chainCalled).isTrue();
    }

    @Test
    void validToken_inQueryParam_passesThrough_forSse() {
        when(jwtUtil.validateToken("good")).thenReturn(true);
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/agent/api/v1/warning/stream")
                        .queryParam("token", "good"));
        filter.filter(ex, chain).block();
        assertThat(chainCalled).isTrue();
    }

    @Test
    void internalPath_isForbidden_evenWithValidToken() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.post("/agent/api/v1/_internal/loop/dry-run")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good"));
        filter.filter(ex, chain).block();
        assertThat(chainCalled).isFalse();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void disabled_passesEverythingThrough() {
        ReflectionTestUtils.setField(filter, "enabled", false);
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/1"));
        filter.filter(ex, chain).block();
        assertThat(chainCalled).isTrue();
        verifyNoInteractions(jwtUtil);
    }

    // ---- A6 会话白名单门（checkSession=true）----

    @Test
    void session_inWhitelist_passesThrough() {
        ReflectionTestUtils.setField(filter, "checkSession", true);
        when(jwtUtil.validateToken("good")).thenReturn(true);
        when(jwtUtil.getSubject("good")).thenReturn("7");
        when(valueOps.get("token:7")).thenReturn(Mono.just("good"));

        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good"));
        filter.filter(ex, chain).block();

        assertThat(chainCalled).isTrue();
    }

    @Test
    void session_missingFromWhitelist_returns401_afterLogout() {
        ReflectionTestUtils.setField(filter, "checkSession", true);
        when(jwtUtil.validateToken("good")).thenReturn(true);
        when(jwtUtil.getSubject("good")).thenReturn("7");
        when(valueOps.get("token:7")).thenReturn(Mono.empty()); // 登出已删 key

        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good"));
        filter.filter(ex, chain).block();

        assertThat(chainCalled).isFalse();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void session_supersededByNewerToken_returns401() {
        ReflectionTestUtils.setField(filter, "checkSession", true);
        when(jwtUtil.validateToken("old")).thenReturn(true);
        when(jwtUtil.getSubject("old")).thenReturn("7");
        when(valueOps.get("token:7")).thenReturn(Mono.just("newer")); // 重登/改密覆盖

        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer old"));
        filter.filter(ex, chain).block();

        assertThat(chainCalled).isFalse();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
