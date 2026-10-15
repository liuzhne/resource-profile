package com.edu.mcp.student.feign;

import com.edu.common.result.Result;
import com.edu.common.security.InternalCallFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * 调用 mental-service (port 8085)。
 * 查询保持 student_info.id 范围，避免共享登录账号导致资料归属丢失。
 * 直连下游不经网关，附 X-Internal-Token 作为内部调用凭证（{@link InternalCallFeignConfig}）。
 */
@FeignClient(name = "mental-service", url = "${MENTAL_SERVICE_URL:}",
        configuration = InternalCallFeignConfig.class)
public interface MentalServiceClient {

    /** 学生的历次评估记录（内部凭据允许完整字段；不使用账号回查） */
    @GetMapping("/mental/students/{studentId}/assessments")
    Result<List<Map<String, Object>>> historyByStudent(@PathVariable("studentId") Long studentId);
}
