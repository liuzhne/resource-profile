-- docker-entrypoint-initdb.d 的 mysql 客户端默认按 latin1 连接，不显式声明会把中文种子数据双重编码
SET NAMES utf8mb4;

-- 问卷调查功能扩展（学生作答闭环）
-- 在 01_init.sql 已建立 mental_questionnaire / mental_assessment 基础上，
-- 补充：等级规则、自增 ID 修复、问题题库、响应记录。

-- 修复历史 schema 中 id 缺失 AUTO_INCREMENT 的问题
ALTER TABLE mental_questionnaire MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE mental_assessment    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

-- 条件添加列（MySQL 8 没有 ADD COLUMN IF NOT EXISTS）
DROP PROCEDURE IF EXISTS p_add_col_if_missing;
DELIMITER //
CREATE PROCEDURE p_add_col_if_missing(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_def);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL p_add_col_if_missing('mental_questionnaire', 'level_rules', 'JSON DEFAULT NULL COMMENT ''等级规则JSON''');
CALL p_add_col_if_missing('mental_questionnaire', 'creator_id',  'BIGINT DEFAULT NULL COMMENT ''创建者用户ID''');

DROP PROCEDURE IF EXISTS p_add_col_if_missing;

-- 题目表（若已存在则保留）
CREATE TABLE IF NOT EXISTS mental_question (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    questionnaire_id BIGINT       NOT NULL COMMENT '所属问卷ID',
    sort_order       INT          DEFAULT 0    COMMENT '排序',
    content          VARCHAR(1000) NOT NULL    COMMENT '题干',
    question_type    VARCHAR(30)  NOT NULL     COMMENT '题型：single_choice/multiple_choice/text',
    options          JSON         DEFAULT NULL COMMENT '选项JSON：[{label,score}]',
    required         TINYINT      DEFAULT 1    COMMENT '是否必答：0/1',
    deleted          TINYINT      DEFAULT 0    COMMENT '逻辑删除',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_questionnaire_id (questionnaire_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷题目表';

-- 学生作答原始记录（一行一份答卷，answers 内含每题作答）
CREATE TABLE IF NOT EXISTS mental_response (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    student_id       BIGINT NOT NULL COMMENT '学生ID（student_info.id）',
    questionnaire_id BIGINT NOT NULL COMMENT '问卷ID',
    answers          JSON   NOT NULL COMMENT '答卷JSON：[{questionId,optionIndices,text}]',
    score            INT    DEFAULT 0 COMMENT '总分',
    status           TINYINT DEFAULT 1 COMMENT '状态：1-已提交',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_student_questionnaire (student_id, questionnaire_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷作答记录表';

-- 给已有问卷写入默认等级规则
UPDATE mental_questionnaire SET level_rules =
    JSON_ARRAY(
        JSON_OBJECT('level','正常','minScore',85,'suggestion','心理状态良好，继续保持适度运动与社交。'),
        JSON_OBJECT('level','轻度','minScore',70,'suggestion','存在轻微心理压力，建议自我调节并多参加集体活动。'),
        JSON_OBJECT('level','中度','minScore',50,'suggestion','心理压力较大，建议预约心理咨询。'),
        JSON_OBJECT('level','重度','minScore',30,'suggestion','建议尽快寻求专业心理援助。'),
        JSON_OBJECT('level','高危','minScore',0, 'suggestion','存在严重风险，请立即联系心理健康中心或专业机构。')
    )
WHERE level_rules IS NULL;
