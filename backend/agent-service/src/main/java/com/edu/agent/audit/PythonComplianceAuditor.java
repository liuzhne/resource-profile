package com.edu.agent.audit;

import com.edu.agent.feign.AiInferenceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JAVA-AUDIT-20261013：parallel 模式下的 Python 一侧。与 Java 一侧使用同一套严格判定
 * （{@link AuditOutcome#fromModelJson}），Feign 异常 / 超时 / 解析失败一律不通过。
 * python 单独模式仍走 {@code AgentTaskServiceImpl} 里原有代码，行为不变。
 */
@Slf4j
public class PythonComplianceAuditor {

    public static final String SOURCE = "python";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final AiInferenceClient client;
    private final Duration timeout;
    private final Executor executor;
    private final ObjectMapper mapper = new ObjectMapper();

    public PythonComplianceAuditor(AiInferenceClient client, Duration timeout, Executor executor) {
        this.client = client;
        this.timeout = timeout;
        this.executor = executor;
    }

    public AuditOutcome audit(Map<String, Object> request) {
        return auditAsync(request).join();
    }

    /** 非阻塞版本：future 永远正常完成（出错也转成不通过的 {@link AuditOutcome}）。 */
    public CompletableFuture<AuditOutcome> auditAsync(Map<String, Object> request) {
        CompletableFuture<String> call;
        try {
            call = CompletableFuture.supplyAsync(() -> client.complianceAudit(request), executor);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(AuditOutcome.failed(SOURCE, AuditOutcome.REMOTE_ERROR, e.getMessage()));
        }
        return call.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((raw, err) -> {
                    if (err != null) {
                        Throwable cause = JavaComplianceAuditor.unwrap(err);
                        if (cause instanceof TimeoutException) {
                            call.cancel(true);
                            return AuditOutcome.failed(SOURCE, AuditOutcome.TIMEOUT, "超过 " + timeout.toMillis() + " ms");
                        }
                        log.warn("[audit-python] 调用失败，按不通过处理: {}", cause.toString());
                        return AuditOutcome.failed(SOURCE, AuditOutcome.REMOTE_ERROR, cause.getMessage());
                    }
                    try {
                        return AuditOutcome.fromModelJson(SOURCE, raw == null ? null : mapper.readValue(raw, MAP));
                    } catch (Exception e) {
                        return AuditOutcome.failed(SOURCE, AuditOutcome.PARSE_ERROR, "Python 返回不是合法 JSON 对象");
                    }
                });
    }
}
