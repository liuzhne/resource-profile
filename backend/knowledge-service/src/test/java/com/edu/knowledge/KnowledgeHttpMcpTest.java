package com.edu.knowledge;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.ai.embedding.EmbeddingModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.ai.mcp.server.enabled=true","knowledge.mcp-token=mcp-test","knowledge.admin-token=admin-test",
        "knowledge.internal-token=internal-test","knowledge.embedding-dim=2"})
class KnowledgeHttpMcpTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @MockBean EmbeddingModel embedding;
    @MockBean MilvusStore store;
    @MockBean KnowledgeCache cache;
    @MockBean Reranker reranker;
    @BeforeEach void setup() {
        when(embedding.embed(anyString())).thenReturn(new float[]{1,0});
        when(store.search(anyString(),any(),anyInt())).thenReturn(List.of(Map.of("chunk_id","doc_0000","content","synthetic evidence","title","title","source","https://example.org/paper","score",.9)));
        when(reranker.rank(anyString(),anyList(),anyInt())).thenAnswer(c->new Reranker.Result(c.getArgument(1),false));
    }
    HttpHeaders headers(String key,String value) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(key!=null)headers.set(key,value);return headers;
    }
    @Test void httpAuthValidationAndRetrievalContractUseRealServletStack() {
        assertThat(http.getForEntity("/health",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        String request="{\"queries\":[\"synthetic query\"],\"sources\":[\"case\"],\"top_k\":5}";
        assertThat(http.postForEntity("/api/v1/rag/retrieve",new HttpEntity<>(request,headers(null,null)),String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var response=http.postForEntity("/api/v1/rag/retrieve",new HttpEntity<>(request,headers("X-Internal-Token","internal-test")),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(response.getBody()).contains("doc_0000","https://example.org/paper","\"fallback\":false");
        assertThat(http.postForEntity("/api/v1/rag/upsert",new HttpEntity<>("{}",headers("X-Admin-Token","admin-test")),String.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(http.postForEntity("/api/v1/rag/initialize",new HttpEntity<>("{}",headers("X-Internal-Token","internal-test")),String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(store,never()).initialize(any());
    }
    @Test void streamableMcpInitializesListsSameToolsAndCallsRag() throws Exception {
        var headers=headers("X-MCP-Token","mcp-test");headers.setAccept(List.of(MediaType.APPLICATION_JSON,MediaType.TEXT_EVENT_STREAM));
        String initialize="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"migration-test\",\"version\":\"1\"}}}";
        var init=http.postForEntity("/mcp",new HttpEntity<>(initialize,headers),String.class);
        assertThat(init.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(init.getBody()).contains("knowledge-rag");
        String session=init.getHeaders().getFirst("Mcp-Session-Id");assertThat(session).isNotBlank();
        headers.set("Mcp-Session-Id",session);headers.set("MCP-Protocol-Version","2025-03-26");
        http.postForEntity("/mcp",new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",headers),String.class);
        var tools=http.postForEntity("/mcp",new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",headers),String.class);
        assertThat(tools.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(tools.getBody()).contains("search_cases","search_policies","search_psychology","top_k");
        var call=http.postForEntity("/mcp",new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"search_cases\",\"arguments\":{\"query\":\"synthetic query\"}}}",headers),String.class);
        assertThat(call.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(call.getBody()).contains("doc_0000");
        verify(store).search(eq("edu_cases"),any(),eq(15));
        http.exchange("/mcp",HttpMethod.DELETE,new HttpEntity<>(headers),String.class);
    }
}
