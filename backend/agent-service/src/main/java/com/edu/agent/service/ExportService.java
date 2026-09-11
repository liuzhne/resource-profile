package com.edu.agent.service;

import com.edu.agent.entity.AgentExportTask;
import org.springframework.core.io.Resource;

public interface ExportService {

    /**
     * 创建一个 PDF 导出任务并提交异步渲染。
     *
     * @return 新建的 jobId
     */
    Long createExportJob(Long taskId, Long userId);

    /**
     * 异步渲染入口。由 createExportJob 内部触发，public 仅为 @Async 代理需要。
     */
    void renderPdfAsync(Long jobId);

    /**
     * 查询导出任务状态（前端轮询用）。
     */
    AgentExportTask getJobStatus(Long jobId);

    /**
     * 加载已完成的 PDF 文件资源（下载端点用）。
     * 任务非 DONE 或文件丢失时抛 IllegalStateException。
     */
    Resource loadFile(Long jobId);
}
