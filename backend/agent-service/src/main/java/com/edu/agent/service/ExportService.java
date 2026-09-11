package com.edu.agent.service;

import com.edu.agent.entity.AgentExportTask;
import org.springframework.core.io.Resource;

public interface ExportService {

    /**
 * Create a PDF export job and submit it for asynchronous rendering.
 *
 * @param taskId the identifier of the task to export
 * @param userId the identifier of the user requesting the export
 * @return the identifier of the newly created export job
 */
    Long createExportJob(Long taskId, Long userId);

    /**
 * Trigger rendering of the PDF for the specified export job.
 *
 * @param jobId the identifier of the export job to render
 */
    void renderPdfAsync(Long jobId);

    /**
 * Retrieve the current status and details of an export job for polling.
 *
 * @param jobId the identifier of the export job to query
 * @return an {@link AgentExportTask} containing the job's current status and metadata
 */
    AgentExportTask getJobStatus(Long jobId);

    /**
 * Load the completed PDF file for the given export job as a Spring Resource.
 *
 * @param jobId the export job identifier
 * @return the PDF file as a Spring {@code Resource}
 * @throws IllegalStateException if the job is not in the DONE state or the file cannot be found
 */
    Resource loadFile(Long jobId);
}
