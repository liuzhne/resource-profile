package com.edu.common.security;

import com.edu.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 服务入口鉴权门 {@link ServiceAuthFilter} 的自动装配：所有依赖 common 的 servlet 服务默认启用。
 *
 * <p>开关 {@code educare.service-auth.enabled}（默认开，fail-closed）。以下服务在各自 application.yml 显式关闭：
 * <ul>
 *   <li>auth-service：{@code /auth/**} 本就公开（登录/刷新），其余路径由自身 Spring Security 拒绝；</li>
 *   <li>agent-service：已有更严的 {@code AgentSelfAuthFilter}（只认 JWT，兼容 SSE {@code ?token=}）；</li>
 *   <li>mcp-student-data：没有业务 controller，{@code /mcp} 由 {@code McpTokenFilter}（X-MCP-Token）把守。</li>
 * </ul>
 * gateway 是 WebFlux，{@code @ConditionalOnWebApplication(SERVLET)} 使其天然不装配。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(value = "educare.service-auth.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceAuthAutoConfiguration {

    @Bean
    public FilterRegistrationBean<ServiceAuthFilter> serviceAuthFilterRegistration(
            JwtUtil jwtUtil, InternalCallCredential internalCredential, ObjectMapper objectMapper) {
        if (!internalCredential.isConfigured()) {
            log.warn("EDUCARE_INTERNAL_TOKEN 未配置：本服务将拒绝所有不带 JWT 的请求，包括 agent-service / mcp-student-data 的内部调用");
        }
        FilterRegistrationBean<ServiceAuthFilter> reg =
                new FilterRegistrationBean<>(new ServiceAuthFilter(jwtUtil, internalCredential, objectMapper));
        reg.addUrlPatterns("/*");
        // 排在业务 filter（含 RoleContextFilter）之前：未通过准入的请求不进入任何后续处理。
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}
