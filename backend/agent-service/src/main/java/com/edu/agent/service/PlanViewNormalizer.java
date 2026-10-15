package com.edu.agent.service;

import com.edu.agent.dto.InterventionPlanView;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Projects both the ReAct schema and the legacy Python plan into one UI/PDF DTO. */
public final class PlanViewNormalizer {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private PlanViewNormalizer() { }

    public static InterventionPlanView normalize(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = JSON.readTree(json);
            if (root == null || !root.isObject()) return null;
            List<InterventionPlanView.Action> actions = new ArrayList<>();
            LinkedHashSet<String> references = new LinkedHashSet<>(strings(root.path("references")));
            for (JsonNode a : array(root.path("immediate_actions"))) {
                if (!a.isObject()) continue;
                String deadline = text(a.path("deadline"));
                if (deadline.isBlank() && a.path("deadline_days").isNumber()) {
                    deadline = a.path("deadline_days").asText() + " 天";
                }
                List<String> refs = strings(a.path("references"));
                references.addAll(refs);
                actions.add(new InterventionPlanView.Action(text(a.path("action")), text(a.path("owner")), deadline, refs));
            }
            List<InterventionPlanView.Goal> goals = new ArrayList<>();
            for (JsonNode p : array(root.path("long_term_plan"))) {
                if (!p.isObject()) continue;
                String goal = text(p.path("goal"));
                if (goal.isBlank()) goal = String.join("；", strings(p.path("goals")));
                String horizon = text(p.path("horizon"));
                if (horizon.isBlank()) horizon = text(p.path("phase"));
                // Legacy metrics describe measurement, not necessarily milestones; retain the distinction.
                goals.add(new InterventionPlanView.Goal(goal, strings(p.path("milestones")), horizon, strings(p.path("metrics"))));
            }
            List<InterventionPlanView.Resource> resources = new ArrayList<>();
            for (JsonNode r : array(root.path("resources"))) {
                if (r.isTextual() && !r.asText().isBlank()) {
                    resources.add(new InterventionPlanView.Resource("", r.asText(), ""));
                } else if (r.isObject()) {
                    resources.add(new InterventionPlanView.Resource(text(r.path("type")), text(r.path("name")), text(r.path("link_or_contact"))));
                }
            }
            return new InterventionPlanView(text(root.path("report_title")), text(root.path("summary")), List.copyOf(actions),
                    List.copyOf(goals), text(root.path("talk_outline")), List.copyOf(resources), List.copyOf(references));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean hasContent(InterventionPlanView plan) {
        return plan != null && (plan.immediate_actions().stream().anyMatch(a -> !a.action().isBlank())
                || plan.long_term_plan().stream().anyMatch(p -> !p.goal().isBlank() || !p.milestones().isEmpty()));
    }

    public static String toJson(InterventionPlanView plan) {
        if (plan == null) return null;
        try {
            return JSON.writeValueAsString(plan);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化方案展示契约", e);
        }
    }

    private static String text(JsonNode node) { return node.isTextual() ? node.asText() : ""; }
    private static Iterable<JsonNode> array(JsonNode node) { return node.isArray() ? node : List.of(); }
    private static List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : array(node)) if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText());
        return List.copyOf(result);
    }
}
