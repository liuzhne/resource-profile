package com.edu.mcp.student.feign;

import com.edu.common.result.Result;
import com.edu.common.security.InternalCallFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * AUTHZ-MCP-20261013：调用 teacher-service (port 8083) 取教师档案（院系），供 {@code StudentAccessPolicy}
 * 判定"教师只能看本院系学生"。直连下游附 X-Internal-Token（{@link InternalCallFeignConfig}）。
 */
@FeignClient(name = "teacher-service", url = "${TEACHER_SERVICE_URL:}",
        configuration = InternalCallFeignConfig.class)
public interface TeacherServiceClient {

    /** 按 sys_user.id 查教师档案；非教师返回 data=null。 */
    @GetMapping("/teacher/by-user/{userId}")
    Result<Map<String, Object>> getByUserId(@PathVariable("userId") Long userId);
}
