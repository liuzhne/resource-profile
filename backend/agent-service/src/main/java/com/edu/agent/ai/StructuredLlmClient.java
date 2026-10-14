package com.edu.agent.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.function.Function;

/**
 * JAVA-RISK-PLAN-20261013：Spring AI 结构化输出 + 有界重试。
 *
 * <p>流程：在 user 消息末尾附 {@link BeanOutputConverter#getFormat()}（Spring AI 生成的 JSON Schema 指令），
 * 调模型 → 先由 BeanOutputConverter 解析（自动去掉 ```json 围栏）→ 再做业务校验。解析或校验失败视为
 * "格式错误"，最多尝试 {@code maxAttempts} 次，仍失败抛 {@link AiStructuredOutputException}
 * （{@code MALFORMED_OUTPUT}）。模型调用异常不在此重试（Spring AI 的 RetryTemplate 已处理 429），
 * 直接抛 {@code MODEL_ERROR}。<b>不会</b>像 Python 端那样静默返回默认值。
 */
@Slf4j
public class StructuredLlmClient {

    private final LlmTextCaller caller;
    private final int maxAttempts;

    public StructuredLlmClient(LlmTextCaller caller, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 必须 ≥ 1");
        }
        this.caller = caller;
        this.maxAttempts = maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * @param validator 返回错误描述，{@code null} 表示通过
     */
    public <T> T call(String route, String system, String user, Class<T> type, Function<T, String> validator) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type, mapper);
        String userWithFormat = user + "\n\n" + converter.getFormat();
        String lastProblem = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String raw;
            try {
                raw = caller.call(system, userWithFormat);
            } catch (Exception e) {
                throw new AiStructuredOutputException(AiStructuredOutputException.Reason.MODEL_ERROR,
                        route, attempt, "模型调用失败: " + e.getMessage(), e);
            }
            try {
                if (raw == null || raw.isBlank()) {
                    throw new IllegalArgumentException("模型返回空内容");
                }
                T parsed = converter.convert(raw);
                if (parsed == null) {
                    throw new IllegalArgumentException("解析结果为空");
                }
                String problem = validator == null ? null : validator.apply(parsed);
                if (problem == null) {
                    if (attempt > 1) {
                        log.info("[{}] 结构化输出第 {} 次尝试成功", route, attempt);
                    }
                    return parsed;
                }
                lastProblem = problem;
            } catch (Exception e) {
                lastProblem = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            log.warn("[{}] 结构化输出不合格（第 {}/{} 次）：{}", route, attempt, maxAttempts, abbreviate(lastProblem));
        }
        throw new AiStructuredOutputException(AiStructuredOutputException.Reason.MALFORMED_OUTPUT,
                route, maxAttempts, "重试 " + maxAttempts + " 次后输出仍不合格: " + abbreviate(lastProblem), null);
    }

    private static String abbreviate(String s) {
        if (s == null) return null;
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
