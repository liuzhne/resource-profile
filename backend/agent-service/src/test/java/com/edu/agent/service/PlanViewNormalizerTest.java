package com.edu.agent.service;

import com.alibaba.fastjson2.JSON;
import com.edu.agent.core.AgentLoopOutputSchema;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PlanViewNormalizerTest {
    @Test
    void validCurrentFinalSchemaPreservesEveryReportFieldAndRoundTrips() throws Exception {
        String original = new ClassPathResource("report/react-final.json").getContentAsString(StandardCharsets.UTF_8);
        var finalAnswer = AgentLoopOutputSchema.parseFinalAnswer(original);
        assertThat(finalAnswer).isNotNull();
        var view = PlanViewNormalizer.normalize(finalAnswer.getJSONObject("intervention_plan").toJSONString());
        assertThat(view.immediate_actions().getFirst().deadline()).isEqualTo("48小时内");
        assertThat(view.long_term_plan().getFirst().goal()).isEqualTo("建立合成学习安排");
        assertThat(view.long_term_plan().getFirst().milestones()).containsExactly("第1周完成合成计划", "第4周复核合成结果");
        assertThat(view.long_term_plan().getFirst().horizon()).isEqualTo("4周");
        assertThat(view.resources().getFirst().name()).isEqualTo("合成支持资源：示例热线");
        assertThat(view.references()).containsExactly("synthetic-policy-001");
        assertThat(PlanViewNormalizer.hasContent(view)).isTrue();
        assertThat(PlanViewNormalizer.normalize(PlanViewNormalizer.toJson(view))).isEqualTo(view);
    }

    @Test
    void legacyKeepsMetricsDistinctAndMergesActualActionAndGlobalReferences() throws Exception {
        String original = new ClassPathResource("report/legacy-plan.json").getContentAsString(StandardCharsets.UTF_8);
        var view = PlanViewNormalizer.normalize(original);
        assertThat(view.immediate_actions().getFirst().deadline()).isEqualTo("3 天");
        assertThat(view.immediate_actions().getFirst().references()).containsExactly("synthetic-case-002");
        assertThat(view.long_term_plan().getFirst().goal()).isEqualTo("完成合成复习；建立合成记录");
        assertThat(view.long_term_plan().getFirst().horizon()).isEqualTo("1月");
        assertThat(view.long_term_plan().getFirst().metrics()).containsExactly("每周合成复核一次");
        assertThat(view.long_term_plan().getFirst().milestones()).isEmpty();
        assertThat(view.resources().getFirst().link_or_contact()).isEqualTo("https://example.invalid/synthetic");
        assertThat(view.references()).containsExactly("synthetic-policy-001", "synthetic-case-002");
        assertThat(JSON.parseObject(original).containsKey("deadline")).isFalse(); // input not rewritten
    }

    @Test
    void emptyPlaceholderMalformedOrDuplicatePlansCannotQualifyForFeedback() {
        for (String json : new String[]{null, "", "{}", "[]", "bad", "{\"summary\":\"LLM暂不可用\",\"talk_outline\":\"转人工\"}",
                "{\"immediate_actions\":[{\"action\":\" \"}]}", "{\"summary\":\"a\",\"summary\":\"b\"}", "{} {}"}) {
            assertThat(PlanViewNormalizer.hasContent(PlanViewNormalizer.normalize(json))).as("empty or invalid plan").isFalse();
        }
    }
}
