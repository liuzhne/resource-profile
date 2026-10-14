package com.edu.agent.rag;

/** RAG-JAVA-CORE-20261013：向量库召回的一条候选（按向量相似度降序返回）。 */
public record VectorHit(String chunkId, String title, String content, String source, double score) {
}
