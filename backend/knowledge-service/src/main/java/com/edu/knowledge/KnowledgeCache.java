package com.edu.knowledge;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
@Component
public class KnowledgeCache {
    private final StringRedisTemplate redis;
    public KnowledgeCache(StringRedisTemplate redis) { this.redis=redis; }
    public String get(String key) { return redis.opsForValue().get(key); }
    public void remove(String key) { redis.delete(key); }
    public void put(String key,String value,long seconds) { redis.opsForValue().set(key,value,Duration.ofSeconds(seconds)); }
    public boolean lock(String key,String owner) { return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,owner,Duration.ofMinutes(15))); }
    public void unlock(String key,String owner) {
        redis.execute(new DefaultRedisScript<>("if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",Long.class), List.of(key),owner);
    }
}
