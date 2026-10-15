package com.edu.teacher.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.edu.common.security.SensitiveField;
import com.edu.common.security.Sensitivity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("teacher_info")
public class Teacher {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("employee_id")
    private String employeeId;

    private String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer gender;

    @TableField(value = "birth_date", updateStrategy = FieldStrategy.ALWAYS)
    @SensitiveField(Sensitivity.HIGH)
    private LocalDate birthDate;

    @TableField("dept_id")
    private Long deptId;

    @TableField(value = "dept_name", updateStrategy = FieldStrategy.ALWAYS)
    private String deptName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;

    @SensitiveField(Sensitivity.MEDIUM)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String education;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String school;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String major;

    @TableField(value = "research_area", updateStrategy = FieldStrategy.ALWAYS)
    private String researchArea;

    @TableField(value = "join_date", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate joinDate;

    @SensitiveField(Sensitivity.HIGH)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String political;

    @SensitiveField(Sensitivity.HIGH)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;

    @SensitiveField(Sensitivity.HIGH)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
