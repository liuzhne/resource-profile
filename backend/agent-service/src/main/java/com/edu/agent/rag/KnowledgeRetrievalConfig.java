package com.edu.agent.rag;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RAG-JAVA-CORE-20261013：{@code educare.ai.rag.impl=java} 时才装配 Java 检索链路；默认 python，本配置整体不生效。
 *
 * <p>java 模式下必须存在 {@link EmbeddingClient} 与 {@link VectorStore} Bean。仓库中尚无实现（embedding 模型、
 * Milvus 字段映射待定），因此当前打开 java 会在启动期显式失败，而不是静默返回空知识。
 */
@Configuration
@ConditionalOnProperty(prefix = "educare.ai.rag", name = "impl", havingValue = "java")
public class KnowledgeRetrievalConfig {

    @Bean
    public RerankClient httpRerankClient(
            @Value("${educare.ai.rag.reranker.base-url:http://localhost:8093}") String baseUrl,
            @Value("${educare.ai.rag.reranker.model:bge-reranker-base}") String model,
            @Value("${educare.ai.rag.reranker.timeout:30s}") Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new HttpRerankClient(http, baseUrl, model, timeout);
    }

    @Bean
    public RagCache redisRagCache(StringRedisTemplate redis) {
        return new RedisRagCache(redis);
    }

    @Bean
    public KnowledgeRetrievalService knowledgeRetrievalService(
            ObjectProvider<EmbeddingClient> embedding,
            ObjectProvider<VectorStore> vectorStore,
            RerankClient reranker,
            RagCache cache,
            @Value("${educare.ai.rag.reranker.enabled:true}") boolean rerankEnabled,
            @Value("${educare.ai.rag.recall-expand:3}") int recallExpand,
            @Value("${educare.ai.rag.cache-ttl:1h}") Duration cacheTtl) {
        return new KnowledgeRetrievalService(
                require(embedding.getIfAvailable(), "EmbeddingClient"),
                require(vectorStore.getIfAvailable(), "VectorStore"),
                reranker, cache, rerankEnabled, recallExpand, cacheTtl);
    }

    static <T> T require(T bean, String name) {
        if (bean == null) {
            throw new IllegalStateException("educare.ai.rag.impl=java 需要 " + name
                    + " 实现，但当前未提供（TODO RAG-JAVA-CORE-20261013：embedding 模型与 Milvus 字段映射待定）。"
                    + "请改回 educare.ai.rag.impl=python。");
        }
        return bean;
    }
}
