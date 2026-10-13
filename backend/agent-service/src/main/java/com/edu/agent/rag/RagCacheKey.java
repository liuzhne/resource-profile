package com.edu.agent.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * RAG-JAVA-CORE-20261013：检索缓存键。在 Python {@code _pipeline_cache_key}（集合、query 去首尾空白并小写、
 * top_k、是否 rerank）基础上<b>增加索引版本</b>，索引重建或换模型后不会命中旧结果。
 * 前缀 {@code edu:rag:pipe:java:} 与 Python 的 {@code edu:rag:pipe:} 隔离，两边并行期间互不读写对方缓存。
 */
public final class RagCacheKey {

    public static final String PREFIX = "edu:rag:pipe:java:";

    private RagCacheKey() {
    }

    public static String of(String collectionKey, String query, int topK, boolean rerank, String indexVersion) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            update(sha, collectionKey);
            update(sha, "|");
            update(sha, query == null ? "" : query.strip().toLowerCase(Locale.ROOT));
            update(sha, "|");
            update(sha, String.valueOf(topK));
            update(sha, rerank ? "|rerank=1" : "|rerank=0");
            update(sha, "|idx=");
            update(sha, indexVersion == null ? "" : indexVersion);
            return PREFIX + HexFormat.of().formatHex(sha.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void update(MessageDigest sha, String s) {
        sha.update(s.getBytes(StandardCharsets.UTF_8));
    }
}
