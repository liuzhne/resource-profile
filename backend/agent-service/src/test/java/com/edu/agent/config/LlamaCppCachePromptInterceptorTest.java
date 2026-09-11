package com.edu.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaCppCachePromptInterceptorTest {

    @Test
    void injectsCachePromptForLlamaCpp() throws Exception {
        byte[] captured = intercept(true, "{\"model\":\"qwen\"}");

        assertTrue(new String(captured, StandardCharsets.UTF_8).contains("\"cache_prompt\":true"));
    }

    @Test
    void preservesOpenAiCompatiblePayloadWhenDisabled() throws Exception {
        byte[] original = "{\"model\":\"qwen\"}".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(original, intercept(false, new String(original, StandardCharsets.UTF_8)));
    }

    private byte[] intercept(boolean enabled, String json) throws Exception {
        HttpRequest request = new HttpRequest() {
            @Override
            public HttpMethod getMethod() {
                return HttpMethod.POST;
            }

            @Override
            public URI getURI() {
                return URI.create("https://example.com/v1/chat/completions");
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }
        };
        AtomicReference<byte[]> captured = new AtomicReference<>();
        ClientHttpRequestExecution execution = (req, body) -> {
            captured.set(body);
            return null;
        };

        new LlamaCppCachePromptInterceptor(enabled)
                .intercept(request, json.getBytes(StandardCharsets.UTF_8), execution);
        return captured.get();
    }
}
