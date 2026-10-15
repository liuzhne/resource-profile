-- AI-PROD-FIX-20261011: additive safe diagnostics; UTC lifecycle time, no inferred historical failure cause.
SET NAMES utf8mb4;
DELIMITER //
DROP PROCEDURE IF EXISTS p_agent_diagnostics//
CREATE PROCEDURE p_agent_diagnostics()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_task' AND COLUMN_NAME='failure_code') THEN
  ALTER TABLE agent_task ADD COLUMN failure_code VARCHAR(48) NULL COMMENT '固定白名单错误类别，不保存原始异常';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_task' AND COLUMN_NAME='failure_stage') THEN
  ALTER TABLE agent_task ADD COLUMN failure_stage VARCHAR(32) NULL COMMENT '失败阶段固定枚举';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_task' AND COLUMN_NAME='failure_http_status') THEN
  ALTER TABLE agent_task ADD COLUMN failure_http_status SMALLINT NULL COMMENT '供应商HTTP错误状态，无值时保持未知';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_task' AND COLUMN_NAME='trace_id') THEN
  ALTER TABLE agent_task ADD COLUMN trace_id CHAR(32) NULL COMMENT '真实Langfuse根trace标识';
 END IF;
 -- Approximate historical end time from the last persisted update; preserve ON UPDATE timestamp.
 -- Never fabricate a provider category/status or a trace association for old failures.
 UPDATE agent_task SET completed_at=updated_at, updated_at=updated_at WHERE deleted=0 AND status IN ('FAILED','REJECTED','COMPLETED') AND completed_at IS NULL AND updated_at IS NOT NULL;
END//
CALL p_agent_diagnostics()//
DROP PROCEDURE p_agent_diagnostics//
DELIMITER ;
