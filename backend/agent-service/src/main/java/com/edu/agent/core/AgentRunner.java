package com.edu.agent.core;

/**
 * Agent 任务执行入口（AGENT-RUNNER-20261013）。
 *
 * <p>业务层（如 {@code AgentTaskServiceImpl}）只依赖本接口，不关心背后是 ReAct 循环、
 * Graph 编排还是多 Agent 结构。当前唯一实现为 {@link AgentLoop}。
 * 命名刻意不用 Orchestrator，以免与 "orchestrator-workers" 模式混淆，该名留给未来的多 Agent 实现。
 */
public interface AgentRunner {

    /** 执行一次 Agent 任务，不做 final_answer 校验。 */
    default AgentLoopResult run(AgentLoopRequest request) {
        return run(request, null);
    }

    /**
     * 执行一次 Agent 任务。
     *
     * @param validator 可为 null；非空时由实现决定如何对 final_answer 做校验与修复
     */
    AgentLoopResult run(AgentLoopRequest request, FinalAnswerValidator validator);
}
