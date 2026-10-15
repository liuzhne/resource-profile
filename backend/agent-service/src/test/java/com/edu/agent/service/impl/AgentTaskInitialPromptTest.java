package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.core.AgentLoopOutputSchema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskInitialPromptTest {
    @ParameterizedTest
    @ValueSource(longs = {346, 750, 175})
    void initialToolExampleUsesRequestedStudentAndDoesNotReplaceLaterObservationWorkflow(long studentId) throws Exception {
        String prompt = AgentTaskServiceImpl.initialAgentUserPrompt(studentId);
        String example = prompt.substring(prompt.indexOf("首轮四字段示例：") + "首轮四字段示例：".length(), prompt.indexOf('\n', prompt.indexOf("首轮四字段示例：")));
        JSONObject turn = AgentLoopOutputSchema.parseStrictObject(example);
        assertThat(turn.keySet()).isEqualTo(Set.of("thought", "plan", "action", "final_answer"));
        assertThat(turn.get("plan")).isNull();
        assertThat(turn.get("final_answer")).isNull();
        assertThat(turn.getJSONObject("action").getString("tool")).isEqualTo("get_student_profile");
        assertThat(turn.getJSONObject("action").getJSONObject("args").get("studentId")).isInstanceOf(Number.class);
        assertThat(turn.getJSONObject("action").getJSONObject("args").getLongValue("studentId")).isEqualTo(studentId);
        assertThat(prompt).contains("尚无任何工具 Observation", "首轮取数指引不再适用", "不重复已经成功执行的工具", "不假设缺失数据");
    }
}
