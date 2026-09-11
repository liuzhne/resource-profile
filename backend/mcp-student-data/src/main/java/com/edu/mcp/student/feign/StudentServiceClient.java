package com.edu.mcp.student.feign;

import com.edu.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 调用 student-service (port 8084)。
 * Gateway 路由 /student/** → student-service (StripPrefix=0)，
 * 本地/Nacos 环境留空 URL 时走 lb://student-service；Render 等无注册中心环境通过
 * STUDENT_SERVICE_URL 注入固定 HTTPS 地址。
 */
@FeignClient(name = "student-service", url = "${STUDENT_SERVICE_URL:}")
public interface StudentServiceClient {

    /** 学生基础档案 */
    @GetMapping("/student/{id}")
    Result<Map<String, Object>> getStudentById(@PathVariable("id") Long id);

    /** 成绩记录列表；term 可空 */
    @GetMapping("/student/{id}/academic")
    Result<List<Map<String, Object>>> listAcademic(
            @PathVariable("id") Long id,
            @RequestParam(value = "term", required = false) String term);

    /** 考勤明细 */
    @GetMapping("/student/{id}/attendance")
    Result<List<Map<String, Object>>> listAttendance(
            @PathVariable("id") Long id,
            @RequestParam(value = "from", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

    /** 考勤汇总（出勤率 / 缺勤数 / 迟到数 / 请假数） */
    @GetMapping("/student/{id}/attendance/summary")
    Result<Map<String, Object>> attendanceSummary(
            @PathVariable("id") Long id,
            @RequestParam(value = "from", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
