-- PORTRAIT-CONTRACT-20261007: additive schema, no generated business records or contact backfill.
SET NAMES utf8mb4;
USE edu_portrait;
DROP PROCEDURE IF EXISTS p_portrait_add_column;
DELIMITER //
CREATE PROCEDURE p_portrait_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition VARCHAR(255))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND COLUMN_NAME=p_column) THEN
    SET @portrait_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE portrait_statement FROM @portrait_ddl;
    EXECUTE portrait_statement;
    DEALLOCATE PREPARE portrait_statement;
  END IF;
END //
DELIMITER ;
CALL p_portrait_add_column('teacher_info','political','VARCHAR(100) DEFAULT NULL COMMENT ''政治面貌（HIGH）''');
CALL p_portrait_add_column('teacher_info','phone','VARCHAR(30) DEFAULT NULL COMMENT ''教师档案个人电话（HIGH）''');
CALL p_portrait_add_column('teacher_info','email','VARCHAR(100) DEFAULT NULL COMMENT ''教师档案个人邮箱（HIGH）''');
DROP PROCEDURE IF EXISTS p_portrait_add_column;
-- Change future defaults only. Existing 0 values remain untouched because their provenance is unknown.
ALTER TABLE student_info ALTER COLUMN gpa SET DEFAULT NULL;
ALTER TABLE student_info ALTER COLUMN credits SET DEFAULT NULL;
CREATE TABLE IF NOT EXISTS teacher_portrait_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 teacher_id BIGINT NOT NULL,
 kind VARCHAR(20) NOT NULL COMMENT 'TEACHING / RESEARCH',
 title VARCHAR(200) NOT NULL,
 event_date DATE NOT NULL,
 description VARCHAR(2000) DEFAULT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_teacher_record (teacher_id,kind,event_date),
 CONSTRAINT fk_portrait_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS teacher_evaluation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 teacher_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL,
 event_date DATE NOT NULL,
 score DECIMAL(5,2) NOT NULL COMMENT '0-100；真实评价分数',
 satisfied TINYINT DEFAULT NULL COMMENT '1满意/0不满意/NULL未回答；统计以非空回答为分母',
 comment VARCHAR(2000) DEFAULT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_teacher_evaluation (teacher_id,event_date),
 CONSTRAINT fk_evaluation_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS student_quality_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 student_id BIGINT NOT NULL,
 kind VARCHAR(20) NOT NULL COMMENT 'COMPETITION / HONOR / VOLUNTEER',
 title VARCHAR(200) NOT NULL,
 event_date DATE NOT NULL,
 description VARCHAR(2000) DEFAULT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_student_quality (student_id,kind,event_date),
 CONSTRAINT fk_quality_student FOREIGN KEY (student_id) REFERENCES student_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Identity policy: do NOT add UNIQUE(user_id) or clean existing shared bindings here.
-- New bindings must reference an enabled student-type account and cannot introduce duplicates.
-- Existing unchanged shared bindings remain editable while administrators correct provenance separately.
-- If one-account-one-student is adopted later, review existing duplicates and historical assessment ownership,
-- define re-binding after logical deletion, then migrate accounts/records before any suitable uniqueness constraint.
