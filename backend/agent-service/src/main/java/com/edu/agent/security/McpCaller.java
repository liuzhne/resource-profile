package com.edu.agent.security;

/**
 * AUTHZ-MCP-20261013：一次 Agent 任务执行期间调用 MCP 时使用的身份。
 *
 * <ul>
 *   <li>{@link #user}：用户在 HTTP 请求里触发的任务 —— 转发该用户的原始 JWT（{@code Authorization} 头原值），
 *       MCP server 按用户本人权限判定（教师只能看本院系学生等）。</li>
 *   <li>{@link #system}：无用户上下文的异步/预警任务（定时扫描等）—— 由 {@link McpCallerTokenResolver}
 *       现签一枚短时系统令牌：只含 {@code student-data:read} scope，绑定本任务学生，记录发起者。</li>
 * </ul>
 *
 * <p>用户 JWT 只存在于内存（闭包 → ThreadLocal），不落库、不写日志。
 */
public record McpCaller(String userAuthorizationHeader, Long studentId, String initiator) {

    public static McpCaller user(String authorizationHeader, Long studentId, String initiator) {
        return new McpCaller(authorizationHeader, studentId, initiator);
    }

    public static McpCaller system(Long studentId, String initiator) {
        return new McpCaller(null, studentId, initiator);
    }

    public boolean isUser() {
        return userAuthorizationHeader != null && !userAuthorizationHeader.isBlank();
    }

    @Override
    public String toString() {
        // 不打印 JWT
        return "McpCaller[" + (isUser() ? "user" : "system") + ", studentId=" + studentId
                + ", initiator=" + initiator + "]";
    }
}
