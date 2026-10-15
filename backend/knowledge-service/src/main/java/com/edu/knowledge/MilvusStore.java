package com.edu.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

/** Milvus 2.4 REST administration/writes and strong gRPC reads, retaining the deployed schema. */
@Component
public class MilvusStore {
    private final RestClient client;
    private final int dimension;
    private final String uri, token;
    private io.milvus.v2.client.MilvusClientV2 reader;
    private io.grpc.ManagedChannel channel;
    // 2.4.1 REST hardcodes BoundedTimestamp and ignores consistencyLevel on reads.
    synchronized io.milvus.v2.client.MilvusClientV2 reader() {
        if(reader==null) {
            channel=new io.milvus.v2.utils.ClientUtils().getChannel(io.milvus.v2.client.ConnectConfig.builder()
                    .uri(uri).token(token.isBlank()?null:token).connectTimeoutMs(5000).build());
            // Apply a fresh deadline per RPC; SDK 2.4.1's rpcDeadlineMs attaches one at client creation.
            var bounded=io.grpc.ClientInterceptors.intercept(channel,new io.grpc.ClientInterceptor() {
                @Override public <Q,S> io.grpc.ClientCall<Q,S> interceptCall(io.grpc.MethodDescriptor<Q,S> method,
                        io.grpc.CallOptions options,io.grpc.Channel next) {
                    return next.newCall(method,options.withDeadlineAfter(30,java.util.concurrent.TimeUnit.SECONDS));
                }
            });
            reader=new io.milvus.v2.client.MilvusClientV2(null);
            reader.setBlockingStub(io.milvus.grpc.MilvusServiceGrpc.newBlockingStub(bounded));
        }
        return reader;
    }
    @jakarta.annotation.PreDestroy synchronized void close() {
        if(channel!=null) channel.shutdownNow();
    }
    public MilvusStore(@Value("${knowledge.milvus-url}") String url, @Value("${knowledge.milvus-token:}") String token,
            @Value("${knowledge.embedding-dim}") int dimension, ObjectMapper mapper) {
        var builder=KnowledgeConfig.http().baseUrl(url).defaultHeader("Accept-Type-Allow-Int64", "true");
        if(!token.isBlank()) builder.defaultHeader("Authorization","Bearer "+token);
        client=builder.build(); this.dimension=dimension; this.uri=url; this.token=token;
    }
    JsonNode post(String endpoint,Object payload) {
        try {
            JsonNode body=client.post().uri("/v2/vectordb/"+endpoint).body(payload).retrieve().body(JsonNode.class);
            if(body==null || !body.has("code") || (body.path("code").asInt(-1)!=0 && body.path("code").asInt(-1)!=200)) throw new KnowledgeException(503,"Milvus operation failed");
            return body.path("data");
        } catch(KnowledgeException e) { throw e; }
        catch(RuntimeException e) { throw new KnowledgeException(503,"Milvus unavailable"); }
    }
    public List<Map<String,Object>> search(String collection,float[] vector,int limit) {
        post("collections/load",Map.of("collectionName",collection));
        try {
            List<Float> values=new ArrayList<>(); for(float value:vector) values.add(value);
            var result=reader().search(io.milvus.v2.service.vector.request.SearchReq.builder()
                    .collectionName(collection).data(List.of(values)).annsField("embedding").topK(limit)
                    .outputFields(List.of("chunk_id","title","content","source"))
                    .searchParams(Map.of("nprobe",16))
                    .consistencyLevel(io.milvus.v2.common.ConsistencyLevel.STRONG).build());
            List<Map<String,Object>> rows=new ArrayList<>();
            if(result.getSearchResults()==null || result.getSearchResults().size()!=1) throw new IllegalStateException();
            for(var hit:result.getSearchResults().getFirst()) {
                if(hit.getDistance()==null || !Float.isFinite(hit.getDistance())) throw new IllegalStateException();
                Map<String,Object> row=new LinkedHashMap<>(hit.getEntity());
                row.put("score",hit.getDistance().doubleValue()); rows.add(row);
            }
            return rows;
        } catch(RuntimeException e) { throw new KnowledgeException(503,"Milvus search unavailable"); }
    }
    public List<Long> documentIds(String collection,String docId) {
        post("collections/load",Map.of("collectionName",collection));
        try {
            var result=reader().query(io.milvus.v2.service.vector.request.QueryReq.builder()
                    .collectionName(collection).filter("chunk_id like \""+docId+"_%\"")
                    .outputFields(List.of("id","chunk_id")).limit(10000)
                    .consistencyLevel(io.milvus.v2.common.ConsistencyLevel.STRONG).build());
            var rows=result.getQueryResults();
            if(rows==null || rows.size()>=10000) throw new IllegalStateException();
            List<Long> ids=new ArrayList<>();
            for(var row:rows) {
                var entity=row.getEntity();
                if(Objects.toString(entity.get("chunk_id"),"").matches(java.util.regex.Pattern.quote(docId)+"_[0-9]{4}")) {
                    String id=Objects.toString(entity.get("id"),"");
                    if(!id.matches("[0-9]+")) throw new IllegalStateException();
                    ids.add(Long.parseLong(id));
                }
            }
            return ids;
        } catch(RuntimeException e) { throw new KnowledgeException(503,"Milvus document lookup unavailable"); }
    }
    public void insert(String collection,List<Map<String,Object>> rows) {
        JsonNode result=post("entities/insert",Map.of("collectionName",collection,"data",rows));
        if(result.path("insertCount").asInt(-1)!=rows.size()) throw new KnowledgeException(503,"Incomplete Milvus insert");
    }
    public void deleteIds(String collection,List<Long> ids) {
        if(ids.isEmpty()) return;
        post("entities/delete",Map.of("collectionName",collection,"filter","id in "+ids));
    }
    public void initialize(Collection<String> collections) {
        for(String collection:collections) {
            JsonNode has=post("collections/has",Map.of("collectionName",collection));
            if(!has.path("has").asBoolean()) {
                var fields=List.of(Map.of("fieldName","id","dataType","Int64","isPrimary",true),
                        scalar("chunk_id",64),scalar("title",255),scalar("content",4096),scalar("source",64),
                        Map.of("fieldName","embedding","dataType","FloatVector","elementTypeParams",Map.of("dim",Integer.toString(dimension))));
                post("collections/create",Map.of("collectionName",collection,
                        "schema",Map.of("autoID",true,"enableDynamicField",false,"fields",fields),
                        "indexParams",List.of(Map.of("fieldName","embedding","indexName","embedding","metricType","IP",
                                "params",Map.of("index_type","IVF_FLAT","nlist","128")))));
            }
            // Refuse an incompatible existing collection; never drop/rebuild user data automatically.
            JsonNode desc=post("collections/describe",Map.of("collectionName",collection));
            boolean vector=false;
            for(JsonNode field:desc.path("fields")) {
                if("embedding".equals(field.path("name").asText(field.path("fieldName").asText()))) {
                    int dim=field.path("elementTypeParams").path("dim").asInt(-1);
                    if(dim<0) for(JsonNode param:field.path("params")) if("dim".equals(param.path("key").asText())) dim=param.path("value").asInt(-1);
                    vector=dim==dimension;
                }
            }
            if(!vector) throw new KnowledgeException(503,"Milvus embedding schema mismatch");
            post("collections/load",Map.of("collectionName",collection));
        }
    }
    private static Map<String,Object> scalar(String name,int max) {
        return Map.of("fieldName",name,"dataType","VarChar","elementTypeParams",Map.of("max_length",Integer.toString(max)));
    }
}
