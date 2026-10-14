package com.edu.agent.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RAG-JAVA-CORE-20261013：翻译自 ai-inference-service/tests/test_rag_pipeline_cache_key.py，并补充 Java 新增约束。 */
class KnowledgeRetrievalServiceTest {

    static final List<VectorHit> HITS = List.of(
            new VectorHit("a", "A", "aaa", null, 0.9),
            new VectorHit("b", "B", "bbb", null, 0.8));
    static final RerankResult GOOD = RerankResult.success(List.of(
            new RerankResult.Scored(1, 0.95), new RerankResult.Scored(0, 0.5)));

    Map<String, String> store;
    AtomicInteger searchCalls;
    AtomicReference<String> indexVersion;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        searchCalls = new AtomicInteger();
        indexVersion = new AtomicReference<>("v1");
    }

    RagCache cache() {
        return new RagCache() {
            public Optional<String> get(String k) { return Optional.ofNullable(store.get(k)); }
            public void put(String k, String v, Duration ttl) { store.put(k, v); }
        };
    }

    VectorStore vs(List<VectorHit> hits) {
        return new VectorStore() {
            public List<VectorHit> search(String c, float[] v, int k) { searchCalls.incrementAndGet(); return hits; }
            public String indexVersion(String c) { return indexVersion.get(); }
        };
    }

    RetrievalResult run(Boolean enable, boolean defaultOn, RerankClient rr) {
        searchCalls.set(0);
        var svc = new KnowledgeRetrievalService(t -> new float[]{0.1f, 0.2f}, vs(HITS), rr, cache(),
                defaultOn, 3, Duration.ofHours(1));
        return svc.retrieve("q", "case", 2, enable);
    }

    RetrievalResult run(Boolean enable, boolean defaultOn) {
        return run(enable, defaultOn, (q, d, n) -> GOOD);
    }

    @Test
    void cacheKeyDiffersForRerankOnOff() {
        String on = RagCacheKey.of("case", "焦虑", 5, true, "v1");
        assertThat(on).isNotEqualTo(RagCacheKey.of("case", "焦虑", 5, false, "v1"));
        assertThat(on).isEqualTo(RagCacheKey.of("case", "  焦虑 ", 5, true, "v1"));
        assertThat(on).startsWith(RagCacheKey.PREFIX);
    }

    @Test
    void cacheKeyIncludesIndexVersion() {
        assertThat(RagCacheKey.of("case", "q", 5, true, "v1")).isNotEqualTo(RagCacheKey.of("case", "q", 5, true, "v2"));
    }

    @Test
    void togglingConfigDoesNotHitStaleCache() {
        RetrievalResult r1 = run(null, false);
        assertThat(r1.reranked()).isFalse();
        assertThat(searchCalls).hasValue(1);
        RetrievalResult r2 = run(null, true);
        assertThat(searchCalls).as("开启 rerank 后不应命中未 rerank 的旧缓存").hasValue(1);
        assertThat(r2.reranked()).isTrue();
        RetrievalResult r3 = run(null, true);
        assertThat(searchCalls).hasValue(0);
        assertThat(r3.reranked()).isTrue();
        assertThat(r3.chunks().get(0).chunkId()).isEqualTo("b");
    }

    @Test
    void perRequestEnableRerankSeparatesCache() {
        run(false, true);
        RetrievalResult r = run(true, true);
        assertThat(searchCalls).hasValue(1);
        assertThat(r.reranked()).isTrue();
    }

    @Test
    void rerankFailureIsNotCachedAndNextCallRetries() {
        RetrievalResult r1 = run(true, true, (q, d, n) -> RerankResult.failed("ALL_ZERO_SCORES"));
        assertThat(r1.reranked()).isFalse();
        assertThat(r1.rerankFailure()).isEqualTo("ALL_ZERO_SCORES");
        assertThat(r1.chunks()).extracting(RetrievalResult.Chunk::chunkId).containsExactly("a", "b");
        assertThat(r1.chunks()).allSatisfy(c -> assertThat(c.rerankScore()).isNull());
        assertThat(searchCalls).hasValue(1);
        assertThat(store).as("rerank 回退结果不应写入缓存").isEmpty();
        RetrievalResult r2 = run(true, true);
        assertThat(searchCalls).hasValue(1);
        assertThat(r2.reranked()).isTrue();
        assertThat(store).hasSize(1);
    }

    @Test
    void rerankClientThrowingIsTreatedAsFailureNotCached() {
        RetrievalResult r = run(true, true, (q, d, n) -> { throw new IllegalStateException("boom"); });
        assertThat(r.reranked()).isFalse();
        assertThat(r.rerankFailure()).startsWith("CLIENT_EXCEPTION");
        assertThat(store).isEmpty();
    }

    @Test
    void rerankReturningNullOrEmptySuccessNotCached() {
        run(true, true, (q, d, n) -> null);
        assertThat(store).isEmpty();
        run(true, true, (q, d, n) -> RerankResult.success(List.of()));
        assertThat(store).isEmpty();
    }

    @Test
    void indexVersionBumpInvalidatesCache() {
        run(true, true);
        run(true, true);
        assertThat(searchCalls).hasValue(0);
        indexVersion.set("v2");
        run(true, true);
        assertThat(searchCalls).hasValue(1);
    }

    @Test
    void emptyRecallIsFallbackAndNotCached() {
        var svc = new KnowledgeRetrievalService(t -> new float[0], vs(List.of()), (q, d, n) -> GOOD, cache(),
                true, 3, Duration.ofHours(1));
        RetrievalResult r = svc.retrieve("q", "case", 2, null);
        assertThat(r.fallback()).isTrue();
        assertThat(store).isEmpty();
    }

    @Test
    void corruptCacheEntryIsIgnored() {
        store.put(RagCacheKey.of("case", "q", 2, true, "v1"), "{not json");
        RetrievalResult r = run(true, true);
        assertThat(searchCalls).hasValue(1);
        assertThat(r.reranked()).isTrue();
    }

    @Test
    void retrieveMultiDedupsAndSorts() {
        var svc = new KnowledgeRetrievalService(t -> new float[0], vs(HITS), (q, d, n) -> GOOD, cache(),
                false, 3, Duration.ofHours(1));
        Map<String, Object> out = svc.retrieveMulti(List.of("q1", "q2"), List.of("case"), 5);
        @SuppressWarnings("unchecked")
        List<RetrievalResult.Chunk> chunks = (List<RetrievalResult.Chunk>) out.get("chunks");
        assertThat(chunks).extracting(RetrievalResult.Chunk::chunkId).containsExactly("a", "b");
        assertThat(out.get("fallback")).isEqualTo(false);
        assertThat(out.get("reranked")).isEqualTo(false);
    }

    @Test
    void javaModeWithoutVectorStoreFailsFast() {
        assertThatThrownBy(() -> KnowledgeRetrievalConfig.require(null, "VectorStore"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("VectorStore");
        assertThat(KnowledgeRetrievalConfig.require("x", "n")).isEqualTo("x");
    }
}
