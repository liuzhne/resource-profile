package com.edu.mcp.student.config;

import com.edu.mcp.student.security.StudentAccessPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.util.Map;

/**
 * AUTHZ-MCP-20261013：替换 Spring AI 自动装配的 Streamable HTTP transport（其 bean 为
 * {@code @ConditionalOnMissingBean}），参数与自动装配一致，只多一个 {@code contextExtractor}：
 * 把每个 MCP HTTP 请求的 {@code Authorization} 头放进 {@link McpTransportContext}，tool 执行时经
 * {@code ToolContext → McpSyncServerExchange.transportContext()} 取出做身份校验。
 *
 * <p>为什么不用 ThreadLocal / RequestContextHolder：MCP SDK 的同步 server 在 Reactor 线程上执行 tool，
 * 不保证与 servlet 请求线程相同；transport context 是 SDK 为此设计的正式通道。
 */
@Configuration
public class McpServerTransportConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            ObjectMapper objectMapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> {
                    String auth = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
                    return auth == null ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of(StudentAccessPolicy.AUTHORIZATION_KEY, auth));
                })
                .build();
    }
}
