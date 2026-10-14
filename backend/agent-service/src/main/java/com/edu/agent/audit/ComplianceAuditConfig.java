package com.edu.agent.audit;

import com.edu.agent.feign.AiInferenceClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JAVA-AUDIT-20261013：合规审核装配。{@code educare.ai.audit.impl=python}（默认）时
 * {@code AgentTaskServiceImpl} 不使用这里的 bean，原链路不变。
 */
@Configuration
public class ComplianceAuditConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService complianceAuditExecutor() {
        AtomicInteger n = new AtomicInteger();
        // 固定 8 线程：审核调用都是阻塞 IO；固定大小避免"核心线程满才扩容"导致排队超时
        return new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256), r -> {
            Thread t = new Thread(r, "audit-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public ComplianceAuditService complianceAuditService(
            ChatClient chatClient,
            AiInferenceClient aiInferenceClient,
            @Value("classpath:prompts/audit.system.md") Resource prompt,
            @Value("${educare.ai.audit.impl:python}") String impl,
            @Value("${educare.ai.audit.timeout:60s}") String timeout) throws IOException {
        ExecutorService executor = complianceAuditExecutor();
        Duration t = DurationStyle.detectAndParse(timeout);
        JavaComplianceAuditor javaAuditor = new JavaComplianceAuditor(
                (system, user) -> chatClient.prompt().system(system).user(user).call().content(),
                prompt.getContentAsString(StandardCharsets.UTF_8), t, executor);
        PythonComplianceAuditor pythonAuditor = new PythonComplianceAuditor(aiInferenceClient, t, executor);
        return new ComplianceAuditService(AuditMode.parse(impl), javaAuditor, pythonAuditor);
    }
}
