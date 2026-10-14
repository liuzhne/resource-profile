package com.edu.agent.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * RAG-JAVA-CORE-20261013：单集合检索结果，JSON 结构与 Python {@code retrieve_from_collection} 返回值一致，
 * 另加 {@code rerank_failure}（精排失败原因，成功或未请求精排时为 null）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrievalResult(
        @JsonProperty("collection") String collection,
        @JsonProperty("query") String query,
        @JsonProperty("top_k") int topK,
        @JsonProperty("reranked") boolean reranked,
        @JsonProperty("fallback") boolean fallback,
        @JsonProperty("rerank_failure") String rerankFailure,
        @JsonProperty("chunks") List<Chunk> chunks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chunk(
            @JsonProperty("chunk_id") String chunkId,
            @JsonProperty("source_db") String sourceDb,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("score") double score,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            @JsonProperty("rerank_score") Double rerankScore) {
    }
}
