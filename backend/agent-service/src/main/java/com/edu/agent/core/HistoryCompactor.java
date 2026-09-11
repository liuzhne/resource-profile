package com.edu.agent.core;

import java.util.List;

/**
 * J-1.1：AgentLoop 历史压缩。
 *
 * <p>问题：原 {@code composeUserPromptWithHistory} 每轮把<b>全量</b>历史原文拼进 user prompt，
 * 多轮 + 大 tool 结果（每条上限 4KB）会让 prompt 无界增长 → 上下文溢出、成本爆、prompt cache 频繁 miss。
 *
 * <p>策略（规则化、确定性、可单测、零额外 LLM 调用）：
 * <ul>
 *   <li><b>最近 {@link #KEEP_RECENT_TURNS} 轮</b>：observation 原文保留（截断到 {@link #RECENT_OBS_MAXLEN}）；</li>
 *   <li><b>更早轮</b>：压缩成一行摘要，observation 重截断到 {@link #OLDER_OBS_MAXLEN}。</li>
 * </ul>
 * 这样总长被"最近 K 轮 × RECENT + 更早 M 轮 × OLDER"上界框住，不随轮数线性爆。
 * 未来要更强语义压缩，可在 {@code composeOlder} 处接一次 LLM 摘要调用（接口不变）。
 */
public final class HistoryCompactor {

    private HistoryCompactor() {
    }

    /** 最近多少轮保留较完整的 observation。 */
    static final int KEEP_RECENT_TURNS = 3;
    /** 最近轮 observation 截断长度。 */
    static final int RECENT_OBS_MAXLEN = 2048;
    /** 更早轮 observation 截断长度（重压缩）。 */
    static final int OLDER_OBS_MAXLEN = 256;
    /** thought 在压缩行里的截断长度。 */
    static final int OLDER_THOUGHT_MAXLEN = 120;

    /**
     * 组装带压缩历史的 user prompt。traces 为空时直接返回原始 userPrompt（与旧行为一致）。
     */
    public static String compose(String userPrompt, List<AgentTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return userPrompt;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(userPrompt).append("\n\n# 历史\n");

        int n = traces.size();
        int recentStart = Math.max(0, n - KEEP_RECENT_TURNS);

        if (recentStart > 0) {
            sb.append("## 较早轮（已压缩）\n");
            for (int i = 0; i < recentStart; i++) {
                appendCompact(sb, traces.get(i));
            }
            sb.append("## 最近轮\n");
        }
        for (int i = recentStart; i < n; i++) {
            appendFull(sb, traces.get(i));
        }
        return sb.toString();
    }

    /** 更早轮：单行高度压缩。 */
    private static void appendCompact(StringBuilder sb, AgentTrace t) {
        sb.append("- iter ").append(t.iteration()).append(": ");
        if (t.thought() != null) {
            sb.append(truncate(t.thought(), OLDER_THOUGHT_MAXLEN));
        }
        if (t.toolName() != null) {
            sb.append(" | ").append(t.toolName());
            if (t.toolResult() != null) {
                sb.append(" -> ").append(truncate(oneLine(t.toolResult()), OLDER_OBS_MAXLEN));
            }
        }
        if (t.parseError() != null) {
            sb.append(" | (parse-error)");
        }
        sb.append('\n');
    }

    /** 最近轮：保留 Thought/Action/Observation 结构，observation 截断到 RECENT。 */
    private static void appendFull(StringBuilder sb, AgentTrace t) {
        if (t.thought() != null) {
            sb.append("Thought: ").append(t.thought()).append('\n');
        }
        if (t.toolName() != null) {
            sb.append("Action: ").append(t.toolName());
            if (t.toolArgs() != null) {
                sb.append(" args=").append(t.toolArgs());
            }
            sb.append('\n');
        }
        if (t.toolResult() != null) {
            sb.append("Observation: ").append(truncate(t.toolResult(), RECENT_OBS_MAXLEN)).append('\n');
        }
        if (t.parseError() != null) {
            sb.append("（上一轮输出解析失败：").append(t.parseError())
                    .append("，请严格按 JSON 协议输出）\n");
        }
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...[+" + (s.length() - max) + "]";
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
    }
}
