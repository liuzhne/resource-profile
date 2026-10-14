package com.edu.mcp.student.security;

import com.edu.common.result.Result;
import com.edu.common.security.Roles;
import com.edu.mcp.student.feign.StudentServiceClient;
import com.edu.mcp.student.feign.TeacherServiceClient;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * AUTHZ-MCP-20261013：student-data MCP 每个 tool 执行前的水平授权（AccessGuard 语义下沉到 MCP）。
 *
 * <p>判定规则（只看已验证身份，<b>不信任模型填的 studentId 以外的任何入参</b>，studentId 本身也要过判定）：
 * <ul>
 *   <li>学生：只能读 {@code student_info.user_id == 自己 userId} 的学生；</li>
 *   <li>admin / psychologist：全校（与现有 STAFF_VIEW / EXTREME_VIEW 一致）；</li>
 *   <li>teacher / counselor / academic_advisor：只能读<b>本院系</b>（teacher_info.dept_id == student_info.dept_id）
 *       学生。仓库里没有"班主任-学生"关系表，院系是现有数据里唯一的归属关系（见 DECISIONS AUTHZ-MCP-20261013）；</li>
 *   <li>系统身份：必须带 {@code student-data:read} scope，且只能读令牌绑定的那个学生；</li>
 *   <li>其余（无角色、查不到学生/教师、下游异常）一律拒绝（fail-closed）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentAccessPolicy {

    /** McpTransportContext 中存放 Authorization 头的 key（见 McpServerTransportConfig）。 */
    public static final String AUTHORIZATION_KEY = "authorization";

    private final McpCallerAuthenticator authenticator;
    private final StudentServiceClient studentClient;
    private final TeacherServiceClient teacherClient;

    /** 从 Spring AI 注入的 ToolContext 取 MCP exchange → transport context → Authorization，再判定。 */
    public CallerIdentity authorize(ToolContext toolContext, Long studentId, String toolName) {
        return authorize(authorizationHeader(toolContext), studentId, toolName);
    }

    public CallerIdentity authorize(String authorizationHeader, Long studentId, String toolName) {
        CallerIdentity caller = authenticator.authenticate(authorizationHeader);
        if (studentId == null) {
            deny(caller, studentId, toolName, "studentId 为空");
        }
        if (caller.isSystem()) {
            if (caller.scopes() == null || !caller.scopes().contains(CallerIdentity.SCOPE_STUDENT_DATA_READ)) {
                deny(caller, studentId, toolName, "系统身份缺少 student-data:read scope");
            }
            if (!studentId.equals(caller.boundStudentId())) {
                deny(caller, studentId, toolName, "系统身份只能读取任务绑定的学生");
            }
            allow(caller, studentId, toolName);
            return caller;
        }
        if (caller.hasRole(Roles.ADMIN) || caller.hasRole(Roles.PSYCHOLOGIST)) {
            allow(caller, studentId, toolName);
            return caller;
        }
        Map<String, Object> student = fetchStudent(caller, studentId, toolName);
        if (caller.hasRole(Roles.TEACHER) || caller.hasRole(Roles.COUNSELOR)
                || caller.hasRole(Roles.ACADEMIC_ADVISOR)) {
            Long teacherDept = fetchTeacherDept(caller, studentId, toolName);
            Long studentDept = asLong(student.get("deptId"));
            if (teacherDept != null && teacherDept.equals(studentDept)) {
                allow(caller, studentId, toolName);
                return caller;
            }
            deny(caller, studentId, toolName, "该学生不属于当前教师所在院系");
        }
        if (caller.hasRole(Roles.STUDENT)) {
            if (Objects.equals(caller.userId(), asLong(student.get("userId")))) {
                allow(caller, studentId, toolName);
                return caller;
            }
            deny(caller, studentId, toolName, "学生只能查看本人数据");
        }
        deny(caller, studentId, toolName, "当前角色无权读取学生数据");
        return caller; // unreachable
    }

    static String authorizationHeader(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        McpSyncServerExchange exchange = McpToolUtils.getMcpExchange(toolContext).orElse(null);
        if (exchange == null) {
            return null;
        }
        McpTransportContext ctx = exchange.transportContext();
        Object v = ctx == null ? null : ctx.get(AUTHORIZATION_KEY);
        return v == null ? null : v.toString();
    }

    private Map<String, Object> fetchStudent(CallerIdentity caller, Long studentId, String toolName) {
        try {
            Result<Map<String, Object>> resp = studentClient.getStudentById(studentId);
            Map<String, Object> data = resp == null ? null : resp.getData();
            if (data != null) {
                return data;
            }
        } catch (Exception e) {
            log.warn("授权判定取学生档案失败 studentId={}: {}", studentId, e.getMessage());
        }
        deny(caller, studentId, toolName, "无法确认学生归属");
        return Map.of(); // unreachable
    }

    private Long fetchTeacherDept(CallerIdentity caller, Long studentId, String toolName) {
        try {
            Result<Map<String, Object>> resp = teacherClient.getByUserId(caller.userId());
            Map<String, Object> data = resp == null ? null : resp.getData();
            return data == null ? null : asLong(data.get("deptId"));
        } catch (Exception e) {
            log.warn("授权判定取教师档案失败 userId={}: {}", caller.userId(), e.getMessage());
            deny(caller, studentId, toolName, "无法确认教师归属");
            return null; // unreachable
        }
    }

    private static void allow(CallerIdentity caller, Long studentId, String toolName) {
        log.info("[MCP-AUTHZ] allow tool={} studentId={} caller={} kind={} initiator={}",
                toolName, studentId, caller.subject(), caller.kind(), caller.initiator());
    }

    private static void deny(CallerIdentity caller, Long studentId, String toolName, String reason) {
        log.warn("[MCP-AUTHZ] deny tool={} studentId={} caller={} kind={} initiator={} reason={}",
                toolName, studentId, caller.subject(), caller.kind(), caller.initiator(), reason);
        throw new McpAccessDeniedException("无权访问该学生数据：" + reason);
    }

    private static Long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
