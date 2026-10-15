package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RagSnapshotCollectorTest {
    private static final String RESULT = "{\"query\":\"纯合成问题\",\"collection\":\"case\",\"top_k\":5,\"reranked\":false,\"fallback\":false,\"count\":1,\"chunks\":[{\"chunk_id\":\"synthetic-case-001\",\"source_db\":\"case\",\"title\":\"纯合成来源\",\"content\":\"纯合成知识片段\",\"score\":0.8}]}";
    private ToolCallback tool(String name, String result) {
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder().name(name).description("synthetic").inputSchema("{}").build());
        when(callback.call(anyString())).thenReturn(result);
        return callback;
    }

    @Test
    void noSearchOrOnlyStudentToolNeverFabricatesRetrieval() {
        var collector = new RagSnapshotCollector();
        var student = tool("get_student_profile", "{}");
        collector.wrap(List.of(student)).getFirst().call("{}");
        var snapshot = JSON.parseObject(collector.snapshot());
        assertThat(snapshot.getBoolean("executed")).isFalse();
        assertThat(snapshot.getJSONArray("searches")).isEmpty();
        assertThat(snapshot.getJSONArray("chunks")).isEmpty();
    }

    @Test
    void actualSpringAiPrefixedMcpCallbackContentArrayCapturesTrueSourceAndDoesNotChangeOutput() {
        var client = mock(McpSyncClient.class);
        when(client.getClientInfo()).thenReturn(new McpSchema.Implementation("synthetic-rag", "1.0.0"));
        var schema = new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null);
        var sdkTool = new McpSchema.Tool("search_cases", null, "synthetic", schema, null, null, null);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(RESULT)), false));
        var original = new SyncMcpToolCallback(client, sdkTool);
        var collector = new RagSnapshotCollector();
        String output = collector.wrap(List.of(original)).getFirst().call("{\"query\":\"纯合成问题\"}");
        assertThat(JSON.parseArray(output).getJSONObject(0).getString("text")).isEqualTo(RESULT);
        var snapshot = JSON.parseObject(collector.snapshot());
        assertThat(snapshot.getBoolean("executed")).isTrue();
        assertThat(snapshot.getJSONArray("searches").getJSONObject(0).getString("status")).isEqualTo("SUCCEEDED");
        assertThat(snapshot.getJSONArray("chunks").getJSONObject(0).getString("chunk_id")).isEqualTo("synthetic-case-001");
        assertThat(snapshot.getJSONArray("chunks").getJSONObject(0).getString("content")).isEqualTo("纯合成知识片段");
        verify(client).callTool(any(McpSchema.CallToolRequest.class));
    }

    @Test
    void thrownOrProviderErrorResultsAreFailedWithoutSensitiveErrorText() {
        var collector = new RagSnapshotCollector();
        var throwing = tool("search_cases", RESULT);
        when(throwing.call(anyString())).thenThrow(new IllegalStateException("SYNTHETIC_SECRET_CONTENT"));
        assertThatThrownBy(() -> collector.wrap(List.of(throwing)).getFirst().call("{}")).isInstanceOf(IllegalStateException.class);
        collector.wrap(List.of(tool("search_policies", "{\"error\":\"SYNTHETIC_SECRET_CONTENT\",\"chunks\":[]}"))).getFirst().call("{}");
        collector.wrap(List.of(tool("search_psychology", "malformed"))).getFirst().call("{}");
        var snapshot = JSON.parseObject(collector.snapshot());
        assertThat(snapshot.getJSONArray("searches")).hasSize(3);
        for (int i = 0; i < 3; i++) assertThat(snapshot.getJSONArray("searches").getJSONObject(i).getString("status")).isEqualTo("FAILED");
        assertThat(snapshot.getJSONArray("chunks")).isEmpty();
        assertThat(collector.snapshot()).doesNotContain("SYNTHETIC_SECRET_CONTENT");
    }

    @Test
    void parallelSearchesDeduplicateTrueChunksAndSnapshotLimitsDoNotPreventToolExecution() {
        var collector = new RagSnapshotCollector();
        var original = tool("search_cases", RESULT);
        var callback = collector.wrap(List.of(original)).getFirst();
        var requests = java.util.stream.IntStream.range(0, 40).mapToObj(i -> CompletableFuture.runAsync(() -> callback.call("{}"))).toList();
        requests.forEach(CompletableFuture::join);
        var snapshot = JSON.parseObject(collector.snapshot());
        assertThat(snapshot.getJSONArray("searches")).hasSize(32);
        assertThat(snapshot.getJSONArray("chunks")).hasSize(1);
        assertThat(snapshot.getBoolean("truncated")).isTrue();
        verify(original, times(40)).call("{}");
    }
}
