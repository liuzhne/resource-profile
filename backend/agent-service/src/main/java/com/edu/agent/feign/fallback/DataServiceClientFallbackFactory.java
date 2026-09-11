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

    /**
     * Create a fallback DataServiceClient that provides empty dashboard statistics when the Feign client fails.
     *
     * @param cause the throwable that triggered creation of this fallback (may be null)
     * @return a DataServiceClient whose getDashboardStatistics logs a warning with the cause message and returns a successful Result containing an empty Map
     */
    @Override
    public DataServiceClient create(Throwable cause) {
        return () -> {
            log.warn("[fallback] data-service getDashboardStatistics 不可用：{}", cause.getMessage());
            return Result.success(Collections.<String, Object>emptyMap());
        };
    }
}
