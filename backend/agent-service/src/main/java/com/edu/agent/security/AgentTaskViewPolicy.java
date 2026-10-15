package com.edu.agent.security;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.service.PlanViewNormalizer;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Set;

/** String JSON is not traversed by common's field advice, so enforce its boundary before serialization. */
public final class AgentTaskViewPolicy {
    private static final Set<String> RISK_TYPES = Set.of("学业滑坡", "心理健康预警", "出勤异常", "经济困难影响", "社交孤立", "综合风险");
    private static final Set<String> INTERVENTIONS = Set.of("学业辅导", "心理疏导", "经济援助", "家校沟通");
    private AgentTaskViewPolicy() { }

    public static AgentTask project(AgentTask source, boolean canSeeRawMental) {
        if (source == null) return null;
        AgentTask view = new AgentTask();
        BeanUtils.copyProperties(source, view);
        var plan = PlanViewNormalizer.normalize(source.getInterventionPlan());
        view.setInterventionPlan(PlanViewNormalizer.toJson(plan));
        view.setPlanViewStatus(source.getInterventionPlan() == null || source.getInterventionPlan().isBlank()
                ? "NOT_GENERATED" : plan == null ? "INVALID" : PlanViewNormalizer.hasContent(plan) ? "AVAILABLE" : "EMPTY");
        if (!canSeeRawMental) {
            view.setRiskType(safeRiskType(source.getRiskType()));
            view.setRiskAnalysisResult(restrictedRisk(source.getRiskAnalysisResult()));
            view.setRetrievedKnowledge(restrictedKnowledge(source.getRetrievedKnowledge()));
        }
        return view;
    }

    private static String restrictedRisk(String json) {
        if (json == null || json.isBlank()) return null;
        JSONObject original = object(json);
        if (original == null) return null; // fail closed for malformed/legacy unstructured raw content
        JSONObject safe = new JSONObject();
        String level = original.get("risk_level") instanceof String value ? value : null;
        if (level != null && Set.of("high", "medium", "low", "none").contains(level.toLowerCase())) safe.put("risk_level", level.toLowerCase());
        Object score = original.get("risk_score");
        if (score instanceof Number number && number.doubleValue() == number.intValue() && number.intValue() >= 0 && number.intValue() <= 100) {
            safe.put("risk_score", number.intValue());
        }
        safe.put("primary_risk_type", safeRiskType(original.get("primary_risk_type") instanceof String value ? value : null));
        safe.put("key_indicators", List.of());
        JSONArray types = original.get("recommended_intervention_types") instanceof JSONArray array ? array : null;
        safe.put("recommended_intervention_types", types == null ? List.of() : types.stream()
                .filter(v -> v instanceof String && INTERVENTIONS.contains(v)).toList());
        safe.put("root_cause_analysis", "详细分析限专业角色");
        safe.put("urgency_reason", "详细分析限专业角色");
        safe.put("risk_details_restricted", true);
        return safe.toJSONString();
    }

    private static String safeRiskType(String type) { return type == null ? null : RISK_TYPES.contains(type) ? type : "综合风险"; }

    private static String restrictedKnowledge(String json) {
        if (json == null || json.isBlank()) return null;
        JSONObject knowledge = object(json);
        if (knowledge == null) return null;
        // Search arguments may repeat individual assessment values. Keep provenance, not the raw query.
        knowledge.remove("query");
        JSONArray searches = knowledge.get("searches") instanceof JSONArray array ? array : null;
        if (searches != null) for (Object value : searches) if (value instanceof JSONObject search) {
            search.remove("query");
            search.remove("arguments");
        }
        return knowledge.toJSONString();
    }

    private static JSONObject object(String json) {
        try { return JSON.parseObject(json); } catch (Exception ignored) { return null; }
    }
}
