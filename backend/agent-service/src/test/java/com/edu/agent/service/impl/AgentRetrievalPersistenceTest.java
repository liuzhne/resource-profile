package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.edu.agent.core.*;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.skill.SkillLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRetrievalPersistenceTest {
    @BeforeEach
    void prepareRealEntityMetadataForLambdaUpdate() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test-agent"), AgentTask.class);
    }
    @SuppressWarnings("unchecked")
    private AgentTaskServiceImpl service(AgentLoop loop, ToolCallback tool, AgentTaskMapper mapper) {
        ObjectProvider<ToolCallbackProvider> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(() -> new ToolCallback[]{tool});
        var skills = mock(SkillLoader.class); when(skills.composeActiveSkillsPrompt()).thenReturn("");
        var subagents = mock(SubAgentRegistry.class); when(subagents.subAgentTools(any())).thenReturn(List.of());
        return new AgentTaskServiceImpl(mapper, null, null, null, null, null, null, loop, providers, skills, subagents, Runnable::run);
    }

    private ToolCallback tool() {
        var tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(ToolDefinition.builder().name("search_cases").description("synthetic").inputSchema("{}").build());
        when(tool.call(anyString())).thenReturn("{\"chunks\":[{\"chunk_id\":\"synthetic-source\",\"content\":\"合成资料\"}],\"fallback\":false}");
        return tool;
    }

    @Test
    void realTaskPathPersistsActualToolResultRatherThanModelCitation() {
        var mapper = mock(AgentTaskMapper.class); var loop = mock(AgentLoop.class); var tool = tool();
        when(loop.run(any(), any())).thenAnswer(inv -> {
            AgentLoopRequest req = inv.getArgument(0);
            req.tools().getFirst().call("{\"query\":\"合成问题\"}");
            return new AgentLoopResult(AgentLoopStatus.COMPLETED, "model mentions fake citation", List.of(), 2);
        });
        var task = new AgentTask(); task.setId(1L); task.setStudentId(1L);
        ReflectionTestUtils.invokeMethod(service(loop, tool, mapper), "runAgentLoopForTask", task);
        assertThat(task.getRetrievedKnowledge()).contains("synthetic-source").doesNotContain("fake citation");
        verify(mapper).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void taskFailureStillPersistsExplicitFailedRetrievalSnapshot() {
        var mapper = mock(AgentTaskMapper.class); var loop = mock(AgentLoop.class); var tool = tool();
        when(tool.call(anyString())).thenThrow(new IllegalStateException("synthetic failure"));
        when(loop.run(any(), any())).thenAnswer(inv -> {
            AgentLoopRequest req = inv.getArgument(0); req.tools().getFirst().call("{}"); return null;
        });
        var task = new AgentTask(); task.setId(1L); task.setStudentId(1L);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service(loop, tool, mapper), "runAgentLoopForTask", task))
                .isInstanceOf(IllegalStateException.class);
        var snapshot = JSON.parseObject(task.getRetrievedKnowledge());
        assertThat(snapshot.getJSONArray("searches").getJSONObject(0).getString("status")).isEqualTo("FAILED");
        assertThat(snapshot.getJSONArray("chunks")).isEmpty();
        verify(mapper).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
