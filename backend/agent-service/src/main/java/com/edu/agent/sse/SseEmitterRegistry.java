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

    public int size() {
        return emitters.size();
    }

    /**
     * 把事件推送给所有已连接 emitter；推送失败的连接被剔除。
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
     * 心跳：给每个 emitter 写一条 SSE 注释行（`: ping`）。
     * 注释行不会触发前端 EventSource 的 onmessage，仅用于穿透 Nginx/Gateway 的 idle 超时。
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
