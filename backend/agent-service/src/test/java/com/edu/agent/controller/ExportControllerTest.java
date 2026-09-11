package com.edu.agent.controller;

import com.edu.agent.entity.AgentExportTask;
import com.edu.agent.service.ExportService;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExportController 越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 报告导出/下载教职工面向；学生越权拒绝（JSON 端点 403、下载端点 HTTP 403），且不触达 service。
 */
class ExportControllerTest {

    private static final String AUTH = "Bearer tok";

    private ExportService exportService;
    private JwtUtil jwtUtil;
    private ExportController controller;

    @BeforeEach
    void setUp() {
        exportService = mock(ExportService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new ExportController(exportService, new AccessGuard(jwtUtil));
    }

    @Test
    void download_student_forbidden() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        ResponseEntity<Resource> r = controller.download(50L, AUTH);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(exportService, never()).loadFile(anyLong());
    }

    @Test
    void createExportJob_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<Map<String, Long>> r = controller.createExportJob(7L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(exportService, never()).createExportJob(anyLong(), anyLong());
    }

    @Test
    void download_internalNoToken_ok() {
        AgentExportTask job = new AgentExportTask();
        job.setTaskId(7L);
        when(exportService.getJobStatus(50L)).thenReturn(job);
        when(exportService.loadFile(50L)).thenReturn(mock(Resource.class));

        ResponseEntity<Resource> r = controller.download(50L, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(exportService).loadFile(50L);
    }
}
