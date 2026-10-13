package com.edu.agent.ai;

/**
 * JAVA-RISK-PLAN-20261013：一次 system + user 的纯文本 LLM 调用。生产实现见
 * {@link JavaAiConfig#llmTextCaller}（Spring AI ChatClient）；单测用 lambda 替身。
 */
@FunctionalInterface
public interface LlmTextCaller {
    String call(String system, String user);
}
