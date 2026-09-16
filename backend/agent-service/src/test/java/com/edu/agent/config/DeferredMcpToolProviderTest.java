package com.edu.agent.config;

import com.edu.common.exception.BusinessException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeferredMcpToolProviderTest {
    @Test
    void dependencyFailureDoesNotBlockConstructionAndFreshClientRecovers() {
        McpSyncClient failing = mock(McpSyncClient.class);
        when(failing.initialize()).thenThrow(new IllegalStateException("429"));
        McpSyncClient healthy = healthyClient("student");
        AtomicInteger calls = new AtomicInteger();
        try (var provider = new DeferredMcpToolProvider(List.of("student"), name -> calls.getAndIncrement() == 0 ? failing : healthy)) {
            assertEquals(0, calls.get(), "construction must not initialize network dependencies");
            provider.refresh("student");
            assertThrows(BusinessException.class, provider::getToolCallbacks);
            verify(failing).close();
            provider.refresh("student");
            assertEquals(1, provider.getToolCallbacks().length);
            assertEquals(2, calls.get(), "failed SDK client must be replaced for retry");
        }
        verify(healthy).close();
    }

    @Test
    void partialToolsCannotBeMistakenForReadyAndFailedPingInvalidatesSnapshot() {
        McpSyncClient student = healthyClient("student");
        McpSyncClient rag = healthyClient("rag");
        try (var provider = new DeferredMcpToolProvider(List.of("student", "rag"), name -> name.equals("student") ? student : rag)) {
            provider.refresh("student");
            assertThrows(BusinessException.class, provider::getToolCallbacks);
            provider.refresh("rag");
            assertEquals(2, provider.getToolCallbacks().length);
            when(rag.ping()).thenThrow(new IllegalStateException("gone"));
            provider.refresh("rag");
            assertThrows(BusinessException.class, provider::getToolCallbacks);
        }
    }

    private McpSyncClient healthyClient(String name) {
        McpSyncClient client = mock(McpSyncClient.class);
        var schema = new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null);
        var tool = new McpSchema.Tool(name, null, "test tool", schema, null, null, null);
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool), null));
        var implementation = new McpSchema.Implementation(name, "1.0.0");
        when(client.getClientInfo()).thenReturn(implementation);
        return client;
    }
}
