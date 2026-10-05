package com.edu.mental.service;

import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;

import java.util.List;

public interface QuestionService {

    List<Question> listByQuestionnaire(Long questionnaireId);

    QuestionnaireFullDto getFull(Long questionnaireId);

    /**
     * 作答视图：去掉计分答案（选项分值、scoringRules、等级规则），未开始的问卷拒绝下发。
     * 学生答题 / 结果页用；教职工预览走 {@link #getFull}。
     */
    QuestionnaireFullDto getForRespondent(Long questionnaireId);

    Question save(Question question);

    void update(Question question);

    void delete(Long questionId);

    /** B-1：删除某问卷下全部题目（Excel 重新导入前清空）。 */
    void deleteByQuestionnaireId(Long questionnaireId);

    /** B-1：批量保存题目到指定问卷（回填 questionnaireId / sort_order）。 */
    void saveBatch(Long questionnaireId, List<Question> questions);
}
