package com.edu.agent.audit;

import java.util.Locale;

/** JAVA-AUDIT-20261013：合规审核实现选择，{@code educare.ai.audit.impl}，默认 python。 */
public enum AuditMode {
    PYTHON, JAVA, PARALLEL;

    public static AuditMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PYTHON;
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("未知的 educare.ai.audit.impl: " + raw + "（可选 python|java|parallel）");
        }
    }
}
