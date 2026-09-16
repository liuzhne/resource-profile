package com.edu.data.service.impl;

import com.edu.data.mapper.DashboardMapper;
import com.edu.data.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    private static final String[] WEEK_DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final DateTimeFormatter MONTH_DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public Map<String, Object> getStatistics() {
        // One DB round trip instead of four sequential TLS-network queries.
        return dashboardMapper.selectStatistics();
    }

    @Override
    public Map<String, Object> getTrend(String period) {
        if (period == null) period = "week";
        return switch (period.toLowerCase()) {
            case "month" -> buildDailyTrend(30, false);
            case "year"  -> buildMonthlyTrend(12);
            default       -> buildDailyTrend(7, true);
        };
    }

    /**
     * 按天分组：weekDays=true 用周一~周日为 X 轴，否则用 MM-dd
     */
    private Map<String, Object> buildDailyTrend(int days, boolean weekDays) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);

        Map<LocalDate, Long> teacherMap = toDayMap(dashboardMapper.selectTeacherTrend(startDate));
        Map<LocalDate, Long> studentMap = toDayMap(dashboardMapper.selectStudentTrend(startDate));

        List<String> labels = new ArrayList<>(days);
        List<Long> teacherData = new ArrayList<>(days);
        List<Long> studentData = new ArrayList<>(days);

        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            if (weekDays) {
                int dayOfWeek = date.getDayOfWeek().getValue() - 1;
                labels.add(WEEK_DAYS[dayOfWeek]);
            } else {
                labels.add(date.format(MONTH_DAY_FMT));
            }
            teacherData.add(teacherMap.getOrDefault(date, 0L));
            studentData.add(studentMap.getOrDefault(date, 0L));
        }
        return assemble(labels, teacherData, studentData);
    }

    /**
     * 按月分组（最近 N 个月）。X 轴标签 "M月"。
     */
    private Map<String, Object> buildMonthlyTrend(int months) {
        YearMonth startYm = YearMonth.now().minusMonths(months - 1L);
        LocalDate startDate = startYm.atDay(1);

        Map<YearMonth, Long> teacherMap = toMonthMap(dashboardMapper.selectTeacherTrendByMonth(startDate));
        Map<YearMonth, Long> studentMap = toMonthMap(dashboardMapper.selectStudentTrendByMonth(startDate));

        List<String> labels = new ArrayList<>(months);
        List<Long> teacherData = new ArrayList<>(months);
        List<Long> studentData = new ArrayList<>(months);

        for (int i = 0; i < months; i++) {
            YearMonth ym = startYm.plusMonths(i);
            labels.add(ym.getMonthValue() + "月");
            teacherData.add(teacherMap.getOrDefault(ym, 0L));
            studentData.add(studentMap.getOrDefault(ym, 0L));
        }
        return assemble(labels, teacherData, studentData);
    }

    private Map<LocalDate, Long> toDayMap(List<Map<String, Object>> rows) {
        Map<LocalDate, Long> m = new HashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate day = ((java.sql.Date) row.get("day")).toLocalDate();
            Long count = ((Number) row.get("count")).longValue();
            m.put(day, count);
        }
        return m;
    }

    private Map<YearMonth, Long> toMonthMap(List<Map<String, Object>> rows) {
        Map<YearMonth, Long> m = new HashMap<>();
        for (Map<String, Object> row : rows) {
            YearMonth ym = YearMonth.parse((String) row.get("ym"));
            Long count = ((Number) row.get("count")).longValue();
            m.put(ym, count);
        }
        return m;
    }

    private Map<String, Object> assemble(List<String> labels, List<Long> teacherData, List<Long> studentData) {
        Map<String, Object> result = new HashMap<>();
        result.put("days", labels);
        result.put("teacherData", teacherData);
        result.put("studentData", studentData);
        return result;
    }

    @Override
    public Map<String, Object> getDistribution() {
        List<Map<String, Object>> list = dashboardMapper.selectUserDistribution();
        Map<String, Object> result = new HashMap<>();
        result.put("data", list);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecentLogins() {
        return dashboardMapper.selectRecentLogins();
    }
}
