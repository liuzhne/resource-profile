package com.edu.agent.feign.fallback;

import com.edu.agent.feign.DataServiceClient;
import com.edu.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class DataServiceClientFallbackFactory implements FallbackFactory<DataServiceClient> {

    @Override
    public DataServiceClient create(Throwable cause) {
        return () -> {
            log.warn("[fallback] data-service getDashboardStatistics 不可用：{}", cause.getMessage());
            return Result.success(Collections.<String, Object>emptyMap());
        };
    }
}
