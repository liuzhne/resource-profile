package com.edu.agent.controller;

import com.edu.agent.dto.FeedbackRequest;
import com.edu.agent.entity.InterventionFeedback;
import com.edu.agent.service.InterventionFeedbackService;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * I-5.2 / I-5.4：干预反馈闭环。
 * - POST /agent/api/v1/intervention/feedback          提交反馈
 * - GET  /agent/api/v1/intervention/feedback/report    月度明细（JSON）
 * - GET  /agent/api/v1/intervention/feedback/report.csv 月度导出（CSV 下载）
 *
 * <p><b>越权(IDOR)修复</b>：干预反馈由教职工填写/查看，经 {@link AccessGuard#allowSelfRoleOrInternal}
 * 限教职工角色或内网直调，否则带 token 的端用户 403。
 */
@Slf4j
@RestController
@RequestMapping("/agent/api/v1/intervention")
@RequiredArgsConstructor
public class InterventionFeedbackController {

    private final InterventionFeedbackService feedbackService;
    private final AccessGuard accessGuard;

    @PostMapping("/feedback")
    public Result<Long> submit(
            @RequestBody FeedbackRequest req,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权提交干预反馈");
        }
        Long userId = accessGuard.currentUserId(authHeader);
        try {
            Long id = feedbackService.submit(req, userId == null ? 0L : userId);
            return Result.success(id);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("I-5：提交干预反馈失败", e);
            return Result.error("提交反馈失败：" + e.getMessage());
        }
    }

    @GetMapping("/feedback/report")
    public Result<List<InterventionFeedback>> report(
            @RequestParam(required = false) String month,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看干预反馈报表");
        }
        try {
            return Result.success(feedbackService.monthlyReport(month));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/feedback/report.csv")
    public ResponseEntity<byte[]> reportCsv(
            @RequestParam(required = false) String month,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return ResponseEntity.status(403).build();
        }
        String csv;
        try {
            csv = feedbackService.exportCsv(month);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().header("X-Error-Reason", e.getMessage()).build();
        }
        // 带 UTF-8 BOM，Excel 打开中文不乱码
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        String filename = "intervention-feedback-" + (month == null ? "current" : month) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
