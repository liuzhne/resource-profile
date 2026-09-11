package com.edu.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * I-5.1：干预反馈。辅导员在干预方案落地后回填评分/结果，闭环复盘 + 月度报表数据源。
 */
@Data
@TableName("intervention_feedback")
public class InterventionFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("student_id")
    private Long studentId;

    @TableField("counselor_id")
    private Long counselorId;

    /** 干预效果评分 1-5。 */
    private Integer score;

    /** 结果标签：improved / unchanged / worsened / escalated。 */
    private String outcome;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
