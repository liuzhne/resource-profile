package com.edu.common.security;

/**
 * 系统角色码（与 auth-service 写入 JWT 的 {@code roles} claim、{@link FieldPermissionAdvice}
 * 用的角色字符串保持一致）。集中常量，避免各处散落字面量。
 */
public final class Roles {

    private Roles() {}

    public static final String ADMIN = "admin";
    public static final String TEACHER = "teacher";
    public static final String COUNSELOR = "counselor";
    public static final String PSYCHOLOGIST = "psychologist";
    public static final String ACADEMIC_ADVISOR = "academic_advisor";
    public static final String STUDENT = "student";

    /** 可查看学生敏感数据的教职工角色集（"本人或授权角色"里的授权角色）。 */
    public static final String[] STAFF_VIEW = {ADMIN, TEACHER, COUNSELOR, ACADEMIC_ADVISOR, PSYCHOLOGIST};

    /** 可写学生主数据的角色（增删改等管理操作）。 */
    public static final String[] STUDENT_WRITE = {ADMIN, TEACHER};
}
