package com.edu.agent.config;

import com.edu.common.exception.BusinessException;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Connect/reconnect MCP off the startup/HTTP threads. Never expose a partial tool set as ready. */
@Slf4j
public class DeferredMcpToolProvider implements ToolCallbackProvider, AutoCloseable {
    private record Connection(McpSyncClient client, ToolCallback[] tools) { }
    private final List<String> names;
    private final Function<String, McpSyncClient> factory;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private volatile boolean closed;

    public DeferredMcpToolProvider(List<String> names, Function<String, McpSyncClient> factory) {
        this.names = List.copyOf(names);
        this.factory = factory;
        this.executor = Executors.newScheduledThreadPool(names.size(), task -> {
            Thread thread = new Thread(task, "mcp-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        for (String name : names) {
            executor.scheduleWithFixedDelay(() -> refresh(name), 0, 30, TimeUnit.SECONDS);
        }
    }

    void refresh(String name) {
        if (closed) return;
        Connection current = connections.get(name);
        McpSyncClient client = current == null ? null : current.client();
        try {
            if (client == null) {
                client = factory.apply(name);
                client.initialize();
            } else {
                client.ping();
            }
            ToolCallback[] tools = new SyncMcpToolCallbackProvider(List.of(client)).getToolCallbacks();
            if (tools.length == 0) throw new IllegalStateException("MCP returned no tools");
            synchronized (connections) {
                if (closed) {
                    client.close();
                    return;
                }
                connections.put(name, new Connection(client, tools));
            }
            if (current == null) log.info("MCP connection={} ready tools={}", name, tools.length);
        } catch (Exception e) {
            connections.remove(name);
            if (client != null) {
                try { client.close(); } catch (Exception ignored) { }
            }
            // Do not log raw response bodies, credentials, URLs or request headers.
            log.warn("MCP connection={} unavailable errorType={}; retry in 30s", name, e.getClass().getSimpleName());
        }
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<Connection> snapshot = names.stream().map(connections::get).toList();
        if (snapshot.stream().anyMatch(connection -> connection == null)) {
            throw new BusinessException(503, "MCP 工具尚未就绪，请稍后重试");
        }
        return snapshot.stream().flatMap(connection -> java.util.Arrays.stream(connection.tools()))
                .toArray(ToolCallback[]::new);
    }

    @PreDestroy
    public void close() {
        List<Connection> closing;
        synchronized (connections) {
            closed = true;
            closing = List.copyOf(connections.values());
            connections.clear();
        }
        executor.shutdownNow();
        closing.forEach(connection -> {
            try { connection.client().close(); } catch (Exception ignored) { }
        });
    }
}
