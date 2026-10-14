package com.edu.agent.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** RAG-JAVA-CORE-20261013：失败必须显式（ok=false），不得返回全零分占位。 */
class HttpRerankClientTest {

    HttpServer server;
    final AtomicReference<String> lastBody = new AtomicReference<>();

    HttpRerankClient start(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rerank", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        return new HttpRerankClient(HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/", "m", Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    RerankResult call(int status, String body) throws Exception {
        return start(status, body).rerank("q", List.of("d0", "d1"), 2);
    }

    @Test
    void success() throws Exception {
        RerankResult r = call(200, "{\"results\":[{\"index\":1,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.1}]}");
        assertThat(r.ok()).isTrue();
        assertThat(r.scores()).containsExactly(new RerankResult.Scored(1, 0.9), new RerankResult.Scored(0, 0.1));
        assertThat(lastBody.get()).contains("\"model\":\"m\"").contains("\"top_n\":2");
    }

    @Test
    void failures() throws Exception {
        assertThat(call(500, "x").failure()).isEqualTo("HTTP_500");
        stop();
        assertThat(call(200, "nope").failure()).isEqualTo("PARSE_ERROR");
        stop();
        assertThat(call(200, "{}").failure()).isEqualTo("MISSING_RESULTS");
        stop();
        assertThat(call(200, "{\"results\":[]}").failure()).isEqualTo("EMPTY_RESULTS");
        stop();
        assertThat(call(200, "{\"results\":[{\"index\":5,\"relevance_score\":1}]}").failure()).isEqualTo("INVALID_INDEX");
        stop();
        assertThat(call(200, "{\"results\":[{\"index\":0,\"relevance_score\":1},{\"index\":0,\"relevance_score\":1}]}").failure()).isEqualTo("INVALID_INDEX");
        stop();
        assertThat(call(200, "{\"results\":[{\"index\":0}]}").failure()).isEqualTo("MALFORMED_RESULT");
        stop();
        RerankResult zero = call(200, "{\"results\":[{\"index\":0,\"relevance_score\":0},{\"index\":1,\"relevance_score\":0}]}");
        assertThat(zero.ok()).isFalse();
        assertThat(zero.failure()).isEqualTo("ALL_ZERO_SCORES");
        assertThat(zero.scores()).isEmpty();
    }

    @Test
    void connectionRefusedIsExplicitFailure() {
        RerankResult r = new HttpRerankClient(HttpClient.newHttpClient(), "http://127.0.0.1:1", "m",
                Duration.ofSeconds(2)).rerank("q", List.of("d"), 1);
        assertThat(r.ok()).isFalse();
        assertThat(r.failure()).startsWith("IO_ERROR");
    }

    @Test
    void emptyDocumentsShortCircuit() {
        RerankResult r = new HttpRerankClient(HttpClient.newHttpClient(), "http://127.0.0.1:1", "m",
                Duration.ofSeconds(1)).rerank("q", List.of(), 1);
        assertThat(r.ok()).isTrue();
        assertThat(r.scores()).isEmpty();
    }
}
