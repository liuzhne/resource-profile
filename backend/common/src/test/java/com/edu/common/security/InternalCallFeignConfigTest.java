package com.edu.common.security;

import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出站内部凭证拦截器单测：按 Feign 真实时序（先 resolve 模板、再跑拦截器、最后生成 Request）验证附头。
 */
class InternalCallFeignConfigTest {

    private static Request applyTo(RequestInterceptor interceptor) {
        RequestTemplate template = new RequestTemplate()
                .method(Request.HttpMethod.GET)
                .uri("/student/1")
                .target("https://edu-portrait-student.onrender.com")
                .resolve(Map.of());
        interceptor.apply(template);
        return template.request();
    }

    @Test
    void configured_attachesHeaderLiterally() {
        // 含 Feign 模板符号与 base64 字符，验证按字面发送而非被当成模板表达式
        String token = "gen{erated}+/=value-at-least-32-chars";
        RequestInterceptor interceptor =
                new InternalCallFeignConfig().internalCallTokenInterceptor(new InternalCallCredential(token));

        assertThat(applyTo(interceptor).headers().get(InternalCallCredential.HEADER)).containsExactly(token);
    }

    @Test
    void unconfigured_attachesNothing() {
        RequestInterceptor interceptor =
                new InternalCallFeignConfig().internalCallTokenInterceptor(new InternalCallCredential(""));

        assertThat(applyTo(interceptor).headers()).doesNotContainKey(InternalCallCredential.HEADER);
    }
}
