package com.edu.mental.service;

import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;

import java.util.List;

public interface QuestionService {

    List<Question> listByQuestionnaire(Long questionnaireId);

    QuestionnaireFullDto getFull(Long questionnaireId);

    /**
 * Persist the given Question entity and return the saved instance.
 *
 * @param question the Question entity to persist; may be a new entity or an existing one to be merged
 * @return the persisted Question instance, potentially with generated identifiers or updated audit fields
 */
Question save(Question question);

    /**
 * Persists multiple Question entities and associates them with the specified questionnaire.
 *
 * @param questionnaireId the ID of the questionnaire to associate the saved questions with
 * @param questions the list of Question entities to persist
 */
void saveBatch(Long questionnaireId, List<Question> questions);

    void delete(Long questionId);

    /** B-1：删除某问卷下全部题目（Excel 重新导入前清空）。 */
    void deleteByQuestionnaireId(Long questionnaireId);

    /** B-1：批量保存题目到指定问卷（回填 questionnaireId / sort_order）。 */
    void saveBatch(Long questionnaireId, List<Question> questions);
}
