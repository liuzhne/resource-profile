package com.edu.agent.controller;

import com.edu.agent.entity.AgentTask;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.edu.agent.service.AgentTaskService;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.InternalCallCredential;
import com.edu.common.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTaskController 越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 教职工面向端点（触发分析 / 看任务产物），学生越权、匿名直连 403 且不触达 service。
 */
class AgentTaskControllerTest {

    private static final String AUTH = "Bearer tok";
    private static final String INTERNAL_TOKEN = "internal-secret-at-least-32-chars-0001";

    private AgentTaskService agentTaskService;
    private JwtUtil jwtUtil;
    private AgentTaskController controller;

    @BeforeEach
    void setUp() {
        agentTaskService = mock(AgentTaskService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new AgentTaskController(agentTaskService,
                new AccessGuard(jwtUtil, new InternalCallCredential(INTERNAL_TOKEN)));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 模拟内部调用：不带 token，只带合法 X-Internal-Token。 */
    private static void asInternalCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalCallCredential.HEADER, INTERNAL_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void trigger_staff_ok() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("counselor"));
        when(agentTaskService.triggerTask(5L)).thenReturn(100L);

        Result<Long> r = controller.trigger(5L, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo(100L);
    }

    @Test
    void trigger_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<Long> r = controller.trigger(5L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(agentTaskService, never()).triggerTask(anyLong());
    }

    @Test
    void detail_student_forbidden() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<AgentTask> r = controller.detail(7L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(agentTaskService, never()).getTaskDetail(anyLong());
    }

    @Test
    void trigger_internalCredential_ok() {
        asInternalCall();
        when(agentTaskService.triggerTask(5L)).thenReturn(100L);

        Result<Long> r = controller.trigger(5L, null);

        assertThat(r.getCode()).isEqualTo(200);
        verify(agentTaskService).triggerTask(5L);
    }

    @Test
    void trigger_anonymous_forbidden_andNoServiceCall() {
        // 既无 token 也无内部凭证 → 403（AgentSelfAuthFilter 被关时的第二道防线）
        Result<Long> r = controller.trigger(5L, null);

        assertThat(r.getCode()).isEqualTo(403);
        verify(agentTaskService, never()).triggerTask(anyLong());
    }

    private AgentTask syntheticReport() throws Exception {
        var full = JSON.parseObject(new ClassPathResource("report/react-final.json").getContentAsString(StandardCharsets.UTF_8));
        full.getJSONObject("risk_analysis").put("unknown_nested", Map.of("score", "SYNTHETIC_RAW_PHQ9:19"));
        full.getJSONObject("risk_analysis").getJSONArray("recommended_intervention_types").add("SYNTHETIC_RAW_PHQ9:19");
        var task = new AgentTask(); task.setId(7L); task.setStudentId(1L);
        task.setRiskAnalysisResult(full.getJSONObject("risk_analysis").toJSONString());
        task.setRiskType("SYNTHETIC_RAW_PHQ9:19");
        task.setInterventionPlan(full.getJSONObject("intervention_plan").toJSONString());
        task.setRetrievedKnowledge("{\"query\":\"SYNTHETIC_RAW_PHQ9:19\",\"executed\":true,\"searches\":[{\"query\":\"SYNTHETIC_RAW_PHQ9:19\",\"arguments\":{\"score\":19},\"tool\":\"search_psychology\",\"status\":\"SUCCEEDED\"}],\"chunks\":[]}");
        return task;
    }

    @ParameterizedTest
    @ValueSource(strings = {"teacher", "counselor", "academic_advisor"})
    void detailAndListNeverExposeStringJsonRawMentalOrUnknownNestedFields(String role) throws Exception {
        when(jwtUtil.getSubject("tok")).thenReturn("9"); when(jwtUtil.parseRoles("tok")).thenReturn(Set.of(role));
        var source = syntheticReport();
        when(agentTaskService.getTaskDetail(7L)).thenReturn(source);
        var page = new Page<AgentTask>(1, 20); page.setTotal(1); page.setRecords(List.of(source));
        when(agentTaskService.listTasks(1, 20, null, null)).thenReturn(page);
        var detail = controller.detail(7L, AUTH).getData();
        var listed = controller.list(1, 20, null, null, AUTH).getData().getRecords().getFirst();
        for (AgentTask view : List.of(detail, listed)) {
            assertThat(JSON.toJSONString(view)).doesNotContain("SYNTHETIC_RAW_PHQ9", "unknown_nested", "\"arguments\"");
            assertThat(view.getRiskAnalysisResult()).contains("详细分析限专业角色", "risk_details_restricted");
            assertThat(view.getInterventionPlan()).contains("48小时内", "合成支持资源：示例热线");
            assertThat(view.getPlanViewStatus()).isEqualTo("AVAILABLE");
        }
        assertThat(source.getRiskAnalysisResult()).contains("SYNTHETIC_RAW_PHQ9", "unknown_nested");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "psychologist"})
    void professionalRolesKeepRawMentalAndReceiveTheSameNormalizedPlan(String role) throws Exception {
        when(jwtUtil.getSubject("tok")).thenReturn("9"); when(jwtUtil.parseRoles("tok")).thenReturn(Set.of(role));
        var source = syntheticReport(); when(agentTaskService.getTaskDetail(7L)).thenReturn(source);
        var view = controller.detail(7L, AUTH).getData();
        assertThat(view.getRiskAnalysisResult()).isEqualTo(source.getRiskAnalysisResult());
        assertThat(view.getInterventionPlan()).contains("\"name\":\"合成支持资源：示例热线\"");
    }
}
