package com.edu.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 密钥。<b>无默认</b>：必须经 {@code JWT_SECRET} 环境变量（Spring relaxed binding 自动映射到
     * {@code jwt.secret}）或 Nacos {@code common.yml} 配置，长度 ≥ 32 字符。缺失/过弱时 {@link com.edu.common.util.JwtUtil}
     * 启动即 fail-fast（A5：密钥不留可预测默认）。
     */
    private String secret = "";

    /**
     * 访问令牌过期时间（毫秒），默认 24 小时
     */
    private long accessTokenExpire = 24 * 60 * 60 * 1000;

    /**
     * 刷新令牌过期时间（毫秒），默认 7 天
     */
    private long refreshTokenExpire = 7 * 24 * 60 * 60 * 1000;

    /**
     * AUTHZ-MCP-20261013：签发访问令牌时写入的 {@code aud}（受众）列表。默认同时包含网关业务 API 与
     * student-data MCP server，使同一枚用户 JWT 可被 agent-service 转发给 MCP 并通过受众校验。
     * 置空列表即不写 aud（回到旧令牌形态）。jjwt 解析不要求 aud 时会忽略它，因此对现有校验方零影响。
     */
    private List<String> audiences = new ArrayList<>(List.of(AUDIENCE_API, AUDIENCE_MCP_STUDENT_DATA));

    /** 系统身份令牌（异步/预警任务）默认有效期（毫秒）：15 分钟，够一次 AgentLoop，泄漏面小。 */
    private long systemTokenExpire = 15 * 60 * 1000;

    /** 业务 API 受众标识。 */
    public static final String AUDIENCE_API = "educare-api";

    /** student-data MCP server 受众标识。 */
    public static final String AUDIENCE_MCP_STUDENT_DATA = "mcp-student-data";
}
