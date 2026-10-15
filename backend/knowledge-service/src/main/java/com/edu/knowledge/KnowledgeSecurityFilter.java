package com.edu.knowledge;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class KnowledgeSecurityFilter extends OncePerRequestFilter {
    private final String mcp, admin, internal;
    public KnowledgeSecurityFilter(@Value("${knowledge.mcp-token:}") String mcp,
            @Value("${knowledge.admin-token:}") String admin, @Value("${knowledge.internal-token:}") String internal) {
        this.mcp=mcp; this.admin=admin; this.internal=internal;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path=request.getServletPath();
        if (path.equals("/health") || path.equals("/actuator/health")) { chain.doFilter(request,response); return; }
        String secret, header;
        if (path.equals("/mcp") || path.startsWith("/mcp/")) { secret=mcp; header="X-MCP-Token"; }
        else if (path.equals("/api/v1/rag/upsert") || path.equals("/api/v1/rag/initialize")) { secret=admin; header="X-Admin-Token"; }
        else { secret=internal; header="X-Internal-Token"; }
        if (secret.isBlank()) { response.setStatus(503); return; }
        String supplied=request.getHeader(header);
        if (supplied==null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401); return;
        }
        chain.doFilter(request,response);
    }
}
