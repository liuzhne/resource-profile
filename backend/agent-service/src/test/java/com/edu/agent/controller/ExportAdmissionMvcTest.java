package com.edu.agent.controller;

import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.mapper.AgentExportTaskMapper;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.service.ExportService;
import com.edu.agent.service.impl.ExportServiceImpl;
import com.edu.common.config.JwtProperties;
import com.edu.common.exception.BusinessException;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.InternalCallCredential;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.TemplateEngine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC + service admission: denied requests never insert jobs, enqueue work or render PDFs. */
class ExportAdmissionMvcTest {
    private static final String PATH = "/agent/api/v1/task/7/export";
    private final AgentTaskMapper tasks = mock(AgentTaskMapper.class);
    private final AgentExportTaskMapper jobs = mock(AgentExportTaskMapper.class);
    private final Executor executor = mock(Executor.class);
    private JwtUtil jwt;
    private AccessGuard guard;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-export-key-at-least-32-bytes");
        jwt = new JwtUtil(properties);
        jwt.init();
        guard = new AccessGuard(jwt, new InternalCallCredential("test-only-internal-key-at-least-32-bytes"));
        ExportService service = new ExportServiceImpl(jobs, tasks, mock(TemplateEngine.class),
                mock(ResourceLoader.class), executor);
        mvc = mvc(service);
    }

    private MockMvc mvc(ExportService service) {
        return MockMvcBuilders.standaloneSetup(new ExportController(service, guard)).build();
    }

    private String auth(String role) {
        return "Bearer " + jwt.generateAccessToken("9", Map.of("roles", List.of(role)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "psychologist"})
    void failedTaskReturnsConflictBusinessCodeAndNeverStartsExport(String role) throws Exception {
        AgentTask source = new AgentTask();
        source.setId(7L);
        source.setDeleted(0);
        source.setStatus(TaskStatus.FAILED);
        when(tasks.selectById(7L)).thenReturn(source);

        mvc.perform(post(PATH).header("Authorization", auth(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("仅完成或审核拒绝的报告可以导出"));

        verify(tasks).selectById(7L);
        verifyNoInteractions(jobs, executor);
    }

    @Test
    void missingTaskReturnsNotFoundBusinessCodeWithoutCreatingJob() throws Exception {
        when(tasks.selectById(7L)).thenReturn(null);

        mvc.perform(post(PATH).header("Authorization", auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("任务不存在"));

        verifyNoInteractions(jobs, executor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"teacher", "student"})
    void nonProfessionalRoleIsDeniedBeforeReadingTaskOrCreatingExport(String role) throws Exception {
        mvc.perform(post(PATH).header("Authorization", auth(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(tasks, jobs, executor);
    }

    @Test
    void fixedQueueBusinessCodeAndMessageArePreserved() throws Exception {
        ExportService service = mock(ExportService.class);
        when(service.createExportJob(7L, 9L))
                .thenThrow(new BusinessException(503, "导出队列已满，请稍后重试"));

        mvc(service).perform(post(PATH).header("Authorization", auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("导出队列已满，请稍后重试"));

        verify(service).createExportJob(7L, 9L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void unknownFailureKeepsGeneric500WithoutReturningPrivateExceptionBody() throws Exception {
        ExportService service = mock(ExportService.class);
        when(service.createExportJob(anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("private-report-content token=private-secret"));

        mvc(service).perform(post(PATH).header("Authorization", auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("创建导出任务失败，请稍后重试"));

        verifyNoInteractions(tasks, jobs, executor);
    }
}
