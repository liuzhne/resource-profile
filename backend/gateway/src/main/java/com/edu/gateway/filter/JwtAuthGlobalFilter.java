package com.edu.gateway.filter;

import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关全局鉴权门：对非公开路由强制校验 JWT（HS256 签名 + 未过期），不合格直接 401。
 *
 * <p><b>P0 修复</b>：此前 7 条路由仅 {@code StripPrefix=0}、各业务服务无 Spring Security，
 * 整套系统对未成年人心理/家庭数据零准入校验。{@code RoleContextFilter}/{@code ExportController}
 * 等下游注释都写「假设 gateway 已做鉴权层」，但实际上游无人拦截 —— 本 filter 即那个缺失的「上游」。
 *
 * <p><b>角色透传</b>无需新发 header：校验通过后 {@code Authorization} 头按默认行为转发给下游，
 * 下游 {@code RoleContextFilter} 解析签名 JWT 的 {@code roles} claim（签名即防伪），
 * 比网关下发明文身份头更安全。
 *
 * <p><b>token 双轨</b>：优先 {@code Authorization: Bearer}；缺失时回退 {@code ?token=}，
 * 兼容 EventSource 等带不了 header 的浏览器原生流（对齐 {@code WarningSseController} 约定）。
 *
 * <p><b>A6 会话撤销</b>：签名+过期之外再比对 Redis 会话白名单 {@code token:{userId}}（auth-service 登录写入、
 * 登出删除、改密/重登覆盖）。token 不在白名单即拒，使登出/改密后的旧 token 立即失效——单靠 JWT 无状态校验做不到这点。
 *
 * <p><b>_internal</b> 前缀（如 AgentLoop dry-run 手测端点）视为内网专用，一律不经公网网关暴露 → 403。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 会话白名单 key 前缀，对齐 auth-service 登录时写入的 {@code token:{userId}}。 */
    private static final String SESSION_KEY_PREFIX = "token:";

    /** 公开路径前缀：登录/刷新/登出 + 健康检查，无需 token。 */
    private static final List<String> PUBLIC_PREFIXES = List.of("/auth/", "/actuator/");

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 默认开（关闭 P0 缺口）；本地免登录联调 / 灰度排障可置 false（改后重启生效）。 */
    @Value("${educare.gateway.auth.enabled:true}")
    private boolean enabled;

    /**
     * A6 会话白名单校验开关，默认开。签名+过期校验之外再比对 Redis {@code token:{userId}}，
     * 使登出/改密/重新登录后的旧 token 立即失效。本地无 Redis 联调时可置 false（仅保留签名校验）。
     */
    @Value("${educare.gateway.auth.check-session:true}")
    private boolean checkSession;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!enabled || isPublic(path)) {
            return chain.filter(exchange);
        }
        // 内网端点禁止经公网网关访问
        if (path.contains("/_internal/")) {
            return deny(exchange, HttpStatus.FORBIDDEN, 403, "内网端点，禁止经网关访问");
        }

        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return deny(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录或缺少令牌");
        }
        if (!jwtUtil.validateToken(token)) {
            return deny(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已过期或令牌无效");
        }
        if (!checkSession) {
            // 仅签名+过期校验（本地无 Redis 联调）。Authorization 头随请求转发，下游解析角色。
            return chain.filter(exchange);
        }
        // A6：再比对 Redis 会话白名单，使登出/改密后的旧 token 立即失效。
        return verifySession(exchange, chain, token);
    }

    /**
     * 校验 token 仍在会话白名单内：{@code token:{userId}} 必须存在且等于当前 token。
     * 不存在（登出/过期清除）或不等（改密/重新登录覆盖）→ 401。
     * Redis 异常 → fail-closed 503（不放行未经校验的请求）。
     */
    private Mono<Void> verifySession(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        String subject;
        try {
            subject = jwtUtil.getSubject(token);
        } catch (Exception e) {
            subject = null;
        }
        if (subject == null) {
            return deny(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已失效，请重新登录");
        }
        // 注意：deny()/chain.filter() 均返回 Mono<Void>（不发元素），故不能用 switchIfEmpty 判 key 缺失
        // （Void 完成会被误判为空）。改用 defaultIfEmpty 把「key 不存在」折叠成空串，统一进比对分支。
        return redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + subject)
                .defaultIfEmpty("")
                .flatMap(stored -> token.equals(stored)
                        ? chain.filter(exchange)
                        : deny(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已失效，请重新登录"))
                .onErrorResume(e -> {
                    log.warn("网关会话校验 Redis 异常，fail-closed 拒绝：{}", e.getMessage());
                    return deny(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, "鉴权服务暂不可用");
                });
    }

    private boolean isPublic(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        // 降级：EventSource / 浏览器直链带不了 header 的场景用 ?token=
        String param = request.getQueryParams().getFirst("token");
        return (param != null && !param.isBlank()) ? param : null;
    }

    private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        // 安全审计：记录被拒访问（debug 级避免被探测流量刷屏）
        log.debug("网关鉴权拒绝 method={} path={} -> {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), code);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Result.error(code, message));
        } catch (Exception e) {
            // ObjectMapper 异常兜底，保证拒绝响应始终有体
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 在路由转发等默认 filter（order ≥ 0）之前执行
        return -100;
    }
}
