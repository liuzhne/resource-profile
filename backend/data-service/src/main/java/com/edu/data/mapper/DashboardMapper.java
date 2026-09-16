package com.edu.data.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    Map<String, Object> selectStatistics();

    Long countActiveTeachers();

    Long countActiveStudents();

    Long countActiveQuestionnaires();

    Long countWarningStudents();

    List<Map<String, Object>> selectTeacherTrend(@Param("startDate") LocalDate startDate);

    List<Map<String, Object>> selectStudentTrend(@Param("startDate") LocalDate startDate);

    /** 按月份分组（年视图）。返回 {ym: 'YYYY-MM', count: n} */
    List<Map<String, Object>> selectTeacherTrendByMonth(@Param("startDate") LocalDate startDate);

    List<Map<String, Object>> selectStudentTrendByMonth(@Param("startDate") LocalDate startDate);

    List<Map<String, Object>> selectUserDistribution();

    List<Map<String, Object>> selectRecentLogins();
}
