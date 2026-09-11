package com.edu.agent.core;

import com.edu.agent.config.LangfuseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H-2.1 + H-2.2：AgentLoop 控制流单测。
 *
 * <p>不拉 Spring 上下文，纯 Mockito + JUnit 5：用 {@link org.mockito.Mockito#RETURNS_DEEP_STUBS}
 * mock ChatClient 的 fluent chain，4 个 case 覆盖：
 * <ol>
 *   <li>{@link #shouldFinishOnFirstTurn()} —— 第一轮即 final_answer，COMPLETED</li>
 *   <li>{@link #shouldExecuteToolThenFinish()} —— 工具调用 + 二轮 final，traces 完整</li>
 *   <li>{@link #shouldHitMaxIterations()} —— 始终 thought-only，触发 MAX_ITERATIONS 保护</li>
 *   <li>{@link #shouldFireLangfuseTraceOnRunComplete()} —— run 终止时上报顶层 `agent.loop` trace</li>
 * </ol>
 */
class AgentLoopTest {

    private ChatClient chatClient;
    private LangfuseClient langfuseClient;
    private AgentLoop agentLoop;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        langfuseClient = mock(LangfuseClient.class);
        agentLoop = new AgentLoop(chatClient, langfuseClient);
    }

    @Test
    void shouldFinishOnFirstTurn() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"thought\":\"直接给答案\",\"final_answer\":\"OK\"}");

        AgentLoopRequest req = new AgentLoopRequest(
                "你是测试助手", "请回答", List.of(), 3, "test-finish");
        AgentLoopResult result = agentLoop.run(req);

        assertThat(result.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.finalAnswer()).isEqualTo("OK");
        assertThat(result.traces()).hasSize(1);
        assertThat(result.traces().get(0).thought()).isEqualTo("直接给答案");
        assertThat(result.traces().get(0).toolName()).isNull();
        assertThat(result.traces().get(0).parseError()).isNull();
    }

    @Test
    void shouldExecuteToolThenFinish() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(
                        "{\"thought\":\"先调用工具\",\"action\":{\"tool\":\"echo\",\"args\":{\"msg\":\"hi\"}}}",
                        "{\"thought\":\"拿到结果\",\"final_answer\":\"done\"}");

        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("echo");
        when(def.description()).thenReturn("回声工具");
        when(def.inputSchema()).thenReturn("{\"type\":\"object\"}");

        ToolCallback echo = mock(ToolCallback.class);
        when(echo.getToolDefinition()).thenReturn(def);
        when(echo.call(anyString())).thenReturn("hello world");

        AgentLoopRequest req = new AgentLoopRequest(
                "你是工具调度助手", "请调用 echo", List.of(echo), 3, "test-tool");
        AgentLoopResult result = agentLoop.run(req);

        assertThat(result.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.finalAnswer()).isEqualTo("done");
        assertThat(result.traces()).hasSize(2);

        AgentTrace first = result.traces().get(0);
        assertThat(first.toolName()).isEqualTo("echo");
        assertThat(first.toolArgs()).contains("\"msg\":\"hi\"");
        assertThat(first.toolResult()).isEqualTo("hello world");
        assertThat(first.parseError()).isNull();

        AgentTrace second = result.traces().get(1);
        assertThat(second.toolName()).isNull();
        assertThat(second.thought()).isEqualTo("拿到结果");
    }

    @Test
    void shouldHitMaxIterations() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"thought\":\"thinking...\"}");

        AgentLoopRequest req = new AgentLoopRequest(
                "你是慢思考助手", "请慢慢想", List.of(), 2, "test-max");
        AgentLoopResult result = agentLoop.run(req);

        assertThat(result.status()).isEqualTo(AgentLoopStatus.MAX_ITERATIONS);
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.finalAnswer()).isEqualTo("thinking...");
        assertThat(result.traces()).hasSize(2);
        assertThat(result.traces().get(0).toolName()).isNull();
        assertThat(result.traces().get(1).toolName()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFireLangfuseTraceOnRunComplete() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"thought\":\"答\",\"final_answer\":\"OK\"}");

        AgentLoopRequest req = new AgentLoopRequest(
                "sys", "请回答", List.of(), 3, "test-langfuse");
        AgentLoopResult result = agentLoop.run(req);

        assertThat(result.status()).isEqualTo(AgentLoopStatus.COMPLETED);
        verify(langfuseClient, atLeastOnce()).traceGeneration(
                eq("agent.loop"),
                anyString(),
                any(),
                eq("OK"),
                anyLong(),
                anyLong(),
                any(Map.class),
                any(Instant.class),
                any(Instant.class)
        );
    }
}
