package com.edu.knowledge;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;
@Component
public class Reranker {
    private final boolean enabled;
    private final String model;
    private final RestClient client;
    public Reranker(@Value("${knowledge.reranker-enabled:false}") boolean enabled,
            @Value("${knowledge.reranker-url}") String url,@Value("${knowledge.reranker-model}") String model) {
        this.enabled=enabled;this.model=model;client=KnowledgeConfig.http().baseUrl(url).build();
    }
    public record Result(List<Map<String,Object>> rows, boolean reranked) { }
    public Result rank(String query,List<Map<String,Object>> rows,int limit) {
        if(enabled && rows.size()>1) try {
            var response=client.post().uri("/rerank").body(Map.of("model",model,"query",query,
                    "documents",rows.stream().map(r->Objects.toString(r.get("content"),"")).toList(),"top_n",limit))
                    .retrieve().body(JsonNode.class);
            List<Map<String,Object>> ordered=new ArrayList<>(); Set<Integer> seen=new HashSet<>();
            if(response==null || !response.path("results").isArray()) return new Result(rows,false);
            boolean positive=false;
            for(JsonNode item:response.path("results")) {
                int index=item.path("index").asInt(-1);double score=item.path("relevance_score").asDouble(Double.NaN);
                if(index<0 || index>=rows.size() || !Double.isFinite(score) || !seen.add(index)) return new Result(rows,false);
                Map<String,Object> row=new LinkedHashMap<>(rows.get(index));row.put("rerank_score",score);ordered.add(row);positive|=score>0;
            }
            if(positive && !ordered.isEmpty()) return new Result(ordered,true);
        } catch(RuntimeException ignored) { /* Optional reranker failure preserves dense order. */ }
        return new Result(rows,false);
    }
}
