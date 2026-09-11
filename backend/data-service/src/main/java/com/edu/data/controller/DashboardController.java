package com.edu.data.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.data.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据看板：全校聚合统计/趋势/分布/最近登录，含跨学生汇总，仅教职工可见。
 *
 * <p><b>越权(IDOR)修复</b>：经 {@link AccessGuard#allowSelfRoleOrInternal} 限教职工角色或内网直调，
 * 否则带 token 的端用户 403，堵住「学生拉取全校聚合数据」。
 */
@RestController
@RequestMapping("/data/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AccessGuard accessGuard;

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看数据看板");
        }
        return Result.success(dashboardService.getStatistics());
    }

    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(
            @RequestParam(defaultValue = "week") String period,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看数据看板");
        }
        return Result.success(dashboardService.getTrend(period));
    }

    @GetMapping("/distribution")
    public Result<Map<String, Object>> distribution(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看数据看板");
        }
        return Result.success(dashboardService.getDistribution());
    }

    @GetMapping("/recentLogins")
    public Result<List<Map<String, Object>>> recentLogins(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看数据看板");
        }
        return Result.success(dashboardService.getRecentLogins());
    }
}
