package com.edu.agent.core;

import com.edu.agent.sse.WarningPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * J-3.2：过程流式钩子。每轮把 thought/tool/进度发到 {@link WarningPublisher#PROGRESS_CHANNEL}，
 * 前端经 SSE 流式展示 AgentLoop 思考过程（不再只看终态）。默认关，避免无人订阅时的 Redis 噪声。
 */
@Component
@RequiredArgsConstructor
public class StreamingHook implements AgentLoopHook {

    private static final int THOUGHT_MAX = 200;

    private final WarningPublisher warningPublisher;

    @Value("${educare.agent.streaming.enabled:false}")
    private boolean enabled;

    @Override
    public void onIteration(String taskTag, AgentTrace trace) {
        if (!enabled) {
            return;
        }
        String thought = trace.thought();
        if (thought != null && thought.length() > THOUGHT_MAX) {
            thought = thought.substring(0, THOUGHT_MAX) + "...";
        }
        warningPublisher.publishProgress(
                taskTag,
                trace.iteration(),
                thought,
                trace.toolName(),
                trace.toolResult() == null ? 0 : trace.toolResult().length());
    }
}
