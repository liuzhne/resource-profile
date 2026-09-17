package com.edu.mental.service.impl;

import com.edu.mental.entity.Question;
import com.edu.mental.mapper.QuestionMapper;
import com.edu.mental.mapper.QuestionnaireMapper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionBulkImportTest {
    @Test
    void importPreservesDefaultsAndExplicitOrderInBoundedBatches() {
        QuestionMapper mapper = mock(QuestionMapper.class);
        QuestionnaireMapper questionnaires = mock(QuestionnaireMapper.class);
        when(mapper.selectCount(any())).thenReturn(205L);
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            Question question = new Question();
            question.setContent("题目" + i);
            questions.add(question);
        }
        questions.get(0).setSortOrder(77);
        questions.get(0).setRequired(0);
        List<Integer> batches = new ArrayList<>();
        doAnswer(invocation -> { List<Question> batch = invocation.getArgument(0); batches.add(batch.size()); return batch.size(); }).when(mapper).insertBulk(anyList());
        new QuestionServiceImpl(mapper, questionnaires).saveBatch(9L, questions);
        assertEquals(List.of(100, 100, 5), batches);
        assertEquals(77, questions.get(0).getSortOrder());
        assertEquals(0, questions.get(0).getRequired());
        assertEquals(205, questions.get(204).getSortOrder());
        assertEquals(1, questions.get(204).getRequired());
        for (Question question : questions) {
            assertEquals(9L, question.getQuestionnaireId());
            assertNotNull(question.getCreateTime());
            assertNotNull(question.getUpdateTime());
        }
        verify(mapper, never()).insert(any(Question.class));
        verify(mapper, times(1)).selectCount(any());
    }
    @Test
    void emptyImportDoesNotGenerateInvalidValuesSql() {
        QuestionMapper mapper = mock(QuestionMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        new QuestionServiceImpl(mapper, mock(QuestionnaireMapper.class)).saveBatch(9L, List.of());
        verify(mapper, never()).insertBulk(anyList());
    }
}
