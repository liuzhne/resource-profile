package com.edu.agent.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.support.GenericApplicationContext;

@Configuration
@ConditionalOnProperty(name = "educare.mcp.deferred-enabled", havingValue = "true")
public class DeferredMcpConfiguration {
    /** Spring AI's default resolver snapshots providers at startup. Resolve MCP only on invocation. */
    @Bean
    public ToolCallbackResolver deferredToolCallbackResolver(GenericApplicationContext context,
                                                            List<ToolCallback> callbacks,
                                                            DeferredMcpToolProvider provider) {
        ToolCallbackResolver dynamic = name -> java.util.Arrays.stream(provider.getToolCallbacks())
                .filter(tool -> name.equals(tool.getToolDefinition().name())).findFirst().orElse(null);
        return new DelegatingToolCallbackResolver(List.of(new StaticToolCallbackResolver(callbacks), dynamic,
                SpringBeanToolCallbackResolver.builder().applicationContext(context).build()));
    }

    @Bean
    public DeferredMcpToolProvider deferredMcpToolProvider(
            @Value("${spring.ai.mcp.client.enabled:true}") boolean bootClientEnabled,
            @Value("${MCP_STUDENT_DATA_URL:http://localhost:8094}") String studentUrl,
            @Value("${MCP_KNOWLEDGE_RAG_URL:http://localhost:8095}") String ragUrl,
            @Value("${MCP_KNOWLEDGE_RAG_ENDPOINT:/mcp}") String ragEndpoint,
            @Value("${MCP_CLIENT_REQUEST_TIMEOUT:120s}") String timeoutValue,
            McpSyncHttpClientRequestCustomizer customizer) {
        if (bootClientEnabled) {
            throw new IllegalStateException("Deferred MCP requires spring.ai.mcp.client.enabled=false to avoid startup initialize");
        }
        Duration timeout = DurationStyle.detectAndParse(timeoutValue);
        Map<String, String> urls = Map.of("student-data", studentUrl, "knowledge-rag", ragUrl);
        return new DeferredMcpToolProvider(List.of("student-data", "knowledge-rag"), name -> {
            var transport = HttpClientStreamableHttpTransport.builder(urls.get(name))
                    .endpoint(name.equals("knowledge-rag") ? ragEndpoint : "/mcp")
                    .connectTimeout(Duration.ofSeconds(5))
                    .httpRequestCustomizer(customizer).build();
            return McpClient.sync(transport).requestTimeout(timeout)
                    .clientInfo(new McpSchema.Implementation("educare-" + name, "1.0.0"))
                    .build();
        });
    }
}
