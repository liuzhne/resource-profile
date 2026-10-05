package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.LevelRule;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.QuestionMapper;
import com.edu.mental.mapper.QuestionnaireMapper;
import com.edu.mental.service.QuestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    /** 问卷状态：0=未开始（可能仍在设计），1=进行中，2=已结束。 */
    private static final int STATUS_NOT_STARTED = 0;
    private static final String OPTION_SCORE = "score";

    private final QuestionMapper questionMapper;
    private final QuestionnaireMapper questionnaireMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Question> listByQuestionnaire(Long questionnaireId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getQuestionnaireId, questionnaireId)
                .orderByAsc(Question::getSortOrder)
                .orderByAsc(Question::getId);
        return questionMapper.selectList(wrapper);
    }

    @Override
    public QuestionnaireFullDto getFull(Long questionnaireId) {
        Questionnaire q = questionnaireMapper.selectById(questionnaireId);
        if (q == null) {
            throw new RuntimeException("问卷不存在: " + questionnaireId);
        }
        QuestionnaireFullDto dto = new QuestionnaireFullDto();
        dto.setQuestionnaire(q);
        dto.setQuestions(listByQuestionnaire(questionnaireId));
        dto.setLevelRules(parseLevelRules(q.getLevelRules()));
        return dto;
    }

    @Override
    public QuestionnaireFullDto getForRespondent(Long questionnaireId) {
        QuestionnaireFullDto dto = getFull(questionnaireId);
        Integer status = dto.getQuestionnaire().getStatus();
        // 已结束的仍要下发：结果页按题目回显本人作答
        if (status == null || status == STATUS_NOT_STARTED) {
            throw new BusinessException(403, "问卷当前未开放");
        }
        // 作答者拿到「哪个选项几分、几分算高危」就能按分挑选项躲过筛查；计分在 submit 时按库内原题完成，
        // 作答页与结果页只用题干和选项文字
        dto.getQuestionnaire().setLevelRules(null);
        dto.setLevelRules(Collections.emptyList());
        for (Question question : dto.getQuestions()) {
            question.setOptions(stripOptionScores(question.getOptions()));
            question.setScoringRules(null);
        }
        return dto;
    }

    @Override
    public Question save(Question question) {
        if (question.getSortOrder() == null) {
            Long count = questionMapper.selectCount(
                    new LambdaQueryWrapper<Question>().eq(Question::getQuestionnaireId, question.getQuestionnaireId())
            );
            question.setSortOrder(count.intValue() + 1);
        }
        if (question.getRequired() == null) {
            question.setRequired(1);
        }
        questionMapper.insert(question);
        syncQuestionCount(question.getQuestionnaireId());
        return question;
    }

    @Override
    public void update(Question question) {
        questionMapper.updateById(question);
    }

    @Override
    public void delete(Long questionId) {
        Question q = questionMapper.selectById(questionId);
        questionMapper.deleteById(questionId);
        if (q != null) {
            syncQuestionCount(q.getQuestionnaireId());
        }
    }

    @Override
    public void deleteByQuestionnaireId(Long questionnaireId) {
        questionMapper.delete(new LambdaQueryWrapper<Question>()
                .eq(Question::getQuestionnaireId, questionnaireId));
        syncQuestionCount(questionnaireId);
    }

    @Override
    public void saveBatch(Long questionnaireId, List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            syncQuestionCount(questionnaireId);
            return;
        }
        int order = 1;
        for (Question question : questions) {
            question.setQuestionnaireId(questionnaireId);
            if (question.getSortOrder() == null) {
                question.setSortOrder(order);
            }
            if (question.getRequired() == null) {
                question.setRequired(1);
            }
            questionMapper.insert(question);
            order++;
        }
        syncQuestionCount(questionnaireId);
    }

    private void syncQuestionCount(Long questionnaireId) {
        Long count = questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getQuestionnaireId, questionnaireId)
        );
        Questionnaire q = new Questionnaire();
        q.setId(questionnaireId);
        q.setQuestions(count.intValue());
        questionnaireMapper.updateById(q);
    }

    private List<LevelRule> parseLevelRules(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析等级规则失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 去掉每个选项的 score，只留选项文字；解析不了就整体不下发（前端对非法 JSON 本来也渲染不出选项）。 */
    private String stripOptionScores(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return optionsJson;
        }
        try {
            JsonNode options = objectMapper.readTree(optionsJson);
            if (!options.isArray()) {
                return null;
            }
            for (JsonNode option : options) {
                if (option instanceof ObjectNode o) {
                    o.remove(OPTION_SCORE);
                }
            }
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            log.warn("作答视图剥离选项分值失败，不下发该题选项: {}", e.getMessage());
            return null;
        }
    }
}
