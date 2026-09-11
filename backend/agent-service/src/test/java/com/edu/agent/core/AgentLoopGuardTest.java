package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * J-1.2：工具守卫接入 AgentLoop —— DENY 不崩，转 TOOL_DENIED observation，循环继续到 final_answer。
 */
class AgentLoopGuardTest {

    @Test
    void deniedToolBecomesObservationAndLoopContinues() {
        String turn1 = "{\"thought\":\"取数\",\"action\":{\"tool\":\"get_student_profile\",\"args\":{}}}";
        String turn2 = "{\"thought\":\"无权取数，直接给结论\",\"final_answer\":\"done\"}";

        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(turn1, turn2);
        LangfuseClient langfuse = mock(LangfuseClient.class);

        AgentLoop loop = new AgentLoop(chatClient, langfuse);
        // 注入一个"一律拒绝"的守卫
        ReflectionTestUtils.setField(loop, "toolGuard",
                (ToolGuard) (tool, args) -> ToolGuard.GuardDecision.deny("测试拒绝"));

        AgentLoopResult r = loop.run(new AgentLoopRequest(
                "sys", "do it", List.of(), 5, "guard-test"));

        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(r.finalAnswer()).isEqualTo("done");
        AgentTrace denied = r.traces().get(0);
        assertThat(denied.toolResult()).startsWith("TOOL_DENIED:").contains("测试拒绝");
        assertThat(denied.parseError()).isEqualTo("tool-denied");
    }
}
