package com.edu.agent.core;

import com.edu.agent.security.AgentContextHolder;
import com.edu.agent.security.AgentSecurityContext;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * J-1.2：默认工具守卫。三道闸：
 * <ol>
 *   <li><b>总开关</b> {@code educare.agent.tool-guard.enabled}（默认 true，关 → 全放行）；</li>
 *   <li><b>工具白名单</b> {@code allowed-tools}（CSV，配置则非名单内工具一律 DENY；空=只查后两道）；</li>
 *   <li><b>参数体量</b>：argsJson 超 {@code MAX_ARGS_LEN} → DENY（防超大/异常 payload）；</li>
 *   <li><b>敏感工具</b> {@code sensitive-tools} 需当前 {@link AgentSecurityContext#isSensitiveDataAllowed()}
 *       或管理员/心理咨询师角色；async 无上下文（系统任务）默认放行敏感工具（由 agent 本职执行）。</li>
 * </ol>
 * 每次 DENY 计数 {@code educare.agent.tool_guard.denied{tool}}，便于在 Prometheus 观测。
 */
@Slf4j
@Component
public class DefaultToolGuard implements ToolGuard {

    static final int MAX_ARGS_LEN = 8192;

    @Value("${educare.agent.tool-guard.enabled:true}")
    private boolean enabled;

    @Value("${educare.agent.tool-guard.allowed-tools:}")
    private String allowedToolsCsv;

    @Value("${educare.agent.tool-guard.sensitive-tools:get_mental_indicators}")
    private String sensitiveToolsCsv;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private static final Set<String> SENSITIVE_OK_ROLES = Set.of(
            AgentSecurityContext.ROLE_ADMIN, AgentSecurityContext.ROLE_PSYCHOLOGIST);

    @Override
    public GuardDecision check(String toolName, String argsJson) {
        if (!enabled) {
            return GuardDecision.allow();
        }
        if (toolName == null || toolName.isBlank()) {
            return deny("empty-tool-name", "(none)");
        }
        List<String> allow = csv(allowedToolsCsv);
        if (!allow.isEmpty() && !allow.contains(toolName)) {
            return deny("工具不在白名单", toolName);
        }
        if (argsJson != null && argsJson.length() > MAX_ARGS_LEN) {
            return deny("参数体量超限(" + argsJson.length() + ">" + MAX_ARGS_LEN + ")", toolName);
        }
        if (csv(sensitiveToolsCsv).contains(toolName) && !sensitiveAllowed()) {
            return deny("敏感工具需 sensitiveDataAllowed 或管理员/心理咨询师角色", toolName);
        }
        return GuardDecision.allow();
    }

    /** 当前线程无 AgentSecurityContext（async 系统任务）→ 默认允许敏感工具（agent 本职取数）。 */
    private boolean sensitiveAllowed() {
        AgentSecurityContext ctx = AgentContextHolder.get();
        if (ctx == null) {
            return true;
        }
        return ctx.isSensitiveDataAllowed()
                || (ctx.getRole() != null && SENSITIVE_OK_ROLES.contains(ctx.getRole()));
    }

    private GuardDecision deny(String reason, String tool) {
        if (meterRegistry != null) {
            meterRegistry.counter("educare.agent.tool_guard.denied", "tool", tool).increment();
        }
        return GuardDecision.deny(reason);
    }

    private static List<String> csv(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String x : Arrays.asList(s.split(","))) {
            String t = x.strip();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
