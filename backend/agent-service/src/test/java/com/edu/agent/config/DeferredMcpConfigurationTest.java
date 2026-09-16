package com.edu.agent.config;

import com.edu.common.exception.BusinessException;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.GenericApplicationContext;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeferredMcpConfigurationTest {
    @Test
    void actualSpringAiToolAutoconfigurationStartsBeforeMcpIsReady() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
                .withUserConfiguration(DeferredMcpConfiguration.class)
                .withBean(McpSyncHttpClientRequestCustomizer.class, () -> mock(McpSyncHttpClientRequestCustomizer.class))
                .withPropertyValues("educare.mcp.deferred-enabled=true", "spring.ai.mcp.client.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(ToolCallbackResolver.class));
                    assertThrows(BusinessException.class, () -> context.getBean(ToolCallbackResolver.class).resolve("student_tool"));
                });
    }
    @Test
    void resolverUsesCurrentToolsInsteadOfEmptyStartupSnapshot() {
        DeferredMcpToolProvider provider = mock(DeferredMcpToolProvider.class);
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            ToolCallbackResolver resolver = new DeferredMcpConfiguration().deferredToolCallbackResolver(context, List.of(), provider);
            verifyNoInteractions(provider);
            ToolCallback first = tool("student_tool");
            ToolCallback refreshed = tool("student_tool");
            when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{first}, new ToolCallback[]{refreshed});
            assertSame(first, resolver.resolve("student_tool"));
            assertSame(refreshed, resolver.resolve("student_tool"));
        }
    }
    private ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder().name(name).description("test").inputSchema("{}").build());
        return callback;
    }
}
