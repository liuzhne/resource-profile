package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * J-3.3：检查点钩子。把每轮轨迹 RPUSH 到 Redis 列表 {@code edu:agent:loop:ckpt:<taskTag>}，
 * 崩溃后可读回做 post-mortem / 续跑基础（onStart 清旧、onFinish 记终态）。默认关。
 *
 * <p>用 Redis 而非新建 DB 表：零 schema 改动、TTL 自动回收；完整的"读回续跑"是后续增量
 * （读这份列表重建 traces 即可，本钩子已提供持久化基础）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointHook implements AgentLoopHook {

    private final StringRedisTemplate redisTemplate;

    @Value("${educare.agent.checkpoint.enabled:false}")
    private boolean enabled;

    @Value("${educare.agent.checkpoint.ttl-seconds:3600}")
    private long ttlSeconds;

    private String key(String taskTag) {
        return "edu:agent:loop:ckpt:" + (taskTag == null ? "unknown" : taskTag);
    }

    @Override
    public void onStart(String taskTag, AgentLoopRequest req) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.delete(key(taskTag));  // 新一轮清旧检查点
        } catch (Exception e) {
            log.debug("checkpoint onStart 清理失败: {}", e.getMessage());
        }
    }

    @Override
    public void onIteration(String taskTag, AgentTrace trace) {
        if (!enabled) {
            return;
        }
        try {
            String k = key(taskTag);
            redisTemplate.opsForList().rightPush(k, JSON.toJSONString(trace));
            redisTemplate.expire(k, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("checkpoint onIteration 落盘失败: {}", e.getMessage());
        }
    }

    @Override
    public void onFinish(String taskTag, AgentLoopResult result) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(taskTag) + ":status",
                    result.status().name(), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("checkpoint onFinish 记终态失败: {}", e.getMessage());
        }
    }
}
