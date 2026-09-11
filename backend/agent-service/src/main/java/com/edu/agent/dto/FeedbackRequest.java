package com.edu.agent.dto;

import lombok.Data;

/**
 * I-5.2：提交干预反馈请求体。校验在 service 层手动做（agent-service 未引入 validation starter）。
 */
@Data
public class FeedbackRequest {

    /** 关联 agent_task.id，必填。 */
    private Long taskId;

    /** 评分 1-5，必填。 */
    private Integer score;

    /** 结果标签：improved / unchanged / worsened / escalated，可空。 */
    private String outcome;

    /** 文字反馈，可空。 */
    private String comment;
}
