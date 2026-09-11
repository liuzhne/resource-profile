package com.edu.agent.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F-2：SSE 连接注册表。
 * 简化策略——广播到所有已连接 emitter，与现有 GET /agent/.../task/list 的安全模型一致
 * （该端点同样不做 class/角色级过滤）。如需 teacher 仅见自己班，需要先建立
 * teacher↔class↔student 的映射，超出 F-2 MVP 范围。
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /**
     * Register an SseEmitter and attach lifecycle handlers.
     *
     * Adds the provided emitter to the registry and attaches handlers that remove it from
     * the registry when the emitter completes, times out, or encounters an error. On timeout
     * the emitter is explicitly completed.
     *
     * @param emitter the SseEmitter to register; its lifecycle events will drive removal from the registry
     */
    public void add(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("F-2：SSE 连接关闭，剩余 {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(ex -> emitters.remove(emitter));
    }

    /**
     * Report how many SseEmitters are currently registered.
     *
     * @return the number of registered SseEmitter instances
     */
    public int size() {
        return emitters.size();
    }

    /**
     * Broadcasts an SSE event with the given name and payload to all registered emitters.
     *
     * Emitters that fail to send are removed from the registry and will be completed with the encountered error;
     * failures that occur while completing an emitter are ignored.
     *
     * @param eventName the SSE event name to send
     * @param data      the event payload (will be serialized for SSE data)
     */
    public void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) return;
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(e);
                try {
                    e.completeWithError(ex);
                } catch (Exception ignore) {
                    // emitter 已经处于完成态，忽略
                }
            }
        }
    }

    /**
     * Sends a periodic SSE comment ("ping") to each registered emitter to keep proxy or gateway connections from idling out.
     *
     * For each emitter, attempts to send an SSE comment; emitters that fail to send are removed from the registry.
     */
    @Scheduled(fixedDelayString = "${educare.sse.heartbeat-millis:30000}")
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(e);
            }
        }
    }
}
