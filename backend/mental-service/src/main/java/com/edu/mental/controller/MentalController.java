package com.edu.mental.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;
import com.edu.mental.entity.Questionnaire;
import com.edu.mental.mapper.MentalAssessmentMapper;
import com.edu.mental.service.MentalAssessmentService;
import com.edu.mental.service.QuestionService;
import com.edu.mental.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 心理管理侧接口：概览（含预警名单）、分析报告、问卷 / 题目 CRUD、完成情况。学生作答走 {@link StudentMentalController}。
 *
 * <p><b>纵向越权修复（MENTAL-AUTHZ-20260914）</b>：网关与服务入口只保证「已登录或内部调用」，此前任何登录用户
 * （含学生）都能读预警名单、增删改问卷。现按 docs/educare/FIELD_PERMISSION.md §4 分级：
 * 读限 {@link Roles#STAFF_VIEW}，写限 {@link Roles#MENTAL_WRITE}，完成情况里的原始分仅 {@link Roles#EXTREME_VIEW} 可见。
 *
 * <p>内部凭证只对 {@code /analysis} 生效（agent-service {@code MentalServiceClient} 是唯一内部调用方）；其余端点用
 * {@link AccessGuard#hasAnyRole}，不带 token 一律 403 —— 共享内部凭证泄露时也读不到预警名单、改不了问卷。
 */
@RestController
@RequestMapping("/mental")
@RequiredArgsConstructor
public class MentalController {

    private static final String VIEW_DENIED = "无权访问心理管理数据";
    private static final String WRITE_DENIED = "仅管理员或心理咨询师可修改问卷";
    /** 完成情况里的量表原始分（§3 EXTREME）。 */
    private static final String SCORE_FIELD = "score";

    private final QuestionnaireService questionnaireService;
    private final QuestionService questionService;
    private final MentalAssessmentService assessmentService;
    private final MentalAssessmentMapper mentalAssessmentMapper;
    private final AccessGuard accessGuard;

    /* ========== 心理概览 / 分析报告（保留原有逻辑） ========== */

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 预警名单是「学生姓名 + 心理等级」，没有内部调用方，不开内部凭证
        if (!accessGuard.hasAnyRole(authHeader, Roles.STAFF_VIEW)) {
            return Result.error(403, VIEW_DENIED);
        }
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> stats = mentalAssessmentMapper.selectRecentStats();
        result.put("recentStats", stats);

        int goodCount = 0, attentionCount = 0, interventionCount = 0, totalCount = 0;
        for (Map<String, Object> stat : stats) {
            String level = (String) stat.get("level");
            int count = ((Number) stat.get("count")).intValue();
            totalCount += count;
            if ("正常".equals(level) || "轻度".equals(level)) goodCount += count;
            else if ("中度".equals(level)) attentionCount += count;
            else if ("重度".equals(level) || "高危".equals(level)) interventionCount += count;
        }
        int goodRate = totalCount > 0 ? (goodCount * 100 / totalCount) : 0;
        int attentionRate = totalCount > 0 ? (attentionCount * 100 / totalCount) : 0;
        int interventionRate = totalCount > 0 ? (interventionCount * 100 / totalCount) : 0;
        result.put("goodRate", goodRate);
        result.put("attentionRate", attentionRate);
        result.put("interventionRate", interventionRate);
        result.put("todayCompleted", totalCount);
        result.put("warningList", mentalAssessmentMapper.selectWarningList());
        result.put("trendData", mentalAssessmentMapper.selectTrendData());
        return Result.success(result);
    }

    @GetMapping("/analysis")
    public Result<Map<String, Object>> analysis(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 只含聚合统计；agent-service 画像聚合带内部凭证来取，这是本类唯一保留内部调用放行的端点
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, VIEW_DENIED);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("deptDistribution", mentalAssessmentMapper.selectDeptDistribution());
        result.put("gradeComparison", mentalAssessmentMapper.selectGradeComparison());
        result.put("focusGroups", mentalAssessmentMapper.selectFocusGroups());
        result.put("genderAnalysis", mentalAssessmentMapper.selectGenderAnalysis());
        return Result.success(result);
    }

    /* ========== 问卷元数据 CRUD（读：教职工；写：admin/psychologist） ========== */

    @GetMapping("/questionnaires")
    public Result<Page<Questionnaire>> listQuestionnaires(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.STAFF_VIEW)) {
            return Result.error(403, VIEW_DENIED);
        }
        return Result.success(questionnaireService.list(page, size));
    }

    @GetMapping("/questionnaires/{id}")
    public Result<Questionnaire> getQuestionnaire(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.STAFF_VIEW)) {
            return Result.error(403, VIEW_DENIED);
        }
        return Result.success(questionnaireService.getById(id));
    }

    @PostMapping("/questionnaires")
    public Result<Void> saveQuestionnaire(
            @RequestBody Questionnaire questionnaire,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.MENTAL_WRITE)) {
            return Result.error(403, WRITE_DENIED);
        }
        questionnaireService.save(questionnaire);
        return Result.success();
    }

    @PutMapping("/questionnaires/{id}")
    public Result<Void> updateQuestionnaire(
            @PathVariable Long id,
            @RequestBody Questionnaire questionnaire,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 等级规则（levelRules）也经此保存
        if (!accessGuard.hasAnyRole(authHeader, Roles.MENTAL_WRITE)) {
            return Result.error(403, WRITE_DENIED);
        }
        questionnaire.setId(id);
        questionnaireService.update(questionnaire);
        return Result.success();
    }

    @DeleteMapping("/questionnaires/{id}")
    public Result<Void> deleteQuestionnaire(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.MENTAL_WRITE)) {
            return Result.error(403, WRITE_DENIED);
        }
        questionnaireService.delete(id);
        return Result.success();
    }

    /* ========== 问卷完整内容（含题目+等级规则）：设计/预览复用 ========== */

    @GetMapping("/questionnaires/{id}/full")
    public Result<QuestionnaireFullDto> getFull(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 含计分答案（选项分值、等级阈值），学生作答走去掉这些的 /mental/student/questionnaires/{id}
        if (!accessGuard.hasAnyRole(authHeader, Roles.STAFF_VIEW)) {
            return Result.error(403, VIEW_DENIED);
        }
        return Result.success(questionService.getFull(id));
    }

    /* ========== 题目 CRUD（读：教职工；写：admin/psychologist） ========== */

    @GetMapping("/questionnaires/{id}/questions")
    public Result<List<Question>> listQuestions(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.STAFF_VIEW)) {
            return Result.error(403, VIEW_DENIED);
        }
        return Result.success(questionService.listByQuestionnaire(id));
    }

    @PostMapping("/questionnaires/{id}/questions")
    public Result<Question> addQuestion(
            @PathVariable Long id,
            @RequestBody Question question,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.MENTAL_WRITE)) {
            return Result.error(403, WRITE_DENIED);
        }
        question.setQuestionnaireId(id);
        return Result.success(questionService.save(question));
    }

    @PutMapping("/questions/{questionId}")
    public Result<Void> updateQuestion(
            @PathVariable Long questionId,
            @RequestBody Question question,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.MENTAL_WRITE)) {
            return Result.error(403, WRITE_DENIED);
        }
        question.setId(questionId);
        questionService.update(question);
        return Result.success();
    }

    @DeleteMapping("/questions/{questionId}")
    public Result<Void> deleteQuestion(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.MENTAL_WRITE)) {
            return Result.error(403, WRITE_DENIED);
        }
        questionService.delete(questionId);
        return Result.success();
    }

    /* ========== 完成情况（教职工看哪些学生答了；原始分仅 admin/psychologist） ========== */

    @GetMapping("/questionnaires/{id}/completion")
    public Result<List<Map<String, Object>>> completion(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.STAFF_VIEW)) {
            return Result.error(403, VIEW_DENIED);
        }
        List<Map<String, Object>> rows = assessmentService.listCompletion(id);
        if (!accessGuard.hasAnyRole(authHeader, Roles.EXTREME_VIEW)) {
            // 行是 Map，不带 @SensitiveField，FieldPermissionAdvice 管不到；按 §4 把 EXTREME 的原始分置 null
            rows.forEach(row -> row.put(SCORE_FIELD, null));
        }
        return Result.success(rows);
    }
}
