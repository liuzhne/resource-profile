package com.edu.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Locale;

/** Includes authentication, routing and upstream response headers, not client transfer time. */
@Component
public class RequestTimingGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.nanoTime();
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().add("Server-Timing", String.format(Locale.ROOT,
                    "gateway;dur=%.2f", (System.nanoTime() - start) / 1_000_000.0));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
