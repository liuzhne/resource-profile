package com.edu.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.edu.agent.entity.AgentTask;

public interface AgentTaskService {

    /**
     * 触发任务：D 阶段加入幂等，30s 内同一 studentId 命中复用现有活跃任务。
     * 内部调用 createTask + asyncExecute；返回的 taskId 可能是新建或已有的。
     */
    Long triggerTask(Long studentId);

    /**
     * AUTHZ-MCP-20261013：以指定 MCP 调用身份触发。用户触发传 {@link com.edu.agent.security.McpCaller#user}
     * （转发其 JWT），无用户上下文时传 {@link com.edu.agent.security.McpCaller#system}。
     * {@link #triggerTask(Long)} 等价于系统身份、发起者 {@code scheduler}。
     */
    Long triggerTask(Long studentId, com.edu.agent.security.McpCaller caller);

    /**
     * 创建任务（同步）
     */
    Long createTask(Long studentId);

    /**
     * 异步执行 4-Agent 流水线
     */
    void asyncExecute(Long taskId);

    /** AUTHZ-MCP-20261013：在 {@code caller} 身份下执行任务（MCP 调用携带该身份）。 */
    void asyncExecute(Long taskId, com.edu.agent.security.McpCaller caller);

    /**
     * 任务详情
     */
    AgentTask getTaskDetail(Long taskId);

    /**
     * 任务分页列表
     *
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @param status    状态过滤（可空）
     * @param riskLevel 风险等级过滤（可空）
     */
    IPage<AgentTask> listTasks(long page, long size, String status, String riskLevel);
}