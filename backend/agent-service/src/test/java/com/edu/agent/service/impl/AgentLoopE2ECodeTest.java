package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.config.LangfuseClient;
import com.edu.agent.core.AgentLoop;
import com.edu.agent.core.AgentLoopRequest;
import com.edu.agent.core.AgentLoopResult;
import com.edu.agent.core.AgentLoopStatus;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.service.impl.AgentTaskServiceImpl.AgentLoopParsed;
import com.edu.agent.skill.SkillLoader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第 2/3 件：AgentLoop "代码级端到端"集成测试（无需 Spring 上下文 / 无需真实 LLM / 无需 infra）。
 *
 * <p>用<b>真实</b> {@link AgentLoop} + 真实 {@code prompts/agent-loop.system.md} + 真实 {@link SkillLoader}
 * （classpath 技能注入），只把"LLM"和"MCP 工具"换成脚本化桩件，驱动一次完整的
 * <pre>think → 调 get_student_profile → observe → final_answer</pre> 循环，证明：
 * <ol>
 *   <li>ReAct 循环能跑完并以 {@link AgentLoopStatus#COMPLETED} 收口；</li>
 *   <li>工具被真实调度（非 mock 掉循环）；</li>
 *   <li>final_answer（双 JSON schema）能被 {@link AgentTaskServiceImpl#parseAgentLoopFinalAnswer}
 *       解析为 risk/plan/level —— 即"能落库"的前置链路。</li>
 * </ol>
 *
 * <p>本测试覆盖的是真实跑通时<b>除"活的 14B 模型生成"以外</b>的全部代码路径；真实模型联调见
 * {@code docs/educare/E2E_RUNBOOK.md}（需 llama.cpp + MCP server + DB 在线）。
 */
class AgentLoopE2ECodeTest {

    /** 模拟 LLM 第 2 轮吐出的 final_answer 内层双 JSON（高风险样例）。 */
    private static final String FINAL_INNER = "{"
            + "\"risk_analysis\":{\"risk_level\":\"high\",\"risk_score\":82,"
            + "\"primary_risk_type\":\"学业滑坡\",\"root_cause_analysis\":\"高数 II 连续挂科\","
            + "\"key_indicators\":[\"GPA:1.9\"],\"recommended_intervention_types\":[\"学业辅导\"],"
            + "\"urgency_reason\":\"成绩持续下滑\"},"
            + "\"intervention_plan\":{\"report_title\":\"学业帮扶方案\",\"summary\":\"一对一辅导\","
            + "\"immediate_actions\":[{\"action\":\"安排补考辅导\",\"owner\":\"学业导师\",\"deadline\":\"2周内\"}],"
            + "\"long_term_plan\":[],\"talk_outline\":\"先共情再帮扶\",\"resources\":[],\"references\":[]}"
            + "}";

    private String loadSystemPrompt() throws Exception {
        return new ClassPathResource("prompts/agent-loop.system.md")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private SkillLoader realSkillLoader() {
        SkillLoader loader = new SkillLoader();
        ReflectionTestUtils.setField(loader, "enabled", true);
        ReflectionTestUtils.setField(loader, "activeCsv",
                "risk-assessment,psychological-screening,intervention-design,compliance-audit");
        return loader;
    }

    private ToolCallback fakeProfileTool() {
        ToolCallback tool = mock(ToolCallback.class, RETURNS_DEEP_STUBS);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("get_student_profile");
        when(def.description()).thenReturn("取学生画像");
        when(def.inputSchema()).thenReturn("{}");
        when(tool.getToolDefinition()).thenReturn(def);
        when(tool.call(anyString())).thenReturn(
                "{\"studentId\":1,\"name\":\"张三\",\"gpa\":1.9,\"failedCourses\":[\"高数 II\"]}");
        return tool;
    }

    @Test
    void realAgentLoopCompletesAndFinalAnswerParsesToRiskAndPlan() throws Exception {
        // --- 脚本化 LLM：第1轮调工具，第2轮给 final_answer ---
        String turn1 = "{\"thought\":\"先取画像\",\"action\":{\"tool\":\"get_student_profile\",\"args\":{\"studentId\":1}}}";
        JSONObject turn2obj = new JSONObject();
        turn2obj.put("thought", "数据已够，给出结论");
        turn2obj.put("final_answer", FINAL_INNER);   // fastjson 自动转义内层 JSON 字符串
        String turn2 = turn2obj.toJSONString();

        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(turn1, turn2);

        LangfuseClient langfuse = mock(LangfuseClient.class);
        AgentLoop loop = new AgentLoop(chatClient, langfuse);

        String systemPrompt = loadSystemPrompt() + "\n\n" + realSkillLoader().composeActiveSkillsPrompt();
        AgentLoopRequest req = new AgentLoopRequest(
                systemPrompt,
                "请对学生 ID = 1 完成风险识别 + 干预方案。",
                List.of(fakeProfileTool()),
                5,
                "e2e-code-test");

        // --- 1) 循环真的跑完 ---
        AgentLoopResult result = loop.run(req);
        assertThat(result.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.finalAnswer()).isNotBlank();

        // --- 2) 工具被真实调度（trace 里有 tool 事件） ---
        boolean toolInvoked = result.traces().stream()
                .anyMatch(t -> "get_student_profile".equals(t.toolName()));
        assertThat(toolInvoked).as("get_student_profile 应被真实调用").isTrue();

        // --- 3) final_answer 能解析为 risk/plan/level（落库前置链路） ---
        AgentLoopParsed parsed = AgentTaskServiceImpl.parseAgentLoopFinalAnswer(result.finalAnswer());
        assertThat(parsed).isNotNull();
        assertThat(parsed.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(parsed.riskJson()).contains("学业滑坡");
        assertThat(parsed.planJson()).contains("学业帮扶方案");
    }

    @Test
    void malformedFinalAnswerParsesToNull() {
        assertThat(AgentTaskServiceImpl.parseAgentLoopFinalAnswer("not json")).isNull();
        assertThat(AgentTaskServiceImpl.parseAgentLoopFinalAnswer("{\"risk_analysis\":{}}")).isNull();
    }
}
