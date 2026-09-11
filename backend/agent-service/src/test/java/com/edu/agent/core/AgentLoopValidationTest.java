package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * J-1.3：final_answer 校验 + 自纠错修复轮。
 */
class AgentLoopValidationTest {

    private AgentLoop loop(ChatClient cc) {
        return new AgentLoop(cc, mock(LangfuseClient.class));
    }

    private String fa(String v) {
        return "{\"thought\":\"t\",\"final_answer\":\"" + v + "\"}";
    }

    /** 校验器：仅当 final_answer == "good" 视为合格。 */
    private final FinalAnswerValidator onlyGood = a ->
            "good".equals(a) ? FinalAnswerValidator.Result.ok()
                    : FinalAnswerValidator.Result.invalid("必须输出 good");

    @Test
    void invalidFirstThenValidTriggersRepairThenCompletes() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(fa("bad"), fa("good"));

        AgentLoopResult r = loop(cc).run(new AgentLoopRequest("s", "u", List.of(), 5, "val"), onlyGood);

        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(r.finalAnswer()).isEqualTo("good");
        assertThat(r.iterations()).isEqualTo(2);
        // 第一轮被判不合格，留下 final-answer-invalid 轨迹
        assertThat(r.traces()).anyMatch(t -> "final-answer-invalid".equals(t.parseError()));
    }

    @Test
    void exhaustedRepairBudgetAcceptsLastAnswer() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        // 始终不合格：repair 预算 2 → 第1、2轮修复，第3轮预算耗尽接受
        when(cc.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(fa("bad"), fa("bad"), fa("bad"));

        AgentLoopResult r = loop(cc).run(new AgentLoopRequest("s", "u", List.of(), 5, "val"), onlyGood);

        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(r.finalAnswer()).isEqualTo("bad");
        assertThat(r.iterations()).isEqualTo(3);
        assertThat(r.traces().stream().filter(t -> "final-answer-invalid".equals(t.parseError())).count())
                .isEqualTo(2);
    }

    @Test
    void noValidatorAcceptsImmediately() {
        ChatClient cc = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(cc.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(fa("whatever"));
        AgentLoopResult r = loop(cc).run(new AgentLoopRequest("s", "u", List.of(), 5, "val"));  // run(req) 无校验器
        assertThat(r.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(r.iterations()).isEqualTo(1);
    }
}
