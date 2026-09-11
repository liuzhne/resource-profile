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

    /**
     * Provide a fallback StudentServiceClient used when remote calls fail.
     *
     * The returned client logs the original cause and:
     * - `getStudentById(Long)` returns a 503 error Result with message "student-service 暂不可用".
     * - `listActiveIds()` returns a successful Result containing an empty list.
     *
     * @param cause the throwable that triggered the fallback
     * @return a StudentServiceClient supplying the described fallback behavior
     */
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
