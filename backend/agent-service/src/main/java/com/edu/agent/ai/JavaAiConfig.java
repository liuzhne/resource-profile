package com.edu.agent.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JAVA-RISK-PLAN-20261013：Java 版 risk / plan 的装配。bean 总是创建（只读 prompt 文件，不触发 LLM 调用），
 * 是否真正使用由 {@code educare.ai.risk.impl} / {@code educare.ai.plan.impl} 决定（见 {@link RiskPlanDispatcher}）。
 */
@Configuration
public class JavaAiConfig {

    @Bean
    public LlmTextCaller llmTextCaller(ChatClient chatClient) {
        return (system, user) -> chatClient.prompt().system(system).user(user).call().content();
    }

    @Bean
    public StructuredLlmClient structuredLlmClient(LlmTextCaller caller,
                                                   @Value("${educare.ai.structured.max-attempts:3}") int maxAttempts) {
        return new StructuredLlmClient(caller, maxAttempts);
    }

    @Bean
    public JavaRiskAssessmentService javaRiskAssessmentService(
            StructuredLlmClient client,
            @Value("classpath:prompts/risk.system.md") Resource prompt) throws IOException {
        return new JavaRiskAssessmentService(client, prompt.getContentAsString(StandardCharsets.UTF_8));
    }

    @Bean
    public JavaPlanGenerationService javaPlanGenerationService(
            StructuredLlmClient client,
            @Value("classpath:prompts/plan.system.md") Resource prompt) throws IOException {
        return new JavaPlanGenerationService(client, prompt.getContentAsString(StandardCharsets.UTF_8));
    }
}
