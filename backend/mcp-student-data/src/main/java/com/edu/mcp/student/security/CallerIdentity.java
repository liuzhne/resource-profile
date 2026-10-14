package com.edu.mcp.student.security;

import java.util.List;
import java.util.Set;

/**
 * AUTHZ-MCP-20261013：MCP 工具调用方的已验证身份。
 *
 * @param kind            用户（端用户 JWT 转发）或系统（异步/预警任务的系统令牌）
 * @param userId          用户身份时为 sys_user.id；系统身份为 null
 * @param roles           用户角色（JWT {@code roles} claim）；系统身份为空
 * @param scopes          系统身份的 scope（如 {@code student-data:read}）；用户身份为空
 * @param boundStudentId  系统令牌绑定的学生（student_info.id）；用户身份为 null
 * @param initiator       发起者：用户身份为其 userId；系统身份取令牌 {@code initiator}（用户 id 或 scheduler）
 * @param subject         JWT subject，仅用于日志
 */
public record CallerIdentity(Kind kind, Long userId, Set<String> roles, List<String> scopes,
                             Long boundStudentId, String initiator, String subject) {

    public enum Kind { USER, SYSTEM }

    public static final String SCOPE_STUDENT_DATA_READ = "student-data:read";

    public boolean isSystem() {
        return kind == Kind.SYSTEM;
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
