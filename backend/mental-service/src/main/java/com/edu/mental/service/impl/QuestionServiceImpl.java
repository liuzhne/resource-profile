package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.mental.dto.LevelRule;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.QuestionMapper;
import com.edu.mental.mapper.QuestionnaireMapper;
import com.edu.mental.service.QuestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

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
}
