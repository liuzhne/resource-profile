package com.edu.auth.config;

import com.edu.common.util.JwtUtil;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Map;

/** Prime required session transport and signing classes before readiness; writes no session. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "educare.performance.database-warmup", havingValue = "true")
public class AuthDependencyWarmupConfiguration {
    @Bean
    public ApplicationRunner authDependencyWarmup(StringRedisTemplate redis, JwtUtil jwt) {
        return args -> {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            if (!"PONG".equals(pong)) throw new IllegalStateException("Redis session dependency is not ready");
            // In-memory only, no token logging/session write; cannot pass the session whitelist.
            jwt.generateAccessToken("0", Map.of());
            jwt.generateRefreshToken("0");
        };
    }
}
