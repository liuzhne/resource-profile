package com.edu.agent.core;

import com.edu.agent.skill.SkillLoader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 锁定三个 SubAgent 与七个 MCP 工具的精确名称契约。 */
class SubAgentRegistryContractTest {

    @Test
    void eachSubAgentReceivesItsCanonicalToolSubset() {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.run(any(AgentLoopRequest.class)))
                .thenReturn(new AgentLoopResult(AgentLoopStatus.COMPLETED, "ok", List.of(), 1));
        SkillLoader skills = mock(SkillLoader.class);
        when(skills.getSkill(anyString())).thenReturn(Optional.empty());

        SubAgentRegistry registry = new SubAgentRegistry(loop, skills);
        ReflectionTestUtils.setField(registry, "enabled", true);
        ReflectionTestUtils.setField(registry, "maxIterations", 4);

        List<ToolCallback> allTools = List.of(
                namedTool("get_student_profile"),
                namedTool("get_academic_history"),
                namedTool("get_mental_indicators"),
                namedTool("get_attendance"),
                namedTool("search_cases"),
                namedTool("search_policies"),
                namedTool("search_psychology"));

        List<ToolCallback> subAgents = registry.subAgentTools(() -> allTools);
        assertThat(subAgents).extracting(cb -> cb.getToolDefinition().name())
                .containsExactly("consult_head_teacher", "consult_psychologist", "consult_academic_advisor");

        subAgents.forEach(cb -> cb.call("{\"query\":\"test\"}"));

        ArgumentCaptor<AgentLoopRequest> requests = ArgumentCaptor.forClass(AgentLoopRequest.class);
        verify(loop, times(3)).run(requests.capture());
        assertThat(toolNames(requests.getAllValues().get(0)))
                .containsExactly("get_academic_history", "get_attendance");
        assertThat(toolNames(requests.getAllValues().get(1)))
                .containsExactly("get_mental_indicators", "search_psychology");
        assertThat(toolNames(requests.getAllValues().get(2)))
                .containsExactly("get_academic_history", "search_cases");
    }

    private static List<String> toolNames(AgentLoopRequest request) {
        return request.tools().stream().map(cb -> cb.getToolDefinition().name()).toList();
    }

    private static ToolCallback namedTool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .build());
        return callback;
    }
}
