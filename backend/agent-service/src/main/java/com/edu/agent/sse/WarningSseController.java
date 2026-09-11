package com.edu.agent.sse;

import com.edu.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    private final JwtUtil jwtUtil;

    @Value("${educare.sse.timeout-millis:1800000}")
    private long timeoutMillis;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        Long userId = extractUserId(authHeader, tokenParam);
        SseEmitter emitter = new SseEmitter(timeoutMillis);

        // 立刻发一帧 hello 让浏览器进入 OPEN 状态，避免 fetchEventSource 重试 backoff
        try {
            emitter.send(SseEmitter.event()
                    .name("hello")
                    .data(Map.of(
                            "userId", userId,
                            "ts", System.currentTimeMillis())));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        registry.add(emitter);
        log.info("F-2：SSE 连接建立 userId={} 当前活跃 {}", userId, registry.size());
        return emitter;
    }

    private Long extractUserId(String authHeader, String tokenParam) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            token = tokenParam;
        }
        if (token == null) return 0L;
        try {
            String subject = jwtUtil.getSubject(token);
            return subject == null ? 0L : Long.parseLong(subject);
        } catch (Exception e) {
            log.debug("F-2：SSE 鉴权失败：{}", e.getMessage());
            return 0L;
        }
    }
}
