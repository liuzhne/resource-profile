package com.edu.common.security;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

/**
 * Feign 出站内部凭证：给请求附 {@link InternalCallCredential#HEADER}，与下游 {@link ServiceAuthFilter} /
 * {@link AccessGuard} 配对。
 *
 * <p>按客户端挂载 {@code @FeignClient(configuration = InternalCallFeignConfig.class)}，<b>不做全局拦截器</b>：
 * 凭证只发给 student/mental/data 这些需要它的服务，不外泄给 ai-inference-service 等其他目标。
 * 故意不标 {@code @Configuration}：一旦被组件扫描到，就会变成所有 Feign 客户端共用。
 *
 * <p>feign-core 在 common 中是 optional 依赖，只有引用本类的模块（agent-service / mcp-student-data）需要 Feign。
 */
@Slf4j
public class InternalCallFeignConfig {

    @Bean
    public RequestInterceptor internalCallTokenInterceptor(InternalCallCredential credential) {
        if (!credential.isConfigured()) {
            log.warn("EDUCARE_INTERNAL_TOKEN 未配置：内部 Feign 调用不附凭证，下游会以 401 拒绝");
            return template -> { };
        }
        String token = credential.token();
        return template -> template.header(InternalCallCredential.HEADER, token);
    }
}
