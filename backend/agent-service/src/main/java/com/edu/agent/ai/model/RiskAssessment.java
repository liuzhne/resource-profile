package com.edu.agent.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * JAVA-RISK-PLAN-20261013：风险识别结构化输出，字段与 Python {@code /api/v1/agent/risk}
 * （prompts/risk.system.md）的 JSON 契约一一对应，序列化后可直接被 {@code eval/run_eval.py} 打分。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskAssessment(
        @JsonProperty(value = "risk_level", required = true)
        @JsonPropertyDescription("high|medium|low|none")
        String riskLevel,
        @JsonProperty(value = "risk_score", required = true)
        @JsonPropertyDescription("0-100 整数")
        Integer riskScore,
        @JsonProperty(value = "primary_risk_type", required = true)
        @JsonPropertyDescription("学业滑坡|心理健康预警|出勤异常|经济困难影响|社交孤立|综合风险")
        String primaryRiskType,
        @JsonProperty(value = "root_cause_analysis", required = true)
        String rootCauseAnalysis,
        @JsonProperty("key_indicators")
        List<String> keyIndicators,
        @JsonProperty("recommended_intervention_types")
        List<String> recommendedInterventionTypes,
        @JsonProperty("urgency_reason")
        String urgencyReason) {

    public static final List<String> LEVELS = List.of("high", "medium", "low", "none");

    /** 结构校验：等级合法、分数在 0..100、必填文本非空。返回问题描述；null 表示通过。 */
    public String validationError() {
        if (riskLevel == null || !LEVELS.contains(riskLevel.toLowerCase())) {
            return "risk_level 不合法: " + riskLevel;
        }
        if (riskScore == null || riskScore < 0 || riskScore > 100) {
            return "risk_score 不在 0..100: " + riskScore;
        }
        if (primaryRiskType == null || primaryRiskType.isBlank()) {
            return "primary_risk_type 为空";
        }
        if (rootCauseAnalysis == null || rootCauseAnalysis.isBlank()) {
            return "root_cause_analysis 为空";
        }
        return null;
    }

    /** 统一小写等级，列表 null → 空列表。 */
    public RiskAssessment normalized() {
        return new RiskAssessment(riskLevel.toLowerCase(), riskScore, primaryRiskType, rootCauseAnalysis,
                keyIndicators == null ? List.of() : keyIndicators,
                recommendedInterventionTypes == null ? List.of() : recommendedInterventionTypes,
                urgencyReason);
    }
}
