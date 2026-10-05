package com.edu.gateway.filter;

import com.edu.common.security.InternalCallCredential;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关剥离内部凭证头单测：客户端伪造的 X-Internal-Token 不得透传到下游（含公开路径）。
 */
class InternalHeaderStripFilterTest {

    private final InternalHeaderStripFilter filter = new InternalHeaderStripFilter();
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @Test
    void spoofedHeader_isRemoved_otherHeadersKept() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/student/1")
                .header(InternalCallCredential.HEADER, "spoofed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good"));

        filter.filter(ex, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.containsKey(InternalCallCredential.HEADER)).isFalse();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer good");
    }

    @Test
    void lowerCaseHeaderOnPublicPath_isAlsoRemoved() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login")
                .header("x-internal-token", "spoofed"));

        filter.filter(ex, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().containsKey(InternalCallCredential.HEADER)).isFalse();
    }

    @Test
    void requestWithoutHeader_passesUnchanged() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/student/1"));

        filter.filter(ex, chain).block();

        assertThat(forwarded.get()).isSameAs(ex);
    }

    @Test
    void runsBeforeJwtAuthFilter() {
        assertThat(filter.getOrder()).isLessThan(new JwtAuthGlobalFilter(null, null, null).getOrder());
    }
}
