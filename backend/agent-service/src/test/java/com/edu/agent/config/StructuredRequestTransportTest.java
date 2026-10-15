package com.edu.agent.config;

import com.edu.agent.core.AgentLoopOutputSchema;
import com.edu.agent.core.AgentLoop;
import com.edu.agent.core.AgentLoopRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Exercise actual Spring AI option merge/serialization against a local HTTP fixture, no provider calls. */
class StructuredRequestTransportTest {
    @Test void structuredTurnRetainsConfiguredOutputBudgetAndReasoning() throws Exception {
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] body = "{\"id\":\"fixture\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var meters = new SimpleMeterRegistry();
        try {
            SpringAiConfig config = new SpringAiConfig();
            ReflectionTestUtils.setField(config,"baseUrl","http://127.0.0.1:"+server.getAddress().getPort());
            ReflectionTestUtils.setField(config,"apiKey","local-fixture-key");
            ReflectionTestUtils.setField(config,"model","openai/gpt-oss-120b");
            ReflectionTestUtils.setField(config,"temperature",0.3d);
            ReflectionTestUtils.setField(config,"maxTokens",2400);
            ReflectionTestUtils.setField(config,"responseFormatType",ResponseFormat.Type.JSON_OBJECT);
            ReflectionTestUtils.setField(config,"reasoningEffort","low");
            ReflectionTestUtils.setField(config,"cachePromptEnabled",false);
            LlmRequestPolicy policy = new LlmRequestPolicy(true,2,1000,0,1,1000,2400,18000);
            ReflectionTestUtils.setField(policy,"sharedCooldownEnabled",false);
            var metrics = new LlmMetricsInterceptor(meters,mock(LangfuseClient.class)); metrics.initMeters();
            var model = config.openAiChatModel(config.openAiApi(metrics,new LlmResponseErrorHandler(),policy),new RetryTemplate(),policy);
            ToolCallback profile = new ToolCallback() {
                public ToolDefinition getToolDefinition() {
                    return ToolDefinition.builder().name("get_student_profile").description("Typed profile fixture")
                            .inputSchema("{\"type\":\"object\",\"properties\":{\"studentId\":{\"type\":\"integer\"}},\"required\":[\"studentId\"]}").build();
                }
                public String call(String input) { throw new AssertionError("Request capture must not execute a business tool"); }
            };
            List<ToolCallback> tools = List.of(profile);
            AgentLoop loop = new AgentLoop(null, mock(LangfuseClient.class));
            ReflectionTestUtils.setField(loop,"structuredOutputEnabled",true);
            String system = ReflectionTestUtils.invokeMethod(loop,"composeSystemPrompt",
                    new AgentLoopRequest("Public protocol fixture","Return a JSON envelope",tools,1,"transport"));
            ChatClient.create(model).prompt().system(system).user("Return a JSON envelope")
                    .options(OpenAiChatOptions.builder().responseFormat(AgentLoopOutputSchema.responseFormat(tools)).build())
                    .call().content();
            assertNotNull(captured.get());
            assertEquals(2400,captured.get().path("max_completion_tokens").asInt(captured.get().path("max_tokens").asInt()));
            assertEquals("low",captured.get().path("reasoning_effort").asText());
            assertEquals("json_schema",captured.get().path("response_format").path("type").asText());
            assertTrue(captured.get().path("response_format").path("json_schema").path("strict").asBoolean());
            assertFalse(captured.get().has("cache_prompt"));
            String sentSystem = captured.get().path("messages").path(0).path("content").asText();
            assertFalse(sentSystem.contains("# 可用工具"));
            assertFalse(sentSystem.contains("name: get_student_profile"));
            assertFalse(sentSystem.contains("description: Typed profile fixture"));
            assertFalse(sentSystem.contains("inputSchema:"));
            assertFalse(sentSystem.contains(profile.getToolDefinition().inputSchema()));
            JsonNode action = captured.get().path("response_format").path("json_schema").path("schema")
                    .path("properties").path("action").path("anyOf").path(0);
            assertEquals("get_student_profile", action.path("properties").path("tool").path("enum").path(0).asText());
            assertEquals("integer", action.path("properties").path("args").path("properties").path("studentId").path("type").asText());
            assertEquals("studentId", action.path("properties").path("args").path("required").path(0).asText());
            assertFalse(action.path("properties").path("args").path("additionalProperties").asBoolean(true));
        } finally { meters.close(); server.stop(0); }
    }
}
