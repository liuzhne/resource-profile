package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.agent.entity.AgentExportTask;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.mapper.AgentExportTaskMapper;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.service.ExportService;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final AgentExportTaskMapper exportMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final TemplateEngine templateEngine;
    private final ResourceLoader resourceLoader;
    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    @Value("${educare.export.storage-path:/tmp/edu-exports}")
    private String storagePath;

    @Value("${educare.export.font-path:classpath:/fonts/NotoSansSC-Regular.ttf}")
    private String fontPath;

    /**
     * 字体在 classpath 内时不能直接被 PDFBox FontFactory 读取（jar 内路径无法 File 化），
     * 所以服务启动时把字体物化到磁盘缓存目录，渲染时直接喂 File。
     */
    private File cachedFontFile;

    /**
     * Initializes the export service by ensuring the configured storage directory exists and
     * materializing the configured font into a cached file used for PDF rendering.
     *
     * This method sets the {@code cachedFontFile} field to the materialized font file or
     * {@code null} when the configured font resource is not available.
     *
     * @throws IOException if creating the storage directory or writing the cached font file fails
     */
    @PostConstruct
    public void init() throws IOException {
        Path dir = Paths.get(storagePath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.info("F-1：创建 PDF 导出目录 {}", dir);
        }
        cachedFontFile = materializeFont();
        if (cachedFontFile == null) {
            log.warn("F-1：未找到中文字体 {}，PDF 中文将显示为方块。请按 deploy.md 下载字体。", fontPath);
        } else {
            log.info("F-1：中文字体加载就绪 {}", cachedFontFile.getAbsolutePath());
        }
    }

    /**
     * Ensures the configured font resource is materialized to the service storage and returns the cached font file.
     *
     * Copies the font located at the configured `fontPath` into `storagePath/.fonts/NotoSansSC-Regular.ttf` if it does not already exist or is empty. Creates parent directories as needed. Returns `null` when the font resource is absent or cannot be read.
     *
     * @return the cached font File pointing to `storagePath/.fonts/NotoSansSC-Regular.ttf`, or `null` if the resource does not exist or could not be materialized
     */
    private File materializeFont() {
        try {
            Resource res = resourceLoader.getResource(fontPath);
            if (!res.exists()) {
                return null;
            }
            File cache = new File(storagePath, ".fonts/NotoSansSC-Regular.ttf");
            if (cache.exists() && cache.length() > 0) {
                return cache;
            }
            cache.getParentFile().mkdirs();
            try (InputStream in = res.getInputStream()) {
                Files.copy(in, cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return cache;
        } catch (IOException e) {
            log.warn("字体物化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new export job for the specified AgentTask and schedules PDF rendering.
     *
     * The method persists an AgentExportTask with pending status and submits a background task
     * to perform the PDF rendering.
     *
     * @param taskId the id of the AgentTask to export
     * @param userId the id of the requesting user; when null, treated as 0L
     * @return the id of the created export job
     * @throws IllegalArgumentException if no AgentTask exists with the given taskId
     */
    @Override
    public Long createExportJob(Long taskId, Long userId) {
        AgentTask source = agentTaskMapper.selectById(taskId);
        if (source == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        AgentExportTask job = new AgentExportTask();
        job.setTaskId(taskId);
        job.setUserId(userId == null ? 0L : userId);
        job.setStatus(AgentExportTask.STATUS_PENDING);
        exportMapper.insert(job);
        log.info("F-1：创建导出任务 jobId={} taskId={} userId={}", job.getId(), taskId, userId);

        agentExecutor.execute(() -> renderPdfAsync(job.getId()));
        return job.getId();
    }

    /**
     * Processes an export job: renders the source task to PDF and updates the job record accordingly.
     *
     * On invocation the job status is set to PROCESSING. If the source task exists, HTML is rendered
     * to PDF and the job is marked DONE with the generated file path and size; on any failure the job
     * is marked FAILED with a truncated error message. If the job record does not exist the method
     * returns without side effects.
     *
     * @param jobId the id of the AgentExportTask to render
     */
    @Override
    @Async("agentExecutor")
    public void renderPdfAsync(Long jobId) {
        AgentExportTask job = exportMapper.selectById(jobId);
        if (job == null) {
            log.warn("F-1：导出任务 {} 不存在", jobId);
            return;
        }

        markStatus(jobId, AgentExportTask.STATUS_PROCESSING, null, null);

        try {
            AgentTask source = agentTaskMapper.selectById(job.getTaskId());
            if (source == null) {
                throw new IllegalStateException("源任务已删除: " + job.getTaskId());
            }

            String html = renderHtml(source);
            File pdf = renderPdf(html, jobId, job.getTaskId());
            markDone(jobId, pdf.getAbsolutePath(), pdf.length());
            log.info("F-1：jobId={} 渲染完成，{} bytes", jobId, pdf.length());
        } catch (Exception e) {
            log.error("F-1：jobId={} 渲染失败", jobId, e);
            markFailed(jobId, truncate(e.getMessage(), 500));
        }
    }

    /**
     * Fetches the export job record for the given job identifier.
     *
     * @param jobId the export job id
     * @return the corresponding {@code AgentExportTask}, or {@code null} if no such job exists
     */
    @Override
    public AgentExportTask getJobStatus(Long jobId) {
        return exportMapper.selectById(jobId);
    }

    /**
     * Load the completed PDF file for the given export job as a Spring Resource.
     *
     * @param jobId the export job id
     * @return a Resource pointing to the exported PDF file
     * @throws IllegalStateException if the export job does not exist, is not finished, or the PDF file is missing
     */
    @Override
    public Resource loadFile(Long jobId) {
        AgentExportTask job = exportMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalStateException("导出任务不存在: " + jobId);
        }
        if (!AgentExportTask.STATUS_DONE.equals(job.getStatus())) {
            throw new IllegalStateException("任务未完成: status=" + job.getStatus());
        }
        File file = new File(job.getFilePath());
        if (!file.exists()) {
            throw new IllegalStateException("PDF 文件丢失: " + job.getFilePath());
        }
        return new FileSystemResource(file);
    }

    /**
     * Render an HTML report for the given AgentTask using the "report" Thymeleaf template.
     *
     * @param source the AgentTask whose data populates the template
     * @return the rendered HTML string for the report
     */

    private String renderHtml(AgentTask source) {
        Context ctx = new Context();
        ctx.setVariable("task", source);
        ctx.setVariable("risk", parseObj(source.getRiskAnalysisResult()));
        ctx.setVariable("knowledgeChunks", parseChunks(source.getRetrievedKnowledge()));
        ctx.setVariable("plan", parseObj(source.getInterventionPlan()));
        ctx.setVariable("audit", parseObj(source.getComplianceAudit()));
        ctx.setVariable("auditItems", parseArr(source.getComplianceAudit(), "audit_items"));
        ctx.setVariable("immediateActions", parseArr(source.getInterventionPlan(), "immediate_actions"));
        ctx.setVariable("longTermPlan", parseArr(source.getInterventionPlan(), "long_term_plan"));
        ctx.setVariable("resources", parseArr(source.getInterventionPlan(), "resources"));
        ctx.setVariable("redactedSuggestions",
                parseStringArr(source.getComplianceAudit(), "redacted_suggestions"));
        ctx.setVariable("keyIndicators",
                parseStringArr(source.getRiskAnalysisResult(), "key_indicators"));
        ctx.setVariable("interventionTypes",
                parseStringArr(source.getRiskAnalysisResult(), "recommended_intervention_types"));
        ctx.setVariable("generatedAt", LocalDateTime.now());
        return templateEngine.process("report", ctx);
    }

    /**
     * Render the provided HTML into a PDF file stored in the service storage directory for the specified job and task.
     *
     * @param html   the HTML content to render into PDF
     * @param jobId  the export job identifier used to compose the output filename
     * @param taskId the source task identifier used to compose the output filename
     * @return       the created PDF file
     * @throws IOException if writing the output file or the PDF rendering process fails
     */
    private File renderPdf(String html, Long jobId, Long taskId) throws IOException {
        File output = new File(storagePath, String.format("report-%d-%d.pdf", taskId, jobId));
        try (FileOutputStream fos = new FileOutputStream(output)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            if (cachedFontFile != null) {
                // 必须用真 TTF（TrueType outlines）。OTF/CFF 字体在 openhtmltopdf 1.0.10 +
                // PDFBox 2.0.24 下 subset 抛 IOException、subset=false 又会静默注册失败
                // → 渲染回退默认 Latin-only 字体、CJK 全是 #。详见 deploy.md 故障表。
                builder.useFont(cachedFontFile, "NotoSansSC", 400,
                        BaseRendererBuilder.FontStyle.NORMAL, true);
            }
            builder.withHtmlContent(html, null);
            builder.toStream(fos);
            builder.run();
        }
        return output;
    }

    /**
     * Parses a JSON object string into a Map of keys to values.
     *
     * <p>Returns an empty map when the input is null, blank, or cannot be parsed as a JSON object.</p>
     *
     * @param json the JSON string expected to contain an object
     * @return the parsed map of key/value pairs, or an empty map if input is null, blank, or invalid
     */

    private Map<String, Object> parseObj(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            JSONObject o = JSON.parseObject(json);
            return o != null ? o : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * Extracts the top-level "chunks" array from a JSON string produced by the RAG stage.
     *
     * @param json JSON string expected to contain an object with a `chunks` array; may be null or blank
     * @return a list of maps representing each object in the `chunks` array; an empty list if the input is null, blank, malformed, or if `chunks` is missing
     */
    private List<Map<String, Object>> parseChunks(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            JSONObject o = JSON.parseObject(json);
            if (o == null) return new ArrayList<>();
            JSONArray arr = o.getJSONArray("chunks");
            if (arr == null) return new ArrayList<>();
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row != null) result.add(row);
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Extracts an array of JSON objects from a JSON string by field name.
     *
     * @param json  a JSON object string that contains the target array; may be null or blank
     * @param field the key whose value is expected to be a JSON array of objects
     * @return      a list of maps representing each object in the array; returns an empty list if the input is null/blank,
     *              the field is missing or not an array, or a parse error occurs
     */
    private List<Map<String, Object>> parseArr(String json, String field) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            JSONObject o = JSON.parseObject(json);
            if (o == null) return new ArrayList<>();
            JSONArray arr = o.getJSONArray(field);
            if (arr == null) return new ArrayList<>();
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row != null) result.add(row);
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Extracts the values of the specified array field from a JSON object as a list of strings.
     *
     * @param json  the JSON string representing an object that may contain the array field
     * @param field the name of the array field to extract
     * @return a list of string representations of the array elements; returns an empty list if the input is null/blank, the field is missing, not an array, or parsing fails (null elements are skipped)
     */
    private List<String> parseStringArr(String json, String field) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            JSONObject o = JSON.parseObject(json);
            if (o == null) return new ArrayList<>();
            JSONArray arr = o.getJSONArray(field);
            if (arr == null) return new ArrayList<>();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                Object v = arr.get(i);
                if (v != null) result.add(v.toString());
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Update an AgentExportTask record's status and optionally its file path or error message.
     *
     * Sets the task identified by `jobId` to `status`. If `filePath` is non-null, updates the task's
     * file path; if `errMsg` is non-null, updates the task's error message. The changes are persisted
     * to the database.
     *
     * @param jobId    the id of the export job to update
     * @param status   the new status value to set on the job
     * @param filePath the output PDF file path to set, or `null` to leave unchanged
     * @param errMsg   the error message to set, or `null` to leave unchanged
     */

    private void markStatus(Long jobId, String status, String filePath, String errMsg) {
        LambdaUpdateWrapper<AgentExportTask> uw = new LambdaUpdateWrapper<AgentExportTask>()
                .eq(AgentExportTask::getId, jobId)
                .set(AgentExportTask::getStatus, status);
        if (filePath != null) uw.set(AgentExportTask::getFilePath, filePath);
        if (errMsg != null) uw.set(AgentExportTask::getErrMsg, errMsg);
        exportMapper.update(null, uw);
    }

    /**
     * Mark the export job as completed and record the produced file's metadata.
     *
     * Sets the job status to `STATUS_DONE`, stores the output `filePath`, the file `size` in bytes,
     * and updates the `finishedAt` timestamp to the current time.
     *
     * @param jobId    the identifier of the AgentExportTask to update
     * @param filePath the filesystem path to the generated PDF
     * @param size     the size of the generated file in bytes
     */
    private void markDone(Long jobId, String filePath, long size) {
        exportMapper.update(null, new LambdaUpdateWrapper<AgentExportTask>()
                .eq(AgentExportTask::getId, jobId)
                .set(AgentExportTask::getStatus, AgentExportTask.STATUS_DONE)
                .set(AgentExportTask::getFilePath, filePath)
                .set(AgentExportTask::getFileSize, size)
                .set(AgentExportTask::getFinishedAt, LocalDateTime.now()));
    }

    /**
     * Mark an export job as failed and record its error message and finished timestamp.
     *
     * @param jobId the id of the export job to update
     * @param errMsg the error message to store for the job (may be null)
     */
    private void markFailed(Long jobId, String errMsg) {
        exportMapper.update(null, new LambdaUpdateWrapper<AgentExportTask>()
                .eq(AgentExportTask::getId, jobId)
                .set(AgentExportTask::getStatus, AgentExportTask.STATUS_FAILED)
                .set(AgentExportTask::getErrMsg, errMsg)
                .set(AgentExportTask::getFinishedAt, LocalDateTime.now()));
    }

    /**
     * Truncates the input string to at most the specified number of characters.
     *
     * @param s   the input string, may be {@code null}
     * @param max the maximum number of characters to keep
     * @return the original string if its length is less than or equal to {@code max}, a substring of the first {@code max}
     *         characters if longer, or {@code null} if {@code s} is {@code null}
     */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
