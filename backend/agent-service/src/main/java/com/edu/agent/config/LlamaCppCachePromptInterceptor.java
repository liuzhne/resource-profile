package com.edu.agent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * G-1.4 (Java)：拦截发往 llama.cpp /v1/chat/completions 的 RestClient 请求，
 * 在 JSON body 注入 {@code "cache_prompt": true}，启用 llama.cpp slot prefix cache。
 *
 * <p>llama.cpp 在新版本（b3038+，2024 mid）默认即开启，但显式传值更稳：
 *  ① 兼容老版本；② 降级时也能在 body 里 grep 到，便于排查。
 *
 * <p>仅对 path 含 {@code /chat/completions} 的请求注入；其它（embeddings 等）原样透传。
 */
@Slf4j
public class LlamaCppCachePromptInterceptor implements ClientHttpRequestInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final boolean enabled;

    public LlamaCppCachePromptInterceptor() {
        this(true);
    }

    public LlamaCppCachePromptInterceptor(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!enabled) {
            return execution.execute(request, body);
        }
        String path = request.getURI().getPath();
        if (path == null || !path.contains("/chat/completions") || body == null || body.length == 0) {
            return execution.execute(request, body);
        }

        try {
            JsonNode root = MAPPER.readTree(body);
            if (root.isObject()) {
                ObjectNode obj = (ObjectNode) root;
                if (!obj.has("cache_prompt")) {
                    obj.put("cache_prompt", true);
                    byte[] mutated = MAPPER.writeValueAsBytes(obj);
                    return execution.execute(request, mutated);
                }
            }
        } catch (IOException e) {
            log.warn("注入 cache_prompt 失败，原 body 透传：{}", e.getMessage());
        }
        return execution.execute(request, body);
    }
}
