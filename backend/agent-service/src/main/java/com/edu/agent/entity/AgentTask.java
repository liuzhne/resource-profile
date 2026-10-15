package com.edu.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_task")
public class AgentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    @TableField("status")
    private TaskStatus status;

    @TableField("risk_level")
    private RiskLevel riskLevel;

    private Integer riskScore;

    @TableField("risk_type")
    private String riskType;

    @TableField("risk_analysis_result")
    private String riskAnalysisResult;

    @TableField("retrieved_knowledge")
    private String retrievedKnowledge;

    @TableField("intervention_plan")
    private String interventionPlan;

    /** Response-only normalization status; provider JSON is preserved in intervention_plan. */
    @TableField(exist = false)
    private String planViewStatus;

    @TableField("compliance_audit")
    private String complianceAudit;

    @TableField(fill = FieldFill.INSERT)
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime completedAt;

    /** Fixed failure diagnostics only, never a provider response or exception message. */
    private String failureCode;
    private String failureStage;
    private Integer failureHttpStatus;
    private String traceId;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
