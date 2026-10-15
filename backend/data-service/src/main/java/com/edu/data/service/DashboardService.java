package com.edu.data.service;

import com.edu.data.dto.*;
import java.util.List;
import java.util.Map;

public interface DashboardService {

    Map<String, Object> getStatistics();

    /**
     * @param period week=最近 7 天按天 / month=最近 30 天按天 / year=最近 12 月按月
     */
    Map<String, Object> getTrend(String period);

    Map<String, Object> getDistribution();

    List<Map<String, Object>> getRecentLogins();
    TodoPageDto getTodos(int limit);

    List<SearchResultDto> search(String query, int limit);
}
