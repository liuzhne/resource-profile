package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoopOutputSchemaTest {
    private static final String FINAL = """
        {"risk_analysis":{"risk_level":"none","risk_score":0,"primary_risk_type":"无风险",
         "root_cause_analysis":"合成数据正常","key_indicators":["出勤98%"],"recommended_intervention_types":[],"urgency_reason":"无需紧急干预"},
         "intervention_plan":{"report_title":"正常跟进","summary":"合成验证","immediate_actions":[],"long_term_plan":[],
         "talk_outline":"保持沟通","resources":[],"references":[]}}
        """;
    @Test void completeFinalAnswerPassesButMissingWrongAndUnknownFieldsFail() {
        assertNotNull(AgentLoopOutputSchema.parseFinalAnswer(FINAL));
        JSONObject answer = JSON.parseObject(FINAL);
        answer.getJSONObject("risk_analysis").remove("risk_level");
        assertFalse(AgentLoopOutputSchema.validFinalAnswer(answer));
        answer = JSON.parseObject(FINAL); answer.getJSONObject("risk_analysis").put("risk_score", "0");
        assertFalse(AgentLoopOutputSchema.validFinalAnswer(answer));
        answer = JSON.parseObject(FINAL); answer.getJSONObject("risk_analysis").put("risk_score", 101);
        assertFalse(AgentLoopOutputSchema.validFinalAnswer(answer));
        answer = JSON.parseObject(FINAL); answer.getJSONObject("intervention_plan").put("extra", true);
        assertFalse(AgentLoopOutputSchema.validFinalAnswer(answer));
        assertNull(AgentLoopOutputSchema.parseFinalAnswer(FINAL + " {}"));
        assertNull(AgentLoopOutputSchema.parseFinalAnswer(FINAL.replace("\"risk_score\":0", "\"risk_score\":0,\"risk_score\":1")));
    }
    @Test void schemaRetainsTypedToolActionsAndStrictFinalObjects() {
        ToolCallback tool = new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("synthetic_tool").description("synthetic")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"studentId\":{\"type\":\"integer\"}},\"required\":[\"studentId\"]}").build();
            }
            public String call(String input) { return "{}"; }
        };
        var format = AgentLoopOutputSchema.responseFormat(List.of(tool));
        assertTrue(format.getJsonSchema().getStrict());
        JSONObject schema = JSON.parseObject(JSON.toJSONString(format.getJsonSchema().getSchema()));
        assertFalse(schema.getBooleanValue("additionalProperties"));
        assertEquals(4, schema.getJSONArray("required").size());
        assertTrue(schema.toJSONString().contains("synthetic_tool"));
        assertTrue(schema.toJSONString().contains("risk_analysis"));
    }
    @Test void singleToolUnionHasConcreteGroqVariantsAndPreservesParallelCalls() {
        ToolCallback tool = fixtureTool("get_profile", """
                {"type":"object","properties":{"studentId":{"type":"integer"}},"required":["studentId"]}
                """);
        JSONObject schema = schema(List.of(tool));
        JSONArray action = schema.getJSONObject("properties").getJSONObject("action").getJSONArray("anyOf");
        // Groq rejects an untyped anyOf-only object as a direct action variant.
        assertEquals("object", action.getJSONObject(0).getString("type"));
        assertEquals(3, action.size());
        assertToolVariant(action.getJSONObject(0), "get_profile", List.of("studentId"));
        assertEquals("integer", action.getJSONObject(0).getJSONObject("properties")
                .getJSONObject("args").getJSONObject("properties").getJSONObject("studentId").getString("type"));
        assertEquals("array", action.getJSONObject(1).getString("type"));
        assertEquals(action.getJSONObject(0), action.getJSONObject(1).getJSONObject("items")
                .getJSONArray("anyOf").getJSONObject(0));
        assertEquals("null", action.getJSONObject(2).getString("type"));
        assertEquals(4, schema.getJSONArray("required").size());
    }
    @Test void multipleToolUnionPreservesEveryTypedArgumentAndNullableDefault() {
        List<ToolCallback> tools = List.of(
                fixtureTool("get_profile", """
                        {"type":"object","properties":{"studentId":{"type":"integer"}},"required":["studentId"]}
                        """),
                fixtureTool("search_policy", """
                        {"type":"object","properties":{"query":{"type":"string"},
                        "top_k":{"type":"integer","default":5}},"required":["query"]}
                        """));
        JSONArray variants = schema(tools).getJSONObject("properties").getJSONObject("action").getJSONArray("anyOf");
        assertEquals("object", variants.getJSONObject(0).getString("type"));
        assertEquals(4, variants.size());
        assertToolVariant(variants.getJSONObject(0), "get_profile", List.of("studentId"));
        assertToolVariant(variants.getJSONObject(1), "search_policy", List.of("query", "top_k"));
        JSONObject args = variants.getJSONObject(1).getJSONObject("properties").getJSONObject("args");
        assertEquals("string", args.getJSONObject("properties").getJSONObject("query").getString("type"));
        JSONArray topK = args.getJSONObject("properties").getJSONObject("top_k").getJSONArray("anyOf");
        assertEquals("integer", topK.getJSONObject(0).getString("type"));
        assertEquals(5, topK.getJSONObject(0).getIntValue("default"));
        assertEquals("null", topK.getJSONObject(1).getString("type"));
        JSONObject parallel = variants.getJSONObject(2);
        assertEquals("array", parallel.getString("type"));
        JSONArray parallelTools = parallel.getJSONObject("items").getJSONArray("anyOf");
        assertEquals(2, parallelTools.size());
        assertEquals(variants.getJSONObject(0), parallelTools.getJSONObject(0));
        assertEquals(variants.getJSONObject(1), parallelTools.getJSONObject(1));
        assertEquals("null", variants.getJSONObject(3).getString("type"));
    }
    @Test void noToolsRestrictsActionToNullAndKeepsStrictFinalAnswer() {
        var format = AgentLoopOutputSchema.responseFormat(List.of());
        assertTrue(format.getJsonSchema().getStrict());
        JSONObject schema = schema(List.of());
        JSONObject action = schema.getJSONObject("properties").getJSONObject("action");
        assertEquals(JSONObject.of("type", "null"), action);
        assertEquals(List.of("thought", "plan", "action", "final_answer").stream().sorted().toList(),
                schema.getJSONArray("required").toJavaList(String.class).stream().sorted().toList());
        JSONObject answer = schema.getJSONObject("properties").getJSONObject("final_answer")
                .getJSONArray("anyOf").getJSONObject(0);
        assertFalse(answer.getBooleanValue("additionalProperties"));
        assertTrue(answer.getJSONArray("required").containsAll(List.of("risk_analysis", "intervention_plan")));
    }
    private static JSONObject schema(List<ToolCallback> tools) {
        return JSON.parseObject(JSON.toJSONString(AgentLoopOutputSchema.responseFormat(tools).getJsonSchema().getSchema()));
    }
    private static ToolCallback fixtureTool(String name, String inputSchema) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("Protocol fixture").inputSchema(inputSchema).build();
            }
            public String call(String input) { throw new AssertionError("Schema tests must never execute a tool"); }
        };
    }
    private static void assertToolVariant(JSONObject variant, String name, List<String> argNames) {
        assertFalse(variant.containsKey("anyOf"));
        assertEquals("object", variant.getString("type"));
        assertFalse(variant.getBooleanValue("additionalProperties"));
        assertEquals(List.of("args", "tool"), variant.getJSONArray("required").toJavaList(String.class)
                .stream().sorted().toList());
        assertEquals(List.of(name), variant.getJSONObject("properties").getJSONObject("tool")
                .getJSONArray("enum").toJavaList(String.class));
        JSONObject args = variant.getJSONObject("properties").getJSONObject("args");
        assertEquals("object", args.getString("type"));
        assertFalse(args.getBooleanValue("additionalProperties"));
        assertEquals(argNames.stream().sorted().toList(), args.getJSONArray("required").toJavaList(String.class)
                .stream().sorted().toList());
    }
    @Test void optionalMcpParametersAreNullableAndNullRestoresDefaults() {
        ToolCallback tool = new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("search_psychology_knowledge").description("synthetic")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"top_k\":{\"type\":\"integer\",\"default\":5}},\"required\":[\"query\"]}").build();
            }
            public String call(String input) { return input; }
        };
        JSONObject schema = JSON.parseObject(JSON.toJSONString(AgentLoopOutputSchema.responseFormat(List.of(tool)).getJsonSchema().getSchema()));
        JSONObject args = schema.getJSONObject("properties").getJSONObject("action").getJSONArray("anyOf")
                .getJSONObject(0).getJSONObject("properties").getJSONObject("args");
        assertEquals(2, args.getJSONArray("required").size());
        assertEquals("null", args.getJSONObject("properties").getJSONObject("top_k").getJSONArray("anyOf").getJSONObject(1).getString("type"));
        assertEquals("{\"query\":\"synthetic\"}", AgentLoopOutputSchema.toolArguments(tool, "{\"query\":\"synthetic\",\"top_k\":null}"));
        assertTrue(AgentLoopOutputSchema.toolArguments(tool, "{\"query\":\"synthetic\",\"top_k\":3}").contains("\"top_k\":3"));
    }
    @Test void rawEmbeddedFinalRejectsDuplicateAndNonJsonBeforeNormalization() {
        AgentLoop loop = new AgentLoop(org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.class),
                org.mockito.Mockito.mock(com.edu.agent.config.LangfuseClient.class));
        String envelope = "{\"thought\":\"synthetic\",\"action\":null,\"final_answer\":" + FINAL + "}";
        assertNull(loop.parseLlmJson(envelope).parseError());
        assertNotNull(loop.parseLlmJson(envelope.replace("\"risk_score\":0", "\"risk_score\":0,\"risk_score\":1")).parseError());
        assertNotNull(loop.parseLlmJson(envelope.replace("\"thought\"", "'thought'")).parseError());
        assertNotNull(loop.parseLlmJson(envelope.replace("\"resources\":[],", "\"resources\":[],,")).parseError());
        assertNotNull(loop.parseLlmJson(envelope + " {}" ).parseError());
    }
}
