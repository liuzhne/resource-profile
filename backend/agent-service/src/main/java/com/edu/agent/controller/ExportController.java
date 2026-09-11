package com.edu.agent.controller;

import com.edu.agent.entity.AgentExportTask;
import com.edu.agent.service.ExportService;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * F-1：PDF 报告异步导出。
 * 三段式：触发 / 状态 / 下载。所有端点走 /agent/api/v1 与现有 task controller 同命名空间。
 *
 * <p><b>越权(IDOR)修复</b>：报告是教职工面向的 AI 分析产物，学生不应访问。
 * 经 {@link AccessGuard#allowSelfRoleOrInternal} 限教职工角色或内网直调；否则带 token 的端用户一律 403，
 * 堵住「任意登录者猜 {@code jobId} 即下载他人学生干预报告」。
 */
@Slf4j
@RestController
@RequestMapping("/agent/api/v1")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final AccessGuard accessGuard;

    /**
     * Creates an asynchronous PDF export job for the specified task.
     *
     * @param taskId     the ID of the task to export
     * @param authHeader optional `Authorization` header (Bearer token); the method extracts the JWT subject as the user ID and uses `0` when the header is missing or invalid
     * @return a `Result` containing a map with key `"jobId"` and the created export job ID on success; returns a `Result.error` with an error message (404 when the task is not found) on failure
     */
    @PostMapping("/task/{taskId}/export")
    public Result<Map<String, Long>> createExportJob(
            @PathVariable Long taskId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权导出报告");
        }
        Long userId = accessGuard.currentUserId(authHeader);
        try {
            Long jobId = exportService.createExportJob(taskId, userId == null ? 0L : userId);
            return Result.success(Map.of("jobId", jobId));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("F-1：创建导出任务失败 taskId={}", taskId, e);
            return Result.error("创建导出任务失败：" + e.getMessage());
        }
    }

    /**
     * Retrieve the current status and details of an export job.
     *
     * @param jobId the identifier of the export job
     * @return a Result containing the AgentExportTask when found, or an error Result with status 404 if the job does not exist
     */
    @GetMapping("/export/{jobId}")
    public Result<AgentExportTask> getExportStatus(
            @PathVariable Long jobId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看导出任务");
        }
        AgentExportTask job = exportService.getJobStatus(jobId);
        if (job == null) {
            return Result.error(404, "导出任务不存在");
        }
        return Result.success(job);
    }

    /**
     * Downloads the PDF file for the specified export job as an attachment.
     *
     * <p>Returns a 200 response with `Content-Type: application/pdf` and a
     * `Content-Disposition` header containing both a plain `filename` and a UTF-8
     * `filename*` when the file is available. Returns 404 if the export job does
     * not exist, or 400 with an `X-Error-Reason` header if the job is not in a
     * state that allows downloading.
     *
     * @param jobId the export job identifier
     * @return a ResponseEntity containing the PDF resource as an attachment, or a 404/400 response when unavailable
     */
    @GetMapping("/export/{jobId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long jobId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Resource resource;
        AgentExportTask job;
        try {
            job = exportService.getJobStatus(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            resource = exportService.loadFile(jobId);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Reason", e.getMessage())
                    .build();
        }

        String filename = String.format("report-%d.pdf", job.getTaskId());
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .body(resource);
    }
}
