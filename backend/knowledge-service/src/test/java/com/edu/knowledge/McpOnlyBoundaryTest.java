package com.edu.knowledge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.ai.mcp.server.enabled=true","knowledge.api-enabled=false","knowledge.mcp-token=mcp-test",
        "knowledge.admin-token=admin-test","knowledge.internal-token=internal-test"})
class McpOnlyBoundaryTest {
    @Autowired TestRestTemplate http;
    @MockBean RagService service;
    @Test void readOnlyMcpProcessCannotImportOrGenerateEvenWithValidApiCredentials() {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Admin-Token","admin-test");
        assertThat(http.postForEntity("/api/v1/rag/upsert",new HttpEntity<>("{}",headers),String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        headers.set("X-Internal-Token","internal-test");
        assertThat(http.postForEntity("/api/v1/agent/risk",new HttpEntity<>("{}",headers),String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/health",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);verifyNoInteractions(service);
    }
}
