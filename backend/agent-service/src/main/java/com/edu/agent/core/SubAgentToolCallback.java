package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * J-2.1：把 {@link SubAgent} 适配成主 loop 可调用的 {@link ToolCallback}（agent-as-tool）。
 *
 * <p>{@code call(toolInput)} 取入参 {@code query}，以子代理的受限配置跑一次**独立** AgentLoop，
 * 返回其 final_answer。子代理只拿到 {@code toolNames} 过滤后的工具子集（不含其它子代理，故无递归）。
 */
@Slf4j
public class SubAgentToolCallback implements ToolCallback {

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
                    + "\"description\":\"交给该专家子代理的问题/上下文\"}},\"required\":[\"query\"]}";

    private final SubAgent spec;
    private final AgentLoop agentLoop;
    private final Supplier<List<ToolCallback>> allToolsSupplier;
    private final ToolDefinition toolDefinition;

    public SubAgentToolCallback(SubAgent spec, AgentLoop agentLoop,
                                Supplier<List<ToolCallback>> allToolsSupplier) {
        this.spec = spec;
        this.agentLoop = agentLoop;
        this.allToolsSupplier = allToolsSupplier;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        String query = extractQuery(toolInput);
        List<ToolCallback> subTools = filterTools();
        AgentLoopRequest req = new AgentLoopRequest(
                spec.systemPrompt(), query, subTools, spec.maxIterations(), "subagent-" + spec.name());
        log.info("[SubAgent][{}] 调用，tools={} queryLen={}", spec.name(), subTools.size(), query.length());
        AgentLoopResult r = agentLoop.run(req);
        if (r.finalAnswer() == null) {
            return "（子代理 " + spec.name() + " 未给出结论，status=" + r.status() + "）";
        }
        return r.finalAnswer();
    }

    /** 从主 loop 全量工具中按 toolNames 过滤出子代理可用子集。 */
    private List<ToolCallback> filterTools() {
        if (spec.toolNames() == null || spec.toolNames().isEmpty() || allToolsSupplier == null) {
            return List.of();
        }
        List<ToolCallback> all = allToolsSupplier.get();
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> out = new ArrayList<>();
        for (ToolCallback cb : all) {
            if (spec.toolNames().contains(cb.getToolDefinition().name())) {
                out.add(cb);
            }
        }
        return out;
    }

    private String extractQuery(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return "";
        }
        try {
            JSONObject o = JSON.parseObject(toolInput);
            if (o != null && o.getString("query") != null) {
                return o.getString("query");
            }
        } catch (Exception ignored) {
            // 非 JSON 入参，原样作为 query
        }
        return toolInput;
    }
}
