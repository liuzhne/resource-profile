-- H-1.2：student-data MCP server 所需的成绩 / 考勤两张表
-- 与现有 student_info 表配合，给 4 个 MCP tool 提供数据

USE edu_portrait;

CREATE TABLE IF NOT EXISTS student_academic_record (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    student_id   BIGINT NOT NULL COMMENT 'student_info.id',
    term         VARCHAR(32) NOT NULL COMMENT '学期 e.g. 2025-2026-1',
    course_code  VARCHAR(64) NOT NULL COMMENT '课程编码',
    course_name  VARCHAR(128) NOT NULL COMMENT '课程名',
    credit       DECIMAL(4,1) NOT NULL COMMENT '学分',
    score        DECIMAL(5,2) DEFAULT NULL COMMENT '百分制成绩 0-100',
    grade_point  DECIMAL(3,2) DEFAULT NULL COMMENT '绩点 0-4',
    deleted      TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_sid_term (student_id, term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生成绩记录';

CREATE TABLE IF NOT EXISTS student_attendance (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    student_id   BIGINT NOT NULL COMMENT 'student_info.id',
    attend_date  DATE NOT NULL COMMENT '考勤日期',
    status       TINYINT NOT NULL COMMENT '0=出勤 1=迟到 2=缺勤 3=请假',
    course_code  VARCHAR(64) DEFAULT NULL COMMENT '课程编码',
    remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted      TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_sid_date (student_id, attend_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生考勤记录';

-- 种子数据：仅 student_id=1（init 已存在的 admin/test 学生），方便 H-1.4 smoke
INSERT INTO student_academic_record (student_id, term, course_code, course_name, credit, score, grade_point) VALUES
    (1, '2025-2026-1', 'CS101', '数据结构',   4.0, 88.0, 3.7),
    (1, '2025-2026-1', 'MA201', '高等数学',   5.0, 76.5, 3.0),
    (1, '2024-2025-2', 'CS100', '程序设计基础', 4.0, 92.0, 4.0);

INSERT INTO student_attendance (student_id, attend_date, status, course_code) VALUES
    (1, '2026-05-12', 0, 'CS101'),
    (1, '2026-05-13', 2, 'CS101'),
    (1, '2026-05-14', 1, 'MA201'),
    (1, '2026-05-15', 0, 'MA201');
