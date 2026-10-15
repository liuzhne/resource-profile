package com.edu.knowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.ai.embedding.EmbeddingModel;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RagServiceTest {
    EmbeddingModel embedding;MilvusStore store;KnowledgeCache cache;Reranker reranker;RagService service;
    Map<String,String> values;
    @BeforeEach void setup() {
        embedding=mock(EmbeddingModel.class);store=mock(MilvusStore.class);cache=mock(KnowledgeCache.class);reranker=mock(Reranker.class);
        values=new HashMap<>();when(cache.get(anyString())).thenAnswer(c->values.get(c.getArgument(0)));
        doAnswer(c->{values.put(c.getArgument(0),c.getArgument(1));return null;}).when(cache).put(anyString(),anyString(),anyLong());
        doAnswer(c->{values.remove(c.getArgument(0));return null;}).when(cache).remove(anyString());
        when(cache.lock(anyString(),anyString())).thenAnswer(c->{String key=c.getArgument(0);if(values.containsKey(key))return false;values.put(key,c.getArgument(1));return true;});
        doAnswer(c->{values.remove(c.getArgument(0));return null;}).when(cache).unlock(anyString(),anyString());
        when(embedding.embed(anyString())).thenReturn(new float[]{1,0});
        when(store.documentIds(anyString(),anyString())).thenReturn(List.of(7L));
        when(reranker.rank(anyString(),anyList(),anyInt())).thenAnswer(c->new Reranker.Result(c.getArgument(1),false));
        service=new RagService(embedding,store,cache,new ObjectMapper(),reranker,2,3,3600,86400,604800,
                "http://embedding.test/v1","model","cases","psy","policy","success");
    }
    Map<String,Object> hit(String id,double score) {return Map.of("chunk_id",id,"content","evidence","title","title","source","https://example.org/paper","score",score);}
    @Test void multiQueryDeduplicatesWithinCollectionButKeepsSameIdAcrossCollectionsAndPreservesSource() {
        when(store.search(anyString(),any(),anyInt())).thenReturn(List.of(hit("doc_0000",.2),hit("doc_0000",.9)));
        var result=service.retrieve(new RagContracts.Retrieve(List.of("q1","q2"),5,List.of("case","policy"),null));
        List<?> chunks=(List<?>)result.get("chunks");assertThat(chunks).hasSize(2);
        assertThat((Map<String,Object>)chunks.getFirst()).containsEntry("score",.9).containsEntry("doc_id","doc").containsEntry("source_url","https://example.org/paper");
        service.retrieve(new RagContracts.Retrieve(List.of("q1","q2"),5,List.of("case","policy"),null));
        verify(store,times(4)).search(anyString(),any(),anyInt());
    }
    @Test void revisionInvalidatesRetrievalCache() {
        when(store.search(anyString(),any(),anyInt())).thenReturn(List.of(hit("doc_0000",1)));
        var query=new RagContracts.Retrieve(List.of("q"),5,List.of("case"),null);
        service.retrieve(query);values.put("edu:rag:revision:case","new");service.retrieve(query);
        verify(store,times(2)).search(anyString(),any(),anyInt());verify(embedding,times(1)).embed("q");
    }
    @Test void emptySearchIsHonestAndUnknownCollectionRejectedBeforeEmbedding() {
        when(store.search(anyString(),any(),anyInt())).thenReturn(List.of());
        assertThat(service.search("q","case",100)).containsEntry("fallback",true).containsEntry("count",0).containsEntry("top_k",20);
        assertThatThrownBy(()->service.search("q","invented",5)).isInstanceOf(KnowledgeException.class);
        verify(embedding,times(1)).embed(anyString());
    }
    @Test void unavailableStoreIsNotMisreportedAsNoEvidence() {
        when(store.search(anyString(),any(),anyInt())).thenThrow(new KnowledgeException(503,"Milvus unavailable"));
        assertThatThrownBy(()->service.search("q","case",5)).hasMessage("Milvus unavailable");
    }
    @Test void invalidVectorsCannotReachSearchOrMutation() {
        for(float[] vector:List.of(new float[]{0,0},new float[]{1},new float[]{Float.NaN,1},new float[]{1,Float.POSITIVE_INFINITY})) {
            when(embedding.embed(anyString())).thenReturn(vector);
            assertThatThrownBy(()->service.search("q","case",5)).hasMessage("Invalid embedding vector");
            assertThatThrownBy(()->service.upsert(doc("text","title"))).hasMessage("Invalid embedding vector");
        }
        verifyNoInteractions(store);
    }
    RagContracts.Upsert doc(String text,String title) {return new RagContracts.Upsert("case","doc",text,Map.of("title",title,"source_url","https://example.org/paper"),null);}
    @Test void idempotencyIncludesMetadataAndInsertsBeforeExactOldIdsAreDeleted() {
        assertThat(service.upsert(doc("text","old"))).containsEntry("skipped",false).containsEntry("chunks_written",1);
        assertThat(service.upsert(doc("text","old"))).containsEntry("skipped",true);
        assertThat(service.upsert(doc("text","new"))).containsEntry("skipped",false);
        var order=inOrder(store);order.verify(store).documentIds("cases","doc");order.verify(store).insert(eq("cases"),anyList());order.verify(store).deleteIds("cases",List.of(7L));
        verify(store,times(2)).insert(eq("cases"),anyList());
    }
    @Test void failedInsertKeepsOldRowsReleasesLockAndDoesNotMarkSuccessfulHash() {
        doThrow(new KnowledgeException(503,"insert failed")).when(store).insert(anyString(),anyList());
        assertThatThrownBy(()->service.upsert(doc("text","title"))).hasMessage("insert failed");
        verify(store,never()).deleteIds(anyString(),anyList());
        assertThat(values.keySet()).noneMatch(k->k.startsWith("edu:rag:upsert:") || k.startsWith("edu:rag:write-lock:"));
    }
    @Test void partialReplacementInvalidatesPriorSuccessHashSoRestoringOldContentDoesNotSkip() {
        service.upsert(doc("old","title"));
        doThrow(new KnowledgeException(503,"partial insert")).doNothing().when(store).insert(anyString(),anyList());
        assertThatThrownBy(()->service.upsert(doc("new","title"))).hasMessage("partial insert");
        assertThat(service.upsert(doc("old","title"))).containsEntry("skipped",false);
        verify(store,times(3)).insert(anyString(),anyList());
    }
    @Test void concurrentWriteAndReadFailClosed() {
        values.put("edu:rag:write-lock:case","other");
        assertThatThrownBy(()->service.upsert(doc("text","title"))).hasMessage("Knowledge update in progress");
        assertThatThrownBy(()->service.search("q","case",5)).hasMessage("Knowledge update in progress");verifyNoInteractions(embedding,store);
    }
    @Test void provenanceNeverInventsDocumentOrUrlForLegacyMarkers() {
        var row=RagService.provenance(Map.of("chunk_id","legacy","source","javascript:alert(1)"),"case");
        assertThat(row).containsEntry("source","javascript:alert(1)").containsEntry("source_url",null).containsEntry("doc_id",null);
        assertThat(RagService.provenance(Map.of("source","https://user:password@example.org/x"),"case")).containsEntry("source_url",null);
    }
    @Test void rejectsInvalidMetadataBeforeAnyNetworkWork() {
        assertThatThrownBy(()->service.upsert(doc("text","学".repeat(86)))).isInstanceOf(KnowledgeException.class);
        assertThatThrownBy(()->service.upsert(new RagContracts.Upsert("case","doc","text",Map.of("source",3),null))).isInstanceOf(KnowledgeException.class);
        verifyNoInteractions(embedding,store);
    }
}
