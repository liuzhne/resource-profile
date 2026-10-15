package com.edu.agent.dto;

import java.util.List;

/** Shared report contract. Stored provider JSON remains unchanged for audit/replay. */
public record InterventionPlanView(
        String report_title, String summary, List<Action> immediate_actions,
        List<Goal> long_term_plan, String talk_outline, List<Resource> resources,
        List<String> references) {
    public record Action(String action, String owner, String deadline, List<String> references) { }
    public record Goal(String goal, List<String> milestones, String horizon, List<String> metrics) { }
    public record Resource(String type, String name, String link_or_contact) { }
}
