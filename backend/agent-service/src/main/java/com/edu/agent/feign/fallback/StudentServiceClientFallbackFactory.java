package com.edu.agent.feign.fallback;

import com.edu.agent.feign.StudentServiceClient;
import com.edu.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StudentServiceClientFallbackFactory implements FallbackFactory<StudentServiceClient> {

    @Override
    public StudentServiceClient create(Throwable cause) {
        return new StudentServiceClient() {
            @Override
            public Result<Map<String, Object>> getStudentById(Long id) {
                log.warn("[fallback] student-service getStudentById({}) 不可用：{}", id, cause.getMessage());
                return Result.error(503, "student-service 暂不可用");
            }

            @Override
            public Result<List<Long>> listActiveIds() {
                log.warn("[fallback] student-service listActiveIds 不可用：{}", cause.getMessage());
                return Result.success(Collections.emptyList());
            }
        };
    }
}
