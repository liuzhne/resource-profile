package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * J-2.4：并行多工具批 —— action 数组解析、合并 observation、单个失败/被拒不影响其它。
 */
class AgentLoopParallelTest {

    private ToolCallback tool(String name, String ret) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = DefaultToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
        when(cb.getToolDefinition()).thenReturn(def);
        when(cb.call(anyString())).thenReturn(ret);
        return cb;
    }

    private AgentLoop loop(ChatClient cc) {
        return new AgentLoop(cc, mock(LangfuseClient.class));
    }

    @Test
    void arrayActionParsedAsParallelCalls() {
        AgentLoop l = loop(mock(ChatClient.class, RETURNS_DEEP_STUBS));
        AgentLoop.ParsedTurn p = l.parseLlmJson(
                "{\"thought\":\"t\",\"action\":[{\"tool\":\"a\",\"args\":{}},{\"tool\":\"b\",\"args\":{}}]}");
        assertThat(p.parallelCalls()).hasSize(2);
        assertThat(p.parallelCalls()).extracting(AgentLoop.ToolCall::name).containsExactly("a", "b");
        assertThat(p.toolName()).isNull();
    }

    @Test
    void executeParallelMergesObservations() {
        AgentLoop l = loop(mock(ChatClient.class, RETURNS_DEEP_STUBS));
        AgentLoopRequest req = new AgentLoopRequest("s", "u",
                List.of(tool("a", "RA"), tool("b", "RB")), 5, "par");
        String merged = l.executeParallel(req,
                List.of(new AgentLoop.ToolCall("a", "{}"), new AgentLoop.ToolCall("b", "{}")));
        assertThat(merged).contains("Observation[a]: RA").contains("Observation[b]: RB");
    }

    @Test
    void parallelBranchEndToEndThenFinalAnswer() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(
                "{\"thought\":\"取两份数据\",\"action\":[{\"tool\":\"a\",\"args\":{}},{\"tool\":\"b\",\"args\":{}}]}",
                "{\"thought\":\"够了\",\"final_answer\":\"done\"}");
        AgentLoopResult r = loop(cc).run(new AgentLoopRequest("s", "u",
                List.of(tool("a", "RA"), tool("b", "RB")), 5, "par"));

        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(r.iterations()).isEqualTo(2);
        AgentTrace batch = r.traces().get(0);
        assertThat(batch.toolName()).isEqualTo("[parallel:2]");
        assertThat(batch.toolResult()).contains("RA").contains("RB");
    }

    @Test
    void unknownToolInBatchBecomesErrorNotCrash() {
        AgentLoop l = loop(mock(ChatClient.class, RETURNS_DEEP_STUBS));
        AgentLoopRequest req = new AgentLoopRequest("s", "u", List.of(tool("a", "RA")), 5, "par");
        String merged = l.executeParallel(req,
                List.of(new AgentLoop.ToolCall("a", "{}"), new AgentLoop.ToolCall("ghost", "{}")));
        assertThat(merged).contains("Observation[a]: RA").contains("ERROR");
    }
}
