package com.edu.mcp.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 最近一次心理评估的关键指标 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentalIndicatorDto {

    private Long studentId;
    /** 评估问卷类型 (e.g. SCL-90, SDS, SAS) */
    private String assessmentType;
    /** 评估时间 ISO8601 */
    private String assessedAt;
    /** 风险等级：low / medium / high；mental-service 未给则为 null */
    private String riskLevel;
    /** 因子分（如 SCL-90 的躯体化、强迫、人际、抑郁等） */
    private Map<String, Object> factorScores;
    /** 是否找到记录（false 时上面字段全为空） */
    private Boolean found;
}
