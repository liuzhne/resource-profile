package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * J-2.3：ReAct 协议 plan 字段解析。
 */
class AgentLoopPlanTest {

    private final AgentLoop loop =
            new AgentLoop(mock(ChatClient.class, RETURNS_DEEP_STUBS), mock(LangfuseClient.class));

    @Test
    void parsesPlanArrayAlongsideAction() {
        String raw = "{\"thought\":\"t\",\"plan\":[\"取画像\",\"判风险\"],"
                + "\"action\":{\"tool\":\"get_student_profile\",\"args\":{}}}";
        AgentLoop.ParsedTurn p = loop.parseLlmJson(raw);
        assertThat(p.toolName()).isEqualTo("get_student_profile");
        assertThat(p.plan()).contains("取画像").contains("判风险");
    }

    @Test
    void parsesPlanStringWithFinalAnswer() {
        String raw = "{\"thought\":\"t\",\"plan\":\"先取数再判断\",\"final_answer\":\"done\"}";
        AgentLoop.ParsedTurn p = loop.parseLlmJson(raw);
        assertThat(p.finalAnswer()).isEqualTo("done");
        assertThat(p.plan()).isEqualTo("先取数再判断");
    }

    @Test
    void noPlanFieldYieldsNull() {
        AgentLoop.ParsedTurn p = loop.parseLlmJson("{\"thought\":\"t\",\"final_answer\":\"x\"}");
        assertThat(p.plan()).isNull();
    }

    @Test
    void extractPlanStaticHandlesTypes() {
        assertThat(AgentLoop.extractPlan(JSON.parseObject("{\"plan\":[1,2]}"))).isEqualTo("[1,2]");
        assertThat(AgentLoop.extractPlan(JSON.parseObject("{\"plan\":\"s\"}"))).isEqualTo("s");
        assertThat(AgentLoop.extractPlan(new JSONObject())).isNull();
    }
}
