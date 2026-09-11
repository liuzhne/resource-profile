package com.edu.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("student_academic_record")
public class AcademicRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    private String term;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    private BigDecimal credit;

    private BigDecimal score;

    @TableField("grade_point")
    private BigDecimal gradePoint;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
