package com.edu.mcp.student.tool;

import com.edu.common.result.Result;
import com.edu.mcp.student.dto.AcademicHistoryDto;
import com.edu.mcp.student.dto.AttendanceDto;
import com.edu.mcp.student.dto.MentalIndicatorDto;
import com.edu.mcp.student.dto.StudentProfileDto;
import com.edu.mcp.student.feign.MentalServiceClient;
import com.edu.mcp.student.feign.StudentServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * H-1.2：student-data MCP server 暴露的 4 个只读 tool。
 *
 * Spring AI 1.1.6 GA 的 @Tool 注解由 McpStudentDataApplication 里的
 * MethodToolCallbackProvider 显式登记，启动后 MCP server 通过 Streamable HTTP
 * （单端点 /mcp，H-1.1.6 切换）暴露这些 tool。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentDataTools {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StudentServiceClient studentClient;
    private final MentalServiceClient mentalClient;

    // -----------------------------------------------------------------------
    // Tool 1：学生基础档案
    // -----------------------------------------------------------------------
    @Tool(name = "get_student_profile", description = "获取学生基础档案：姓名、性别、年级、专业、院系、班级、学籍状态。" +
            "用于 Agent Loop 拿到学生身份后做后续画像分析。" +
            "入参 studentId 来自 student_info.id（不是学号）。")
    public StudentProfileDto getStudentProfile(
            @ToolParam(description = "学生主键 student_info.id（数据库自增主键，非学号）") Long studentId) {

        Result<Map<String, Object>> resp = studentClient.getStudentById(studentId);
        Map<String, Object> data = resp == null ? null : resp.getData();
        if (data == null) {
            log.warn("getStudentProfile: studentId={} 未返回数据", studentId);
            return StudentProfileDto.builder().studentId(studentId).build();
        }
        return StudentProfileDto.builder()
                .studentId(asLong(data.get("id")))
                .name(asStr(data.get("name")))
                .gender(asInt(data.get("gender")))
                .grade(asStr(data.get("grade")))
                .majorName(asStr(data.get("majorName")))
                .deptName(asStr(data.get("deptName")))
                .className(asStr(data.get("className")))
                .studentNo(asStr(data.get("studentId")))
                .status(asInt(data.get("status")))
                .build();
    }

    // -----------------------------------------------------------------------
    // Tool 2：成绩历史
    // -----------------------------------------------------------------------
    @Tool(name = "get_academic_history", description = "获取学生历次成绩：逐门课程的学分/分数/绩点 + 区间加权平均分与平均绩点。" +
            "term 为空表示全部学期；指定 term（如 '2025-2026-1'）则仅返回该学期。" +
            "用于 LLM 评估学业表现、识别挂科或下滑趋势。")
    public AcademicHistoryDto getAcademicHistory(
            @ToolParam(description = "学生主键 student_info.id") Long studentId,
            @ToolParam(description = "学期，如 '2025-2026-1'；可空表示全部", required = false) String term) {

        Result<List<Map<String, Object>>> resp = studentClient.listAcademic(studentId, term);
        List<Map<String, Object>> rows = resp == null || resp.getData() == null
                ? Collections.emptyList() : resp.getData();

        List<AcademicHistoryDto.CourseRecord> records = new ArrayList<>(rows.size());
        BigDecimal weightedScoreSum = BigDecimal.ZERO;
        BigDecimal weightedGpSum = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            BigDecimal credit = asBigDecimal(row.get("credit"));
            BigDecimal score  = asBigDecimal(row.get("score"));
            BigDecimal gp     = asBigDecimal(row.get("gradePoint"));
            records.add(AcademicHistoryDto.CourseRecord.builder()
                    .term(asStr(row.get("term")))
                    .courseCode(asStr(row.get("courseCode")))
                    .courseName(asStr(row.get("courseName")))
                    .credit(credit)
                    .score(score)
                    .gradePoint(gp)
                    .build());
            if (credit != null) {
                if (score != null) weightedScoreSum = weightedScoreSum.add(score.multiply(credit));
                if (gp != null)    weightedGpSum    = weightedGpSum.add(gp.multiply(credit));
                totalCredit = totalCredit.add(credit);
            }
        }

        BigDecimal avgScore = totalCredit.signum() == 0 ? null
                : weightedScoreSum.divide(totalCredit, 2, RoundingMode.HALF_UP);
        BigDecimal avgGp = totalCredit.signum() == 0 ? null
                : weightedGpSum.divide(totalCredit, 2, RoundingMode.HALF_UP);

        return AcademicHistoryDto.builder()
                .studentId(studentId)
                .term(term)
                .records(records)
                .averageScore(avgScore)
                .averageGradePoint(avgGp)
                .build();
    }

    // -----------------------------------------------------------------------
    // Tool 3：心理评估指标
    // -----------------------------------------------------------------------
    @Tool(name = "get_mental_indicators", description = "获取学生最近一次心理评估的因子分与风险等级。" +
            "返回 found=false 表示该学生未做过任何评估（不报错）。" +
            "用于 LLM 判断心理风险及是否需要介入。")
    public MentalIndicatorDto getMentalIndicators(
            @ToolParam(description = "学生主键 student_info.id") Long studentId) {

        // mental-service 的查询以 sys_user.userId 为参，需要先取 Student.userId
        Result<Map<String, Object>> stuResp = studentClient.getStudentById(studentId);
        Map<String, Object> stu = stuResp == null ? null : stuResp.getData();
        Long userId = stu == null ? null : asLong(stu.get("userId"));
        if (userId == null) {
            log.warn("getMentalIndicators: studentId={} 未找到对应 userId", studentId);
            return MentalIndicatorDto.builder().studentId(studentId).found(false).build();
        }

        Result<List<Map<String, Object>>> assResp = mentalClient.myHistory(userId);
        List<Map<String, Object>> list = assResp == null || assResp.getData() == null
                ? Collections.emptyList() : assResp.getData();
        if (list.isEmpty()) {
            return MentalIndicatorDto.builder().studentId(studentId).found(false).build();
        }

        // 取最近一次：mental-service 返回顺序未明确，显式按 assessTime（缺则 createTime）倒序取首条
        // （yyyy-MM-dd HH:mm:ss / ISO 格式下字典序即时间序）。
        Map<String, Object> latest = list.stream()
                .max(java.util.Comparator.comparing(StudentDataTools::assessKey))
                .orElse(list.get(0));
        return MentalIndicatorDto.builder()
                .studentId(studentId)
                .found(true)
                .assessmentType(asStr(latest.getOrDefault("questionnaireType", latest.get("type"))))
                .assessedAt(asStr(latest.getOrDefault("assessTime", latest.get("createTime"))))
                .riskLevel(asStr(latest.get("riskLevel")))
                .factorScores(extractFactorScores(latest))
                .build();
    }

    // -----------------------------------------------------------------------
    // Tool 4：考勤
    // -----------------------------------------------------------------------
    @Tool(name = "get_attendance", description = "获取学生考勤数据：区间内逐日明细 + summary（总天数、出勤数、迟到数、缺勤数、请假数、出勤率）。" +
            "from/to 均可空：默认查询最近 30 天。日期格式 yyyy-MM-dd。" +
            "用于 LLM 评估学生出勤稳定性，结合成绩判断是否有学习投入下滑。")
    public AttendanceDto getAttendance(
            @ToolParam(description = "学生主键 student_info.id") Long studentId,
            @ToolParam(description = "起始日 yyyy-MM-dd；可空（默认 today-30d）", required = false) String from,
            @ToolParam(description = "结束日 yyyy-MM-dd；可空（默认 today）", required = false) String to) {

        LocalDate toDate = parseDateOrDefault(to, LocalDate.now());
        LocalDate fromDate = parseDateOrDefault(from, toDate.minusDays(30));

        Result<Map<String, Object>> summaryResp =
                studentClient.attendanceSummary(studentId, fromDate, toDate);
        Result<List<Map<String, Object>>> listResp =
                studentClient.listAttendance(studentId, fromDate, toDate);

        Map<String, Object> summary = summaryResp == null ? null : summaryResp.getData();
        List<Map<String, Object>> rows = listResp == null || listResp.getData() == null
                ? Collections.emptyList() : listResp.getData();

        List<AttendanceDto.AttendanceEntry> entries = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            entries.add(AttendanceDto.AttendanceEntry.builder()
                    .date(asStr(r.get("attendDate")))
                    .status(asInt(r.get("status")))
                    .courseCode(asStr(r.get("courseCode")))
                    .remark(asStr(r.get("remark")))
                    .build());
        }
        return AttendanceDto.builder()
                .studentId(studentId)
                .from(fromDate.format(DATE_FMT))
                .to(toDate.format(DATE_FMT))
                .summary(summary)
                .entries(entries)
                .build();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractFactorScores(Map<String, Object> assessment) {
        Object raw = assessment.get("factorScores");
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        // 兼容字段名 scores
        Object alt = assessment.get("scores");
        if (alt instanceof Map<?, ?> m2) {
            return (Map<String, Object>) m2;
        }
        return Collections.emptyMap();
    }

    private static LocalDate parseDateOrDefault(String s, LocalDate fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (Exception e) {
            log.warn("parseDate failed: {}, fallback to {}", s, fallback);
            return fallback;
        }
    }

    private static String asStr(Object v) {
        return v == null ? null : v.toString();
    }

    /** 心理测评排序键：assessTime 优先，缺则 createTime；null→空串（排到最旧）。 */
    private static String assessKey(Map<String, Object> row) {
        Object v = row.getOrDefault("assessTime", row.get("createTime"));
        return v == null ? "" : v.toString();
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(v.toString()); } catch (Exception e) { return null; }
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.valueOf(v.toString()); } catch (Exception e) { return null; }
    }

    private static BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }
}
