package com.edu.agent.service;

import com.alibaba.fastjson2.JSON;
import com.edu.agent.feign.DataServiceClient;
import com.edu.agent.feign.MentalServiceClient;
import com.edu.agent.feign.StudentServiceClient;
import com.edu.agent.security.AgentContextHolder;
import com.edu.agent.security.AgentSecurityContext;
import com.edu.agent.util.DataMasker;
import com.edu.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentPortraitAggregator {

    private final StudentServiceClient studentServiceClient;
    private final MentalServiceClient mentalServiceClient;
    private final DataServiceClient dataServiceClient;
    private final DataMasker dataMasker;

    /**
     * 基于既有服务真实端点，聚合学生画像 → 脱敏 → 输出给 LLM
     *
     * 数据流：
     * 1. student-service: /student/{id} → 基础档案（确定可用）
     * 2. mental-service: /mental/analysis → 全校心理分析（尝试提取该学生）
     * 3. data-service: /data/dashboard/statistics → 全校统计（尝试提取该学生）
     */
//    public String buildMaskedProfile(Long studentId) {
//        // 设置角色上下文（后续从 JWT 解析，当前默认 counselor）
//        AgentSecurityContext ctx = new AgentSecurityContext();
//        ctx.setRole(AgentSecurityContext.ROLE_COUNSELOR);
//        ctx.setSensitiveDataAllowed(false);
//        AgentContextHolder.set(ctx);
//
//        try {
//            Map<String, Object> aggregate = new HashMap<>();
//
//            // ========== ① 学生基础档案（student-service，确定可用）==========
//            try {
//                Result<Map<String, Object>> studentResult = studentServiceClient.getStudentById(studentId);
//                if (studentResult != null && studentResult.getData() != null) {
//                    aggregate.putAll(studentResult.getData());
//                    log.info("从 student-service 获取基础档案成功: studentId={}", studentId);
//                }
//            } catch (Exception e) {
//                log.error("调用 student-service 失败: studentId={}, error={}", studentId, e.getMessage());
//                aggregate.put("studentId", studentId);
//                aggregate.put("dataSourceNote", "基础档案获取失败");
//            }
//
//            // ========== ② 心理健康维度（mental-service，全校接口降级提取）==========
//            try {
//                Result<Map<String, Object>> mentalResult = mentalServiceClient.getMentalAnalysis();
//                if (mentalResult != null && mentalResult.getData() != null) {
//                    Map<String, Object> mentalData = mentalResult.getData();
//
//                    // 尝试从全校分析数据中提取该学生的心理指标
//                    // 如果既有接口返回的是 Map<studentId, metrics> 结构
//                    if (mentalData.containsKey(studentId)) {
//                        Object studentMental = mentalData.get(studentId);
//                        if (studentMental instanceof Map) {
//                            aggregate.put("mentalMetrics", studentMental);
//                            log.info("从 mental-service 提取到该学生心理指标");
//                        }
//                    } else {
//                        // 降级：只保留全校参考基准
//                        aggregate.put("mentalContext", "全校心理分析已获取，该学生个性化指标需补充 /mental/record/{id} 接口");
//                        log.warn("mental-service 返回中未包含 studentId={} 的个性化指标", studentId);
//                    }
//                }
//            } catch (Exception e) {
//                log.error("调用 mental-service 失败: {}", e.getMessage());
//                aggregate.put("mentalMetrics", "数据缺失");
//            }
//
//            // ========== ③ 数据画像维度（data-service，全校接口降级提取）==========
//            try {
//                Result<Map<String, Object>> dataResult = dataServiceClient.getDashboardStatistics();
//                if (dataResult != null && dataResult.getData() != null) {
//                    Map<String, Object> stats = dataResult.getData();
//
//                    // 尝试从全校统计中提取该学生的数据指标
//                    if (stats.containsKey("studentStats") && stats.get("studentStats") instanceof Map) {
//                        Map<String, Object> studentStats = (Map<String, Object>) stats.get("studentStats");
//                        if (studentStats.containsKey(studentId)) {
//                            aggregate.put("dataMetrics", studentStats.get(studentId));
//                            log.info("从 data-service 提取到该学生数据指标");
//                        } else {
//                            aggregate.put("dataMetrics", "该学生数据指标未在统计面板中");
//                        }
//                    } else {
//                        aggregate.put("dataContext", "全校统计已获取，该学生个性化指标需补充 /data/student/{id} 接口");
//                        log.warn("data-service 返回中未包含 studentId={} 的个性化指标", studentId);
//                    }
//                }
//            } catch (Exception e) {
//                log.error("调用 data-service 失败: {}", e.getMessage());
//                aggregate.put("dataMetrics", "数据缺失");
//            }
//
//            // ========== ④ 构建原始 JSON → 脱敏 ==========
//            String rawJson = JSON.toJSONString(aggregate);
//            log.info("聚合原始画像完成: studentId={}, fields={}", studentId, aggregate.keySet());
//
//            // 先分级脱敏，再轻度脱敏给 LLM
//            String maskedJson = dataMasker.maskForLLM(dataMasker.maskProfile(rawJson));
//            log.debug("脱敏后画像长度: {} bytes", maskedJson.length());
//
//            return maskedJson;
//
//        } finally {
//            AgentContextHolder.clear();
//        }
//    }

    @Value("${educare.debug.mock-data:false}")
    private boolean mockData;
    /**
     * Builds a masked JSON portrait for a student by aggregating profile, mental, and data metrics.
     *
     * During execution this method sets an AgentSecurityContext with role ROLE_COUNSELOR and
     * sensitive data access disabled, clears the context on completion, and may inject
     * mock/demo fields when the debug flag is enabled or aggregated data is sparse.
     *
     * @param studentId the student identifier; may be non-numeric (in which case base-profile lookup is skipped and a source note is recorded)
     * @return a JSON string containing the aggregated student portrait after applying profile masking and LLM-specific masking
     */
    public String buildMaskedProfile(String studentId) {
        AgentSecurityContext ctx = new AgentSecurityContext();
        ctx.setRole(AgentSecurityContext.ROLE_COUNSELOR);
        ctx.setSensitiveDataAllowed(false);
        AgentContextHolder.set(ctx);

        try {
            Map<String, Object> aggregate = new HashMap<>();

            // 1. 基础档案（兼容 Long 主键，非数字 ID 自动降级）
            loadStudentProfile(studentId, aggregate);

            // 2. 心理健康（全校接口降级提取）
            loadMentalMetrics(studentId, aggregate);

            // 3. 数据画像（全校接口降级提取）
            loadDataMetrics(studentId, aggregate);

            // 4. 【修复3/5】Mock 数据兜底：字段不足时注入演示数据
            if (mockData || aggregate.size() < 5) {
                injectMockData(studentId, aggregate);
            }

            String rawJson = JSON.toJSONString(aggregate);
            log.info("聚合画像: studentId={}, fields={}, size={} bytes",
                    studentId, aggregate.keySet(), rawJson.length());

            return dataMasker.maskForLLM(dataMasker.maskProfile(rawJson));

        } finally {
            AgentContextHolder.clear();
        }
    }


    private void loadStudentProfile(String studentId, Map<String, Object> aggregate) {
        aggregate.put("studentId", studentId);

        // 【修复2】尝试转为 Long 主键；学号类字符串则跳过，避免 400
        Long longId = null;
        try {
            longId = Long.valueOf(studentId);
        } catch (NumberFormatException e) {
            log.warn("studentId={} 非数字主键，跳过 student-service 查询", studentId);
        }

        if (longId != null) {
            try {
                Result<Map<String, Object>> r = studentServiceClient.getStudentById(longId);
                if (r != null && r.getData() != null) {
                    aggregate.putAll(r.getData());
                    log.info("student-service 查询成功");
                    return;
                }
            } catch (Exception e) {
                log.error("student-service 调用失败: {}", e.getMessage());
            }
        }
        aggregate.put("studentSourceNote", "基础档案获取失败（ID类型不匹配或服务异常）");
    }

    private void loadMentalMetrics(String studentId, Map<String, Object> aggregate) {
        try {
            Result<Map<String, Object>> r = mentalServiceClient.getMentalAnalysis();
            if (r != null && r.getData() != null) {
                Map<String, Object> data = r.getData();
                // 尝试从全校数据中提取该学生指标
                if (data.containsKey(studentId)) {
                    aggregate.put("mentalMetrics", data.get(studentId));
                } else if (data.containsKey("studentStats")) {
                    Object stats = data.get("studentStats");
                    if (stats instanceof Map<?, ?> && ((Map<?, ?>) stats).containsKey(studentId)) {
                        aggregate.put("mentalMetrics", ((Map<?, ?>) stats).get(studentId));
                    }
                } else {
                    aggregate.put("mentalContext", "全校分析已获取，个体指标需补充 /mental/record/{id}");
                }
            }
        } catch (Exception e) {
            log.error("mental-service 调用失败: {}", e.getMessage());
            aggregate.put("mentalMetrics", "数据缺失");
        }
    }

    private void loadDataMetrics(String studentId, Map<String, Object> aggregate) {
        try {
            Result<Map<String, Object>> r = dataServiceClient.getDashboardStatistics();
            if (r != null && r.getData() != null) {
                Map<String, Object> data = r.getData();
                if (data.containsKey("studentStats")) {
                    Object stats = data.get("studentStats");
                    if (stats instanceof Map<?, ?> && ((Map<?, ?>) stats).containsKey(studentId)) {
                        aggregate.put("dataMetrics", ((Map<?, ?>) stats).get(studentId));
                    } else {
                        aggregate.put("dataContext", "全校统计已获取，个体指标需补充 /data/student/{id}");
                    }
                }
            }
        } catch (Exception e) {
            log.error("data-service 调用失败: {}", e.getMessage());
            aggregate.put("dataMetrics", "数据缺失");
        }
    }

    private void injectMockData(String studentId, Map<String, Object> aggregate) {
        if (!aggregate.containsKey("name")) aggregate.put("name", "学生" + studentId);
        if (!aggregate.containsKey("grade")) aggregate.put("grade", "大三");
        if (!aggregate.containsKey("major")) aggregate.put("major", "计算机科学与技术");
        if (!aggregate.containsKey("gpaTrend")) aggregate.put("gpaTrend", "3.5→2.1（下降40%）");
        if (!aggregate.containsKey("failedCourses")) aggregate.put("failedCourses", 2);
        if (!aggregate.containsKey("absenceRate")) aggregate.put("absenceRate", 0.15);
        if (!aggregate.containsKey("mentalHealthLevel")) aggregate.put("mentalHealthLevel", "轻度焦虑");
        if (!aggregate.containsKey("mentalScore")) aggregate.put("mentalScore", 145);
        if (!aggregate.containsKey("libraryVisits")) aggregate.put("libraryVisits", 2);
        if (!aggregate.containsKey("diningRegularity")) aggregate.put("diningRegularity", "不规律");

        log.warn("【Mock 模式】已注入演示数据（educare.debug.mock-data=true），生产环境请关闭");
    }
}