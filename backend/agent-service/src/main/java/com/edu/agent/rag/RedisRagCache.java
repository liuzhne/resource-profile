package com.edu.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/** RAG-JAVA-CORE-20261013：基于 agent-service 现有 Redis 的缓存实现；Redis 故障时当作未命中。 */
@Slf4j
public class RedisRagCache implements RagCache {

    private final StringRedisTemplate redis;

    public RedisRagCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("RAG 缓存读取失败 key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("RAG 缓存写入失败 key={}: {}", key, e.getMessage());
        }
    }
}
