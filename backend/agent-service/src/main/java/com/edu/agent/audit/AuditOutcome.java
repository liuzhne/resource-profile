package com.edu.agent.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JAVA-AUDIT-20261013：一侧（python 或 java）的审核结论。
 *
 * @param approved 是否通过。只有模型明确给出"通过"且结果自洽时才为 true
 * @param failure  出错原因（TIMEOUT / MODEL_ERROR / PARSE_ERROR / REMOTE_ERROR / INCONSISTENT）；模型正常给出结论时为 null
 * @param body     与原 Python 审核 JSON 同结构的结果（audit_passed / audit_items / redacted_suggestions / manual_review_required）
 */
public record AuditOutcome(String source, boolean approved, String failure, Map<String, Object> body) {

    public static final String TIMEOUT = "TIMEOUT";
    public static final String MODEL_ERROR = "MODEL_ERROR";
    public static final String PARSE_ERROR = "PARSE_ERROR";
    public static final String REMOTE_ERROR = "REMOTE_ERROR";

    /** 出错一律不通过（fail-closed），body 与原 fallbackAudit 同形。 */
    public static AuditOutcome failed(String source, String failure, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dimension", "system");
        item.put("passed", false);
        item.put("issue", source + " 审核出错（" + failure + "）：" + (detail == null ? "" : detail));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audit_passed", false);
        body.put("audit_items", new ArrayList<>(List.of(item)));
        body.put("redacted_suggestions", new ArrayList<>(List.of("请人工审核此方案")));
        body.put("manual_review_required", true);
        body.put("audit_source", source);
        body.put("audit_failure", failure);
        return new AuditOutcome(source, false, failure, body);
    }

    /**
     * 严格判定模型给出的审核 JSON：
     * {@code audit_passed} 必须是 JSON 布尔 true；{@code audit_items} 若存在必须是列表且每项 {@code passed} 为布尔 true；
     * {@code manual_review_required} 不能为 true。缺字段/类型不对按 PARSE_ERROR 处理，结论自相矛盾按不通过处理。
     */
    @SuppressWarnings("unchecked")
    public static AuditOutcome fromModelJson(String source, Map<String, Object> parsed) {
        if (parsed == null) {
            return failed(source, PARSE_ERROR, "空结果");
        }
        Object passed = parsed.get("audit_passed");
        if (!(passed instanceof Boolean)) {
            return failed(source, PARSE_ERROR, "audit_passed 不是布尔值: " + passed);
        }
        Object items = parsed.get("audit_items");
        if (items != null && !(items instanceof List<?>)) {
            return failed(source, PARSE_ERROR, "audit_items 不是列表");
        }
        boolean allItemsPassed = true;
        if (items instanceof List<?> list) {
            for (Object it : list) {
                if (!(it instanceof Map<?, ?> m) || !(m.get("passed") instanceof Boolean b)) {
                    return failed(source, PARSE_ERROR, "audit_items 项缺少布尔 passed");
                }
                allItemsPassed &= b;
            }
        }
        boolean manual = Boolean.TRUE.equals(parsed.get("manual_review_required"));
        boolean approved = (Boolean) passed && allItemsPassed && !manual;
        Map<String, Object> body = new LinkedHashMap<>(parsed);
        body.put("audit_passed", approved);
        body.put("audit_source", source);
        if ((Boolean) passed && !approved) {
            body.put("audit_note", "模型给出通过，但存在未通过项或要求人工复核，按不通过处理");
        }
        return new AuditOutcome(source, approved, null, body);
    }
}
