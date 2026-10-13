package com.edu.agent.rag;

/**
 * RAG-JAVA-CORE-20261013：文本向量化接口。Java 侧<b>尚无实现</b>。
 * TODO(RAG-JAVA-CORE-20261013, 待用户决策)：Embedding 模型未定，见 {@link VectorStore} 说明。
 */
public interface EmbeddingClient {
    float[] embed(String text);
}
