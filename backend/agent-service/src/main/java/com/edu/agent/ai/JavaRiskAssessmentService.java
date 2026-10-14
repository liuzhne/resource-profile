package com.edu.agent.ai;

import com.edu.agent.ai.model.RiskAssessment;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JAVA-RISK-PLAN-20261013：Java 版风险识别（对应 Python {@code POST /api/v1/agent/risk}）。
 * system prompt = {@code classpath:prompts/risk.system.md}（从 Python 原样移植）；输出映射为 {@link RiskAssessment}。
 * 失败抛 {@link AiStructuredOutputException}，不返回默认"中风险"。
 */
@Slf4j
public class JavaRiskAssessmentService {

    public static final String ROUTE = "risk";

    private final StructuredLlmClient client;
    private final String systemPrompt;

    public JavaRiskAssessmentService(StructuredLlmClient client, String systemPrompt) {
        this.client = client;
        this.systemPrompt = systemPrompt;
    }

    public RiskAssessment assess(Map<String, Object> studentProfile) {
        String user = PromptPayloads.build(List.of(
                new PromptPayloads.Section("student_profile", studentProfile == null ? Map.of() : studentProfile)));
        RiskAssessment result = client.call(ROUTE, systemPrompt, user, RiskAssessment.class,
                RiskAssessment::validationError);
        return result.normalized();
    }
}
