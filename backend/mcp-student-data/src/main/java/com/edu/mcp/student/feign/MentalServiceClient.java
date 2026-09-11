package com.edu.mcp.student.feign;

import com.edu.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 调用 mental-service (port 8085)。
 * 注意：mental-service 的查询以 sys_user.userId 为参，而本服务 tool 入参是 student_info.id；
 * StudentDataTools 需要先 Feign 取 Student.userId 再调本接口。
 */
@FeignClient(name = "mental-service", url = "${MENTAL_SERVICE_URL:}")
public interface MentalServiceClient {

    /** 学生的历次评估记录（按 userId 拉） */
    @GetMapping("/mental/student/assessments")
    Result<List<Map<String, Object>>> myHistory(@RequestParam("userId") Long userId);
}
