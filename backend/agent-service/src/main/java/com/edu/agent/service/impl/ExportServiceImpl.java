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

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final AgentExportTaskMapper exportMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final TemplateEngine templateEngine;
    private final ResourceLoader resourceLoader;

    @Value("${educare.export.storage-path:/tmp/edu-exports}")
    private String storagePath;

    @Value("${educare.export.font-path:classpath:/fonts/NotoSansSC-Regular.ttf}")
    private String fontPath;

    /**
     * 字体在 classpath 内时不能直接被 PDFBox FontFactory 读取（jar 内路径无法 File 化），
     * 所以服务启动时把字体物化到磁盘缓存目录，渲染时直接喂 File。
     */
    private File cachedFontFile;

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

        renderPdfAsync(job.getId());
        return job.getId();
    }

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

    @Override
    public AgentExportTask getJobStatus(Long jobId) {
        return exportMapper.selectById(jobId);
    }

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

    // ==================== 渲染细节 ====================

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

    // ==================== JSON 解析辅助 ====================

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
     * RAG 阶段的产物结构：{"chunks":[...], "reranked":bool, "fallback":bool}
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

    // ==================== 状态变更 ====================

    private void markStatus(Long jobId, String status, String filePath, String errMsg) {
        LambdaUpdateWrapper<AgentExportTask> uw = new LambdaUpdateWrapper<AgentExportTask>()
                .eq(AgentExportTask::getId, jobId)
                .set(AgentExportTask::getStatus, status);
        if (filePath != null) uw.set(AgentExportTask::getFilePath, filePath);
        if (errMsg != null) uw.set(AgentExportTask::getErrMsg, errMsg);
        exportMapper.update(null, uw);
    }

    private void markDone(Long jobId, String filePath, long size) {
        exportMapper.update(null, new LambdaUpdateWrapper<AgentExportTask>()
                .eq(AgentExportTask::getId, jobId)
                .set(AgentExportTask::getStatus, AgentExportTask.STATUS_DONE)
                .set(AgentExportTask::getFilePath, filePath)
                .set(AgentExportTask::getFileSize, size)
                .set(AgentExportTask::getFinishedAt, LocalDateTime.now()));
    }

    private void markFailed(Long jobId, String errMsg) {
        exportMapper.update(null, new LambdaUpdateWrapper<AgentExportTask>()
                .eq(AgentExportTask::getId, jobId)
                .set(AgentExportTask::getStatus, AgentExportTask.STATUS_FAILED)
                .set(AgentExportTask::getErrMsg, errMsg)
                .set(AgentExportTask::getFinishedAt, LocalDateTime.now()));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
