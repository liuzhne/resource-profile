package com.edu.agent.core;

/**
 * J-1.2：工具守卫。AgentLoop 调用任何工具前过一道授权/校验，决定 ALLOW / DENY。
 *
 * <p>被拒不抛异常 —— AgentLoop 把 DENY 转成结构化 {@code TOOL_DENIED} observation 喂回 LLM，
 * 循环继续、模型可自适应（换工具或直接给结论），而非整轮崩溃。
 */
public interface ToolGuard {

    GuardDecision check(String toolName, String argsJson);

    /** 守卫决策。 */
    record GuardDecision(boolean allowed, String reason) {
        public static GuardDecision allow() {
            return new GuardDecision(true, null);
        }

        public static GuardDecision deny(String reason) {
            return new GuardDecision(false, reason);
        }
    }
}
