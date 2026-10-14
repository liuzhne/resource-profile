package com.edu.agent.rag;

import java.util.List;

/**
 * RAG-JAVA-CORE-20261013：向量库访问接口。Java 侧<b>尚无实现</b>。
 *
 * <p>TODO(RAG-JAVA-CORE-20261013, 待用户决策)：Milvus 实现。需要用户先确定
 * <ol>
 *   <li>Embedding 模型（决定向量维度与 {@link EmbeddingClient} 实现，是否沿用 Python 当前的 EMBEDDING_MODEL）；</li>
 *   <li>Milvus 集合字段映射（主键 / 向量字段名 / chunk_id、title、content、source 输出字段、度量类型）。</li>
 * </ol>
 * 在此之前 {@code educare.ai.rag.impl=java} 会在启动时失败（见 {@code KnowledgeRetrievalConfig}），默认 python 不受影响。
 */
public interface VectorStore {

    /**
     * @param collectionKey 逻辑集合名（case / psychology / policy / success），由实现映射到物理集合
     * @return 至多 {@code topK} 条，按相似度降序；集合不存在/为空返回空列表
     */
    List<VectorHit> search(String collectionKey, float[] queryVector, int topK);

    /**
     * 该集合当前的索引版本（重建索引 / 换 embedding 模型后必须变化），参与缓存键，
     * 保证索引更新后不会命中旧缓存。
     */
    String indexVersion(String collectionKey);
}
