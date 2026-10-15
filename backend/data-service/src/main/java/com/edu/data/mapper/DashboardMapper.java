package com.edu.data.mapper;

import com.edu.data.dto.SearchResultDto;
import com.edu.data.dto.TodoItemDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    Map<String, Object> selectStatistics(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    List<Map<String, Object>> selectTeacherTrend(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<Map<String, Object>> selectStudentTrend(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<Map<String, Object>> selectTeacherTrendByMonth(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<Map<String, Object>> selectStudentTrendByMonth(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<Map<String, Object>> selectPortraitDistribution();
    List<Map<String, Object>> selectRecentLogins();
    Long countTodos();
    List<TodoItemDto> selectTodos(@Param("limit") int limit);
    List<SearchResultDto> search(@Param("query") String query, @Param("limit") int limit);
}
