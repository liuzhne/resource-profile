package com.edu.agent.sse;

import com.edu.agent.security.AgentSessionVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F-2：SSE 连接注册表。
 * 仅向持有有效会话和 STAFF_VIEW/agent:read 范围的连接广播，与任务列表权限一致。
 * 每条事件及心跳前重新验证 JWT/Redis，会话撤销后不再发送业务事件。如需 teacher 仅见自己班，需要先建立
 * teacher↔class↔student 的映射，超出 F-2 MVP 范围。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry {

    private final AgentSessionVerifier sessions;
    private final Map<SseEmitter, AgentSessionVerifier.Session> emitters = new ConcurrentHashMap<>();

    public void add(SseEmitter emitter, AgentSessionVerifier.Session session) {
        if (session == null || !session.warningsAllowed()) throw new IllegalArgumentException("SSE requires authorized staff");
        emitters.put(emitter, session);
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
        for (Map.Entry<SseEmitter, AgentSessionVerifier.Session> entry : emitters.entrySet()) {
            SseEmitter e = entry.getKey();
            if (!sessions.isActiveWarningSession(entry.getValue())) {
                close(e);
                continue;
            }
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

    public void close(SseEmitter emitter) {
        emitters.remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }

    /**
     * 心跳：给每个 emitter 写一条 SSE 注释行（`: ping`）。
     * 注释行不会触发前端 EventSource 的 onmessage，仅用于穿透 Nginx/Gateway 的 idle 超时。
     */
    @Scheduled(fixedDelayString = "${educare.sse.heartbeat-millis:30000}")
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        for (Map.Entry<SseEmitter, AgentSessionVerifier.Session> entry : emitters.entrySet()) {
            SseEmitter e = entry.getKey();
            if (!sessions.isActiveWarningSession(entry.getValue())) {
                close(e);
                continue;
            }
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                close(e);
            }
        }
    }
}
