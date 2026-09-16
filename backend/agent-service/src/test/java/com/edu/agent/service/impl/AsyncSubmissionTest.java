package com.edu.agent.service.impl;

import com.edu.agent.entity.AgentExportTask;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.mapper.AgentExportTaskMapper;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.thymeleaf.TemplateEngine;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression: submission must return without executing the model or PDF on the caller thread. */
class AsyncSubmissionTest {
    @Test
    void triggerOnlyEnqueuesAndWorkerExecutesLater() {
        List<Runnable> queue = new ArrayList<>();
        AgentTaskServiceImpl service = triggerService(queue::add, mock(AgentTaskMapper.class));
        doReturn(42L).when(service).createTask(7L);
        doNothing().when(service).asyncExecute(42L);
        assertEquals(42L, service.triggerTask(7L));
        verify(service, never()).asyncExecute(anyLong());
        assertEquals(1, queue.size());
        queue.get(0).run();
        verify(service).asyncExecute(42L);
    }

    @Test
    void rejectedTriggerFailsPersistedTaskInsteadOfExecutingOnCaller() {
        AgentTaskMapper mapper = mock(AgentTaskMapper.class);
        AgentTaskServiceImpl service = triggerService(task -> { throw new RejectedExecutionException(); }, mapper);
        doReturn(42L).when(service).createTask(7L);
        assertThrows(BusinessException.class, () -> service.triggerTask(7L));
        verify(mapper).updateById(argThat((AgentTask task) -> task.getId() == 42L && task.getStatus() == TaskStatus.FAILED));
        verify(service, never()).asyncExecute(anyLong());
    }

    @SuppressWarnings("unchecked")
    private AgentTaskServiceImpl triggerService(Executor executor, AgentTaskMapper mapper) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        return spy(new AgentTaskServiceImpl(mapper, null, null, null, redis, null, null, null, null, null, executor));
    }

    @Test
    void pdfSubmissionOnlyEnqueues() {
        AgentTaskMapper source = mock(AgentTaskMapper.class);
        AgentExportTaskMapper jobs = mock(AgentExportTaskMapper.class);
        when(source.selectById(7L)).thenReturn(new AgentTask());
        doAnswer(invocation -> { ((AgentExportTask) invocation.getArgument(0)).setId(99L); return 1; }).when(jobs).insert(any(AgentExportTask.class));
        List<Runnable> queue = new ArrayList<>();
        ExportServiceImpl service = spy(new ExportServiceImpl(jobs, source, mock(TemplateEngine.class), mock(ResourceLoader.class), queue::add));
        doNothing().when(service).renderPdfAsync(99L);
        assertEquals(99L, service.createExportJob(7L, 1L));
        verify(service, never()).renderPdfAsync(anyLong());
        queue.get(0).run();
        verify(service).renderPdfAsync(99L);
    }
}
