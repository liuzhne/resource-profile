package com.edu.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.core.AgentExecutionException;
import com.edu.agent.core.AgentFailure;
import com.edu.agent.core.AgentLoopOutputSchema;
import com.edu.common.util.PromptSanitizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All generation stays in Spring AI; no automatic Python fallback or duplicate model request. */
@Service
public class AgentGenerationService {
    private final ChatClient chat;
    private final String planPrompt;
    private final String riskPrompt;
    private final String auditPrompt;
    private final JSONObject auditSchema;
    public AgentGenerationService(ChatClient chat) throws IOException {
        this.chat = chat;
        riskPrompt = resource("risk-analyze.system.md");
        planPrompt = resource("plan-generate.system.md");
        auditPrompt = resource("compliance-audit.system.md");
        auditSchema = JSON.parseObject(resource("compliance-audit.schema.json"));
    }
    private static String resource(String name) throws IOException {
        return new ClassPathResource("prompts/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
    public String analyzeRisk(Map<String, Object> request) {
        JSONObject risk = call(riskPrompt, data("student_profile", request.get("student_profile")), "educare_risk", AgentLoopOutputSchema.riskSchema());
        if (!AgentLoopOutputSchema.validRisk(risk)) throw invalid("FINAL_VALIDATION");
        return risk.toJSONString();
    }
    public String chat(String system, String message) {
        return chat.prompt().system(system).user(message)
                .options(OpenAiChatOptions.builder().responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build()).build())
                .call().content();
    }
    public String generatePlan(Map<String, Object> request) {
        String user = data("student_profile", request.get("student_profile"))
                + data("risk_analysis", request.get("risk_analysis"))
                + data("knowledge_chunks", request.getOrDefault("knowledge_chunks", List.of()));
        JSONObject result = call(planPrompt, user, "educare_plan", AgentLoopOutputSchema.interventionPlanSchema());
        if (!AgentLoopOutputSchema.validInterventionPlan(result)) throw invalid("FINAL_VALIDATION");
        return result.toJSONString();
    }
    public String complianceAudit(Map<String, Object> request) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (request.get("student_profile") instanceof Map<?, ?> profile) {
            for (String key : List.of("studentId", "grade", "major"))
                if (profile.containsKey(key)) meta.put(key, profile.get(key));
        }
        JSONObject result = call(auditPrompt, data("student_profile_meta", meta)
                + data("intervention_plan", request.get("intervention_plan")), "educare_audit", auditSchema);
        if (!validAudit(result)) throw invalid("COMPLIANCE_CHECKING");
        return result.toJSONString();
    }
    private JSONObject call(String system, String user, String name, JSONObject schema) {
        ResponseFormat format = ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder().name(name).strict(true).schema(schema.toJSONString()).build()).build();
        String text = chat.prompt().system(system).user(user)
                .options(OpenAiChatOptions.builder().responseFormat(format).build()).call().content();
        try { return AgentLoopOutputSchema.parseStrictObject(text == null ? "" : text); }
        catch (IOException | RuntimeException invalid) { throw invalid(name.equals("educare_audit") ? "COMPLIANCE_CHECKING" : "FINAL_VALIDATION"); }
    }
    private static String data(String tag, Object value) {
        String json = JSON.toJSONString(PromptSanitizer.sanitizeJsonPayload(value));
        // Prevent untrusted data from closing its delimiting XML tag.
        json = json.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return PromptSanitizer.wrap(tag, json) + "\n";
    }
    private static AgentExecutionException invalid(String stage) {
        return new AgentExecutionException(new AgentFailure("OUTPUT_INVALID", stage, null));
    }
    static boolean validAudit(JSONObject value) {
        if (!value.keySet().equals(Set.of("audit_passed", "audit_items", "redacted_suggestions", "manual_review_required"))
                || !(value.get("audit_passed") instanceof Boolean passed)
                || !(value.get("manual_review_required") instanceof Boolean manual)
                || !(value.get("audit_items") instanceof List<?> items) || items.size() != 4
                || !(value.get("redacted_suggestions") instanceof List<?> suggestions)
                || suggestions.stream().anyMatch(item -> !(item instanceof String))) return false;
        Set<String> dimensions = new HashSet<>();
        boolean allPassed = true;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)
                    || !row.keySet().equals(Set.of("dimension", "passed", "issue"))
                    || !(row.get("dimension") instanceof String dimension)
                    || !Set.of("privacy", "necessity", "ethics", "third_party").contains(dimension)
                    || !dimensions.add(dimension) || !(row.get("passed") instanceof Boolean ok)
                    || !(row.get("issue") instanceof String)) return false;
            allPassed &= ok;
        }
        return !passed || (allPassed && !manual);
    }
}
