package com.edu.agent.core;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * H-2.1：AgentLoop 输入。
 *
 * @param systemPrompt   调用方提供的 base system prompt（不含工具 schema；AgentLoop 自动追加）
 * @param userPrompt     初始 user 消息
 * @param tools          可用 ToolCallback 列表；可为空，空时退化为纯 LLM 单轮调用
 * @param maxIterations  循环上限。0 或负数将被 {@link #normalized()} 改写为默认 {@link #DEFAULT_MAX_ITERATIONS}
 * @param taskTag        日志/trace 用 tag，例如 "task-42-risk"
 */
public record AgentLoopRequest(
        String systemPrompt,
        String userPrompt,
        List<ToolCallback> tools,
        int maxIterations,
        String taskTag
) {
    public static final int DEFAULT_MAX_ITERATIONS = 5;

    /** 规整非法值：tools=null → empty；maxIterations <= 0 → 默认值。 */
    public AgentLoopRequest normalized() {
        return new AgentLoopRequest(
                systemPrompt,
                userPrompt,
                tools == null ? List.of() : tools,
                maxIterations <= 0 ? DEFAULT_MAX_ITERATIONS : maxIterations,
                taskTag == null ? "" : taskTag
        );
    }
}
