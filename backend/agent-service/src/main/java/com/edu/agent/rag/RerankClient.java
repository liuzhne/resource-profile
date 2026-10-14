package com.edu.agent.rag;

import java.util.List;

/** RAG-JAVA-CORE-20261013：Cross-Encoder 精排。实现不得抛异常，失败返回 {@link RerankResult#failed}。 */
public interface RerankClient {
    RerankResult rerank(String query, List<String> documents, int topN);
}
