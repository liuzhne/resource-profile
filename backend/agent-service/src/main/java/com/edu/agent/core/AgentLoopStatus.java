package com.edu.agent.core;

/**
 * H-2.1：AgentLoop 终止状态。
 *
 * <ul>
 *   <li>{@link #COMPLETED} —— LLM 在 maxIterations 内给出 final_answer，正常结束</li>
 *   <li>{@link #MAX_ITERATIONS} —— 用尽 maxIterations 仍未拿到 final_answer，保护性退出</li>
 *   <li>{@link #TOOL_ERROR} —— 工具调用抛异常且重试 1 次仍失败，立即终止</li>
 *   <li>{@link #PARSE_ERROR} —— LLM 连续 2 轮输出非合法 JSON，保护性退出</li>
 *   <li>{@link #VALIDATION_ERROR} —— 最终输出未通过调用方 schema/语义校验</li>
 * </ul>
 */
public enum AgentLoopStatus {
    COMPLETED,
    MAX_ITERATIONS,
    TOOL_ERROR,
    PARSE_ERROR,
    VALIDATION_ERROR
}
