package com.edu.agent.ai;

import com.edu.agent.ai.model.InterventionPlan;

import java.util.List;
import java.util.Map;

/**
 * JAVA-RISK-PLAN-20261013：Java 版干预方案生成（对应 Python {@code POST /api/v1/agent/plan}）。
 * system prompt = {@code classpath:prompts/plan.system.md}（从 Python 原样移植）；三段数据与 Python 相同顺序包裹。
 */
public class JavaPlanGenerationService {

    public static final String ROUTE = "plan";

    private final StructuredLlmClient client;
    private final String systemPrompt;

    public JavaPlanGenerationService(StructuredLlmClient client, String systemPrompt) {
        this.client = client;
        this.systemPrompt = systemPrompt;
    }

    public InterventionPlan generate(Map<String, Object> studentProfile, Map<String, Object> riskAnalysis,
                                     List<?> knowledgeChunks) {
        String user = PromptPayloads.build(List.of(
                new PromptPayloads.Section("student_profile", studentProfile == null ? Map.of() : studentProfile),
                new PromptPayloads.Section("risk_analysis", riskAnalysis == null ? Map.of() : riskAnalysis),
                new PromptPayloads.Section("knowledge_chunks", knowledgeChunks == null ? List.of() : knowledgeChunks)));
        InterventionPlan plan = client.call(ROUTE, systemPrompt, user, InterventionPlan.class,
                InterventionPlan::validationError);
        return plan.normalized();
    }
}
