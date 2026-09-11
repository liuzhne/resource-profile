package com.edu.mental.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.edu.common.security.SensitiveField;
import com.edu.common.security.Sensitivity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mental_assessment")
public class MentalAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("questionnaire_id")
    private Long questionnaireId;

    @SensitiveField(Sensitivity.EXTREME)
    private Integer score;

    @SensitiveField(Sensitivity.MEDIUM)
    private String level;

    @SensitiveField(Sensitivity.EXTREME)
    private String result;

    @SensitiveField(Sensitivity.EXTREME)
    private String suggestion;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
