package com.edu.mcp.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDto {

    private Long studentId;
    private String from;          // 起始日期 ISO yyyy-MM-dd
    private String to;            // 结束日期
    /** 汇总：total / present / late / absent / leave / attendanceRate */
    private Map<String, Object> summary;
    /** 区间内逐日明细 */
    private List<AttendanceEntry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceEntry {
        private String date;       // yyyy-MM-dd
        private Integer status;    // 0=出勤 1=迟到 2=缺勤 3=请假
        private String courseCode;
        private String remark;
    }
}
