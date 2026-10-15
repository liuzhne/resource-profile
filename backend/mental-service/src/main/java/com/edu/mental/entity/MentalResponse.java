package com.edu.mental.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mental_response")
public class MentalResponse {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("questionnaire_id")
    private Long questionnaireId;

    private String answers;

    /** Submission-time respondent template; never reconstruct old answers from current questions. */
    private String questionnaireSnapshot;

    private Integer score;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
