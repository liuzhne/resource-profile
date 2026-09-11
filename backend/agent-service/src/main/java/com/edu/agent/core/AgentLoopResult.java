package com.edu.agent.core;

import java.util.List;

/**
 * H-2.1：AgentLoop 输出。
 *
 * @param status       终止状态
 * @param finalAnswer  命中 final_answer 时填；MAX_ITERATIONS 时填最后一轮 thought；其余状态填 null
 * @param traces       每轮事实记录，顺序与执行顺序一致
 * @param iterations   实际执行轮数（1-based）
 */
public record AgentLoopResult(
        AgentLoopStatus status,
        String finalAnswer,
        List<AgentTrace> traces,
        int iterations
) {
}
