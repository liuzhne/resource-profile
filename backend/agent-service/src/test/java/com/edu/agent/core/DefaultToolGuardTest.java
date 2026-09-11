package com.edu.agent.core;

import com.edu.agent.security.AgentContextHolder;
import com.edu.agent.security.AgentSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * J-1.2：DefaultToolGuard 决策矩阵单测。
 */
class DefaultToolGuardTest {

    private DefaultToolGuard guard(boolean enabled, String allowed, String sensitive) {
        DefaultToolGuard g = new DefaultToolGuard();
        ReflectionTestUtils.setField(g, "enabled", enabled);
        ReflectionTestUtils.setField(g, "allowedToolsCsv", allowed);
        ReflectionTestUtils.setField(g, "sensitiveToolsCsv", sensitive);
        return g;
    }

    @AfterEach
    void clearCtx() {
        AgentContextHolder.clear();
    }

    @Test
    void disabledAllowsEverything() {
        assertThat(guard(false, "a", "x").check("anything", "{}").allowed()).isTrue();
    }

    @Test
    void allowlistDeniesOutsidersAllowsMembers() {
        DefaultToolGuard g = guard(true, "get_student_profile,get_attendance", "");
        assertThat(g.check("get_attendance", "{}").allowed()).isTrue();
        assertThat(g.check("rm_minus_rf", "{}").allowed()).isFalse();
    }

    @Test
    void emptyAllowlistSkipsAllowlistGate() {
        assertThat(guard(true, "", "").check("any_tool", "{}").allowed()).isTrue();
    }

    @Test
    void oversizeArgsDenied() {
        String big = "x".repeat(DefaultToolGuard.MAX_ARGS_LEN + 1);
        assertThat(guard(true, "", "").check("t", big).allowed()).isFalse();
    }

    @Test
    void sensitiveToolAllowedWhenNoContext() {
        // async 系统任务无上下文 → 敏感工具默认放行
        assertThat(guard(true, "", "get_mental_indicators")
                .check("get_mental_indicators", "{}").allowed()).isTrue();
    }

    @Test
    void sensitiveToolDeniedForUnprivilegedRole() {
        AgentSecurityContext ctx = new AgentSecurityContext();
        ctx.setRole(AgentSecurityContext.ROLE_COUNSELOR);
        ctx.setSensitiveDataAllowed(false);
        AgentContextHolder.set(ctx);
        assertThat(guard(true, "", "get_mental_indicators")
                .check("get_mental_indicators", "{}").allowed()).isFalse();
    }

    @Test
    void sensitiveToolAllowedForPsychologist() {
        AgentSecurityContext ctx = new AgentSecurityContext();
        ctx.setRole(AgentSecurityContext.ROLE_PSYCHOLOGIST);
        AgentContextHolder.set(ctx);
        assertThat(guard(true, "", "get_mental_indicators")
                .check("get_mental_indicators", "{}").allowed()).isTrue();
    }
}
