-- I-5.1：干预反馈闭环表。
-- 辅导员在干预方案落地一段时间（建议 1 个月）后回填评分与结果，用于闭环复盘与月度报表。
-- 与 agent_task 同库（edu_portrait），task_id 关联 agent_task.id。

CREATE TABLE IF NOT EXISTS intervention_feedback (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL COMMENT '关联 agent_task.id',
    student_id  BIGINT       NULL COMMENT '冗余学生 id，便于按生统计',
    counselor_id BIGINT      NULL COMMENT '提交反馈的辅导员 user id（0=未鉴权填充）',
    score       TINYINT      NOT NULL COMMENT '干预效果评分 1-5',
    outcome     VARCHAR(32)  NULL COMMENT '结果标签：improved/unchanged/worsened/escalated',
    comment     VARCHAR(1000) NULL COMMENT '文字反馈',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=删',
    PRIMARY KEY (id),
    KEY idx_task (task_id),
    KEY idx_student (student_id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='I-5 干预反馈闭环';
