package com.edu.agent.security;

import com.edu.common.config.JwtProperties;
import com.edu.common.util.JwtUtil;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * AUTHZ-MCP-20261013：决定每次 MCP 出站请求携带哪个 {@code Authorization} 头。
 *
 * <p>优先级：
 * <ol>
 *   <li>线程上的 {@link McpCaller}：用户身份 → 原样转发用户 JWT；系统身份 → 现签短时系统令牌；</li>
 *   <li>否则若当前线程处于 HTTP 请求内且带 {@code Authorization: Bearer} → 转发该用户 JWT；</li>
 *   <li>否则不带身份（MCP server 对 tool 调用 fail-closed 拒绝；initialize / tools/list 不需要身份）。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class McpCallerTokenResolver {

    public static final String CONTEXT_KEY = "authorization";
    public static final String SYSTEM_IDENTITY_NAME = "agent-task";
    public static final String SCOPE_STUDENT_DATA_READ = "student-data:read";

    private final JwtUtil jwtUtil;

    /** 当前线程应携带的 Authorization 头值；没有身份时返回 {@code null}。 */
    public String currentAuthorization() {
        McpCaller caller = McpCallerContext.current();
        if (caller != null) {
            if (caller.isUser()) {
                return caller.userAuthorizationHeader();
            }
            return "Bearer " + jwtUtil.generateSystemToken(SYSTEM_IDENTITY_NAME,
                    JwtProperties.AUDIENCE_MCP_STUDENT_DATA, List.of(SCOPE_STUDENT_DATA_READ),
                    caller.studentId(), caller.initiator());
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String header = attrs.getRequest().getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                return header;
            }
        }
        return null;
    }

    /** 供 MCP client 的 transportContextProvider 在<b>调用线程</b>上捕获身份。 */
    public McpTransportContext currentTransportContext() {
        String auth = currentAuthorization();
        return auth == null ? McpTransportContext.EMPTY : McpTransportContext.create(Map.of(CONTEXT_KEY, auth));
    }
}
