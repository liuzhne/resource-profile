package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * J-3.4：native 协议双轨 —— protocol=native 走 Spring AI 原生 tool-calling，单条 trace 返回最终文本。
 */
class AgentLoopNativeTest {

    @Test
    void nativeProtocolUsesToolCallbacksAndReturnsContent() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).toolCallbacks(anyList()).call().content())
                .thenReturn("native 最终答复");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        ReflectionTestUtils.setField(loop, "protocol", "native");

        AgentLoopResult r = loop.run(new AgentLoopRequest("sys", "u", List.of(), 5, "native-test"));

        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(r.finalAnswer()).isEqualTo("native 最终答复");
        assertThat(r.iterations()).isEqualTo(1);
        assertThat(r.traces()).hasSize(1);
    }

    @Test
    void defaultProtocolNullGoesReactPath() {
        // 单测 new AgentLoop(...) 时 protocol 字段为 null → 走 ReAct（非 native）
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"thought\":\"t\",\"final_answer\":\"done\"}");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        AgentLoopResult r = loop.run(new AgentLoopRequest("s", "u", List.of(), 5, "t"));
        assertThat(r.finalAnswer()).isEqualTo("done");
    }

    @Test
    void nativeToolCallbackIsGuardedAtInvocationTime() {
        AgentLoop loop = new AgentLoop(mock(ChatClient.class), mock(LangfuseClient.class));
        ToolGuard guard = mock(ToolGuard.class);
        when(guard.check("get_mental_indicators", "{}"))
                .thenReturn(ToolGuard.GuardDecision.deny("forbidden"));
        ReflectionTestUtils.setField(loop, "toolGuard", guard);

        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name("get_mental_indicators")
                .description("sensitive")
                .inputSchema("{\"type\":\"object\"}")
                .build());

        ToolCallback guarded = loop.guardNativeTools(List.of(delegate)).get(0);

        assertThat(guarded.call("{}")).isEqualTo("TOOL_DENIED: forbidden");
        verify(guard).check("get_mental_indicators", "{}");
        verify(delegate, never()).call("{}");
    }

    @Test
    void nativeToolCallbackDelegatesWhenGuardAllows() {
        AgentLoop loop = new AgentLoop(mock(ChatClient.class), mock(LangfuseClient.class));
        ToolGuard guard = mock(ToolGuard.class);
        when(guard.check("get_student_profile", "{\"studentId\":1}"))
                .thenReturn(ToolGuard.GuardDecision.allow());
        ReflectionTestUtils.setField(loop, "toolGuard", guard);

        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name("get_student_profile")
                .description("profile")
                .inputSchema("{\"type\":\"object\"}")
                .build());
        when(delegate.call("{\"studentId\":1}")).thenReturn("profile-data");

        ToolCallback guarded = loop.guardNativeTools(List.of(delegate)).get(0);

        assertThat(guarded.call("{\"studentId\":1}")).isEqualTo("profile-data");
        verify(delegate).call("{\"studentId\":1}");
    }

    @Test
    void invalidNativeFinalAnswerDoesNotComplete() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).toolCallbacks(anyList()).call().content())
                .thenReturn("not-json");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        ReflectionTestUtils.setField(loop, "protocol", "native");

        AgentLoopResult r = loop.run(
                new AgentLoopRequest("sys", "u", List.of(), 5, "native-invalid"),
                answer -> FinalAnswerValidator.Result.invalid("schema mismatch"));

        assertThat(r.status()).isEqualTo(AgentLoopStatus.VALIDATION_ERROR);
        assertThat(r.finalAnswer()).isEqualTo("not-json");
        assertThat(r.traces()).singleElement()
                .satisfies(trace -> assertThat(trace.parseError()).contains("final-answer-invalid"));
    }

    @Test
    void nativeProtocolFiresStartIterationAndFinishHooks() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).toolCallbacks(anyList()).call().content())
                .thenReturn("ok");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        AgentLoopHook hook = mock(AgentLoopHook.class);
        ReflectionTestUtils.setField(loop, "protocol", "native");
        ReflectionTestUtils.setField(loop, "hooks", List.of(hook));
        AgentLoopRequest request = new AgentLoopRequest("sys", "u", List.of(), 5, "native-hooks");

        AgentLoopResult result = loop.run(request);

        verify(hook).onStart("native-hooks", request.normalized());
        verify(hook).onIteration("native-hooks", result.traces().get(0));
        verify(hook).onFinish("native-hooks", result);
    }
}
