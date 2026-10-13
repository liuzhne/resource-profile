package com.edu.agent.security;

import java.util.function.Supplier;

/**
 * AUTHZ-MCP-20261013：当前线程（AgentLoop 执行线程）的 MCP 调用身份。
 * 由 {@code AgentTaskServiceImpl.asyncExecute} 在任务执行前后 set / clear。
 */
public final class McpCallerContext {

    private static final ThreadLocal<McpCaller> CURRENT = new ThreadLocal<>();

    private McpCallerContext() {}

    public static McpCaller current() {
        return CURRENT.get();
    }

    /** 在 {@code caller} 身份下执行 {@code body}，结束后恢复原值（支持嵌套）。 */
    public static <T> T callAs(McpCaller caller, Supplier<T> body) {
        McpCaller previous = CURRENT.get();
        CURRENT.set(caller);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(McpCaller caller, Runnable body) {
        callAs(caller, () -> {
            body.run();
            return null;
        });
    }
}
