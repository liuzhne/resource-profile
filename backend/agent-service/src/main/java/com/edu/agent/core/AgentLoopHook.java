package com.edu.agent.core;

/**
 * J-3.1：AgentLoop 生命周期钩子。把审计 / 流式 / 检查点等横切关注从循环主体解耦 ——
 * 实现该接口并注册为 Spring bean 即自动接入；钩子异常被吞掉（debug 日志），不影响主循环。
 *
 * <p>已内置实现：{@code StreamingHook}（J-3.2 过程流式）、{@code CheckpointHook}（J-3.3 检查点）。
 */
public interface AgentLoopHook {

    /** 循环开始。 */
    default void onStart(String taskTag, AgentLoopRequest req) {
    }

    /** 每产生一条轨迹（一轮的 think/tool/observe/final 快照）。 */
    default void onIteration(String taskTag, AgentTrace trace) {
    }

    /** 循环终止。 */
    default void onFinish(String taskTag, AgentLoopResult result) {
    }
}
