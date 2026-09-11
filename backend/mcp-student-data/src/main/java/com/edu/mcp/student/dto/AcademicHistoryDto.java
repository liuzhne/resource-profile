package com.edu.mcp.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicHistoryDto {

    private Long studentId;
    private String term;                  // 若入参指定 term 则等于该值，否则为 null
    private List<CourseRecord> records;
    private BigDecimal averageScore;      // 区间内加权平均分（按学分加权）
    private BigDecimal averageGradePoint; // 区间内加权平均绩点

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseRecord {
        private String term;
        private String courseCode;
        private String courseName;
        private BigDecimal credit;
        private BigDecimal score;
        private BigDecimal gradePoint;
    }
}
