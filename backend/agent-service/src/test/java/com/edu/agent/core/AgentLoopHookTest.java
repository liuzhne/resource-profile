package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * J-3.1：AgentLoopHook 生命周期触发（onStart / onIteration / onFinish）。
 */
class AgentLoopHookTest {

    static class RecordingHook implements AgentLoopHook {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger iterations = new AtomicInteger();
        final AtomicInteger finishes = new AtomicInteger();

        public void onStart(String t, AgentLoopRequest r) {
            starts.incrementAndGet();
        }

        public void onIteration(String t, AgentTrace tr) {
            iterations.incrementAndGet();
        }

        public void onFinish(String t, AgentLoopResult r) {
            finishes.incrementAndGet();
        }
    }

    @Test
    void hooksFiredAroundLoop() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"thought\":\"t\",\"final_answer\":\"done\"}");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        RecordingHook hook = new RecordingHook();
        ReflectionTestUtils.setField(loop, "hooks", List.of(hook));

        loop.run(new AgentLoopRequest("s", "u", List.of(), 5, "hooktest"));

        assertThat(hook.starts.get()).isEqualTo(1);
        assertThat(hook.iterations.get()).isEqualTo(1);  // 一轮 final_answer 一条轨迹
        assertThat(hook.finishes.get()).isEqualTo(1);
    }

    @Test
    void throwingHookDoesNotBreakLoop() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"thought\":\"t\",\"final_answer\":\"done\"}");
        AgentLoop loop = new AgentLoop(cc, mock(LangfuseClient.class));
        AgentLoopHook boom = new AgentLoopHook() {
            public void onIteration(String t, AgentTrace tr) {
                throw new RuntimeException("boom");
            }
        };
        ReflectionTestUtils.setField(loop, "hooks", List.of(boom));

        AgentLoopResult r = loop.run(new AgentLoopRequest("s", "u", List.of(), 5, "h"));
        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);  // 钩子异常被吞，主循环正常
    }
}
