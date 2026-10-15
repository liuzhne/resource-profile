package com.edu.mcp.student.tool;

import com.edu.common.result.Result;
import com.edu.mcp.student.feign.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real tool methods with synthetic Feign responses, no server/model/network access. */
class StudentDataToolsDataContractTest {
    StudentServiceClient students; MentalServiceClient mental; StudentDataTools tools;
    @BeforeEach void setup(){students=mock(StudentServiceClient.class);mental=mock(MentalServiceClient.class);tools=new StudentDataTools(students,mental);}
    Map<String,Object> assessment(long owner,String time,String level,Object score) {
        var row=new HashMap<String,Object>();row.put("studentId",owner);row.put("createTime",time);row.put("level",level);row.put("score",score);row.put("questionnaireType","虚构自定义问卷");return row;
    }
    @Test void scopeIsExactStudentRecordIdAndLatestRealSourceFieldsArePreserved() {
        var older=assessment(701,"2026-10-01T08:00:00","中度",21);
        var newest=assessment(701,"2026-10-07T08:00:00","正常",0);
        when(mental.historyByStudent(701L)).thenReturn(Result.success(List.of(newest,older)));
        var result=tools.getMentalIndicators(701L);assertTrue(result.getFound());assertEquals(701L,result.getStudentId());assertEquals("none",result.getRiskLevel());assertEquals("正常",result.getAssessmentLevel());assertEquals("虚构自定义问卷",result.getAssessmentType());assertEquals(0,BigDecimal.ZERO.compareTo(result.getTotalScore()));assertTrue(result.getFactorScores().isEmpty());verify(mental).historyByStudent(701L);verifyNoInteractions(students);
    }
    @Test void onlySuccessfulEmptyListMeansNoAssessmentAndNullOrErrorRemainErrors() {
        when(mental.historyByStudent(701L)).thenReturn(Result.success(List.of()));assertFalse(tools.getMentalIndicators(701L).getFound());
        when(mental.historyByStudent(701L)).thenReturn(null);assertThrows(IllegalStateException.class,()->tools.getMentalIndicators(701L));
        when(mental.historyByStudent(701L)).thenReturn(Result.error(503,"虚构下游失败"));assertThrows(IllegalStateException.class,()->tools.getMentalIndicators(701L));
        when(mental.historyByStudent(701L)).thenReturn(Result.success(null));assertThrows(IllegalStateException.class,()->tools.getMentalIndicators(701L));
        when(mental.historyByStudent(701L)).thenThrow(new IllegalStateException("虚构Feign网络失败"));assertThrows(IllegalStateException.class,()->tools.getMentalIndicators(701L));
    }
    @Test void mismatchedMissingOrNullRowOwnershipFailsInsteadOfReadingAnotherStudentsRecord() {
        for(var rows:List.<List<Map<String,Object>>>of(List.of(assessment(702,"2026-10-07","正常",0)),List.of(new HashMap<String,Object>()),Arrays.<Map<String,Object>>asList((Map<String,Object>)null))) {
            when(mental.historyByStudent(701L)).thenReturn(Result.success(rows));assertThrows(IllegalStateException.class,()->tools.getMentalIndicators(701L));
        }
        assertThrows(IllegalArgumentException.class,()->tools.getMentalIndicators(0L));
    }
    @Test void unknownLevelIsNotInventedAsLowAndMissingScoreIsNotZero() {
        when(mental.historyByStudent(701L)).thenReturn(Result.success(List.of(assessment(701,"2026-10-07","上游未知级别",null))));
        var unknown=tools.getMentalIndicators(701L);assertTrue(unknown.getFound());assertNull(unknown.getRiskLevel());assertNull(unknown.getTotalScore());assertEquals("上游未知级别",unknown.getAssessmentLevel());assertTrue(unknown.getFactorScores().isEmpty());
        when(mental.historyByStudent(701L)).thenReturn(Result.success(List.of(assessment(701,"2026-10-07",null,null))));assertNull(tools.getMentalIndicators(701L).getRiskLevel());
    }
    @Test void knownEnglishNormalIsNoneAndUnknownEnglishLevelStaysUnknown() {
        for(String level:List.of("normal","NORMAL"," 正常 ")) {
            when(mental.historyByStudent(701L)).thenReturn(Result.success(List.of(assessment(701,"2026-10-07",level,0))));
            assertEquals("none",tools.getMentalIndicators(701L).getRiskLevel());
        }
        when(mental.historyByStudent(701L)).thenReturn(Result.success(List.of(assessment(701,"2026-10-07","unrecognized",0))));assertNull(tools.getMentalIndicators(701L).getRiskLevel());
    }
    @Test void factorScoresExistOnlyWhenSourceExplicitlyProvidesThem() {
        var row=assessment(701,"2026-10-07","轻度",18);row.put("factorScores",Map.of("虚构因子",4));when(mental.historyByStudent(701L)).thenReturn(Result.success(List.of(row)));
        var result=tools.getMentalIndicators(701L);assertEquals("low",result.getRiskLevel());assertEquals(4,result.getFactorScores().get("虚构因子"));assertEquals(0,BigDecimal.valueOf(18).compareTo(result.getTotalScore()));
    }
    Map<String,Object> course(Object credit,Object score,Object gp) {var row=new HashMap<String,Object>();row.put("credit",credit);row.put("score",score);row.put("gradePoint",gp);return row;}
    @Test void scoreAndGpaUseTheirOwnGradedCreditsAndUnscoredCoursesDoNotDiluteTheAverage() {
        when(students.listAcademic(701L,null)).thenReturn(Result.success(List.of(course(2,80,null),course(4,null,3),course(2,0,0),course(10,null,null),course(null,99,4))));
        var result=tools.getAcademicHistory(701L,null);assertEquals(new BigDecimal("40.00"),result.getAverageScore());assertEquals(new BigDecimal("2.00"),result.getAverageGradePoint());assertEquals(5,result.getRecords().size());
        when(students.listAcademic(701L,null)).thenReturn(Result.success(List.of(course(2,null,null),course(0,80,4))));var noScore=tools.getAcademicHistory(701L,null);assertNull(noScore.getAverageScore());assertNull(noScore.getAverageGradePoint());
    }
    @Test void invalidNegativeCreditFailsAndNullOrFailedGradeApiIsNotAnEmptyGradeHistory() {
        when(students.listAcademic(701L,null)).thenReturn(Result.success(List.of(course(-1,90,4))));assertThrows(IllegalStateException.class,()->tools.getAcademicHistory(701L,null));
        when(students.listAcademic(701L,null)).thenReturn(Result.error(503,"虚构失败"));assertThrows(IllegalStateException.class,()->tools.getAcademicHistory(701L,null));
        when(students.listAcademic(701L,null)).thenReturn(null);assertThrows(IllegalStateException.class,()->tools.getAcademicHistory(701L,null));
        when(students.listAcademic(701L,null)).thenReturn(Result.success(null));assertThrows(IllegalStateException.class,()->tools.getAcademicHistory(701L,null));
    }
}
