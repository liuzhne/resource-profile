package com.edu.agent.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JAVA-AUDIT-20261013：java / parallel 模式的合规审核入口。返回写入 {@code agent_task.compliance_audit} 的 JSON，
 * 其中 {@code audit_passed} 就是最终结论（调用方原有的 {@code parseAuditPassed} 无需改动）。
 *
 * <ul>
 *   <li>java：只跑 Java 审核。</li>
 *   <li>parallel：Python 与 Java 并发执行；<b>任一侧不通过（含出错）即不通过</b>。结果合并两侧的
 *       audit_items / redacted_suggestions，并在 {@code parallel} 字段保留两侧原始结论便于对比。</li>
 * </ul>
 * 本方法自身也 fail-closed：任何意外异常都返回不通过。
 */
@Slf4j
public class ComplianceAuditService {

    private final AuditMode mode;
    private final JavaComplianceAuditor javaAuditor;
    private final PythonComplianceAuditor pythonAuditor;
    private final ObjectMapper mapper = new ObjectMapper();

    public ComplianceAuditService(AuditMode mode, JavaComplianceAuditor javaAuditor,
                                  PythonComplianceAuditor pythonAuditor) {
        this.mode = mode;
        this.javaAuditor = javaAuditor;
        this.pythonAuditor = pythonAuditor;
    }

    public AuditMode mode() {
        return mode;
    }

    /** @param request 与原 Feign 请求相同：{@code student_profile} + {@code intervention_plan} */
    @SuppressWarnings("unchecked")
    public String audit(Map<String, Object> request) {
        AuditOutcome result;
        try {
            Map<String, Object> profile = asMap(request.get("student_profile"));
            Map<String, Object> plan = asMap(request.get("intervention_plan"));
            result = switch (mode) {
                case JAVA -> javaAuditor.audit(profile, plan);
                case PARALLEL -> parallel(request, profile, plan);
                case PYTHON -> throw new IllegalStateException("python 模式走原有代码路径");
            };
        } catch (Exception e) {
            log.error("[audit] 审核编排异常，按不通过处理", e);
            result = AuditOutcome.failed(mode.name().toLowerCase(), AuditOutcome.MODEL_ERROR, e.getMessage());
        }
        log.info("[audit] mode={} approved={} failure={}", mode, result.approved(), result.failure());
        return toJson(result.body());
    }

    private AuditOutcome parallel(Map<String, Object> request, Map<String, Object> profile, Map<String, Object> plan) {
        // 两侧各自在线程池上发起并自带超时，这里只等结果，不嵌套占用线程
        CompletableFuture<AuditOutcome> py = pythonAuditor.auditAsync(request)
                .exceptionally(e -> AuditOutcome.failed(PythonComplianceAuditor.SOURCE, AuditOutcome.REMOTE_ERROR, e.getMessage()));
        CompletableFuture<AuditOutcome> java = javaAuditor.auditAsync(profile, plan)
                .exceptionally(e -> AuditOutcome.failed(JavaComplianceAuditor.SOURCE, AuditOutcome.MODEL_ERROR, e.getMessage()));
        AuditOutcome p = py.join();
        AuditOutcome j = java.join();
        return merge(p, j);
    }

    static AuditOutcome merge(AuditOutcome p, AuditOutcome j) {
        boolean approved = p.approved() && j.approved();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audit_passed", approved);
        body.put("audit_items", concat(p.body().get("audit_items"), j.body().get("audit_items")));
        body.put("redacted_suggestions", concat(p.body().get("redacted_suggestions"), j.body().get("redacted_suggestions")));
        body.put("manual_review_required", !approved
                || Boolean.TRUE.equals(p.body().get("manual_review_required"))
                || Boolean.TRUE.equals(j.body().get("manual_review_required")));
        body.put("audit_source", "parallel");
        Map<String, Object> sides = new LinkedHashMap<>();
        sides.put("python", Map.of("approved", p.approved(), "failure", String.valueOf(p.failure())));
        sides.put("java", Map.of("approved", j.approved(), "failure", String.valueOf(j.failure())));
        sides.put("agree", p.approved() == j.approved());
        body.put("parallel", sides);
        String failure = p.failure() != null ? p.failure() : j.failure();
        return new AuditOutcome("parallel", approved, failure, body);
    }

    private static List<Object> concat(Object a, Object b) {
        List<Object> out = new ArrayList<>();
        if (a instanceof List<?> l) out.addAll(l);
        if (b instanceof List<?> l) out.addAll(l);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o == null) return Map.of();
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return mapper.convertValue(o, Map.class);
    }

    private String toJson(Map<String, Object> body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            // 序列化都失败时仍给出不通过
            return "{\"audit_passed\":false,\"manual_review_required\":true,\"audit_failure\":\"SERIALIZE_ERROR\"}";
        }
    }
}
