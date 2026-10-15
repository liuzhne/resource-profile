package com.edu.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class RagService {
    private final EmbeddingModel embedding;
    private final MilvusStore store;
    private final KnowledgeCache cache;
    private final ObjectMapper json;
    private final Reranker reranker;
    private final Map<String,String> collections;
    private final int dimension, expand;
    private final long cacheTtl, embeddingTtl, hashTtl;
    private final String embeddingIdentity;
    public RagService(EmbeddingModel embedding, MilvusStore store,KnowledgeCache cache,ObjectMapper json,Reranker reranker,
            @Value("${knowledge.embedding-dim}") int dimension,@Value("${knowledge.recall-expand:3}") int expand,
            @Value("${knowledge.cache-ttl:3600}") long cacheTtl,@Value("${knowledge.embedding-cache-ttl:86400}") long embeddingTtl,
            @Value("${knowledge.upsert-hash-ttl:604800}") long hashTtl,
            @Value("${knowledge.embedding-url}") String embeddingUrl,@Value("${knowledge.embedding-model}") String embeddingModel,
            @Value("${MILVUS_COLLECTION_CASE:edu_cases}") String cases,@Value("${MILVUS_COLLECTION_PSY:edu_psychology}") String psychology,
            @Value("${MILVUS_COLLECTION_POLICY:edu_policies}") String policy,@Value("${MILVUS_COLLECTION_SUCCESS:edu_success}") String success) {
        this.embedding=embedding;this.store=store;this.cache=cache;this.json=json;this.reranker=reranker;
        this.dimension=dimension;this.expand=Math.clamp(expand,1,10);this.cacheTtl=cacheTtl;this.embeddingTtl=embeddingTtl;this.hashTtl=hashTtl;
        embeddingIdentity=hash(embeddingUrl+"|"+embeddingModel+"|"+dimension);
        collections=Map.of("case",cases,"psychology",psychology,"policy",policy,"success",success);
    }
    public void initialize() { store.initialize(collections.values()); }
    private String collection(String key) {
        String name=collections.get(key);if(name==null) throw new KnowledgeException(422,"unknown collection");return name;
    }
    static int topK(Integer value) { return value==null || value<=0?5:Math.min(value,20); }
    private String revision(List<String> sources) {
        StringBuilder revision=new StringBuilder();
        for(String source:new TreeSet<>(sources)) {
            if(cache.get("edu:rag:write-lock:"+source)!=null) throw new KnowledgeException(503,"Knowledge update in progress");
            revision.append(source).append(':').append(Objects.toString(cache.get("edu:rag:revision:"+source),"initial")).append('|');
        }
        return revision.toString();
    }
    public Map<String,Object> retrieve(RagContracts.Retrieve request) {
        if(request.queries()==null || request.queries().isEmpty() || request.queries().size()>10
                || request.queries().stream().anyMatch(q->q==null || q.isBlank() || q.length()>8000))
            throw new KnowledgeException(422,"invalid queries");
        int top=topK(request.topK());
        List<String> sources=request.sources()==null || request.sources().isEmpty()?new ArrayList<>(new TreeSet<>(collections.keySet())):request.sources().stream().distinct().toList();
        sources.forEach(this::collection);
        String rev=revision(sources);
        String key="edu:rag:java:v1:"+hash(embeddingIdentity+encode(request)+rev);
        String cached=cache.get(key);
        if(cached!=null) try {return json.readValue(cached,new TypeReference<Map<String,Object>>(){});} catch(Exception ignored) { }
        Map<String,Map<String,Object>> best=new LinkedHashMap<>();
        for(String query:request.queries()) {
            float[] vector=embed(query);
            for(String source:sources) for(Map<String,Object> hit:store.search(collection(source),vector,top*expand)) {
                Map<String,Object> row=provenance(hit,source);
                String id=source+":"+row.get("chunk_id");
                if(!best.containsKey(id) || score(row)>score(best.get(id))) best.put(id,row);
            }
        }
        List<Map<String,Object>> candidates=best.values().stream().sorted(Comparator.comparingDouble(RagService::score).reversed()).limit((long)top*expand).toList();
        Reranker.Result ranked=reranker.rank(request.queries().getFirst(),candidates,top);
        List<Map<String,Object>> chunks=ranked.rows().stream().limit(top).toList();
        Map<String,Object> result=Map.of("chunks",chunks,"fallback",chunks.isEmpty(),"reranked",ranked.reranked());
        if(!rev.equals(revision(sources))) throw new KnowledgeException(503,"Knowledge changed during retrieval; retry");
        cache.put(key,encode(result),cacheTtl);return result;
    }
    public Map<String,Object> search(String query,String source,Integer requestedTop) {
        int top=topK(requestedTop);
        Map<String,Object> result;
        if(query==null || query.isBlank()) result=Map.of("chunks",List.of(),"fallback",true,"reranked",false,"error","empty query");
        else result=retrieve(new RagContracts.Retrieve(List.of(query),top,List.of(source),null));
        Map<String,Object> response=new LinkedHashMap<>(result);response.put("query",query);response.put("collection",source);
        response.put("top_k",top);response.put("count",((List<?>)response.get("chunks")).size());return response;
    }
    private float[] embed(String text) {
        String key="edu:emb:java:v1:"+hash(embeddingIdentity+"\0"+text);
        String cached=cache.get(key);
        if(cached!=null) try {float[] values=json.readValue(cached,float[].class);if(validVector(values)) return values;} catch(Exception ignored) { }
        float[] vector;
        try { vector=embedding.embed(text); } catch(RuntimeException error) { throw new KnowledgeException(503,"Embedding unavailable"); }
        if(!validVector(vector)) throw new KnowledgeException(503,"Invalid embedding vector");
        cache.put(key,encode(vector),embeddingTtl);return vector;
    }
    private boolean validVector(float[] vector) {
        if(vector==null || vector.length!=dimension) return false;
        boolean nonzero=false;for(float value:vector) {if(!Float.isFinite(value)) return false;nonzero|=value!=0;}return nonzero;
    }
    public Map<String,Object> upsert(RagContracts.Upsert request) {
        String name=collection(request.collection());
        if(request.docId()==null || !request.docId().matches("[A-Za-z0-9_-]{1,48}") || request.text()==null || request.text().isBlank() || request.text().length()>800000)
            throw new KnowledgeException(422,"invalid document");
        String title=metadata(request.metadata(),"title",255),source=metadata(request.metadata(),"source_url",64);
        if(source.isEmpty()) source=metadata(request.metadata(),"source",64);
        List<String> chunks=TextChunks.split(request.text(),request.strategy());
        String digest=hash(encode(List.of(request.text(),title,source,request.strategy(),embeddingIdentity)));
        String hashKey="edu:rag:upsert:java:v1:"+request.collection()+":"+request.docId();
        String lock="edu:rag:write-lock:"+request.collection(), owner=UUID.randomUUID().toString();
        if(!cache.lock(lock,owner)) throw new KnowledgeException(409,"Knowledge update in progress");
        long started=System.nanoTime();
        try {
            if(digest.equals(cache.get(hashKey))) return upsertResponse(request,0,0,0,0,true);
            List<Map<String,Object>> rows=new ArrayList<>();
            for(int i=0;i<chunks.size();i++) {
                if((System.nanoTime()-started)/1_000_000>600000) throw new KnowledgeException(503,"Embedding deadline exceeded");
                rows.add(Map.of("chunk_id",request.docId()+"_"+String.format(Locale.ROOT,"%04d",i),"title",title.isEmpty()?request.docId()+" #"+i:title,
                        "content",chunks.get(i),"source",source,"embedding",embed(chunks.get(i))));
            }
            double embedMs=(System.nanoTime()-started)/1_000_000.0;
            if(!owner.equals(cache.get(lock))) throw new KnowledgeException(409,"Knowledge write lease lost");
            long writeStarted=System.nanoTime();
            List<Long> old=store.documentIds(name,request.docId());
            // Insert first: a failed embedding/insert never deletes the last good document. Delete exact old PKs only.
            // Invalidate before mutation too, so partial failures cannot reuse stale retrieval caches.
            cache.put("edu:rag:revision:"+request.collection(),UUID.randomUUID().toString(),Math.max(cacheTtl+60,hashTtl));
            cache.remove(hashKey);
            store.insert(name,rows);
            store.deleteIds(name,old);
            cache.put("edu:rag:revision:"+request.collection(),UUID.randomUUID().toString(),Math.max(cacheTtl+60,hashTtl));
            cache.put(hashKey,digest,hashTtl);
            return upsertResponse(request,chunks.size(),old.size(),embedMs,(System.nanoTime()-writeStarted)/1_000_000.0,false);
        } finally { cache.unlock(lock,owner); }
    }
    private Map<String,Object> upsertResponse(RagContracts.Upsert request,int written,int deleted,double embedMs,double milvusMs,boolean skipped) {
        return Map.of("doc_id",request.docId(),"collection",request.collection(),"chunks_written",written,"chunks_deleted_first",deleted,
                "embedding_ms",embedMs,"milvus_ms",milvusMs,"skipped",skipped);
    }
    private static String metadata(Map<String,Object> data,String key,int maxBytes) {
        Object value=data.get(key);if(value==null) return "";
        if(!(value instanceof String text)) throw new KnowledgeException(422,"Invalid source metadata");
        text=text.strip();if(text.getBytes(StandardCharsets.UTF_8).length>maxBytes) throw new KnowledgeException(422,"Source metadata exceeds schema limit");return text;
    }
    static Map<String,Object> provenance(Map<String,Object> hit,String sourceDb) {
        Map<String,Object> row=new LinkedHashMap<>(hit);row.remove("id");row.put("source_db",sourceDb);
        String id=Objects.toString(hit.get("chunk_id"),"unknown");row.put("chunk_id",id);
        var match=Pattern.compile("^([A-Za-z0-9_-]{1,48})_[0-9]{4}$").matcher(id);
        row.put("doc_id",match.matches()?match.group(1):null);
        String source=hit.get("source") instanceof String s && !s.isBlank()?s:null;
        row.put("source",source);row.put("source_url",safeUrl(source));row.putIfAbsent("rerank_score",null);return row;
    }
    private static String safeUrl(String source) {
        if(source==null || source.chars().anyMatch(Character::isWhitespace)) return null;
        try { URI uri=URI.create(source);return Set.of("http","https").contains(Objects.toString(uri.getScheme(),""))
                && uri.getHost()!=null && uri.getUserInfo()==null?source:null; } catch(IllegalArgumentException e) {return null;}
    }
    private static double score(Map<String,Object> row) {return row.get("score") instanceof Number n?n.doubleValue():0;}
    private String encode(Object value) {try {return json.writeValueAsString(value);}catch(Exception e){throw new KnowledgeException(422,"Invalid JSON payload");}}
    private static String hash(String text) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
}
