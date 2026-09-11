-- EduCare Agent 子系统数据库初始化
-- 依赖：edu_portrait 数据库已在 01_init.sql 中创建
USE edu_portrait;

-- ========== 1. Agent 任务主表（4 阶段流水线状态机）==========
CREATE TABLE IF NOT EXISTS agent_task (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    student_id            BIGINT       NOT NULL COMMENT '学生ID',
    status                VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
        COMMENT '状态: PENDING/RISK_ANALYZING/KNOWLEDGE_RETRIEVING/PLAN_GENERATING/COMPLIANCE_CHECKING/COMPLETED/REJECTED/FAILED',
    risk_level            VARCHAR(16)  DEFAULT NULL COMMENT '风险等级: NONE/LOW/MEDIUM/HIGH',
    risk_score            INT          DEFAULT NULL COMMENT '风险分数 0-100',
    risk_type             VARCHAR(64)  DEFAULT NULL COMMENT '主要风险类型',
    risk_analysis_result  TEXT         DEFAULT NULL COMMENT '阶段1: 风险识别结果(JSON)',
    retrieved_knowledge   MEDIUMTEXT   DEFAULT NULL COMMENT '阶段2: RAG 召回知识片段(JSON Array)',
    intervention_plan     MEDIUMTEXT   DEFAULT NULL COMMENT '阶段3: 干预方案(JSON)',
    compliance_audit      TEXT         DEFAULT NULL COMMENT '阶段4: 合规审核结果(JSON)',
    created_at            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at          DATETIME     DEFAULT NULL COMMENT '完成时间',
    deleted               TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    INDEX idx_student_status (student_id, status),
    INDEX idx_status_created (status, created_at),
    INDEX idx_risk_level (risk_level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 任务表';

-- ========== 2. 干预报告（任务完成后聚合产物，用于前端展示与 PDF 导出）==========
CREATE TABLE IF NOT EXISTS intervention_report (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id           BIGINT       NOT NULL COMMENT '关联 agent_task.id',
    student_id        BIGINT       NOT NULL COMMENT '学生ID(冗余便于查询)',
    title             VARCHAR(255) DEFAULT NULL COMMENT '报告标题',
    summary           VARCHAR(512) DEFAULT NULL COMMENT '一句话摘要',
    risk_level        VARCHAR(16)  DEFAULT NULL COMMENT '风险等级',
    immediate_actions TEXT         DEFAULT NULL COMMENT '立即行动 JSON',
    long_term_plan    TEXT         DEFAULT NULL COMMENT '长期跟进 JSON',
    talk_outline      TEXT         DEFAULT NULL COMMENT '谈话提纲',
    resources         TEXT         DEFAULT NULL COMMENT '资源推荐 JSON Array',
    pdf_path          VARCHAR(255) DEFAULT NULL COMMENT 'PDF 导出路径',
    pushed            TINYINT      DEFAULT 0 COMMENT '是否已推送辅导员: 0-否 1-是',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      DEFAULT 0,
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '干预报告';

-- ========== 3. 知识引用回填（方便前端展示"本方案引用了哪些案例/政策"）==========
CREATE TABLE IF NOT EXISTS knowledge_citation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id         BIGINT       NOT NULL COMMENT '关联 agent_task.id',
    source_db       VARCHAR(32)  NOT NULL COMMENT '知识库: case/psychology/policy/success',
    chunk_id        VARCHAR(64)  NOT NULL COMMENT 'Milvus 中的原文档/段落ID',
    chunk_title     VARCHAR(255) DEFAULT NULL COMMENT '段落标题或案例名',
    chunk_excerpt   VARCHAR(1024) DEFAULT NULL COMMENT '段落摘要',
    relevance_score FLOAT        DEFAULT NULL COMMENT '召回相关度',
    rerank_score    FLOAT        DEFAULT NULL COMMENT '重排序分数',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识引用';

-- ========== 5. 报告异步导出任务（F-1：PDF 导出）==========
CREATE TABLE IF NOT EXISTS agent_export_task (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL COMMENT '关联 agent_task.id',
    user_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '触发导出的用户ID（0=未登录态/未解析）',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
        COMMENT '状态: PENDING/PROCESSING/DONE/FAILED',
    file_path   VARCHAR(512) DEFAULT NULL COMMENT '生成的 PDF 文件绝对路径',
    file_size   BIGINT       DEFAULT NULL COMMENT '文件字节数',
    err_msg     VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    finished_at DATETIME     DEFAULT NULL COMMENT '完成或失败时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_task_id (task_id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报告异步导出任务';

-- ========== 4. 合规审计日志（满足教育数据安全审计要求）==========
CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id      BIGINT       DEFAULT NULL COMMENT '关联任务(若有)',
    actor_role   VARCHAR(32)  DEFAULT NULL COMMENT '触发者角色: counselor/admin/system',
    actor_id     BIGINT       DEFAULT NULL COMMENT '触发者ID',
    action       VARCHAR(64)  NOT NULL COMMENT '操作: TRIGGER/VIEW_REPORT/EXPORT_PDF/REJECT...',
    target_type  VARCHAR(32)  DEFAULT NULL COMMENT '目标类型: student/task/report',
    target_id    VARCHAR(64)  DEFAULT NULL COMMENT '目标ID(stringified)',
    detail       TEXT         DEFAULT NULL COMMENT '详情JSON',
    audit_passed TINYINT      DEFAULT NULL COMMENT '若是合规检查: 1-通过 0-未通过',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (task_id),
    INDEX idx_action_time (action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计日志';
