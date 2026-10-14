package com.edu.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG-JAVA-CORE-20261013：Cohere {@code /rerank} 兼容协议客户端（Infinity / Voyage / Cohere），与 Python
 * {@code reranker_client.py} 同协议：
 * 请求 {@code {"model","query","documents","top_n"}}，响应 {@code {"results":[{"index","relevance_score"}]}}。
 *
 * <p>与 Python 不同：任何失败都返回 {@link RerankResult#failed}（附原因），不返回全零分占位。
 * 判为失败的情形：网络/超时异常、非 2xx、响应不是 JSON、缺 results、非空文档却得到空结果、
 * index 越界或重复、分数缺失/非数字、所有分数 ≤ 0（与 Python "全零即回退"的判定保持一致）。
 */
@Slf4j
public class HttpRerankClient implements RerankClient {

    private final HttpClient http;
    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpRerankClient(HttpClient http, String baseUrl, String model, Duration timeout) {
        this.http = http;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/rerank");
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public RerankResult rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return RerankResult.success(List.of());
        }
        HttpResponse<String> resp;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", topN);
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("INTERRUPTED");
        } catch (Exception e) {
            return fail("IO_ERROR: " + e.getClass().getSimpleName());
        }
        if (resp.statusCode() / 100 != 2) {
            return fail("HTTP_" + resp.statusCode());
        }
        JsonNode results;
        try {
            results = mapper.readTree(resp.body()).get("results");
        } catch (Exception e) {
            return fail("PARSE_ERROR");
        }
        if (results == null || !results.isArray()) {
            return fail("MISSING_RESULTS");
        }
        if (results.isEmpty()) {
            return fail("EMPTY_RESULTS");
        }
        List<RerankResult.Scored> scores = new ArrayList<>(results.size());
        boolean[] seen = new boolean[documents.size()];
        boolean anyPositive = false;
        for (JsonNode r : results) {
            JsonNode idx = r.get("index");
            JsonNode score = r.get("relevance_score");
            if (idx == null || !idx.canConvertToInt() || score == null || !score.isNumber()) {
                return fail("MALFORMED_RESULT");
            }
            int i = idx.asInt();
            if (i < 0 || i >= documents.size() || seen[i]) {
                return fail("INVALID_INDEX");
            }
            seen[i] = true;
            double s = score.asDouble();
            anyPositive |= s > 0;
            scores.add(new RerankResult.Scored(i, s));
        }
        if (!anyPositive) {
            return fail("ALL_ZERO_SCORES");
        }
        return RerankResult.success(scores);
    }

    private static RerankResult fail(String reason) {
        log.warn("Rerank 失败: {}", reason);
        return RerankResult.failed(reason);
    }
}
