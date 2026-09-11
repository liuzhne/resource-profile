package com.edu.agent.sse;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * F-2：订阅 Redis Pub/Sub 通道，把消息 fan-out 到本机 SSE emitter。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WarningSubscriberConfig {

    private final SseEmitterRegistry registry;

    @Bean
    public RedisMessageListenerContainer warningListenerContainer(RedisConnectionFactory cf) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                Map<String, Object> data = JSON.parseObject(body, Map.class);
                registry.broadcast("warning", data);
            } catch (Exception e) {
                log.warn("F-2：分发事件失败：body={} err={}", body, e.getMessage());
            }
        }, new ChannelTopic(WarningPublisher.CHANNEL));
        log.info("F-2：订阅 Redis 通道 {}", WarningPublisher.CHANNEL);
        return container;
    }
}
