package com.edu.knowledge;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
@Configuration
public class KnowledgeConfig {
    static RestClient.Builder http() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(30000);
        return RestClient.builder().requestFactory(factory);
    }
    @Bean EmbeddingModel embeddingModel(@Value("${knowledge.embedding-url}") String url,
            @Value("${knowledge.embedding-key}") String key, @Value("${knowledge.embedding-model}") String model) {
        // Existing environment supplies a /v1 URL; keep custom provider prefixes such as Cloudflare accounts/.../ai/v1.
        String base = url.replaceAll("/+$", "");
        var api = OpenAiApi.builder().baseUrl(base).embeddingsPath("/embeddings")
                .apiKey(key).restClientBuilder(http()).build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build(), RetryTemplate.builder().maxAttempts(1).build());
    }
    @Bean ToolCallbackProvider knowledgeToolCallbacks(KnowledgeTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
