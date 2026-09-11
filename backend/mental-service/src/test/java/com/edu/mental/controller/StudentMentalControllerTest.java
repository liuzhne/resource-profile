package com.edu.mental.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import com.edu.mental.dto.SubmitAnswerRequest;
import com.edu.mental.entity.MentalAssessment;
import com.edu.mental.service.MentalAssessmentService;
import com.edu.mental.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 学生侧心理接口越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 端用户带 token 必须本人，否则 403；内网无 token 直调放行（AI 取数链路）。
 */
class StudentMentalControllerTest {

    private static final String AUTH = "Bearer tok";

    private MentalAssessmentService assessmentService;
    private QuestionService questionService;
    private JwtUtil jwtUtil;
    private StudentMentalController controller;

    @BeforeEach
    void setUp() {
        assessmentService = mock(MentalAssessmentService.class);
        questionService = mock(QuestionService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new StudentMentalController(assessmentService, questionService, new AccessGuard(jwtUtil));
    }

    @Test
    void myHistory_self_ok() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        when(assessmentService.myHistory(7L)).thenReturn(List.of(new MentalAssessment()));

        Result<List<MentalAssessment>> r = controller.myHistory(7L, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        verify(assessmentService).myHistory(7L);
    }

    @Test
    void myHistory_otherUser_forbidden() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");

        Result<List<MentalAssessment>> r = controller.myHistory(7L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verifyNoInteractions(assessmentService);
    }

    @Test
    void myHistory_internalNoToken_ok() {
        // 内网 Feign 匿名直调（无 Authorization）→ 放行（回归保护）
        when(assessmentService.myHistory(7L)).thenReturn(List.of(new MentalAssessment()));

        Result<List<MentalAssessment>> r = controller.myHistory(7L, null);

        assertThat(r.getCode()).isEqualTo(200);
        verify(assessmentService).myHistory(7L);
    }

    @Test
    void myQuestionnaires_otherUser_forbidden() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");

        Result<?> r = controller.myQuestionnaires(7L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verifyNoInteractions(assessmentService);
    }

    @Test
    void myDetail_otherUser_forbidden() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");

        Result<?> r = controller.myDetail(7L, 100L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verifyNoInteractions(assessmentService);
    }

    @Test
    void submit_asOtherUser_forbidden() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setUserId(7L);

        Result<MentalAssessment> r = controller.submit(req, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verifyNoInteractions(assessmentService);
    }

    @Test
    void submit_asSelf_ok() {
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setUserId(7L);
        when(assessmentService.submit(any())).thenReturn(new MentalAssessment());

        Result<MentalAssessment> r = controller.submit(req, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        verify(assessmentService).submit(req);
    }
}
