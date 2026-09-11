package com.edu.agent.core;

/**
 * H-2.1：AgentLoop 每轮 think→tool→observe 的事实快照。
 *
 * <p>每个字段可空（除 iteration / rawLlmOutput / latencyMs 外），意义见下：
 * <ul>
 *   <li>{@code thought} —— LLM 解析后的思考；解析失败时为 null</li>
 *   <li>{@code toolName / toolArgs / toolResult} —— 若本轮命中 action，3 字段联动填充</li>
 *   <li>{@code parseError} —— LLM 输出非合法 JSON 时的简短错误信息</li>
 * </ul>
 *
 * <p>H-2.2 接 Langfuse 时会把 traces 上报到 trace span，因此尽量保留原始信息，
 * 不做语义聚合（toolResult 仅做长度截断防止失控）。
 */
public record AgentTrace(
        int iteration,
        String rawLlmOutput,
        String thought,
        String toolName,
        String toolArgs,
        String toolResult,
        long latencyMs,
        String parseError
) {
}
