package com.edu.agent.feign;

import com.edu.agent.feign.fallback.MentalServiceClientFallbackFactory;
import com.edu.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 调用 mental-service (Port: 8085)
 * 文档端点: GET /mental/analysis（全校心理分析）
 * Gateway 路由: /mental/** → mental-service (StripPrefix=0)
 *
 * 注意：既有文档未提供按 studentId 查询的个性化端点。
 * 当前调用全校分析接口，尝试从中提取该学生指标。
 * 建议后续在 mental-service 中补充：GET /mental/record/{studentId}
 */
@FeignClient(
        name = "mental-service",
        url = "${MENTAL_SERVICE_URL:}",
        fallbackFactory = MentalServiceClientFallbackFactory.class
)
public interface MentalServiceClient {

    /**
     * 获取全校心理健康分析数据
     * 返回结构中如包含按学生维度的指标，Aggregator 会尝试提取
     */
    @GetMapping("/mental/analysis")
    Result<Map<String, Object>> getMentalAnalysis();
}
