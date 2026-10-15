package com.edu.agent.sse;

import com.edu.agent.security.AgentSessionVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * F-2：SSE 实时预警推送端点。
 *
 * <p>EventSource 浏览器 API 不支持自定义 header，鉴权走两种方式（择一）：
 * <ul>
 *   <li>{@code Authorization: Bearer <token>} ——前端用 fetch-event-source 库时</li>
 *   <li>{@code ?token=<token>} ——降级方案 / 命令行测试</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/agent/api/v1/warning")
@RequiredArgsConstructor
public class WarningSseController {

    private final SseEmitterRegistry registry;
    private final AgentSessionVerifier sessions;

    @Value("${educare.sse.timeout-millis:1800000}")
    private long timeoutMillis;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        String token = extractToken(authHeader, tokenParam);
        AgentSessionVerifier.Verification verification = sessions.verify(token);
        if (verification.status() != AgentSessionVerifier.Status.VALID) {
            return ResponseEntity.status(verification.status() == AgentSessionVerifier.Status.UNAVAILABLE ? 503 : 401).build();
        }
        AgentSessionVerifier.Session session = verification.session();
        // Same staff + agent:read boundary as the ordinary task list; no anonymous/internal SSE shortcut.
        if (!session.warningsAllowed()) return ResponseEntity.status(403).build();
        Long userId = session.userId();
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        registry.add(emitter, session);

        // 立刻发一帧 hello 让浏览器进入 OPEN 状态，避免 fetchEventSource 重试 backoff
        try {
            emitter.send(SseEmitter.event()
                    .name("hello")
                    .data(Map.of(
                            "userId", userId,
                            "ts", System.currentTimeMillis())));
        } catch (IOException e) {
            emitter.completeWithError(e);
            registry.close(emitter);
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
        }

        log.info("F-2：SSE 连接建立 userId={} 当前活跃 {}", userId, registry.size());
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    private String extractToken(String authHeader, String tokenParam) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        return null;
    }
}
