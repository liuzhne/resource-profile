package com.edu.mental.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.security.*;
import com.edu.common.util.JwtUtil;
import com.edu.mental.dto.*;
import com.edu.mental.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MentalContractControllerTest {
    static final String SECRET="test-internal-secret-at-least-32-characters";
    MentalAssessmentService assessments; QuestionnaireService questionnaires; MentalManagementService management;
    JwtUtil jwt; MentalContractController controller;
    @BeforeEach void setUp() {
        assessments=mock(MentalAssessmentService.class);questionnaires=mock(QuestionnaireService.class);management=mock(MentalManagementService.class);jwt=mock(JwtUtil.class);
        when(jwt.parseToken("tok")).thenReturn(io.jsonwebtoken.Jwts.claims().build());
        controller=new MentalContractController(assessments,questionnaires,management,new AccessGuard(jwt,new InternalCallCredential(SECRET)));
    }
    @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();RequestContext.clear();}
    void role(String value){when(jwt.parseRoles("tok")).thenReturn(Set.of(value));when(jwt.getSubject("tok")).thenReturn("91");}
    void internal(String token){var req=new MockHttpServletRequest();req.addHeader(InternalCallCredential.HEADER,token);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));}
    @Test void scopedHistoryRequiresStaffOrVerifiedInternalAndNeverGuessesAccountScope() {
        assertEquals(403,controller.studentHistory(701L,null).getCode());internal("incorrect");assertEquals(403,controller.studentHistory(701L,null).getCode());
        internal(SECRET);when(assessments.historyForStudent(701L)).thenReturn(List.of());assertEquals(200,controller.studentHistory(701L,null).getCode());verify(assessments).historyForStudent(701L);
    }
    @Test void studentCannotBecomeInternalByAddingHeaderAndTeacherCanReadControlledHistory() {
        role("student");internal(SECRET);assertEquals(403,controller.studentHistory(701L,"Bearer tok").getCode());verifyNoInteractions(assessments);
        role("teacher");assertEquals(200,controller.studentHistory(701L,"Bearer tok").getCode());verify(assessments).historyForStudent(701L);
    }
    @Test void readsAndWritesHaveSeparateRolePoliciesAndInternalCannotManage() {
        var file=new MockMultipartFile("file","test.xlsx","application/octet-stream",new byte[]{1});var action=new WarningActionRequest();action.setStatus("RESOLVED");action.setNote("虚构处置");
        internal(SECRET);assertEquals(403,controller.preview(1L,file,null).getCode());assertEquals(403,controller.handle(2L,action,null).getCode());
        role("teacher");assertEquals(403,controller.preview(1L,file,"Bearer tok").getCode());assertEquals(403,controller.importFile(1L,file,"Bearer tok").getCode());assertEquals(403,controller.handle(2L,action,"Bearer tok").getCode());verifyNoInteractions(questionnaires,management);
        role("psychologist");assertEquals(200,controller.preview(1L,file,"Bearer tok").getCode());assertEquals(200,controller.importFile(1L,file,"Bearer tok").getCode());assertEquals(200,controller.handle(2L,action,"Bearer tok").getCode());verify(management).handleWarning(2L,action,91L);
    }
    @Test void warningNotesAreRemovedForTeacherAndPresentForPsychologist() {
        var page=new Page<Map<String,Object>>(1,10);var row=new HashMap<String,Object>();row.put("warningNote","专业处置说明");row.put("level","中度");page.setRecords(List.of(row));when(management.warnings(1,10,null)).thenReturn(page);
        role("teacher");var r=controller.warnings(1,10,null,"Bearer tok");assertEquals(200,r.getCode());assertFalse(r.getData().getRecords().get(0).containsKey("warningNote"));assertEquals("中度",row.get("level"));
        row.put("warningNote","专业处置说明");role("psychologist");assertEquals("专业处置说明",controller.warnings(1,10,null,"Bearer tok").getData().getRecords().get(0).get("warningNote"));
    }
    @Test void csvExportRequiresStaffJwtAndSetsDownloadContract() {
        internal(SECRET);assertEquals(403,controller.export(null).getStatusCode().value());role("student");assertEquals(403,controller.export("Bearer tok").getStatusCode().value());verifyNoInteractions(management);
        role("teacher");when(management.exportAnalysis()).thenReturn("虚构报表".getBytes(java.nio.charset.StandardCharsets.UTF_8));var r=controller.export("Bearer tok");assertEquals(200,r.getStatusCode().value());assertEquals("text/csv;charset=UTF-8",r.getHeaders().getContentType().toString());assertTrue(r.getHeaders().getFirst("Content-Disposition").contains("mental-analysis.csv"));
    }
    MentalHistoryDto datum(){var d=new MentalHistoryDto();d.setStudentId(701L);d.setScore(0);d.setLevel("正常");d.setQuestionnaireType("虚构类型");d.setSuggestion("虚构建议");return d;}
    @Test void historySensitiveFieldsUseRealAdviceAndPreserveZeroForAuthorizedCallers() {
        var advice=new FieldPermissionAdvice();RequestContext.setRoles(Set.of("teacher"));var teacher=datum();advice.beforeBodyWrite(teacher,null,null,null,null,null);assertNull(teacher.getScore());assertNull(teacher.getSuggestion());assertEquals("正常",teacher.getLevel());assertEquals("虚构类型",teacher.getQuestionnaireType());
        RequestContext.setRoles(Set.of("student"));var student=datum();advice.beforeBodyWrite(student,null,null,null,null,null);assertNull(student.getLevel());
        RequestContext.setRoles(Set.of());RequestContext.setInternal(true);var full=datum();advice.beforeBodyWrite(full,null,null,null,null,null);assertEquals(0,full.getScore());assertEquals("虚构建议",full.getSuggestion());
    }
}
