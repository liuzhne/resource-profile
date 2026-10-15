package com.edu.knowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class MilvusStoreTest {
    MilvusStore store;MockRestServiceServer server;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("http://milvus.test");server=MockRestServiceServer.bindTo(builder).build();
        store=new MilvusStore("http://milvus.test","",2,new ObjectMapper());ReflectionTestUtils.setField(store,"client",builder.build());
    }
    void expect(String path,String response) {server.expect(requestTo("http://milvus.test/v2/vectordb/"+path)).andRespond(withSuccess(response,MediaType.APPLICATION_JSON));}
    @Test void searchUsesStrongConsistencyAndReturnsProvenance() {
        expect("collections/load","{\"code\":200,\"data\":{}}");
        var reader=org.mockito.Mockito.mock(io.milvus.v2.client.MilvusClientV2.class);
        ReflectionTestUtils.setField(store,"reader",reader);
        org.mockito.Mockito.when(reader.search(org.mockito.ArgumentMatchers.any())).thenReturn(
            io.milvus.v2.service.vector.response.SearchResp.builder().searchResults(List.of(List.of(
                io.milvus.v2.service.vector.response.SearchResp.SearchResult.builder().distance(.8f)
                .entity(Map.of("chunk_id","doc_0000","content","evidence")).build()))).build());
        assertThat(store.search("edu_cases",new float[]{1,0},15)).singleElement().satisfies(r->assertThat(r).containsEntry("content","evidence"));
        org.mockito.Mockito.verify(reader).search(org.mockito.ArgumentMatchers.argThat(r->r.getConsistencyLevel()==io.milvus.v2.common.ConsistencyLevel.STRONG && r.getTopK()==15 && r.getAnnsField().equals("embedding")));
        server.verify();
    }
    @Test void appLevelErrorInHttp200IsNotSuccess() {
        expect("collections/load","{\"code\":1800,\"message\":\"private server payload\"}");
        assertThatThrownBy(()->store.search("edu_cases",new float[]{1,0},5)).hasMessage("Milvus operation failed");
    }
    @Test void oldDocumentIdsAreExactAndKeepInt64Precision() {
        expect("collections/load","{\"code\":0,\"data\":{}}");
        var reader=org.mockito.Mockito.mock(io.milvus.v2.client.MilvusClientV2.class);
        ReflectionTestUtils.setField(store,"reader",reader);
        org.mockito.Mockito.when(reader.query(org.mockito.ArgumentMatchers.any())).thenReturn(
            io.milvus.v2.service.vector.response.QueryResp.builder().queryResults(List.of(
                io.milvus.v2.service.vector.response.QueryResp.QueryResult.builder().entity(Map.of("id",9007199254740993L,"chunk_id","doc_0000")).build(),
                io.milvus.v2.service.vector.response.QueryResp.QueryResult.builder().entity(Map.of("id",2L,"chunk_id","doc_child_0000")).build())).build());
        assertThat(store.documentIds("cases","doc")).containsExactly(9007199254740993L);
        org.mockito.Mockito.verify(reader).query(org.mockito.ArgumentMatchers.argThat(r->r.getConsistencyLevel()==io.milvus.v2.common.ConsistencyLevel.STRONG));
        server.verify();
    }
    @Test void grpcFailureIsSanitized() {
        expect("collections/load","{\"code\":200,\"data\":{}}");
        var reader=org.mockito.Mockito.mock(io.milvus.v2.client.MilvusClientV2.class);
        ReflectionTestUtils.setField(store,"reader",reader);
        org.mockito.Mockito.when(reader.query(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("private endpoint and payload"));
        assertThatThrownBy(()->store.documentIds("cases","doc")).hasMessage("Milvus document lookup unavailable");
    }
    @Test void milvus241SuccessCode200IsAccepted() {
        expect("entities/insert","{\"code\":200,\"data\":{\"insertCount\":1}}");
        store.insert("cases",List.of(Map.of("chunk_id","doc_0000")));server.verify();
    }
    @Test void incompleteInsertIsRejected() {
        expect("entities/insert","{\"code\":0,\"data\":{\"insertCount\":0}}");
        assertThatThrownBy(()->store.insert("cases",List.of(Map.of("chunk_id","doc_0000")))).hasMessage("Incomplete Milvus insert");
    }
}
