package com.edu.agent.sse;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * F-2：风险任务终态事件发布器。
 * 单实例场景下也走 Redis Pub/Sub —— 同一应用内 publisher 与 subscriber 通过 Redis 解耦，
 * 加多实例时无需改代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarningPublisher {

    public static final String CHANNEL = "edu:agent:warning:new";
    /** J-3.2：AgentLoop 过程流式事件频道（思考/工具/进度），与终态频道分离。 */
    public static final String PROGRESS_CHANNEL = "edu:agent:progress";

    private final StringRedisTemplate redisTemplate;

    /** J-3.2：发布一条 AgentLoop 过程进度事件（每轮一条），供前端流式展示。失败 fail-soft。 */
    public void publishProgress(String taskTag, int iteration, String thought, String tool, int observationLen) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskTag", taskTag);
        payload.put("iteration", iteration);
        payload.put("thought", thought);
        payload.put("tool", tool);
        payload.put("observationLen", observationLen);
        payload.put("ts", System.currentTimeMillis());
        try {
            redisTemplate.convertAndSend(PROGRESS_CHANNEL, JSON.toJSONString(payload));
        } catch (Exception e) {
            log.debug("J-3.2：发布进度到 {} 失败：{}", PROGRESS_CHANNEL, e.getMessage());
        }
    }

    public void publishTerminal(Long taskId, String status, String riskLevel, Long studentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", status);
        payload.put("riskLevel", riskLevel);
        payload.put("studentId", studentId);
        payload.put("ts", System.currentTimeMillis());
        try {
            redisTemplate.convertAndSend(CHANNEL, JSON.toJSONString(payload));
            log.debug("F-2：发布终态事件 taskId={} status={}", taskId, status);
        } catch (Exception e) {
            log.warn("F-2：发布到 {} 失败：{}", CHANNEL, e.getMessage());
        }
    }
}
