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

    /** Nullable for historical/imported assessments with no submitted response. */
    private Long responseId;

    @TableField(exist = false)
    private String questionnaireTitle;
    @TableField(exist = false)
    private String questionnaireType;

    @SensitiveField(Sensitivity.EXTREME)
    private Integer score;

    @SensitiveField(Sensitivity.MEDIUM)
    private String level;

    @SensitiveField(Sensitivity.EXTREME)
    private String result;

    @SensitiveField(Sensitivity.EXTREME)
    private String suggestion;

    private Integer status;

    private String warningStatus;

    @SensitiveField(Sensitivity.EXTREME)
    private String warningNote;

    private Long warningHandledBy;
    private LocalDateTime warningHandledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
