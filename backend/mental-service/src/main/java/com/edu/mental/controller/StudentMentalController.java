package com.edu.mental.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.dto.SubmitAnswerRequest;
import com.edu.mental.entity.MentalAssessment;
import com.edu.mental.service.MentalAssessmentService;
import com.edu.mental.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学生侧问卷接口：仅允许查看/提交「本人」数据。
 *
 * <p><b>越权(IDOR)修复</b>：经 {@link AccessGuard#allowSelfRoleOrInternal} 判定 ——
 * 端用户（带 token）的入参 {@code userId} 必须等于 JWT subject，否则 403，堵住
 * 「登录后 {@code ?userId=他人} 直读他人心理测评史」；mcp-student-data 的内部 Feign 调用
 * （不带 token、出示 {@code X-Internal-Token}）放行，保证 AI 画像取数链路不被打断。
 *
 * <p><b>作答视图（MENTAL-AUTHZ-20260914）</b>：{@code GET /questionnaires/{id}} 没有内部调用方，只认合法 JWT；
 * 返回去掉计分答案（选项分值、等级规则）的作答视图，且不下发未开始的问卷。教职工预览完整问卷走
 * {@code GET /mental/questionnaires/{id}/full}。
 *
 * <p>教师/咨询师/管理员查看他人走管理侧端点（{@code MentalController} 等），不复用本「学生侧」接口，
 * 故此处不放开特权角色（仅本人或内部）。
 */
@RestController
@RequestMapping("/mental/student")
@RequiredArgsConstructor
public class StudentMentalController {

    private final MentalAssessmentService assessmentService;
    private final QuestionService questionService;
    private final AccessGuard accessGuard;

    @GetMapping("/questionnaires")
    public Result<List<Map<String, Object>>> myQuestionnaires(
            @RequestParam Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, userId)) {
            return Result.error(403, "无权访问他人数据");
        }
        return Result.success(assessmentService.listForStudent(userId));
    }

    @GetMapping("/questionnaires/{id}")
    public Result<QuestionnaireFullDto> getForTaking(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 问卷模板非个人数据，任何已登录用户可取；但没有内部调用方，内部凭证不能代替登录
        if (accessGuard.currentUserId(authHeader) == null) {
            return Result.error(403, "请登录后作答");
        }
        return Result.success(questionService.getForRespondent(id));
    }

    @PostMapping("/assessments")
    public Result<MentalAssessment> submit(
            @RequestBody SubmitAnswerRequest req,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (req == null || !accessGuard.allowSelfRoleOrInternal(authHeader, req.getUserId())) {
            return Result.error(403, "无权以他人身份提交测评");
        }
        return Result.success(assessmentService.submit(req));
    }

    @GetMapping("/assessments")
    public Result<List<MentalAssessment>> myHistory(
            @RequestParam Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, userId)) {
            return Result.error(403, "无权访问他人心理测评数据");
        }
        return Result.success(assessmentService.myHistory(userId));
    }

    @GetMapping("/assessments/{assessmentId}")
    public Result<Map<String, Object>> myDetail(
            @RequestParam Long userId,
            @PathVariable Long assessmentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, userId)) {
            return Result.error(403, "无权访问他人心理测评数据");
        }
        return Result.success(assessmentService.getMyAssessmentDetail(userId, assessmentId));
    }
}
