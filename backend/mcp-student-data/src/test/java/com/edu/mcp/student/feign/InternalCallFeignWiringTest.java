package com.edu.mcp.student.feign;

import com.edu.common.security.InternalCallFeignConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 工具经 Feign 取学生/心理数据：两个客户端都必须附内部凭证，否则下游 student/mental 会 401。
 */
class InternalCallFeignWiringTest {

    private static boolean carriesInternalCredential(Class<?> client) {
        return Arrays.asList(client.getAnnotation(FeignClient.class).configuration())
                .contains(InternalCallFeignConfig.class);
    }

    @Test
    void downstreamClients_carryInternalCredential() {
        assertThat(carriesInternalCredential(StudentServiceClient.class)).isTrue();
        assertThat(carriesInternalCredential(MentalServiceClient.class)).isTrue();
    }
}
