package com.edu.agent.rag;

import java.time.Duration;
import java.util.Optional;

/** RAG-JAVA-CORE-20261013：检索结果缓存。实现出错时应吞掉异常（缓存不可用不影响检索）。 */
public interface RagCache {
    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);
}
