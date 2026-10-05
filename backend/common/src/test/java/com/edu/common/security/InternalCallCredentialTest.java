package com.edu.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InternalCallCredential 纯逻辑单测：比对的正反例 + 未配置即 fail-closed。
 */
class InternalCallCredentialTest {

    private static final String SECRET = "internal-secret-at-least-32-chars-0001";

    @Test
    void matches_exactValue() {
        assertThat(new InternalCallCredential(SECRET).matches(SECRET)).isTrue();
    }

    @Test
    void rejects_wrongPrefixLongerEmptyOrNull() {
        InternalCallCredential c = new InternalCallCredential(SECRET);
        assertThat(c.matches("wrong")).isFalse();
        assertThat(c.matches(SECRET.substring(0, 10))).isFalse();   // 前缀
        assertThat(c.matches(SECRET + "x")).isFalse();              // 更长
        assertThat(c.matches("")).isFalse();
        assertThat(c.matches(null)).isFalse();
    }

    @Test
    void unconfigured_neverMatches_failClosed() {
        for (String blank : new String[]{null, "", "   "}) {
            InternalCallCredential c = new InternalCallCredential(blank);
            assertThat(c.isConfigured()).isFalse();
            assertThat(c.token()).isNull();
            // 未配置时空串/空白头也不能匹配，否则"两边都为空"会被当成内部调用
            assertThat(c.matches("")).isFalse();
            assertThat(c.matches("   ")).isFalse();
            assertThat(c.matches(null)).isFalse();
        }
    }

    @Test
    void configuredValue_isStripped() {
        InternalCallCredential c = new InternalCallCredential("  " + SECRET + "\n");
        assertThat(c.isConfigured()).isTrue();
        assertThat(c.token()).isEqualTo(SECRET);
        assertThat(c.matches(SECRET)).isTrue();
    }
}
