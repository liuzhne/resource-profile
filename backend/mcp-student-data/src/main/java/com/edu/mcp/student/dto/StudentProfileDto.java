package com.edu.mcp.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** LLM 看到的学生档案字段；不暴露 PII（手机号、身份证、邮箱），只保留分析相关属性。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDto {
    private Long studentId;
    private String name;
    private Integer gender;       // 1=male 2=female
    private String grade;         // 年级
    private String majorName;
    private String deptName;
    private String className;
    private String studentNo;     // 学号（business id）
    private Integer status;       // 1=在读 0=离校
}
