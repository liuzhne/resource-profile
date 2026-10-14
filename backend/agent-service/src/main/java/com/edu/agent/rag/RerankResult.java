package com.edu.agent.rag;

import java.util.List;

/**
 * RAG-JAVA-CORE-20261013：精排结果。失败<b>显式</b>表达为 {@code ok=false + failure}，
 * 绝不像 Python reranker_client 那样用"全零分占位"冒充成功。
 */
public record RerankResult(boolean ok, List<Scored> scores, String failure) {

    /** index 指向入参 documents 的下标。 */
    public record Scored(int index, double relevanceScore) {
    }

    public static RerankResult success(List<Scored> scores) {
        return new RerankResult(true, List.copyOf(scores), null);
    }

    public static RerankResult failed(String failure) {
        return new RerankResult(false, List.of(), failure);
    }
}
