package com.edu.mental.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mental_question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("questionnaire_id")
    private Long questionnaireId;

    @TableField("sort_order")
    private Integer sortOrder;

    private String content;

    @TableField("question_type")
    private String questionType;

    private String options;

    /** B-1：计分规则 JSON {选项值: 分值}，量表/单选计分用。 */
    @TableField("scoring_rules")
    private String scoringRules;

    /** B-1：scale 题型下限。 */
    @TableField("scale_min")
    private Integer scaleMin;

    /** B-1：scale 题型上限。 */
    @TableField("scale_max")
    private Integer scaleMax;

    /** B-1：scale 两端标签 JSON {"min": "...", "max": "..."}。 */
    @TableField("scale_labels")
    private String scaleLabels;

    private Integer required;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
