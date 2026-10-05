package com.edu.gateway.filter;

import com.edu.common.security.InternalCallCredential;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 剥掉客户端自带的内部调用凭证头 {@value InternalCallCredential#HEADER}，使其无法经网关伪造或透传到下游。
 *
 * <p>该凭证只应出现在服务间直连（agent-service / mcp-student-data 的 Feign）上；经网关进来的都是端用户流量。
 * 排在所有 filter 之前，且不看 {@code educare.gateway.auth.enabled}、不区分公开路径（含 {@code /auth/**}）——
 * 无论后续是否鉴权，下游都收不到客户端给的这个头。
 */
@Component
public class InternalHeaderStripFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getHeaders().containsKey(InternalCallCredential.HEADER)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(InternalCallCredential.HEADER))
                .build();
        return chain.filter(exchange.mutate().request(stripped).build());
    }

    @Override
    public int getOrder() {
        // 早于 JwtAuthGlobalFilter（-100）与路由转发
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
