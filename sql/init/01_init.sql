-- 师生资源画像系统数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS edu_portrait DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE edu_portrait;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像',
    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT NULL COMMENT '电话',
    user_type TINYINT DEFAULT 0 COMMENT '用户类型：0-管理员，1-教师，2-学生',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(50) NOT NULL COMMENT '角色名称',
    code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(50) NOT NULL COMMENT '权限名称',
    code VARCHAR(100) NOT NULL COMMENT '权限编码',
    type TINYINT DEFAULT 1 COMMENT '类型：1-菜单，2-按钮',
    parent_id BIGINT DEFAULT 0 COMMENT '父ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    icon VARCHAR(50) DEFAULT NULL COMMENT '图标',
    path VARCHAR(255) DEFAULT NULL COMMENT '路由路径',
    component VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
    status TINYINT DEFAULT 1 COMMENT '状态',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 教师信息表
CREATE TABLE IF NOT EXISTS teacher_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    employee_id VARCHAR(50) NOT NULL COMMENT '工号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender TINYINT DEFAULT NULL COMMENT '性别：0-女，1-男',
    birth_date DATE DEFAULT NULL COMMENT '出生日期',
    dept_id BIGINT DEFAULT NULL COMMENT '学院ID',
    dept_name VARCHAR(100) DEFAULT NULL COMMENT '学院名称',
    title VARCHAR(50) DEFAULT NULL COMMENT '职称',
    education VARCHAR(50) DEFAULT NULL COMMENT '学历',
    school VARCHAR(100) DEFAULT NULL COMMENT '毕业院校',
    major VARCHAR(100) DEFAULT NULL COMMENT '专业方向',
    research_area VARCHAR(500) DEFAULT NULL COMMENT '研究方向',
    join_date DATE DEFAULT NULL COMMENT '入职日期',
    status TINYINT DEFAULT 1 COMMENT '状态：0-离职，1-在职',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师信息表';

-- 学生信息表
CREATE TABLE IF NOT EXISTS student_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender TINYINT DEFAULT NULL COMMENT '性别：0-女，1-男',
    birth_date DATE DEFAULT NULL COMMENT '出生日期',
    dept_id BIGINT DEFAULT NULL COMMENT '学院ID',
    dept_name VARCHAR(100) DEFAULT NULL COMMENT '学院名称',
    major_id BIGINT DEFAULT NULL COMMENT '专业ID',
    major_name VARCHAR(100) DEFAULT NULL COMMENT '专业名称',
    grade VARCHAR(20) DEFAULT NULL COMMENT '年级',
    class_name VARCHAR(50) DEFAULT NULL COMMENT '班级',
    enrollment_date DATE DEFAULT NULL COMMENT '入学日期',
    expected_graduation DATE DEFAULT NULL COMMENT '预计毕业日期',
    gpa DECIMAL(3,2) DEFAULT 0.00 COMMENT 'GPA',
    credits INT DEFAULT 0 COMMENT '已修学分',
    status TINYINT DEFAULT 1 COMMENT '状态：0-退学，1-在读，2-毕业',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生信息表';

-- 插入默认数据（口令约定：password = username；U-9 轮换 2026-08-26，原哈希为来源不明孤儿凭据）
INSERT INTO sys_user (username, password, nickname, user_type, status) VALUES
('admin', '$2a$10$kSW6bhnZl4yxdqpyasyXSus06DUHTzsgkD3OZYvVed7P5UtykFKGe', '管理员', 0, 1),
('teacher', '$2a$10$OnV5fZmbtiEpppXjdpYrI.P02/zX9BnS0RRT/3F5oD5bY56bK0Z8.', '测试教师', 1, 1),
('student', '$2a$10$ng9lo6M8mJbf0xeT22Jx.OpKMNFls8YHpsnWIgDNTdXEq/0ZrBlxS', '测试学生', 2, 1);

INSERT INTO sys_role (name, code, description) VALUES
('系统管理员', 'admin', '拥有系统所有权限'),
('教师', 'teacher', '可以查看教师画像和学生信息'),
('学生', 'student', '可以查看自己的画像信息'),
('心理健康教师', 'mental_teacher', '可以管理心理健康相关功能');

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3);

-- 问卷表
CREATE TABLE IF NOT EXISTS mental_questionnaire (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    title VARCHAR(200) NOT NULL COMMENT '问卷标题',
    type VARCHAR(50) DEFAULT NULL COMMENT '问卷类型',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    questions INT DEFAULT 0 COMMENT '题目数量',
    start_time DATE DEFAULT NULL COMMENT '开始时间',
    end_time DATE DEFAULT NULL COMMENT '结束时间',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    template_content JSON DEFAULT NULL COMMENT '上传的问卷模板JSON原文'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理健康问卷表';

-- 问卷题目表
CREATE TABLE IF NOT EXISTS mental_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    questionnaire_id BIGINT NOT NULL COMMENT '所属问卷ID',
    sort_order INT DEFAULT 0 COMMENT '题目排序',
    content VARCHAR(1000) NOT NULL COMMENT '题目内容',
    question_type VARCHAR(30) NOT NULL COMMENT '题目类型：single_choice/multiple_choice/text/scale',
    options JSON DEFAULT NULL COMMENT '选项列表JSON',
    scoring_rules JSON DEFAULT NULL COMMENT '评分规则JSON',
    scale_min INT DEFAULT NULL COMMENT '量表最小值',
    scale_max INT DEFAULT NULL COMMENT '量表最大值',
    scale_labels JSON DEFAULT NULL COMMENT '量表两端标签JSON',
    required TINYINT DEFAULT 1 COMMENT '是否必答：0-否，1-是',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_questionnaire_id (questionnaire_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷题目表';

-- 问卷答题记录表
CREATE TABLE IF NOT EXISTS mental_response (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    questionnaire_id BIGINT NOT NULL COMMENT '问卷ID',
    answers JSON NOT NULL COMMENT '答题内容JSON',
    score INT DEFAULT 0 COMMENT '自动计算得分',
    status TINYINT DEFAULT 0 COMMENT '状态：0-已提交，1-已评估',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    UNIQUE KEY uk_student_questionnaire (student_id, questionnaire_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷答题记录表';

-- 心理评估表
CREATE TABLE IF NOT EXISTS mental_assessment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    questionnaire_id BIGINT NOT NULL COMMENT '问卷ID',
    score INT DEFAULT 0 COMMENT '得分',
    level VARCHAR(50) DEFAULT NULL COMMENT '等级：正常、轻度、中度、重度、高危',
    result VARCHAR(500) DEFAULT NULL COMMENT '评估结果',
    suggestion VARCHAR(500) DEFAULT NULL COMMENT '建议',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心理评估记录表';

-- 登录日志表
CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    role VARCHAR(50) DEFAULT NULL COMMENT '角色',
    ip VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    status TINYINT DEFAULT 1 COMMENT '状态：0-失败，1-成功'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- 插入默认教师数据
INSERT INTO teacher_info (user_id, employee_id, name, gender, dept_name, title, education, status) VALUES
(2, 'T2024001', '张老师', 1, '计算机学院', '副教授', '博士', 1),
(2, 'T2024002', '李老师', 0, '计算机学院', '教授', '博士', 1),
(2, 'T2024003', '王老师', 1, '软件学院', '讲师', '硕士', 1);

-- 插入默认学生数据
INSERT INTO student_info (user_id, student_id, name, gender, dept_name, major_name, grade, class_name, status) VALUES
(3, 'S2024001', '赵同学', 1, '计算机学院', '计算机科学与技术', '2024', '1班', 1),
(3, 'S2024002', '钱同学', 0, '计算机学院', '软件工程', '2024', '2班', 1),
(3, 'S2024003', '孙同学', 1, '软件学院', '人工智能', '2023', '1班', 1),
(3, 'S2024004', '周同学', 0, '软件学院', '数据科学', '2023', '2班', 1);

-- 插入默认问卷数据
INSERT INTO mental_questionnaire (title, type, description, questions, start_time, end_time, status) VALUES
('大学生心理健康普查', '普查', '学期初心理健康状况全面普查', 20, '2026-04-01', '2026-04-30', 1),
('焦虑自评量表 SAS', '焦虑', '焦虑症状自我评估问卷', 15, '2026-04-10', '2026-05-10', 1),
('抑郁自评量表 SDS', '抑郁', '抑郁症状自我评估问卷', 15, '2026-04-15', '2026-05-15', 1);

-- 插入默认心理评估数据
INSERT INTO mental_assessment (student_id, questionnaire_id, score, level, result, suggestion, status) VALUES
(1, 1, 85, '正常', '心理健康状况良好', '继续保持，适度运动', 1),
(2, 1, 72, '轻度', '存在轻微心理压力', '建议多参加集体活动', 1),
(3, 1, 45, '中度', '心理压力较大，需关注', '建议预约心理咨询', 1),
(4, 1, 35, '高危', '严重心理问题预警', '建议立即寻求专业帮助', 1);

-- 插入登录日志数据
INSERT INTO sys_login_log (user_id, username, role, ip, login_time, status) VALUES
(1, 'admin', '管理员', '192.168.1.102', DATE_SUB(NOW(), INTERVAL 10 MINUTE), 1),
(2, 'teacher', '教师', '192.168.1.100', DATE_SUB(NOW(), INTERVAL 30 MINUTE), 1),
(3, 'student', '学生', '192.168.1.101', DATE_SUB(NOW(), INTERVAL 45 MINUTE), 1),
(1, 'admin', '管理员', '192.168.1.103', DATE_SUB(NOW(), INTERVAL 2 HOUR), 1);

INSERT INTO sys_permission (name, code, type, parent_id, sort_order, icon, path) VALUES
('数据面板', 'dashboard', 1, 0, 1, 'DataLine', '/dashboard'),
('教师画像', 'teacher', 1, 0, 2, 'UserFilled', '/teacher'),
('教师列表', 'teacher:list', 2, 2, 1, '', '/teacher/list'),
('教师详情', 'teacher:detail', 2, 2, 2, '', '/teacher/detail'),
('学生画像', 'student', 1, 0, 3, 'Reading', '/student'),
('学生列表', 'student:list', 2, 5, 1, '', '/student/list'),
('学生详情', 'student:detail', 2, 5, 2, '', '/student/detail'),
('心理健康', 'mental', 1, 0, 4, 'FirstAidKit', '/mental'),
('心理概览', 'mental:overview', 2, 8, 1, '', '/mental/overview'),
('问卷管理', 'mental:questionnaire', 2, 8, 2, '', '/mental/questionnaire'),
('分析报告', 'mental:analysis', 2, 8, 3, '', '/mental/analysis'),
('填写问卷', 'mental:student', 2, 8, 4, '', '/mental/student'),
('系统管理', 'admin', 1, 0, 5, 'Setting', '/admin'),
('用户管理', 'admin:user', 2, 12, 1, '', '/admin/users'),
('角色权限', 'admin:role', 2, 12, 2, '', '/admin/roles');
