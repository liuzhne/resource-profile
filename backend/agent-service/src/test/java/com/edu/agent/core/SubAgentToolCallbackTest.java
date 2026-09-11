package com.edu.agent.core;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * J-2.1：SubAgentToolCallback（agent-as-tool）单测。
 */
class SubAgentToolCallbackTest {

    private ToolCallback namedTool(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = DefaultToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }

    private SubAgent spec(List<String> toolNames) {
        return new SubAgent("consult_psychologist", "心理咨询师视角", "你是心理咨询师", toolNames, 4);
    }

    @Test
    void toolDefinitionReflectsSpec() {
        SubAgentToolCallback cb = new SubAgentToolCallback(spec(List.of()), mock(AgentLoop.class), List::of);
        assertThat(cb.getToolDefinition().name()).isEqualTo("consult_psychologist");
        assertThat(cb.getToolDefinition().description()).contains("心理咨询师");
        assertThat(cb.getToolDefinition().inputSchema()).contains("query");
    }

    @Test
    void callRunsSubLoopAndReturnsFinalAnswer() {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.run(any(AgentLoopRequest.class)))
                .thenReturn(new AgentLoopResult(AgentLoopStatus.COMPLETED, "专家结论：低风险", List.of(), 1));
        SubAgentToolCallback cb = new SubAgentToolCallback(spec(List.of()), loop, List::of);

        String out = cb.call("{\"query\":\"评估学生1\"}");
        assertThat(out).isEqualTo("专家结论：低风险");
    }

    @Test
    void filtersToolsByNameAndPassesQuery() {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.run(any(AgentLoopRequest.class)))
                .thenReturn(new AgentLoopResult(AgentLoopStatus.COMPLETED, "ok", List.of(), 1));
        ToolCallback a = namedTool("get_mental_indicators");
        ToolCallback b = namedTool("get_attendance");
        // 子代理只允许 get_mental_indicators
        SubAgentToolCallback cb = new SubAgentToolCallback(
                spec(List.of("get_mental_indicators")), loop, () -> List.of(a, b));

        cb.call("{\"query\":\"q1\"}");

        ArgumentCaptor<AgentLoopRequest> cap = ArgumentCaptor.forClass(AgentLoopRequest.class);
        verify(loop).run(cap.capture());
        AgentLoopRequest req = cap.getValue();
        assertThat(req.userPrompt()).isEqualTo("q1");
        assertThat(req.tools()).hasSize(1);
        assertThat(req.tools().get(0).getToolDefinition().name()).isEqualTo("get_mental_indicators");
    }

    @Test
    void nullFinalAnswerReturnsFallback() {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.run(any(AgentLoopRequest.class)))
                .thenReturn(new AgentLoopResult(AgentLoopStatus.MAX_ITERATIONS, null, List.of(), 4));
        SubAgentToolCallback cb = new SubAgentToolCallback(spec(List.of()), loop, List::of);
        assertThat(cb.call("{\"query\":\"x\"}")).contains("未给出结论").contains("MAX_ITERATIONS");
    }

    @Test
    void nonJsonInputUsedAsQuery() {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.run(any(AgentLoopRequest.class)))
                .thenReturn(new AgentLoopResult(AgentLoopStatus.COMPLETED, "ok", List.of(), 1));
        SubAgentToolCallback cb = new SubAgentToolCallback(spec(List.of()), loop, List::of);
        cb.call("纯文本问题");
        ArgumentCaptor<AgentLoopRequest> cap = ArgumentCaptor.forClass(AgentLoopRequest.class);
        verify(loop).run(cap.capture());
        assertThat(cap.getValue().userPrompt()).isEqualTo("纯文本问题");
    }
}
