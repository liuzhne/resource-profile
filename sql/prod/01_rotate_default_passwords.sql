-- ============================================================
-- 生产改密：把 sql/init/01_init.sql 种子账号的默认密码（=用户名）换成强密码
-- 背景：admin/teacher/student 出厂密码等于用户名，全新生产库初始化后必须改。
--
-- 步骤：
--   1) 生成 bcrypt hash（任选其一）：
--        python3 -c "import bcrypt;print(bcrypt.hashpw(b'你的强密码', bcrypt.gensalt(rounds=10)).decode())"
--        # 或在后端任意服务中： new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("你的强密码")
--   2) 用生成的 hash 替换下方 REPLACE_WITH_BCRYPT_*（保留单引号）。
--   3) 对生产库执行： mysql -u root -p edu_portrait < sql/prod/01_rotate_default_passwords.sql
--   4) 用新密码登录验证，确认旧密码（=用户名）已失效。
--
-- 注：未替换占位符前请勿执行——非法 hash 会让对应账号无法登录（虽更安全但非预期）。
-- ============================================================

UPDATE sys_user SET password = 'REPLACE_WITH_BCRYPT_ADMIN'   WHERE username = 'admin';
UPDATE sys_user SET password = 'REPLACE_WITH_BCRYPT_TEACHER' WHERE username = 'teacher';
UPDATE sys_user SET password = 'REPLACE_WITH_BCRYPT_STUDENT' WHERE username = 'student';

-- 可选：若生产不需要演示用 teacher/student 账号，直接停用（确认本项目 status 语义：1=启用/0=禁用）
-- UPDATE sys_user SET status = 0 WHERE username IN ('teacher', 'student');

-- 校验：确认三个账号 password 已非出厂值（输出应为 3 条且 hash 不再是默认）
SELECT username, LEFT(password, 7) AS hash_prefix, status FROM sys_user WHERE username IN ('admin','teacher','student');
