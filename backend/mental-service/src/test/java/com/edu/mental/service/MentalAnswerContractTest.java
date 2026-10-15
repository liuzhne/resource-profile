package com.edu.mental.service;

import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.SubmitAnswerRequest;
import com.edu.mental.entity.Question;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MentalAnswerContractTest {
    static Question choice(long id,String type) {
        var q=new Question(); q.setId(id); q.setContent("虚构题目"); q.setQuestionType(type); q.setRequired(1);
        q.setOptions("[{\"label\":\"选项A\",\"score\":2},{\"label\":\"选项B\",\"score\":3}]"); return q;
    }
    static SubmitAnswerRequest.AnswerItem answer(long id,Integer... picks) {
        var a=new SubmitAnswerRequest.AnswerItem(); a.setQuestionId(id); a.setOptionIndices(Arrays.asList(picks)); return a;
    }
    @Test void optionScoresAndLegacyValueRulesHaveTheSameAuthoritativeScore() {
        Question current=choice(1,"single_choice"),legacy=choice(2,"single_choice");
        legacy.setOptions("[{\"label\":\"A\",\"value\":\"A\"},{\"label\":\"B\",\"value\":\"B\"}]"); legacy.setScoringRules("{\"A\":2,\"B\":3}");
        assertEquals(5,MentalAnswerContract.score(List.of(current,legacy),List.of(answer(1,0),answer(2,1))));
    }
    @Test void multipleAndNumericScaleAndReverseScaleAndTextAreActuallyCalculated() {
        var multi=choice(1,"multiple_choice"); var scale=choice(2,"scale"); scale.setOptions(null); scale.setScaleMin(1); scale.setScaleMax(3);
        var reverse=choice(3,"scale"); reverse.setOptions(null); reverse.setScaleMin(1); reverse.setScaleMax(3); reverse.setScoringRules("{\"1\":3,\"2\":2,\"3\":1}");
        var text=choice(4,"text"); text.setOptions(null);
        var a=answer(2); a.setScaleValue(3); var b=answer(3); b.setScaleValue(1); var c=answer(4); c.setText("虚构回复");
        assertEquals(11,MentalAnswerContract.score(List.of(multi,scale,reverse,text),List.of(answer(1,0,1),a,b,c)));
    }
    @Test void malformedAnswerCollectionsAreRejectedRatherThanPersistableZeroScores() {
        var q=choice(1,"single_choice");
        for(var answers:List.of(List.<SubmitAnswerRequest.AnswerItem>of(),List.of(answer(9,0)),List.of(answer(1,0),answer(1,1)),List.of(answer(1,0,1)),List.of(answer(1,9)),List.of(answer(1,-1)),List.of(answer(1))))
            assertThrows(BusinessException.class,()->MentalAnswerContract.score(List.of(q),answers));
        q.setQuestionType("multiple_choice"); assertThrows(BusinessException.class,()->MentalAnswerContract.score(List.of(q),List.of(answer(1,0,0))));
        q.setRequired(0); assertEquals(0,MentalAnswerContract.score(List.of(q),List.of()));
    }
    @Test void textAndScaleRequirednessAndBoundariesAreValidatedAtServer() {
        var text=choice(1,"text"); text.setOptions(null); assertThrows(BusinessException.class,()->MentalAnswerContract.score(List.of(text),List.of(answer(1))));
        var scale=choice(1,"scale"); scale.setOptions(null); scale.setScaleMin(1); scale.setScaleMax(3);
        for(Integer value:Arrays.asList(null,0,4)) {var a=answer(1);a.setScaleValue(value);assertThrows(BusinessException.class,()->MentalAnswerContract.score(List.of(scale),List.of(a)));}
        var a=answer(1);a.setScaleValue(2);a.setText("2");assertThrows(BusinessException.class,()->MentalAnswerContract.score(List.of(scale),List.of(a)));
    }
    @Test void malformedAndConflictingScoreDefinitionsCannotBePublished() {
        assertThrows(BusinessException.class,()->MentalAnswerContract.levels("[{\"level\":\"正常\",\"minScore\":0.5}]"));
        var q=choice(1,"single_choice");q.setScoringRules("{\"0\":999,\"1\":3}");assertThrows(BusinessException.class,()->MentalAnswerContract.validateQuestion(q));
        q.setScoringRules("null");assertThrows(BusinessException.class,()->MentalAnswerContract.validateQuestion(q));
        q.setScoringRules(null);q.setOptions("null");assertThrows(BusinessException.class,()->MentalAnswerContract.validateQuestion(q));
        q.setOptions("[{\"label\":\"A\"},{\"label\":\"B\"}]");assertThrows(BusinessException.class,()->MentalAnswerContract.validateQuestion(q));
        q.setQuestionType("scale");q.setOptions(null);q.setScaleMin(1);q.setScaleMax(3);q.setScoringRules("{\"1\":1}");assertThrows(BusinessException.class,()->MentalAnswerContract.validateQuestion(q));
        q.setScoringRules("{\"1\":1,\"1\":2}");assertThrows(BusinessException.class,()->MentalAnswerContract.validateQuestion(q));
    }
    @Test void snapshotIsACopyWithOnlyRespondentLabelsAndNoRules() {
        var questionnaire=new com.edu.mental.entity.Questionnaire();questionnaire.setId(1L);questionnaire.setTitle("虚构问卷");questionnaire.setStartTime(java.time.LocalDate.of(2026,10,7));questionnaire.setLevelRules("[]");
        var q=choice(1,"single_choice");var snap=MentalAnswerContract.respondentSnapshot(questionnaire,List.of(q));
        assertNull(snap.getQuestionnaire().getLevelRules());assertFalse(snap.getQuestions().get(0).getOptions().contains("score"));
        assertNotNull(questionnaire.getLevelRules());assertTrue(q.getOptions().contains("score"));
    }
    @Test void publicationRequiresARealQuestionSetAndCoveringGradeRules() {
        var q=new com.edu.mental.entity.Questionnaire();q.setLevelRules("[{\"level\":\"正常\",\"minScore\":85}]");
        assertThrows(BusinessException.class,()->MentalAnswerContract.validatePublished(q,List.of()));
        assertThrows(BusinessException.class,()->MentalAnswerContract.validatePublished(q,List.of(choice(1,"single_choice"))));
        q.setLevelRules("[{\"level\":\"正常\",\"minScore\":0}]");assertDoesNotThrow(()->MentalAnswerContract.validatePublished(q,List.of(choice(1,"single_choice"))));
    }
}
