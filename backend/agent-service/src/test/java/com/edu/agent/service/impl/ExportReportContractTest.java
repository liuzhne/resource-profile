package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.mapper.AgentExportTaskMapper;
import com.edu.agent.mapper.AgentTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Renders the actual production HTML template, including expressions and field lookup. */
class ExportReportContractTest {
    private ExportServiceImpl service() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new ExportServiceImpl(mock(AgentExportTaskMapper.class), mock(AgentTaskMapper.class), engine,
                new DefaultResourceLoader(), Runnable::run);
    }

    private AgentTask task(String plan) {
        var task = new AgentTask();
        task.setId(1L); task.setStudentId(1L); task.setStatus(TaskStatus.COMPLETED); task.setRiskLevel(RiskLevel.MEDIUM);
        task.setInterventionPlan(plan);
        task.setRetrievedKnowledge("{\"executed\":false,\"searches\":[],\"chunks\":[]}");
        return task;
    }

    @Test
    void currentReActTemplateActuallyRendersDeadlineGoalsMilestonesResourcesAndGlobalCitation() throws Exception {
        var full = JSON.parseObject(new ClassPathResource("report/react-final.json").getContentAsString(StandardCharsets.UTF_8));
        var source = task(full.getJSONObject("intervention_plan").toJSONString());
        String html = service().renderHtml(source);
        assertThat(html).contains("48小时内", "建立合成学习安排", "第1周完成合成计划", "第4周复核合成结果", "4周",
                "合成支持资源：示例热线", "synthetic-policy-001", "方案引用");
        assertThat(html).doesNotContain("— 天", "立即行动（7 天内）", "检索完成");
    }

    @Test
    void legacyTemplatePreservesDeadlinePhaseAllGoalsMetricsObjectResourcesAndActionCitation() throws Exception {
        String plan = new ClassPathResource("report/legacy-plan.json").getContentAsString(StandardCharsets.UTF_8);
        String html = service().renderHtml(task(plan));
        assertThat(html).contains("3 天", "1月", "完成合成复习；建立合成记录", "每周合成复核一次", "合成学习课程",
                "https://example.invalid/synthetic", "synthetic-case-002", "synthetic-policy-001");
    }

    @Test
    void failedRetrievalAndInvalidPlanRemainExplicit() {
        var task = task("invalid");
        task.setRetrievedKnowledge("{\"executed\":true,\"searches\":[{\"tool\":\"search_cases\",\"status\":\"FAILED\"}],\"chunks\":[]}");
        String html = service().renderHtml(task);
        assertThat(html).contains("检索失败", "方案数据格式异常").doesNotContain("检索完成");
    }

    @Test
    void taskTimesUseTheSameUtcPlusEightInstantAsTheReportPageIncludingDateRollover() {
        var source = task("{}");
        source.setCreatedAt(LocalDateTime.of(2026, 10, 11, 3, 49, 56));
        source.setCompletedAt(LocalDateTime.of(2026, 10, 11, 23, 52, 12));
        String html = service().renderHtml(source);
        assertThat(html).contains("2026-10-11 11:49", "2026-10-12 07:52")
                .doesNotContain("2026-10-11 03:49", "2026-10-11 23:52");
        // Rendering is a view conversion; the database/API UTC values remain unchanged.
        assertThat(source.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 11, 3, 49, 56));
        assertThat(source.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 11, 23, 52, 12));
    }

    @Test
    void missingTaskTimesStayUnknownRatherThanUsingCurrentOrInventedTimes() {
        String html = service().renderHtml(task("{}"));
        assertThat(html).contains("创建时间", "完成时间", "—");
    }
}
