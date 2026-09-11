package com.edu.agent.feign;

import com.edu.agent.feign.fallback.DataServiceClientFallbackFactory;
import com.edu.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 调用 data-service (Port: 8086)
 * 文档端点: GET /data/dashboard/statistics（全校统计面板）
 * Gateway 路由: /data/** → data-service (StripPrefix=0)
 *
 * 注意：既有文档未提供按 studentId 查询的个性化端点。
 * 当前调用全校统计接口，尝试从中提取该学生指标。
 * 建议后续在 data-service 中补充：GET /data/student/{studentId}
 */
@FeignClient(
        name = "data-service",
        url = "${DATA_SERVICE_URL:}",
        fallbackFactory = DataServiceClientFallbackFactory.class
)
public interface DataServiceClient {

    /**
     * 获取全校数据统计面板
     * 返回结构中如包含按学生维度的指标，Aggregator 会尝试提取
     */
    @GetMapping("/data/dashboard/statistics")
    Result<Map<String, Object>> getDashboardStatistics();
}
