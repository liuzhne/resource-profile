-- FIELD-CONTRACT-REPAIR-20261007: additive mental contracts. No answer snapshot is invented.
SET NAMES utf8mb4;
DELIMITER //
DROP PROCEDURE IF EXISTS p_mental_contract_column//
CREATE PROCEDURE p_mental_contract_column(IN col_table VARCHAR(64), IN col_name VARCHAR(64), IN col_def VARCHAR(500))
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=col_table AND COLUMN_NAME=col_name) THEN
  SET @mental_ddl=CONCAT('ALTER TABLE `',col_table,'` ADD COLUMN `',col_name,'` ',col_def);
  PREPARE mental_stmt FROM @mental_ddl; EXECUTE mental_stmt; DEALLOCATE PREPARE mental_stmt;
 END IF;
END//
CALL p_mental_contract_column('mental_response','questionnaire_snapshot','JSON NULL COMMENT ''提交时安全题目快照；历史无快照不伪造''')//
CALL p_mental_contract_column('mental_assessment','response_id','BIGINT NULL COMMENT ''本次测评关联的答卷ID''')//
CALL p_mental_contract_column('mental_assessment','warning_status','VARCHAR(32) NOT NULL DEFAULT ''OPEN'' COMMENT ''OPEN/IN_PROGRESS/RESOLVED''')//
CALL p_mental_contract_column('mental_assessment','warning_note','VARCHAR(1000) NULL COMMENT ''仅心理专业角色可读处置说明''')//
CALL p_mental_contract_column('mental_assessment','warning_handled_by','BIGINT NULL COMMENT ''处置者用户ID''')//
CALL p_mental_contract_column('mental_assessment','warning_handled_at','DATETIME NULL COMMENT ''业务时区Asia/Shanghai''')//
DROP PROCEDURE p_mental_contract_column//
DROP PROCEDURE IF EXISTS p_mental_contract_index//
CREATE PROCEDURE p_mental_contract_index()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mental_assessment' AND INDEX_NAME='uk_mental_assessment_response') THEN
  ALTER TABLE mental_assessment ADD UNIQUE KEY uk_mental_assessment_response(response_id);
 END IF;
END//
CALL p_mental_contract_index()//
DROP PROCEDURE p_mental_contract_index//
DELIMITER ;
-- Only unambiguous historical pairs may be linked; multiple assessments remain unlinked.
UPDATE mental_assessment ma
JOIN (SELECT student_id, questionnaire_id, MIN(id) AS only_id FROM (SELECT id,student_id,questionnaire_id FROM mental_assessment) src GROUP BY student_id,questionnaire_id HAVING COUNT(*)=1) uq ON uq.only_id=ma.id
JOIN mental_response mr ON mr.student_id=ma.student_id AND mr.questionnaire_id=ma.questionnaire_id
SET ma.response_id=mr.id WHERE ma.response_id IS NULL;
-- Reconcile misleading metadata counts with the actual active question collection.
UPDATE mental_questionnaire q SET q.questions=(SELECT COUNT(*) FROM mental_question mq WHERE mq.questionnaire_id=q.id AND mq.deleted=0);
