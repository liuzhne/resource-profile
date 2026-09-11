package com.edu.agent.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.service.AgentTaskService;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI 风险分析任务端点。教职工面向（手动触发分析 / 看任务产物），学生不调。
 *
 * <p><b>越权(IDOR)修复</b>：经 {@link AccessGuard#allowSelfRoleOrInternal} 限教职工角色或内网直调，
 * 否则带 token 的端用户 403，堵住「学生触发他人分析 / 拉取含 4 阶段敏感产物的任务详情」。
 */
@Slf4j
@RestController
@RequestMapping("/agent/api/v1/task")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService agentTaskService;
    private final AccessGuard accessGuard;

    /**
     * 手动触发单学生分析。
     * D 阶段保护：
     *  - Sentinel QPS 限流（资源名 agent:trigger）
     *  - 服务层 Redis 幂等：同一 studentId 30s 内复用最近活跃任务
     */
    @PostMapping("/trigger/{studentId}")
    @SentinelResource(value = "agent:trigger", blockHandler = "triggerBlocked")
    public Result<Long> trigger(
            @PathVariable Long studentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权触发风险分析");
        }
        Long taskId = agentTaskService.triggerTask(studentId);
        return Result.success(taskId);
    }

    /** Sentinel 限流降级：方法签名必须与原方法一致（含 authHeader）并多接 BlockException。 */
    public Result<Long> triggerBlocked(Long studentId, String authHeader, BlockException ex) {
        log.warn("trigger 被限流：studentId={}, rule={}", studentId, ex.getRuleLimitApp());
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    /**
     * 任务详情（含 4 阶段产物 JSON）
     */
    @GetMapping("/{taskId}")
    public Result<AgentTask> detail(
            @PathVariable Long taskId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看任务详情");
        }
        AgentTask task = agentTaskService.getTaskDetail(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 任务分页列表（前端 AI 预警中心用）
     */
    @GetMapping("/list")
    public Result<IPage<AgentTask>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权查看任务列表");
        }
        return Result.success(agentTaskService.listTasks(page, size, status, riskLevel));
    }
}
