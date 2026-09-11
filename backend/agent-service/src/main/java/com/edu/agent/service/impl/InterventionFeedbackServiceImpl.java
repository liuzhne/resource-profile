package com.edu.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.agent.dto.FeedbackRequest;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.entity.InterventionFeedback;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.mapper.InterventionFeedbackMapper;
import com.edu.agent.service.InterventionFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * I-5.2 / I-5.4：干预反馈提交 + 月度报表 / CSV 导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterventionFeedbackServiceImpl implements InterventionFeedbackService {

    private final InterventionFeedbackMapper feedbackMapper;
    private final AgentTaskMapper agentTaskMapper;

    private static final Set<String> VALID_OUTCOMES =
            Set.of("improved", "unchanged", "worsened", "escalated");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Long submit(FeedbackRequest req, Long counselorId) {
        if (req == null || req.getTaskId() == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (req.getScore() == null || req.getScore() < 1 || req.getScore() > 5) {
            throw new IllegalArgumentException("score 必须是 1-5");
        }
        if (req.getOutcome() != null && !req.getOutcome().isBlank()
                && !VALID_OUTCOMES.contains(req.getOutcome())) {
            throw new IllegalArgumentException("outcome 非法，应为 " + VALID_OUTCOMES);
        }

        AgentTask task = agentTaskMapper.selectById(req.getTaskId());
        if (task == null || (task.getDeleted() != null && task.getDeleted() == 1)) {
            throw new IllegalArgumentException("任务不存在: " + req.getTaskId());
        }

        InterventionFeedback fb = new InterventionFeedback();
        fb.setTaskId(req.getTaskId());
        fb.setStudentId(task.getStudentId());
        fb.setCounselorId(counselorId);
        fb.setScore(req.getScore());
        fb.setOutcome(req.getOutcome());
        fb.setComment(req.getComment());
        feedbackMapper.insert(fb);
        log.info("I-5：收到干预反馈 taskId={} score={} outcome={} by={}",
                req.getTaskId(), req.getScore(), req.getOutcome(), counselorId);
        return fb.getId();
    }

    @Override
    public List<InterventionFeedback> monthlyReport(String month) {
        YearMonth ym = parseMonth(month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        return feedbackMapper.selectList(new LambdaQueryWrapper<InterventionFeedback>()
                .ge(InterventionFeedback::getCreatedAt, start)
                .lt(InterventionFeedback::getCreatedAt, end)
                .orderByAsc(InterventionFeedback::getCreatedAt));
    }

    @Override
    public String exportCsv(String month) {
        List<InterventionFeedback> rows = monthlyReport(month);
        StringBuilder sb = new StringBuilder();
        sb.append("id,task_id,student_id,counselor_id,score,outcome,comment,created_at\n");
        for (InterventionFeedback f : rows) {
            sb.append(f.getId()).append(',')
                    .append(nz(f.getTaskId())).append(',')
                    .append(nz(f.getStudentId())).append(',')
                    .append(nz(f.getCounselorId())).append(',')
                    .append(nz(f.getScore())).append(',')
                    .append(csv(f.getOutcome())).append(',')
                    .append(csv(f.getComment())).append(',')
                    .append(f.getCreatedAt() == null ? "" : f.getCreatedAt().format(TS_FMT))
                    .append('\n');
        }
        return sb.toString();
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);  // yyyy-MM
        } catch (Exception e) {
            throw new IllegalArgumentException("month 格式应为 yyyy-MM，收到: " + month);
        }
    }

    private static String nz(Object o) {
        return o == null ? "" : o.toString();
    }

    /** CSV 字段转义：含逗号/引号/换行时加双引号并转义内部引号。 */
    private static String csv(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
