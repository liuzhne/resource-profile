package com.edu.knowledge;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
class EmbeddingProtocolTest {
    @Test void springAiUsesExistingProviderPrefixAndIndependentCredential() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var path=new AtomicReference<String>();var auth=new AtomicReference<String>();var body=new AtomicReference<String>();
        server.createContext("/", exchange->{
            path.set(exchange.getRequestURI().getPath());auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response="{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1.0,0.5]}],\"model\":\"fixture-model\",\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var model=new KnowledgeConfig().embeddingModel("http://127.0.0.1:"+server.getAddress().getPort()+"/accounts/synthetic/ai/v1/","embedding-only","fixture-model");
            assertThat(model.embed("synthetic")).containsExactly(1f,.5f);
            assertThat(path.get()).isEqualTo("/accounts/synthetic/ai/v1/embeddings");assertThat(auth.get()).isEqualTo("Bearer embedding-only");
            assertThat(new ObjectMapper().readTree(body.get()).path("model").asText()).isEqualTo("fixture-model");
        } finally {server.stop(0);}
    }
}
