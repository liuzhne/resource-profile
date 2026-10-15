package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;

/** Strict ReAct envelope; actions still execute explicitly through the existing MCP callbacks. */
public final class AgentLoopOutputSchema {
    private static final JSONObject FINAL_SCHEMA = loadFinalSchema();
    private static final ObjectMapper STRICT_JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private AgentLoopOutputSchema() { }

    private static JSONObject loadFinalSchema() {
        try (var stream = new ClassPathResource("prompts/agent-final-answer.schema.json").getInputStream()) {
            return JSON.parseObject(stream.readAllBytes());
        } catch (IOException e) { throw new IllegalStateException("Missing Agent final-answer schema", e); }
    }

    public static ResponseFormat responseFormat(List<ToolCallback> tools) {
        JSONArray variants = new JSONArray();
        for (ToolCallback tool : tools) {
            var definition = tool.getToolDefinition();
            JSONObject args = JSON.parseObject(definition.inputSchema());
            closeObjects(args);
            variants.add(object(JSONObject.of("tool", JSONObject.of("type", "string", "enum", List.of(definition.name())),
                    "args", args)));
        }
        JSONObject action = JSONObject.of("type", "null");
        if (!variants.isEmpty()) {
            // Groq requires concrete direct action variants; an anyOf-only wrapper is rejected.
            JSONArray actionVariants = new JSONArray();
            actionVariants.addAll(variants);
            actionVariants.add(JSONObject.of("type", "array", "items", JSONObject.of("anyOf", variants)));
            actionVariants.add(JSONObject.of("type", "null"));
            action = JSONObject.of("anyOf", actionVariants);
        }
        JSONObject schema = object(JSONObject.of(
                "thought", JSONObject.of("type", "string"),
                "plan", nullable(JSONObject.of("type", "array", "items", JSONObject.of("type", "string"))),
                "action", action,
                "final_answer", nullable(FINAL_SCHEMA)));
        return ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder().name("educare_react_turn").strict(true)
                        .schema(schema.toJSONString()).build()).build();
    }

    private static JSONObject nullable(JSONObject schema) {
        return JSONObject.of("anyOf", List.of(schema, JSONObject.of("type", "null")));
    }
    private static JSONObject object(JSONObject properties) {
        return JSONObject.of("type", "object", "properties", properties,
                "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }
    private static void closeObjects(Object value) {
        if (value instanceof JSONObject schema) {
            schema.remove("$schema");
            if (schema.get("properties") instanceof JSONObject properties) {
                JSONArray originallyRequired = schema.getJSONArray("required");
                for (String key : List.copyOf(properties.keySet())) {
                    if (originallyRequired == null || !originallyRequired.contains(key)) {
                        properties.put(key, nullable(properties.getJSONObject(key)));
                    }
                }
                schema.put("additionalProperties", false);
                schema.put("required", List.copyOf(properties.keySet()));
            }
            schema.values().forEach(AgentLoopOutputSchema::closeObjects);
        } else if (value instanceof JSONArray values) values.forEach(AgentLoopOutputSchema::closeObjects);
    }

    /** Local schema enforcement also covers compatible providers without constrained decoding. */
    public static JSONObject riskSchema() {
        return JSON.parseObject(FINAL_SCHEMA.getJSONObject("properties").getJSONObject("risk_analysis").toJSONString());
    }
    public static boolean validRisk(JSONObject risk) {
        if (!matches(riskSchema(), risk)) return false;
        BigInteger score = new BigInteger(risk.get("risk_score").toString());
        return score.signum() >= 0 && score.compareTo(BigInteger.valueOf(100)) <= 0;
    }
    public static JSONObject interventionPlanSchema() {
        return JSON.parseObject(FINAL_SCHEMA.getJSONObject("properties").getJSONObject("intervention_plan").toJSONString());
    }
    public static boolean validInterventionPlan(JSONObject plan) {
        return matches(interventionPlanSchema(), plan);
    }

    public static boolean validFinalAnswer(JSONObject answer) {
        if (!matches(FINAL_SCHEMA, answer)) return false;
        BigInteger score = new BigInteger(answer.getJSONObject("risk_analysis").get("risk_score").toString());
        return score.signum() >= 0 && score.compareTo(BigInteger.valueOf(100)) <= 0;
    }
    public static JSONObject parseFinalAnswer(String text) {
        try {
            JSONObject answer = parseStrictObject(text);
            return validFinalAnswer(answer) ? answer : null;
        } catch (Exception ignored) { return null; }
    }
    public static JSONObject parseStrictObject(String text) throws IOException {
        var tree = STRICT_JSON.readTree(text);
        if (tree == null || !tree.isObject()) throw new IOException("Expected JSON object");
        return JSON.parseObject(tree.toString());
    }
    /** Constrained output emits optional fields as null; restore MCP omission/default semantics. */
    public static String toolArguments(ToolCallback tool, String arguments) {
        JSONObject args = JSON.parseObject(arguments);
        JSONObject schema = JSON.parseObject(tool.getToolDefinition().inputSchema());
        removeOptionalNulls(schema, args);
        return args.toJSONString();
    }
    private static void removeOptionalNulls(JSONObject schema, Object value) {
        if (value instanceof JSONObject object && schema.get("properties") instanceof JSONObject properties) {
            JSONArray required = schema.getJSONArray("required");
            for (String key : List.copyOf(object.keySet())) {
                if (object.get(key) == null && properties.containsKey(key)
                        && (required == null || !required.contains(key))) object.remove(key);
                else if (properties.get(key) instanceof JSONObject child) removeOptionalNulls(child, object.get(key));
            }
        } else if (value instanceof JSONArray array && schema.get("items") instanceof JSONObject child) {
            array.forEach(item -> removeOptionalNulls(child, item));
        }
    }
    private static boolean matches(JSONObject schema, Object value) {
        if (value == null) return false;
        switch (schema.getString("type")) {
            case "object":
                if (!(value instanceof JSONObject object)) return false;
                JSONObject properties = schema.getJSONObject("properties");
                if (!object.keySet().equals(properties.keySet())) return false;
                for (String key : properties.keySet()) if (!matches(properties.getJSONObject(key), object.get(key))) return false;
                return true;
            case "array":
                if (!(value instanceof JSONArray array)) return false;
                return array.stream().allMatch(item -> matches(schema.getJSONObject("items"), item));
            case "string":
                if (!(value instanceof String text) || text.isBlank()) return false;
                JSONArray allowed = schema.getJSONArray("enum");
                return allowed == null || allowed.contains(text);
            case "integer":
                return value instanceof Integer || value instanceof Long || value instanceof BigInteger;
            default: return false;
        }
    }
}
