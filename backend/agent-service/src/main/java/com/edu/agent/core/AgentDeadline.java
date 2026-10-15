package com.edu.agent.core;

/** Propagates the bounded run deadline to synchronous model calls, including nested same-thread loops. */
public final class AgentDeadline implements AutoCloseable {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private final Long previous;
    private AgentDeadline(long milliseconds) {
        previous = DEADLINE.get();
        long requested = System.currentTimeMillis() + milliseconds;
        DEADLINE.set(previous == null ? requested : Math.min(previous, requested));
    }
    public static AgentDeadline start(long milliseconds) { return new AgentDeadline(milliseconds); }
    public static long remainingMillis() {
        Long value = DEADLINE.get();
        return value == null ? Long.MAX_VALUE : Math.max(0, value - System.currentTimeMillis());
    }
    @Override public void close() {
        if (previous == null) DEADLINE.remove(); else DEADLINE.set(previous);
    }
}
