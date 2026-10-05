package com.edu.agent.feign;

import com.edu.common.security.InternalCallFeignConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部凭证只挂在调用下游业务服务的 Feign 客户端上：student/mental/data 漏挂会被下游 401，
 * 挂到 ai-inference 则会把凭证发给不需要它的服务。
 */
class InternalCallFeignWiringTest {

    private static boolean carriesInternalCredential(Class<?> client) {
        return Arrays.asList(client.getAnnotation(FeignClient.class).configuration())
                .contains(InternalCallFeignConfig.class);
    }

    @Test
    void downstreamBusinessClients_carryInternalCredential() {
        assertThat(carriesInternalCredential(StudentServiceClient.class)).isTrue();
        assertThat(carriesInternalCredential(MentalServiceClient.class)).isTrue();
        assertThat(carriesInternalCredential(DataServiceClient.class)).isTrue();
    }

    @Test
    void aiInferenceClient_doesNotReceiveInternalCredential() {
        assertThat(carriesInternalCredential(AiInferenceClient.class)).isFalse();
    }
}
