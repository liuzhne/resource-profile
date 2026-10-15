package com.edu.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.edu.common.security.SensitiveField;
import com.edu.common.security.Sensitivity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("student_info")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("student_id")
    private String studentId;

    private String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer gender;

    @TableField(value = "birth_date", updateStrategy = FieldStrategy.ALWAYS)
    @SensitiveField(Sensitivity.HIGH)
    private LocalDate birthDate;

    /** DTO field-presence marker; never persisted or returned to clients. */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean clearBirthDate;

    @TableField("dept_id")
    private Long deptId;

    @TableField(value = "dept_name", updateStrategy = FieldStrategy.ALWAYS)
    private String deptName;

    @TableField("major_id")
    private Long majorId;

    @TableField(value = "major_name", updateStrategy = FieldStrategy.ALWAYS)
    private String majorName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String grade;

    @TableField(value = "class_name", updateStrategy = FieldStrategy.ALWAYS)
    private String className;

    @TableField(value = "enrollment_date", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate enrollmentDate;

    @TableField(value = "expected_graduation", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedGraduation;

    @SensitiveField(Sensitivity.MEDIUM)
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal gpa;

    @SensitiveField(Sensitivity.MEDIUM)
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.ALWAYS)
    private Integer credits;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
