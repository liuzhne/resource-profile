package com.edu.agent.ai;

import com.edu.agent.ai.model.RiskAssessment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JAVA-RISK-PLAN-20261013：让 {@code eval/run_eval.py} 不改代码就能评测 Java 风险识别。
 *
 * <p>run_eval.py 固定 POST {@code {base-url}/api/v1/agent/risk}，body 为 {@code {"student_profile": {...}}}，
 * 读取响应里的 {@code risk_level} 等字段。本端点挂在 {@code /agent/_internal/eval} 下，所以
 * {@code --base-url http://127.0.0.1:8087/agent/_internal/eval} 即可。
 *
 * <p>安全：默认不注册（{@code educare.ai.eval-endpoint.enabled=false}）；{@code /_internal/} 路径被网关 403，
 * agent-service 端口只绑 127.0.0.1。仅用于离线评测，生产保持关闭。
 * 失败返回 502（eval 计为错误用例），不返回默认值。
 */
@Slf4j
@RestController
@RequestMapping("/agent/_internal/eval")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "educare.ai.eval-endpoint.enabled", havingValue = "true")
public class RiskEvalController {

    private final RiskPlanDispatcher dispatcher;

    @PostMapping("/api/v1/agent/risk")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> risk(@RequestBody Map<String, Object> body) {
        Object profile = body == null ? null : body.get("student_profile");
        if (!(profile instanceof Map<?, ?>)) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 student_profile"));
        }
        try {
            RiskAssessment r = dispatcher.assessWithJava((Map<String, Object>) profile);
            return ResponseEntity.ok(r);
        } catch (AiStructuredOutputException e) {
            log.warn("eval risk 失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.reason().name(), "message", String.valueOf(e.getMessage())));
        }
    }
}
