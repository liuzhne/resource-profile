package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-task evidence captured at the actual MCP invocation, independently of model thoughts/citations. */
public final class RagSnapshotCollector {
    private static final Set<String> SEARCH_TOOLS = Set.of("search_cases", "search_policies", "search_psychology");
    private static final int MAX_SEARCHES = 32;
    private static final int MAX_CHUNKS = 100;
    private final List<JSONObject> searches = new ArrayList<>();
    private final Map<String, JSONObject> chunks = new LinkedHashMap<>();
    private boolean executed;
    private boolean truncated;

    public List<ToolCallback> wrap(List<ToolCallback> tools) {
        return tools.stream().map(this::wrap).toList();
    }

    private ToolCallback wrap(ToolCallback delegate) {
        String originalName = delegate instanceof SyncMcpToolCallback mcp ? mcp.getOriginalToolName() : delegate.getToolDefinition().name();
        if (!SEARCH_TOOLS.contains(originalName)) return delegate;
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            @Override public String call(String input) { return invoke(input, null, false); }
            @Override public String call(String input, ToolContext context) { return invoke(input, context, true); }
            private String invoke(String input, ToolContext context, boolean hasContext) {
                JSONObject search = begin(originalName, input);
                try {
                    String result = hasContext ? delegate.call(input, context) : delegate.call(input);
                    finish(search, result);
                    return result; // the model receives exactly the original tool result
                } catch (RuntimeException e) {
                    failed(search, e.getClass().getSimpleName());
                    throw e;
                }
            }
        };
    }

    private synchronized JSONObject begin(String tool, String input) {
        executed = true;
        if (searches.size() >= MAX_SEARCHES) { truncated = true; return null; }
        JSONObject search = new JSONObject();
        search.put("tool", tool);
        search.put("status", "RUNNING");
        try {
            JSONObject arguments = JSON.parseObject(input);
            if (arguments != null) {
                Object query = arguments.get("query");
                if (query instanceof String value) search.put("query", bounded(value, 4096));
                if (arguments.get("top_k") instanceof Number count) search.put("requested_top_k", count.intValue());
            }
        } catch (Exception ignored) { /* do not persist arbitrary unstructured input */ }
        searches.add(search);
        return search;
    }

    private synchronized void finish(JSONObject search, String raw) {
        if (search == null) return;
        try {
            JSONObject result = unwrap(raw);
            if (result == null || Boolean.TRUE.equals(result.getBoolean("isError")) || result.containsKey("error")
                    || !(result.get("chunks") instanceof JSONArray results)) {
                failed(search, "InvalidRetrievalResult");
                return;
            }
            search.put("status", "SUCCEEDED");
            for (String key : List.of("collection", "top_k", "reranked", "fallback", "count")) {
                Object value = result.get(key);
                if (value instanceof String text) search.put(key, bounded(text, 256));
                else if (value instanceof Boolean || value instanceof Number) search.put(key, value);
            }
            search.put("returned_count", results.size());
            List<String> ids = new ArrayList<>();
            for (Object value : results) {
                if (!(value instanceof JSONObject row)) continue;
                JSONObject chunk = new JSONObject();
                for (String key : List.of("chunk_id", "source_db", "source", "doc_id", "source_url", "title", "content", "score", "rerank_score")) {
                    Object field = row.get(key);
                    if (field instanceof String text) chunk.put(key, bounded(text, key.equals("content") ? 32768 : key.equals("source_url") ? 2048 : 1024));
                    else if (field instanceof Number) chunk.put(key, field);
                }
                if (chunk.isEmpty()) continue;
                String id = String.valueOf(chunk.getOrDefault("chunk_id", ""));
                if (!id.isBlank()) ids.add(id);
                String identity = chunk.getString("source_db") + ":" + id + ":" + chunk.getString("content");
                if (!chunks.containsKey(identity)) {
                    if (chunks.size() >= MAX_CHUNKS) { truncated = true; continue; }
                    chunks.put(identity, chunk);
                }
            }
            search.put("chunk_ids", ids);
        } catch (Exception e) {
            failed(search, "InvalidRetrievalResult");
        }
    }

    private synchronized void failed(JSONObject search, String errorType) {
        if (search == null) return;
        search.put("status", "FAILED");
        search.put("error_type", errorType);
    }

    public synchronized String snapshot() {
        JSONObject snapshot = new JSONObject();
        snapshot.put("schema_version", 1);
        snapshot.put("executed", executed);
        snapshot.put("searches", searches);
        snapshot.put("chunks", new ArrayList<>(chunks.values()));
        snapshot.put("reranked", searches.stream().anyMatch(s -> Boolean.TRUE.equals(s.getBoolean("reranked"))));
        snapshot.put("fallback", searches.stream().anyMatch(s -> Boolean.TRUE.equals(s.getBoolean("fallback"))));
        snapshot.put("truncated", truncated);
        return snapshot.toJSONString();
    }

    private String bounded(String value, int max) {
        if (value.length() <= max) return value;
        truncated = true;
        return value.substring(0, max);
    }

    /** Spring AI 1.1.6 SyncMcpToolCallback returns the JSON array of MCP content blocks. */
    private static JSONObject unwrap(String raw) {
        if (raw == null || raw.length() > 262144) return null;
        Object parsed = JSON.parse(raw);
        if (parsed instanceof JSONObject object) {
            if (Boolean.TRUE.equals(object.getBoolean("isError"))) return null;
            if (object.get("structuredContent") instanceof JSONObject structured) return structured;
            if (object.get("chunks") instanceof JSONArray) return object;
            parsed = object.get("content");
        }
        if (parsed instanceof JSONArray content) {
            // ModelOptionsUtils's mapper can omit the polymorphic "type" when serializing TextContent.
            for (Object block : content) if (block instanceof JSONObject item
                    && ("text".equals(item.getString("type")) || !item.containsKey("type"))
                    && item.get("text") instanceof String) {
                String text = item.getString("text");
                JSONObject result = JSON.parseObject(text);
                if (result != null && result.get("chunks") instanceof JSONArray) return result;
            }
        }
        return null;
    }
}
