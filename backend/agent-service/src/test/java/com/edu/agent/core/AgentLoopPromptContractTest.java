package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks the model-visible examples against the strict envelope, without any model or tool calls. */
class AgentLoopPromptContractTest {
    private static String prompt(boolean structured) {
        return prompt(structured, List.of());
    }

    private static String prompt(boolean structured, List<ToolCallback> tools) {
        AgentLoop loop = new AgentLoop(null, null);
        ReflectionTestUtils.setField(loop, "structuredOutputEnabled", structured);
        return ReflectionTestUtils.invokeMethod(loop, "composeSystemPrompt",
                new AgentLoopRequest("Task-specific schema", "Protocol test", tools, 1, "prompt-test"));
    }

    @Test
    void strictExamplesCarryEveryRequiredFieldAndUseEmbeddedFinalObjects() throws Exception {
        String prompt = prompt(true);
        JSONObject schema = JSON.parseObject(JSON.toJSONString(
                AgentLoopOutputSchema.responseFormat(List.of()).getJsonSchema().getSchema()));
        Set<String> required = Set.copyOf(schema.getJSONArray("required").toJavaList(String.class));
        var examples = Pattern.compile("结构示例：(\\{[^\\n]*?\\})。").matcher(prompt);
        int count = 0;
        while (examples.find()) {
            JSONObject turn = AgentLoopOutputSchema.parseStrictObject(examples.group(1));
            assertThat(turn.keySet()).isEqualTo(required);
            assertThat(turn.get("thought")).isInstanceOf(String.class);
            assertThat(turn.get("plan")).isNull();
            if (turn.get("action") != null) {
                assertThat(turn.get("action")).isInstanceOf(JSONObject.class);
                assertThat(turn.get("final_answer")).isNull();
            } else {
                JSONObject answer = turn.getJSONObject("final_answer");
                JSONObject answerSchema = schema.getJSONObject("properties").getJSONObject("final_answer")
                        .getJSONArray("anyOf").getJSONObject(0);
                assertThat(answer.keySet()).isEqualTo(Set.copyOf(answerSchema.getJSONArray("required").toJavaList(String.class)));
                assertThat(answer.get("risk_analysis")).isInstanceOf(JSONObject.class);
                assertThat(answer.get("intervention_plan")).isInstanceOf(JSONObject.class);
                // The prompt must explicitly prohibit copying the incomplete structural placeholders.
                assertThat(AgentLoopOutputSchema.validFinalAnswer(answer)).isFalse();
                assertThat(prompt).contains("全部必需字段", "空对象不是合法最终结果");
            }
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void strictPromptNeverAuthorizesTextFinalAnswersOrOmittingOptionalTopLevelFields() {
        assertThat(prompt(true))
                .contains("final_answer 必须是双对象 JSON", "plan 若使用则必须是字符串数组", "不使用时仍须填写 null", "本次无可用工具")
                .doesNotContain("普通文本答案可使用字符串", "（可选）首轮可在 JSON 里附");
    }

    @Test
    void unstructuredPromptRetainsStringCompatibilityWithoutStrictFourFieldExamples() {
        assertThat(prompt(false))
                .contains("普通文本答案可使用字符串", "（可选）首轮可在 JSON 里附")
                .doesNotContain("所有顶层字段", "两种四字段形态", "final_answer 必须是双对象 JSON");
    }

    @Test
    void strictPromptOmitsMetadataWhileUnstructuredPromptRetainsCompleteCatalog() {
        ToolCallback tool = profileTool();
        String inputSchema = tool.getToolDefinition().inputSchema();
        assertThat(prompt(true, List.of(tool)))
                .startsWith("Task-specific schema\n\n# 输出协议")
                .doesNotContain("# 可用工具", "name: get_student_profile", "description: Typed synthetic profile", "inputSchema:", inputSchema);
        assertThat(prompt(false, List.of(tool)))
                .contains("name: get_student_profile", "description: Typed synthetic profile", "inputSchema: " + inputSchema);
    }

    @Test
    void strictPromptChangeDoesNotCoerceOrAcceptInvalidCallbackArguments() {
        ToolCallback tool = profileTool();
        AgentLoop loop = new AgentLoop(null, null);
        ReflectionTestUtils.setField(loop, "structuredOutputEnabled", true);
        assertThat(loop.invokeTool(List.of(tool), "get_student_profile", "{\"studentId\":750,\"top_k\":null}"))
                .isEqualTo("{\"studentId\":750}");
        assertThatThrownBy(() -> loop.invokeTool(List.of(tool), "get_student_profile", "{\"studentId\":\"750\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ToolCallback profileTool() {
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("get_student_profile").description("Typed synthetic profile")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"studentId\":{\"type\":\"integer\"},\"top_k\":{\"type\":\"integer\",\"default\":5}},\"required\":[\"studentId\"]}").build();
            }
            @Override public String call(String input) {
                JSONObject args = JSON.parseObject(input);
                if (!(args.get("studentId") instanceof Integer)) throw new IllegalArgumentException("Student ID must be an integer");
                assertThat(args).doesNotContainKey("top_k");
                return args.toJSONString();
            }
        };
    }
}
