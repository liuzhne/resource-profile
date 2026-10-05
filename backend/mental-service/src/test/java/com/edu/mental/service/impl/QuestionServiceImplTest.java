package com.edu.mental.service.impl;

import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.QuestionMapper;
import com.edu.mental.mapper.QuestionnaireMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 作答视图单测（MENTAL-AUTHZ-20260914）：学生答题 / 结果页拿到的问卷不含计分答案，未开始的问卷不下发；
 * 教职工走的 getFull 保持完整。
 */
class QuestionServiceImplTest {

    private static final String SCORED_OPTIONS =
            "[{\"label\":\"从不\",\"score\":0},{\"label\":\"总是\",\"score\":3}]";
    private static final String LEVEL_RULES =
            "[{\"level\":\"高危\",\"minScore\":30,\"suggestion\":\"立即转介\"}]";

    private QuestionMapper questionMapper;
    private QuestionnaireMapper questionnaireMapper;
    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        questionMapper = mock(QuestionMapper.class);
        questionnaireMapper = mock(QuestionnaireMapper.class);
        service = new QuestionServiceImpl(questionMapper, questionnaireMapper);
    }

    private void givenQuestionnaire(Integer status, Question... questions) {
        Questionnaire q = new Questionnaire();
        q.setId(1L);
        q.setStatus(status);
        q.setLevelRules(LEVEL_RULES);
        when(questionnaireMapper.selectById(1L)).thenReturn(q);
        when(questionMapper.selectList(any())).thenReturn(List.of(questions));
    }

    private static Question question(String options) {
        Question q = new Question();
        q.setId(10L);
        q.setOptions(options);
        q.setScoringRules("{\"总是\":3}");
        return q;
    }

    @Test
    void getForRespondent_stripsScoringKey() {
        givenQuestionnaire(1, question(SCORED_OPTIONS));

        QuestionnaireFullDto dto = service.getForRespondent(1L);

        assertThat(dto.getLevelRules()).isEmpty();
        assertThat(dto.getQuestionnaire().getLevelRules()).isNull();
        Question out = dto.getQuestions().get(0);
        assertThat(out.getOptions()).isEqualTo("[{\"label\":\"从不\"},{\"label\":\"总是\"}]");
        assertThat(out.getScoringRules()).isNull();
    }

    @Test
    void getForRespondent_endedQuestionnaire_stillServed() {
        // 结果页要按题目回显本人作答，已结束的问卷仍需下发（同样去掉计分答案）
        givenQuestionnaire(2, question(SCORED_OPTIONS));

        assertThat(service.getForRespondent(1L).getQuestions().get(0).getOptions()).doesNotContain("score");
    }

    @Test
    void getForRespondent_notStarted_rejected() {
        givenQuestionnaire(0, question(SCORED_OPTIONS));

        assertThatThrownBy(() -> service.getForRespondent(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void getForRespondent_nullStatus_rejected() {
        givenQuestionnaire(null, question(SCORED_OPTIONS));

        assertThatThrownBy(() -> service.getForRespondent(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void getForRespondent_malformedOrNonArrayOptions_dropped() {
        // 解析不了就整体不下发，宁可无选项也不带出分值
        givenQuestionnaire(1, question("not-json"), question("{\"score\":3}"), question(null));

        List<Question> out = service.getForRespondent(1L).getQuestions();

        assertThat(out.get(0).getOptions()).isNull();
        assertThat(out.get(1).getOptions()).isNull();
        // 简答题本就没有选项
        assertThat(out.get(2).getOptions()).isNull();
    }

    @Test
    void getFull_keepsScoringKeyForStaffPreview() {
        givenQuestionnaire(0, question(SCORED_OPTIONS));

        QuestionnaireFullDto dto = service.getFull(1L);

        assertThat(dto.getLevelRules()).hasSize(1);
        assertThat(dto.getQuestions().get(0).getOptions()).isEqualTo(SCORED_OPTIONS);
    }
}
