package com.edu.mcp.student.security;

import com.edu.common.config.JwtProperties;
import com.edu.common.result.Result;
import com.edu.common.util.JwtUtil;
import com.edu.mcp.student.feign.MentalServiceClient;
import com.edu.mcp.student.feign.StudentServiceClient;
import com.edu.mcp.student.feign.TeacherServiceClient;
import com.edu.mcp.student.tool.StudentDataTools;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AUTHZ-MCP-20261013：student-data MCP 4 个 tool 的身份与水平授权测试。
 *
 * <p>数据：学生 S1（student_info.id=11, user_id=101, dept=1）、S2（id=22, user_id=202, dept=2）；
 * 教师 T（user_id=301, dept=1）。所有令牌用真实 {@link JwtUtil} 签发/校验，只 mock 下游 Feign。
 */
class StudentDataToolsAuthorizationTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long!!";

    private JwtUtil jwtUtil;
    private StudentServiceClient studentClient;
    private MentalServiceClient mentalClient;
    private TeacherServiceClient teacherClient;
    private StudentDataTools tools;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        jwtUtil = new JwtUtil(props);
        jwtUtil.init();
        studentClient = mock(StudentServiceClient.class);
        mentalClient = mock(MentalServiceClient.class);
        teacherClient = mock(TeacherServiceClient.class);
        StudentAccessPolicy policy = new StudentAccessPolicy(
                new McpCallerAuthenticator(jwtUtil, false), studentClient, teacherClient);
        tools = new StudentDataTools(studentClient, mentalClient, policy);

        when(studentClient.getStudentById(11L)).thenReturn(Result.success(
                Map.of("id", 11, "userId", 101, "deptId", 1, "name", "S1")));
        when(studentClient.getStudentById(22L)).thenReturn(Result.success(
                Map.of("id", 22, "userId", 202, "deptId", 2, "name", "S2")));
        when(teacherClient.getByUserId(301L)).thenReturn(Result.success(Map.of("userId", 301, "deptId", 1)));
        when(studentClient.listAcademic(anyLong(), any())).thenReturn(Result.success(List.of()));
        when(mentalClient.myHistory(anyLong())).thenReturn(Result.success(List.of()));
    }

    private String userToken(long userId, String... roles) {
        return jwtUtil.generateAccessToken(String.valueOf(userId), Map.of("roles", List.of(roles)));
    }

    private static ToolContext ctx(String bearerToken) {
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.transportContext()).thenReturn(bearerToken == null ? McpTransportContext.EMPTY
                : McpTransportContext.create(Map.of(StudentAccessPolicy.AUTHORIZATION_KEY, "Bearer " + bearerToken)));
        return new ToolContext(Map.of("exchange", exchange));
    }

    // ---------------- 允许路径 ----------------

    @Test
    void student_canReadOwnData() {
        var dto = tools.getStudentProfile(11L, ctx(userToken(101, "student")));
        assertThat(dto.getName()).isEqualTo("S1");
    }

    @Test
    void teacher_canReadStudentInOwnDept() {
        var dto = tools.getStudentProfile(11L, ctx(userToken(301, "teacher")));
        assertThat(dto.getName()).isEqualTo("S1");
    }

    @Test
    void admin_canReadAnyStudent() {
        assertThat(tools.getStudentProfile(22L, ctx(userToken(1, "admin"))).getName()).isEqualTo("S2");
    }

    @Test
    void systemIdentity_canReadOnlyBoundStudent() {
        String sys = jwtUtil.generateSystemToken("agent-task", JwtProperties.AUDIENCE_MCP_STUDENT_DATA,
                List.of(CallerIdentity.SCOPE_STUDENT_DATA_READ), 11L, "scheduler");
        assertThat(tools.getStudentProfile(11L, ctx(sys)).getName()).isEqualTo("S1");
        assertThatThrownBy(() -> tools.getStudentProfile(22L, ctx(sys)))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void systemIdentity_withoutScope_isRejected() {
        String sys = jwtUtil.generateSystemToken("agent-task", JwtProperties.AUDIENCE_MCP_STUDENT_DATA,
                List.of(), 11L, "scheduler");
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(sys)))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    // ---------------- 越权路径（要求覆盖） ----------------

    @Test
    void student_readingSomeoneElse_isRejected_onAllTools() {
        ToolContext c = ctx(userToken(101, "student"));
        assertThatThrownBy(() -> tools.getStudentProfile(22L, c)).isInstanceOf(McpAccessDeniedException.class);
        assertThatThrownBy(() -> tools.getAcademicHistory(22L, null, c)).isInstanceOf(McpAccessDeniedException.class);
        assertThatThrownBy(() -> tools.getMentalIndicators(22L, c)).isInstanceOf(McpAccessDeniedException.class);
        assertThatThrownBy(() -> tools.getAttendance(22L, null, null, c)).isInstanceOf(McpAccessDeniedException.class);
        verify(studentClient, never()).listAcademic(anyLong(), any());
        verify(mentalClient, never()).myHistory(anyLong());
        verify(studentClient, never()).attendanceSummary(anyLong(), any(), any());
    }

    @Test
    void teacher_readingStudentNotTheirs_isRejected() {
        assertThatThrownBy(() -> tools.getMentalIndicators(22L, ctx(userToken(301, "teacher"))))
                .isInstanceOf(McpAccessDeniedException.class);
        verify(mentalClient, never()).myHistory(anyLong());
    }

    @Test
    void teacherWithoutTeacherProfile_isRejected() {
        when(teacherClient.getByUserId(999L)).thenReturn(Result.success(null));
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(userToken(999, "teacher"))))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void forgedToken_isRejected() {
        String forged = Jwts.builder().subject("1").claim("roles", List.of("admin"))
                .audience().add(JwtProperties.AUDIENCE_MCP_STUDENT_DATA).and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("attacker-secret-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(forged)))
                .isInstanceOf(McpAccessDeniedException.class);
        verify(studentClient, never()).getStudentById(anyLong());
    }

    @Test
    void expiredToken_isRejected() {
        String expired = Jwts.builder().subject("1").claim("roles", List.of("admin"))
                .audience().add(JwtProperties.AUDIENCE_MCP_STUDENT_DATA).and()
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(expired)))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void wrongAudience_isRejected_evenInCompatMode() {
        String wrongAud = Jwts.builder().subject("1").claim("roles", List.of("admin"))
                .audience().add("some-other-service").and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(wrongAud)))
                .isInstanceOf(McpAccessDeniedException.class);
        McpCallerAuthenticator compat = new McpCallerAuthenticator(jwtUtil, true);
        assertThatThrownBy(() -> compat.authenticate("Bearer " + wrongAud))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void missingAudience_rejectedByDefault_acceptedOnlyInCompatMode() {
        String legacy = Jwts.builder().subject("1").claim("roles", List.of("admin"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(legacy)))
                .isInstanceOf(McpAccessDeniedException.class);
        CallerIdentity id = new McpCallerAuthenticator(jwtUtil, true).authenticate("Bearer " + legacy);
        assertThat(id.userId()).isEqualTo(1L);
    }

    @Test
    void missingToken_isRejected() {
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(null)))
                .isInstanceOf(McpAccessDeniedException.class);
        assertThatThrownBy(() -> tools.getStudentProfile(11L, null))
                .isInstanceOf(McpAccessDeniedException.class);
        verify(studentClient, never()).getStudentById(anyLong());
    }

    @Test
    void refreshToken_cannotBeUsed_becauseItHasNoAudience() {
        String refresh = jwtUtil.generateRefreshToken("1");
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(refresh)))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    /** 走 Spring AI 的 MethodToolCallback：模型给的 studentId 也必须过授权，ToolContext 不在 schema 里。 */
    @Test
    void modelSuppliedStudentId_viaToolCallback_cannotBypass() {
        ToolCallback cb = Arrays.stream(MethodToolCallbackProvider.builder().toolObjects(tools).build()
                        .getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals("get_student_profile"))
                .findFirst().orElseThrow();
        assertThat(cb.getToolDefinition().inputSchema()).doesNotContain("toolContext");
        ToolContext c = ctx(userToken(101, "student"));
        assertThat(cb.call("{\"studentId\":11}", c)).contains("S1");
        assertThatThrownBy(() -> cb.call("{\"studentId\":22}", c)).hasRootCauseInstanceOf(McpAccessDeniedException.class);
    }

    // ---------------- fail-closed 边界 ----------------

    @Test
    void downstreamErrors_failClosed() {
        when(studentClient.getStudentById(33L)).thenThrow(new RuntimeException("student-service down"));
        assertThatThrownBy(() -> tools.getStudentProfile(33L, ctx(userToken(101, "student"))))
                .isInstanceOf(McpAccessDeniedException.class);
        when(studentClient.getStudentById(44L)).thenReturn(Result.success(null));
        assertThatThrownBy(() -> tools.getStudentProfile(44L, ctx(userToken(101, "student"))))
                .isInstanceOf(McpAccessDeniedException.class);
        when(teacherClient.getByUserId(302L)).thenThrow(new RuntimeException("teacher-service down"));
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(userToken(302, "counselor"))))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void psychologist_allowed_noRole_andNullStudent_rejected() {
        assertThat(tools.getStudentProfile(22L, ctx(userToken(5, "psychologist"))).getName()).isEqualTo("S2");
        assertThatThrownBy(() -> tools.getStudentProfile(11L, ctx(userToken(6))))
                .isInstanceOf(McpAccessDeniedException.class);
        assertThatThrownBy(() -> tools.getStudentProfile(null, ctx(userToken(1, "admin"))))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void malformedHeaders_rejected() {
        McpCallerAuthenticator a = new McpCallerAuthenticator(jwtUtil, false);
        assertThatThrownBy(() -> a.authenticate("Basic abc")).isInstanceOf(McpAccessDeniedException.class);
        assertThatThrownBy(() -> a.authenticate("Bearer   ")).isInstanceOf(McpAccessDeniedException.class);
        String nonNumeric = Jwts.builder().subject("alice")
                .audience().add(JwtProperties.AUDIENCE_MCP_STUDENT_DATA).and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThatThrownBy(() -> a.authenticate("Bearer " + nonNumeric)).isInstanceOf(McpAccessDeniedException.class);
        assertThat(StudentAccessPolicy.authorizationHeader(new ToolContext(Map.of()))).isNull();
    }
}
