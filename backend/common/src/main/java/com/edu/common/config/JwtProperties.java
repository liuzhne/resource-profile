package com.edu.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}
