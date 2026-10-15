package com.edu.agent.service.impl;

import com.edu.agent.entity.AgentExportTask;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.mapper.AgentExportTaskMapper;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ResourceLoader;
import org.thymeleaf.TemplateEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Endpoint-independent admission gate: no model calls, PDF files or production data. */
class ExportAdmissionTest {
    private final AgentTaskMapper tasks = mock(AgentTaskMapper.class);
    private final AgentExportTaskMapper jobs = mock(AgentExportTaskMapper.class);
    private final Executor executor = mock(Executor.class);

    private ExportServiceImpl service(Executor target) {
        return new ExportServiceImpl(jobs, tasks, mock(TemplateEngine.class), mock(ResourceLoader.class), target);
    }

    private AgentTask source(TaskStatus status, int deleted) {
        AgentTask task = new AgentTask();
        task.setId(7L); task.setStudentId(17L); task.setStatus(status); task.setDeleted(deleted);
        when(tasks.selectById(7L)).thenReturn(task);
        return task;
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "REJECTED"}, mode = EnumSource.Mode.EXCLUDE)
    void activeOrFailedSourceCannotCreateJobOrEnqueuePdf(TaskStatus status) {
        source(status, 0);
        BusinessException error = assertThrows(BusinessException.class, () -> service(executor).createExportJob(7L, 1L));
        assertEquals(409, error.getCode());
        verifyNoInteractions(jobs, executor);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "REJECTED"})
    void deletedSourceCannotCreateJobEvenWhenItsStatusIsExportable(TaskStatus status) {
        source(status, 1);
        BusinessException error = assertThrows(BusinessException.class, () -> service(executor).createExportJob(7L, 1L));
        assertEquals(409, error.getCode());
        verifyNoInteractions(jobs, executor);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "REJECTED"})
    void terminalSourceCreatesExactlyOnePendingJobAndEnqueuesWithoutRendering(TaskStatus status) {
        source(status, 0);
        List<Runnable> queue = new ArrayList<>();
        doAnswer(call -> {
            AgentExportTask job = call.getArgument(0);
            assertEquals(7L, job.getTaskId()); assertEquals(1L, job.getUserId());
            assertEquals(AgentExportTask.STATUS_PENDING, job.getStatus());
            job.setId(88L);
            return 1;
        }).when(jobs).insert(any(AgentExportTask.class));
        assertEquals(88L, service(queue::add).createExportJob(7L, 1L));
        assertEquals(1, queue.size());
        verify(jobs).insert(any(AgentExportTask.class));
        verify(jobs, never()).selectById(anyLong());
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void absentSourceCannotCreateJobOrStartRendering() {
        when(tasks.selectById(7L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service(executor).createExportJob(7L, 1L));
        verifyNoInteractions(jobs, executor);
    }

    @Test
    void unknownStatusCannotCreateJob() {
        source(null, 0);
        assertThrows(BusinessException.class, () -> service(executor).createExportJob(7L, 1L));
        verifyNoInteractions(jobs, executor);
    }
}
