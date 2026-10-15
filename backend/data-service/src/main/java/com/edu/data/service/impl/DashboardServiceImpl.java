package com.edu.data.service.impl;

import com.edu.data.mapper.DashboardMapper;
import com.edu.data.dto.*;
import com.edu.common.exception.BusinessException;
import java.time.Clock;
import com.edu.data.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Clock dashboardClock;

    private static final String[] WEEK_DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final DateTimeFormatter MONTH_DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public Map<String, Object> getStatistics() {
        // One DB round trip instead of four sequential TLS-network queries.
        LocalDate day = LocalDate.now(dashboardClock);
        Map<String, Object> stats = new HashMap<>(dashboardMapper.selectStatistics(day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
        stats.put("date", day.toString());
        stats.put("timezone", dashboardClock.getZone().getId());
        return stats;
    }

    @Override
    public Map<String, Object> getTrend(String period) {
        if (period == null) period = "week";
        return switch (period.toLowerCase(java.util.Locale.ROOT)) {
            case "month" -> buildDailyTrend(30, false);
            case "year"  -> buildMonthlyTrend(12);
            case "week" -> buildDailyTrend(7, true);
            default -> throw new BusinessException(400, "期间须为 week/month/year");
        };
    }

    /**
     * 按天分组：weekDays=true 用周一~周日为 X 轴，否则用 MM-dd
     */
    private Map<String, Object> buildDailyTrend(int days, boolean weekDays) {
        LocalDate today = LocalDate.now(dashboardClock);
        LocalDate startDate = today.minusDays(days - 1L);

        Map<LocalDate, Long> teacherMap = toDayMap(dashboardMapper.selectTeacherTrend(startDate, today.plusDays(1)));
        Map<LocalDate, Long> studentMap = toDayMap(dashboardMapper.selectStudentTrend(startDate, today.plusDays(1)));

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
     * 按月分组（最近 N 个月）。X 轴标签 "YYYY-MM"。
     */
    private Map<String, Object> buildMonthlyTrend(int months) {
        LocalDate today = LocalDate.now(dashboardClock);
        YearMonth startYm = YearMonth.from(today).minusMonths(months - 1L);
        LocalDate startDate = startYm.atDay(1);

        Map<YearMonth, Long> teacherMap = toMonthMap(dashboardMapper.selectTeacherTrendByMonth(startDate, today.plusDays(1)));
        Map<YearMonth, Long> studentMap = toMonthMap(dashboardMapper.selectStudentTrendByMonth(startDate, today.plusDays(1)));

        List<String> labels = new ArrayList<>(months);
        List<Long> teacherData = new ArrayList<>(months);
        List<Long> studentData = new ArrayList<>(months);

        for (int i = 0; i < months; i++) {
            YearMonth ym = startYm.plusMonths(i);
            labels.add(ym.toString());
            teacherData.add(teacherMap.getOrDefault(ym, 0L));
            studentData.add(studentMap.getOrDefault(ym, 0L));
        }
        return assemble(labels, teacherData, studentData);
    }

    private Map<LocalDate, Long> toDayMap(List<Map<String, Object>> rows) {
        Map<LocalDate, Long> m = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object rawDay = row.get("day");
            LocalDate day = rawDay instanceof LocalDate date ? date : rawDay instanceof java.sql.Date date ? date.toLocalDate() : LocalDate.parse(rawDay.toString());
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
        List<Map<String, Object>> list = dashboardMapper.selectPortraitDistribution();
        Map<String, Object> result = new HashMap<>();
        result.put("data", list);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecentLogins() {
        return dashboardMapper.selectRecentLogins();
    }
    @Override
    @Transactional(readOnly = true)
    public TodoPageDto getTodos(int limit) {
        int boundedLimit = Math.max(1, Math.min(20, limit));
        return new TodoPageDto(dashboardMapper.countTodos(), dashboardMapper.selectTodos(boundedLimit));
    }

    @Override
    public List<SearchResultDto> search(String query, int limit) {
        if (query == null || query.strip().length() < 2 || query.strip().length() > 50) {
            throw new BusinessException(400, "搜索关键词须为 2–50 字");
        }
        // LOCATE treats percent/underscore literally, so a wildcard cannot enumerate the directory.
        return dashboardMapper.search(query.strip(), Math.max(1, Math.min(20, limit)));
    }

}
