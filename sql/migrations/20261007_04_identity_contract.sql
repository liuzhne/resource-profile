-- FIELD-CONTRACT-FIX-20261007: additive identity contracts; no credentials or business backfill.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS sys_schema_migration (
 version VARCHAR(100) PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
DELIMITER //
DROP PROCEDURE IF EXISTS p_identity_contract//
CREATE PROCEDURE p_identity_contract()
BEGIN
 DECLARE identity_guard BIGINT DEFAULT NULL;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND COLUMN_NAME='dept_name') THEN
  ALTER TABLE sys_user ADD COLUMN dept_name VARCHAR(100) NULL COMMENT '管理员维护的账号部门';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND COLUMN_NAME='bio') THEN
  ALTER TABLE sys_user ADD COLUMN bio VARCHAR(1000) NULL COMMENT '本人维护的个人简介';
 END IF;
 START TRANSACTION;
 SELECT id INTO identity_guard FROM sys_role WHERE code='admin' AND deleted=0 FOR UPDATE;
 IF identity_guard IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing canonical admin role; identity migration refused'; END IF;
 IF NOT EXISTS(SELECT 1 FROM sys_schema_migration WHERE version='20261007_identity_catalog') THEN
  IF EXISTS(SELECT 1 FROM sys_role WHERE code='mental_teacher' AND status=1 AND deleted=0)
   AND EXISTS(SELECT 1 FROM sys_role WHERE code='psychologist' AND (status<>1 OR deleted<>0)) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Active legacy mental role conflicts with inactive psychologist; administrator review required';
  END IF;
  INSERT INTO sys_role(name,code,description) SELECT '心理教师','psychologist','心理专业人员；敏感测评及干预操作' WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE code='psychologist');
  INSERT INTO sys_role(name,code,description) SELECT '辅导员','counselor','授权范围内的学生与预警摘要' WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE code='counselor');
  INSERT INTO sys_role(name,code,description) SELECT '学业导师','academic_advisor','授权范围内的学生及学业摘要' WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE code='academic_advisor');
  INSERT IGNORE INTO sys_user_role(user_id,role_id) SELECT ur.user_id,canonical.id FROM sys_user_role ur JOIN sys_role legacy ON legacy.id=ur.role_id JOIN sys_role canonical ON canonical.code='psychologist' WHERE legacy.code='mental_teacher' AND legacy.status=1 AND legacy.deleted=0;
  INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT canonical.id,rp.permission_id FROM sys_role_permission rp JOIN sys_role legacy ON legacy.id=rp.role_id JOIN sys_role canonical ON canonical.code='psychologist' WHERE legacy.code='mental_teacher' AND legacy.status=1 AND legacy.deleted=0;
  UPDATE sys_role SET deleted=1,status=0 WHERE code='mental_teacher';
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '教师查看','teacher:read',2,0 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='teacher:read' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '教师维护','teacher:write',2,1 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='teacher:write' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '学生查看','student:read',2,2 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='student:read' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '学生维护','student:write',2,3 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='student:write' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '心理查看','mental:read',2,4 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='mental:read' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '心理维护','mental:write',2,5 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='mental:write' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '面板查看','data:read',2,6 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='data:read' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT 'Agent查看','agent:read',2,7 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='agent:read' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT 'Agent执行及反馈','agent:write',2,8 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='agent:write' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '账号查看','user:read',2,9 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='user:read' AND deleted=0);
  INSERT INTO sys_permission(name,code,type,sort_order) SELECT '账号维护','user:write',2,10 WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE code='user:write' AND deleted=0);
  -- Catalog permissions restrict staff management endpoints; student self-service uses verified ownership.
  -- Default grants stay within canonical controller role bounds. Admin retains its fixed maintenance bypass.
  INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
   SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.deleted=0 AND p.deleted=0 AND p.status=1 AND (
    (r.code='admin' AND p.code IN ('teacher:read','teacher:write','student:read','student:write','mental:read','mental:write','data:read','agent:read','agent:write','user:read','user:write')) OR
    (r.code IN ('teacher','psychologist','counselor','academic_advisor') AND p.code IN ('teacher:read','student:read','mental:read','data:read','agent:read')) OR
    (r.code='teacher' AND p.code='student:write') OR
    (r.code='psychologist' AND p.code IN ('mental:write','agent:write')) OR
    (r.code IN ('teacher','counselor','academic_advisor') AND p.code='agent:write')
   );
  INSERT INTO sys_schema_migration(version) VALUES('20261007_identity_catalog');
 END IF;
 COMMIT;
END//
CALL p_identity_contract()//
DROP PROCEDURE p_identity_contract//
DELIMITER ;
CREATE TABLE IF NOT EXISTS sys_account_request (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 type VARCHAR(20) NOT NULL COMMENT 'OPEN_ACCOUNT / PASSWORD_RECOVERY',
 username VARCHAR(50) NULL,
 contact VARCHAR(100) NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 resolution VARCHAR(1000) NULL,
 handled_by BIGINT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_request_status(status,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员人工核验开通与找回申请；不自动重置密码';
