package com.edu.knowledge;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class KnowledgeSecurityTest {
    int request(String path,String header,String value,String mcp,String admin,String internal) throws Exception {
        var filter=new KnowledgeSecurityFilter(mcp,admin,internal);var req=new MockHttpServletRequest("POST",path);req.setServletPath(path);
        if(header!=null)req.addHeader(header,value);var res=new MockHttpServletResponse();
        filter.doFilter(req,res,(r,s)->((jakarta.servlet.http.HttpServletResponse)s).setStatus(204));return res.getStatus();
    }
    @Test void missingConfigurationIsClosedExceptHealth() throws Exception {
        for(String path:java.util.List.of("/mcp","/api/v1/rag/upsert","/api/v1/rag/retrieve")) assertThat(request(path,null,null,"","","")).isEqualTo(503);
        assertThat(request("/health",null,null,"","","")).isEqualTo(204);
    }
    @Test void credentialsCannotCrossPrivilegeBoundaries() throws Exception {
        assertThat(request("/api/v1/rag/upsert","X-MCP-Token","mcp","mcp","admin","internal")).isEqualTo(401);
        assertThat(request("/mcp","X-MCP-Token","mcp","mcp","admin","internal")).isEqualTo(204);
        assertThat(request("/api/v1/rag/upsert","X-Admin-Token","admin","mcp","admin","internal")).isEqualTo(204);
        assertThat(request("/api/v1/rag/retrieve","X-Internal-Token","internal","mcp","admin","internal")).isEqualTo(204);
        assertThat(request("/api/v1/rag/initialize","X-Internal-Token","internal","mcp","admin","internal")).isEqualTo(401);
    }
}
