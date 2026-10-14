package com.edu.agent.ai;

import com.edu.agent.ai.model.InterventionPlan;
import com.edu.agent.ai.model.RiskAssessment;
import com.edu.agent.feign.AiInferenceClient;
import com.edu.agent.service.RiskAnalyzeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * JAVA-RISK-PLAN-20261013：legacy 流水线（{@code educare.agent.loop.enabled=false}）中风险识别与方案生成的
 * 实现切换点。返回值都是 JSON 字符串，与原来写入 {@code agent_task} 的格式一致。
 *
 * <ul>
 *   <li>{@code educare.ai.risk.impl}：{@code legacy}（默认，等于 main 现状 {@link RiskAnalyzeService}）| {@code python} | {@code java}</li>
 *   <li>{@code educare.ai.plan.impl}：{@code python}（默认，等于 main 现状）| {@code java}</li>
 * </ul>
 * java 模式失败时抛 {@link AiStructuredOutputException}，由任务执行入口标记 FAILED（显式失败，不静默降级）。
 */
@Slf4j
@Component
public class RiskPlanDispatcher {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final RiskAnalyzeService legacyRisk;
    private final AiInferenceClient python;
    private final JavaRiskAssessmentService javaRisk;
    private final JavaPlanGenerationService javaPlan;
    private final AiImplMode riskMode;
    private final AiImplMode planMode;
    private final ObjectMapper mapper = new ObjectMapper();

    public RiskPlanDispatcher(RiskAnalyzeService legacyRisk, AiInferenceClient python,
                              JavaRiskAssessmentService javaRisk, JavaPlanGenerationService javaPlan,
                              @Value("${educare.ai.risk.impl:legacy}") String riskImpl,
                              @Value("${educare.ai.plan.impl:python}") String planImpl) {
        this.legacyRisk = legacyRisk;
        this.python = python;
        this.javaRisk = javaRisk;
        this.javaPlan = javaPlan;
        this.riskMode = AiImplMode.parse(riskImpl, AiImplMode.LEGACY);
        this.planMode = AiImplMode.parse(planImpl, AiImplMode.PYTHON);
        if (planMode == AiImplMode.LEGACY) {
            throw new IllegalStateException("educare.ai.plan.impl 只支持 python|java");
        }
        log.info("JAVA-RISK-PLAN：risk.impl={} plan.impl={}", riskMode, planMode);
    }

    public AiImplMode riskMode() { return riskMode; }
    public AiImplMode planMode() { return planMode; }

    /** @param maskedProfileJson 画像聚合器产出的脱敏画像 JSON */
    public String analyzeRisk(String maskedProfileJson) {
        return switch (riskMode) {
            case LEGACY -> legacyRisk.analyzeRisk(maskedProfileJson);
            case PYTHON -> python.riskAnalyze(Map.of("student_profile", parse(maskedProfileJson)));
            case JAVA -> toJson(javaRisk.assess(parse(maskedProfileJson)));
        };
    }

    public String generatePlan(Map<String, Object> request) {
        if (planMode == AiImplMode.JAVA) {
            InterventionPlan plan = javaPlan.generate(asMap(request.get("student_profile")),
                    asMap(request.get("risk_analysis")), asList(request.get("knowledge_chunks")));
            return toJson(plan);
        }
        return python.generatePlan(request);
    }

    private Map<String, Object> parse(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("画像 JSON 无法解析", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o == null) return Map.of();
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return mapper.convertValue(o, MAP);
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** eval 端点用：直接返回结构化对象。 */
    public RiskAssessment assessWithJava(Map<String, Object> profile) {
        return javaRisk.assess(profile);
    }
}
