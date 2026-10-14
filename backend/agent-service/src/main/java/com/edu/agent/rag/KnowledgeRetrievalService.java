package com.edu.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RAG-JAVA-CORE-20261013：Java 版单集合检索（对应 Python {@code rag_pipeline.retrieve_from_collection}）。
 *
 * <ul>
 *   <li>缓存键含是否 rerank 与索引版本（{@link RagCacheKey}）；</li>
 *   <li>请求了精排但精排失败（{@link RerankResult#ok()} 为 false）→ 保留向量原始顺序、<b>不写缓存</b>、打 warn 日志，
 *       结果带 {@code reranked=false} 与 {@code rerank_failure}；下次请求重新尝试精排；</li>
 *   <li>空召回 → {@code fallback=true}、不写缓存（与 Python 一致）。</li>
 * </ul>
 */
@Slf4j
public class KnowledgeRetrievalService {

    private final EmbeddingClient embedding;
    private final VectorStore vectorStore;
    private final RerankClient reranker;
    private final RagCache cache;
    private final boolean rerankEnabledByDefault;
    private final int recallExpand;
    private final Duration cacheTtl;
    private final ObjectMapper mapper = new ObjectMapper();

    public KnowledgeRetrievalService(EmbeddingClient embedding, VectorStore vectorStore, RerankClient reranker,
                                     RagCache cache, boolean rerankEnabledByDefault, int recallExpand,
                                     Duration cacheTtl) {
        this.embedding = embedding;
        this.vectorStore = vectorStore;
        this.reranker = reranker;
        this.cache = cache;
        this.rerankEnabledByDefault = rerankEnabledByDefault;
        this.recallExpand = Math.max(1, recallExpand);
        this.cacheTtl = cacheTtl;
    }

    /** @param enableRerank null → 跟随配置；true/false 强制 */
    public RetrievalResult retrieve(String query, String collectionKey, int topK, Boolean enableRerank) {
        boolean doRerank = enableRerank == null ? rerankEnabledByDefault : enableRerank;
        String key = RagCacheKey.of(collectionKey, query, topK, doRerank, vectorStore.indexVersion(collectionKey));
        Optional<String> cached = cache.get(key);
        if (cached.isPresent()) {
            try {
                RetrievalResult hit = mapper.readValue(cached.get(), RetrievalResult.class);
                log.info("rag-java X-Cache: HIT key={} collection={}", key, collectionKey);
                return hit;
            } catch (Exception e) {
                log.warn("rag-java 缓存反序列化失败 key={}: {}", key, e.getMessage());
            }
        }

        int recall = Math.max(topK * recallExpand, topK);
        List<VectorHit> hits = vectorStore.search(collectionKey, embedding.embed(query), recall);
        if (hits == null || hits.isEmpty()) {
            log.info("rag-java 空召回 collection={}", collectionKey);
            return new RetrievalResult(collectionKey, query, topK, false, true, null, List.of());
        }

        List<VectorHit> ordered = hits;
        Map<Integer, Double> rerankScores = Map.of();
        boolean reranked = false;
        String rerankFailure = null;
        if (doRerank && hits.size() > 1) {
            List<String> docs = hits.stream()
                    .map(h -> h.content() != null ? h.content() : (h.title() != null ? h.title() : ""))
                    .toList();
            RerankResult rr;
            try {
                rr = reranker.rerank(query, docs, topK);
            } catch (Exception e) {
                rr = RerankResult.failed("CLIENT_EXCEPTION: " + e.getClass().getSimpleName());
            }
            if (rr != null && rr.ok() && !rr.scores().isEmpty()) {
                List<RerankResult.Scored> sorted = new ArrayList<>(rr.scores());
                sorted.sort(Comparator.comparingDouble(RerankResult.Scored::relevanceScore).reversed());
                List<VectorHit> re = new ArrayList<>(sorted.size());
                Map<Integer, Double> byNewPos = new LinkedHashMap<>();
                for (RerankResult.Scored s : sorted) {
                    byNewPos.put(re.size(), s.relevanceScore());
                    re.add(hits.get(s.index()));
                }
                ordered = re;
                rerankScores = byNewPos;
                reranked = true;
            } else {
                rerankFailure = rr == null ? "NULL_RESULT" : (rr.ok() ? "EMPTY_SCORES" : String.valueOf(rr.failure()));
                log.warn("rag-java 精排失败（{}），回退向量原始顺序，本次结果不写缓存 collection={}",
                        rerankFailure, collectionKey);
            }
        }

        List<RetrievalResult.Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, ordered.size()); i++) {
            VectorHit h = ordered.get(i);
            chunks.add(new RetrievalResult.Chunk(h.chunkId() == null ? "unknown" : h.chunkId(), collectionKey,
                    h.title(), h.content(), h.score(), reranked ? rerankScores.get(i) : null));
        }
        RetrievalResult result = new RetrievalResult(collectionKey, query, topK, reranked, false, rerankFailure, chunks);
        if (rerankFailure != null) {
            return result;
        }
        try {
            cache.put(key, mapper.writeValueAsString(result), cacheTtl);
        } catch (Exception e) {
            log.warn("rag-java 结果序列化失败，跳过缓存: {}", e.getMessage());
        }
        log.info("rag-java X-Cache: MISS key={} collection={} reranked={}", key, collectionKey, reranked);
        return result;
    }

    /**
     * 任务链路用：多路 query × 多集合，chunk_id 去重保留最高分，按（精排分优先，否则向量分）降序取 topK。
     * 返回 {@code {"chunks":[...], "fallback":bool, "reranked":bool}}，与 Python {@code /api/v1/rag/retrieve} 同形。
     */
    public Map<String, Object> retrieveMulti(List<String> queries, List<String> collectionKeys, int topK) {
        Map<String, RetrievalResult.Chunk> byId = new LinkedHashMap<>();
        boolean anyReranked = false;
        for (String q : queries) {
            for (String c : collectionKeys) {
                RetrievalResult r = retrieve(q, c, topK, null);
                anyReranked |= r.reranked();
                for (RetrievalResult.Chunk ch : r.chunks()) {
                    String id = ch.sourceDb() + ":" + ch.chunkId();
                    RetrievalResult.Chunk prev = byId.get(id);
                    if (prev == null || rank(ch) > rank(prev)) {
                        byId.put(id, ch);
                    }
                }
            }
        }
        List<RetrievalResult.Chunk> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparingDouble(KnowledgeRetrievalService::rank).reversed());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("chunks", all.subList(0, Math.min(topK, all.size())));
        out.put("fallback", all.isEmpty());
        out.put("reranked", anyReranked);
        return out;
    }

    private static double rank(RetrievalResult.Chunk c) {
        return c.rerankScore() != null ? c.rerankScore() : c.score();
    }
}
