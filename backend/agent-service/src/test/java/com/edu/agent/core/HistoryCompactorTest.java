package com.edu.agent.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * J-1.1：HistoryCompactor 窗口压缩单测（纯函数，无 Spring）。
 */
class HistoryCompactorTest {

    private AgentTrace toolTrace(int i, String thought, String tool, String obs) {
        return new AgentTrace(i, "raw", thought, tool, "{}", obs, 1L, null);
    }

    private List<AgentTrace> traces(int count, int obsLen) {
        List<AgentTrace> list = new ArrayList<>();
        String obs = "x".repeat(obsLen);
        for (int i = 1; i <= count; i++) {
            list.add(toolTrace(i, "思考" + i, "get_student_profile", "iter" + i + "-" + obs));
        }
        return list;
    }

    @Test
    void emptyTracesReturnsUserPromptUnchanged() {
        assertThat(HistoryCompactor.compose("UP", List.of())).isEqualTo("UP");
        assertThat(HistoryCompactor.compose("UP", null)).isEqualTo("UP");
    }

    @Test
    void withinRecentWindowNoCompactedSection() {
        String out = HistoryCompactor.compose("UP", traces(HistoryCompactor.KEEP_RECENT_TURNS, 50));
        assertThat(out).contains("# 历史").contains("Observation:");
        assertThat(out).doesNotContain("较早轮（已压缩）");  // 未超窗口
    }

    @Test
    void beyondWindowHasCompactedAndRecentSections() {
        String out = HistoryCompactor.compose("UP", traces(HistoryCompactor.KEEP_RECENT_TURNS + 2, 50));
        assertThat(out).contains("较早轮（已压缩）").contains("最近轮");
        assertThat(out).contains("- iter 1:");   // 最早轮被压成单行
    }

    @Test
    void olderObservationsAreHeavilyTruncated() {
        // 第一轮 observation 极大，应被压到 OLDER 量级，而非原样
        List<AgentTrace> t = traces(HistoryCompactor.KEEP_RECENT_TURNS + 1, 5000);
        String out = HistoryCompactor.compose("UP", t);
        // 最早轮那一行不应包含完整 5000 字符（被截断标记）
        assertThat(out).contains("...[+");
    }

    @Test
    void totalLengthIsBoundedNotLinearInTurns() {
        // 20 轮、每轮 4KB observation。压缩后总长应远小于 20*4KB=80KB。
        String out = HistoryCompactor.compose("UP", traces(20, 4096));
        int budgetUpper = HistoryCompactor.KEEP_RECENT_TURNS * (HistoryCompactor.RECENT_OBS_MAXLEN + 200)
                + 20 * (HistoryCompactor.OLDER_OBS_MAXLEN + 200);
        assertThat(out.length()).isLessThan(budgetUpper);
        assertThat(out.length()).isLessThan(20 * 4096);  // 明显小于全量
    }

    @Test
    void truncateHelper() {
        assertThat(HistoryCompactor.truncate("abc", 10)).isEqualTo("abc");
        assertThat(HistoryCompactor.truncate("abcdef", 3)).startsWith("abc").contains("...[+3]");
        assertThat(HistoryCompactor.truncate(null, 5)).isEmpty();
    }
}
