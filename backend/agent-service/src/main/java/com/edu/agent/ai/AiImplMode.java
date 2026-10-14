package com.edu.agent.ai;

import java.util.Locale;

/**
 * JAVA-RISK-PLAN-20261013：AI 阶段实现选择。
 * <ul>
 *   <li>{@code legacy}：仅风险识别有 —— main 上 legacy 流水线一直用 {@code RiskAnalyzeService}（Java ChatClient
 *       + risk-analyze.system.md，失败返回默认中风险），不经 Python；作为风险识别默认值以保证行为不变。</li>
 *   <li>{@code python}：Feign 调 ai-inference-service。</li>
 *   <li>{@code java}：本包内的 Spring AI 结构化输出实现。</li>
 * </ul>
 */
public enum AiImplMode {
    LEGACY, PYTHON, JAVA;

    public static AiImplMode parse(String raw, AiImplMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("未知的 AI 实现选择: " + raw + "（可选 legacy|python|java）");
        }
    }
}
