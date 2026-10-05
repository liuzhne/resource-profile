package com.edu.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 服务间内部调用凭证：共享密钥 {@code EDUCARE_INTERNAL_TOKEN}（或 {@code educare.internal.token}），
 * 经 {@value #HEADER} 头传递。
 *
 * <p><b>为何需要</b>：Render 部署下每个下游服务（student/mental/data/teacher/user/agent/mcp-student）都是
 * 独立的公网 Web Service，网关不是唯一入口；免费实例又收不到私网流量，agent-service / mcp-student-data 的
 * Feign 调用也只能走公网 HTTPS。「请求没带 JWT 就一定来自内网」因此不成立，内部调用必须出示正向凭证：
 * <ul>
 *   <li>出站：{@link InternalCallFeignConfig} 给指定 Feign 客户端附头；</li>
 *   <li>入站：{@link ServiceAuthFilter}（服务入口准入）、{@link AccessGuard}（端点授权）、
 *       {@link RoleContextFilter}（字段脱敏上下文）都用 {@link #matches} 判定；</li>
 *   <li>网关 {@code InternalHeaderStripFilter} 剥掉客户端自带的同名头，防止经网关伪造。</li>
 * </ul>
 *
 * <p><b>未配置 = 不承认任何内部调用</b>（所有 profile 一致，fail-closed）：此时无 JWT 的请求一律被拒 /
 * 按最低权限脱敏，AI 取数链路会断，但不会泄露数据。本地开发默认值见 docker-compose 与 RUNBOOK §4.4；
 * 生产由 Render {@code generateValue} 或 {@code scripts/preflight-prod.sh} 硬门（≥32 字符）保证。
 *
 * <p>凭证只证明「调用方是持有密钥的内部服务」，不代表任何用户身份。
 */
@Component
public class InternalCallCredential {

    /** 内部调用凭证头。 */
    public static final String HEADER = "X-Internal-Token";

    /** 出站附带的原值；未配置为 {@code null}。 */
    private final String token;
    /** 期望值的 SHA-256 摘要：比较等长摘要，耗时与入参的内容和长度都无关（常量时间）。 */
    private final byte[] expectedDigest;

    public InternalCallCredential(@Value("${educare.internal.token:${EDUCARE_INTERNAL_TOKEN:}}") String token) {
        String stripped = token == null ? "" : token.strip();
        this.token = stripped.isEmpty() ? null : stripped;
        this.expectedDigest = this.token == null ? null : sha256(this.token);
    }

    /** 是否配置了内部凭证；未配置时 {@link #matches} 恒为 false。 */
    public boolean isConfigured() {
        return token != null;
    }

    /** 出站调用要附带的凭证值；未配置返回 {@code null}。 */
    public String token() {
        return token;
    }

    /** 入站头是否为合法内部凭证。未配置 / 头缺失 → false（fail-closed）。 */
    public boolean matches(String presented) {
        if (expectedDigest == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, sha256(presented));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Java 规范要求每个 JRE 都提供 SHA-256，走到这里说明运行环境损坏
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
