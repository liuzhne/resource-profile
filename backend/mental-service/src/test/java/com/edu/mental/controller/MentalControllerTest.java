package com.edu.mental.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.InternalCallCredential;
import com.edu.common.util.JwtUtil;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.MentalAssessmentMapper;
import com.edu.mental.service.MentalAssessmentService;
import com.edu.mental.service.QuestionService;
import com.edu.mental.service.QuestionnaireService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MentalController 纵向越权单测（MENTAL-AUTHZ-20260914）：真实 AccessGuard + mock JwtUtil。
 * 读限教职工、写限 admin/psychologist，学生 / 匿名 403 且不触达 service；
 * 内部凭证只放行 agent-service 实际调用的 /analysis；完成情况的原始分只对 admin/psychologist 保留。
 */
class MentalControllerTest {

    private static final String AUTH = "Bearer tok";
    private static final String INTERNAL_TOKEN = "internal-secret-at-least-32-chars-0001";

    private QuestionnaireService questionnaireService;
    private QuestionService questionService;
    private MentalAssessmentService assessmentService;
    private MentalAssessmentMapper mentalAssessmentMapper;
    private JwtUtil jwtUtil;
    private MentalController controller;

    @BeforeEach
    void setUp() {
        questionnaireService = mock(QuestionnaireService.class);
        questionService = mock(QuestionService.class);
        assessmentService = mock(MentalAssessmentService.class);
        mentalAssessmentMapper = mock(MentalAssessmentMapper.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new MentalController(questionnaireService, questionService, assessmentService,
                mentalAssessmentMapper, new AccessGuard(jwtUtil, new InternalCallCredential(INTERNAL_TOKEN)));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 以指定角色登录（JWT 合法）。 */
    private void loginAs(String... roles) {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of(roles));
    }

    /** 当前请求带 X-Internal-Token（模拟 agent-service 的 Feign 调用）。 */
    private static void withInternalHeader(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalCallCredential.HEADER, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void assertNoDataAccess() {
        verifyNoInteractions(questionnaireService, questionService, assessmentService, mentalAssessmentMapper);
    }

    /** 全部读端点（含 /analysis）的业务码。 */
    private List<Integer> readCodes(String auth) {
        return List.of(
                controller.overview(auth).getCode(),
                controller.analysis(auth).getCode(),
                controller.listQuestionnaires(1, 10, auth).getCode(),
                controller.getQuestionnaire(1L, auth).getCode(),
                controller.getFull(1L, auth).getCode(),
                controller.listQuestions(1L, auth).getCode(),
                controller.completion(1L, auth).getCode());
    }

    /** 全部写端点的业务码。 */
    private List<Integer> writeCodes(String auth) {
        return List.of(
                controller.saveQuestionnaire(new Questionnaire(), auth).getCode(),
                controller.updateQuestionnaire(1L, new Questionnaire(), auth).getCode(),
                controller.deleteQuestionnaire(1L, auth).getCode(),
                controller.addQuestion(1L, new Question(), auth).getCode(),
                controller.updateQuestion(2L, new Question(), auth).getCode(),
                controller.deleteQuestion(2L, auth).getCode());
    }

    /* ---------- 读：限教职工 ---------- */

    @Test
    void reads_student_forbidden_andNoDataAccess() {
        // 漏洞回归：学生 token 经网关可读预警名单、完成情况里的原始分
        loginAs("student");

        assertThat(readCodes(AUTH)).containsOnly(403);
        assertNoDataAccess();
    }

    @Test
    void reads_anonymous_forbidden() {
        assertThat(readCodes(null)).containsOnly(403);
        assertNoDataAccess();
    }

    @Test
    void reads_staff_ok() {
        loginAs("academic_advisor");
        when(questionnaireService.list(1, 10)).thenReturn(new Page<>());
        when(questionService.getFull(1L)).thenReturn(new QuestionnaireFullDto());

        assertThat(readCodes(AUTH)).containsOnly(200);
        verify(mentalAssessmentMapper).selectWarningList();
        verify(mentalAssessmentMapper).selectDeptDistribution();
        verify(questionnaireService).list(1, 10);
        verify(questionnaireService).getById(1L);
        verify(questionService).getFull(1L);
        verify(questionService).listByQuestionnaire(1L);
        verify(assessmentService).listCompletion(1L);
    }

    @Test
    void overview_staff_computesRatesAndWarningList() {
        loginAs("teacher");
        when(mentalAssessmentMapper.selectRecentStats()).thenReturn(List.of(
                Map.of("level", "正常", "count", 6),
                Map.of("level", "中度", "count", 2),
                Map.of("level", "高危", "count", 2)));
        List<Map<String, Object>> warnings = List.of(Map.of("name", "张三", "level", "高危"));
        when(mentalAssessmentMapper.selectWarningList()).thenReturn(warnings);

        Result<Map<String, Object>> r = controller.overview(AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData())
                .containsEntry("goodRate", 60)
                .containsEntry("attentionRate", 20)
                .containsEntry("interventionRate", 20)
                .containsEntry("todayCompleted", 10)
                .containsEntry("warningList", warnings);
    }

    /* ---------- /analysis：唯一保留内部调用放行的端点 ---------- */

    @Test
    void analysis_internalCredential_ok() {
        // 回归保护：agent-service 画像聚合经 MentalServiceClient 带内部凭证取全校分析
        withInternalHeader(INTERNAL_TOKEN);

        Result<Map<String, Object>> r = controller.analysis(null);

        assertThat(r.getCode()).isEqualTo(200);
        verify(mentalAssessmentMapper).selectDeptDistribution();
        verify(mentalAssessmentMapper).selectGenderAnalysis();
    }

    @Test
    void analysis_wrongInternalCredential_forbidden() {
        withInternalHeader("wrong-secret-at-least-32-chars-000000");

        assertThat(controller.analysis(null).getCode()).isEqualTo(403);
        assertNoDataAccess();
    }

    @Test
    void analysis_studentTokenWithInternalHeader_stillForbidden() {
        // 带 token 即按端用户判定，同时附内部凭证头也不提权
        loginAs("student");
        withInternalHeader(INTERNAL_TOKEN);

        assertThat(controller.analysis(AUTH).getCode()).isEqualTo(403);
        assertNoDataAccess();
    }

    @Test
    void internalCredential_doesNotOpenOtherEndpoints() {
        // 最小权限：内部凭证读不到预警名单 / 完成情况 / 问卷，也改不了问卷
        withInternalHeader(INTERNAL_TOKEN);

        assertThat(controller.overview(null).getCode()).isEqualTo(403);
        assertThat(controller.listQuestionnaires(1, 10, null).getCode()).isEqualTo(403);
        assertThat(controller.completion(1L, null).getCode()).isEqualTo(403);
        assertThat(writeCodes(null)).containsOnly(403);
        assertNoDataAccess();
    }

    /* ---------- 写：限 admin / psychologist ---------- */

    @Test
    void writes_student_forbidden() {
        loginAs("student");

        assertThat(writeCodes(AUTH)).containsOnly(403);
        assertNoDataAccess();
    }

    @Test
    void writes_teacher_forbidden() {
        // teacher 能看问卷，但不能改计分规则（改规则 = 改所有学生的风险判定）
        loginAs("teacher");

        assertThat(writeCodes(AUTH)).containsOnly(403);
        assertNoDataAccess();
    }

    @Test
    void writes_anonymous_forbidden() {
        assertThat(writeCodes(null)).containsOnly(403);
        assertNoDataAccess();
    }

    @Test
    void writes_psychologist_ok() {
        loginAs("psychologist");
        Questionnaire questionnaire = new Questionnaire();
        Question question = new Question();
        when(questionService.save(question)).thenReturn(question);

        assertThat(controller.saveQuestionnaire(questionnaire, AUTH).getCode()).isEqualTo(200);
        assertThat(controller.updateQuestionnaire(5L, questionnaire, AUTH).getCode()).isEqualTo(200);
        assertThat(controller.deleteQuestionnaire(5L, AUTH).getCode()).isEqualTo(200);
        assertThat(controller.addQuestion(5L, question, AUTH).getCode()).isEqualTo(200);
        assertThat(controller.updateQuestion(8L, question, AUTH).getCode()).isEqualTo(200);
        assertThat(controller.deleteQuestion(8L, AUTH).getCode()).isEqualTo(200);

        verify(questionnaireService).save(questionnaire);
        verify(questionnaireService).update(questionnaire);
        verify(questionnaireService).delete(5L);
        verify(questionService).save(question);
        verify(questionService).update(question);
        verify(questionService).delete(8L);
        assertThat(questionnaire.getId()).isEqualTo(5L);
        assertThat(question.getQuestionnaireId()).isEqualTo(5L);
        assertThat(question.getId()).isEqualTo(8L);
    }

    @Test
    void writes_admin_ok() {
        loginAs("admin");

        assertThat(writeCodes(AUTH)).containsOnly(200);
    }

    /* ---------- 完成情况：原始分按 §4 EXTREME 收口 ---------- */

    @Test
    void completion_teacher_scoreNulled_restKept() {
        loginAs("teacher");
        when(assessmentService.listCompletion(1L)).thenReturn(completionRows());

        Result<List<Map<String, Object>>> r = controller.completion(1L, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        Map<String, Object> row = r.getData().get(0);
        assertThat(row).containsKey("score");
        assertThat(row.get("score")).isNull();
        assertThat(row).containsEntry("level", "重度").containsEntry("name", "张三");
    }

    @Test
    void completion_psychologist_scoreKept() {
        loginAs("psychologist");
        when(assessmentService.listCompletion(1L)).thenReturn(completionRows());

        Result<List<Map<String, Object>>> r = controller.completion(1L, AUTH);

        assertThat(r.getData().get(0)).containsEntry("score", 27);
    }

    /** MyBatis 返回可变 HashMap 行；用可变结构才测得到置 null。 */
    private static List<Map<String, Object>> completionRows() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "张三");
        row.put("level", "重度");
        row.put("score", 27);
        return List.of(row);
    }
}
