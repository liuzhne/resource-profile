package com.edu.knowledge;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
/** Transitional HTTP contracts; all generation is executed by agent-service's Spring AI policy/telemetry chain. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="knowledge.api-enabled",havingValue="true",matchIfMissing=true)
public class GenerationCompatibilityController {
    private final RestClient client;
    private final String token;
    public GenerationCompatibilityController(@Value("${AGENT_SERVICE_URL:http://localhost:8087}") String url,
            @Value("${knowledge.internal-token:}") String token) {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);factory.setReadTimeout(300000);
        client=RestClient.builder().requestFactory(factory).baseUrl(url).build();this.token=token;
    }
    @PostMapping("/api/v1/agent/{route:risk|plan|audit}")
    public JsonNode generate(@PathVariable String route,@RequestBody JsonNode request) {return forward(route,request);}
    @PostMapping("/api/v1/llm/chat") public JsonNode chat(@RequestBody JsonNode request) {return forward("chat",request);}
    @GetMapping("/api/v1/diagnostics/llm-metrics")
    public JsonNode metrics() {
        if(token.isBlank()) throw new KnowledgeException(503,"Internal credential missing");
        try {return client.get().uri("/_internal/ai/metrics").header("X-Internal-Token",token).retrieve().body(JsonNode.class);}
        catch(RuntimeException error) {throw new KnowledgeException(503,"Generation metrics unavailable");}
    }
    private JsonNode forward(String route,JsonNode request) {
        if(token.isBlank()) throw new KnowledgeException(503,"Internal credential missing");
        try {return client.post().uri("/_internal/ai/"+route).header("X-Internal-Token",token).body(request).retrieve().body(JsonNode.class);}
        catch(RuntimeException error) {throw new KnowledgeException(503,"Generation unavailable");}
    }
}
