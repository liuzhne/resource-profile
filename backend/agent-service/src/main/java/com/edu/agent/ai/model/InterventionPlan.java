package com.edu.agent.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JAVA-RISK-PLAN-20261013：干预方案结构化输出，字段与 Python {@code /api/v1/agent/plan}
 * （prompts/plan.system.md）的 JSON 契约一一对应，前端/PDF 导出读取的字段名不变。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InterventionPlan(
        @JsonProperty(value = "report_title", required = true) String reportTitle,
        @JsonProperty(value = "summary", required = true) String summary,
        @JsonProperty("immediate_actions") List<ImmediateAction> immediateActions,
        @JsonProperty("long_term_plan") List<LongTermPhase> longTermPlan,
        @JsonProperty("talk_outline") String talkOutline,
        @JsonProperty("resources") List<SupportResource> resources,
        @JsonProperty("references") List<String> references) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImmediateAction(
            @JsonProperty("action") String action,
            @JsonProperty("owner") String owner,
            @JsonProperty("deadline_days") Integer deadlineDays,
            @JsonProperty("references") List<String> references) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LongTermPhase(
            @JsonProperty("phase") String phase,
            @JsonProperty("goals") List<String> goals,
            @JsonProperty("metrics") List<String> metrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SupportResource(
            @JsonProperty("type") String type,
            @JsonProperty("name") String name,
            @JsonProperty("link_or_contact") String linkOrContact) {
    }

    /** 结构校验：标题/摘要非空，immediate_actions 每项有 action，deadline_days 若给出须在 1..7。 */
    public String validationError() {
        if (reportTitle == null || reportTitle.isBlank()) {
            return "report_title 为空";
        }
        if (summary == null || summary.isBlank()) {
            return "summary 为空";
        }
        if (immediateActions != null) {
            for (ImmediateAction a : immediateActions) {
                if (a == null || a.action() == null || a.action().isBlank()) {
                    return "immediate_actions 存在空 action";
                }
                if (a.deadlineDays() != null && (a.deadlineDays() < 1 || a.deadlineDays() > 7)) {
                    return "deadline_days 不在 1..7: " + a.deadlineDays();
                }
            }
        }
        return null;
    }

    public InterventionPlan normalized() {
        return new InterventionPlan(reportTitle, summary,
                immediateActions == null ? List.of() : immediateActions,
                longTermPlan == null ? List.of() : longTermPlan,
                talkOutline,
                resources == null ? List.of() : resources,
                references == null ? List.of() : references);
    }
}
