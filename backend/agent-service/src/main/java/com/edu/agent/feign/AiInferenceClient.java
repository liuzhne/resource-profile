package com.edu.agent.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "ai-inference-service",
        configuration = com.edu.common.security.InternalCallFeignConfig.class,
        url = "${ai.inference.url:http://localhost:8090}"
)
public interface AiInferenceClient {

    @PostMapping("/api/v1/rag/retrieve")
    String retrieveKnowledge(@RequestBody Map<String, Object> query);

}
