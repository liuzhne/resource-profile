package com.edu.agent.core;

import java.util.List;

/**
 * J-2.1：子代理（专家视角）配置。
 *
 * <p>每个子代理是一个**受限的 AgentLoop**：独立 system prompt（专家人设 + 技能子集）、
 * 独立工具子集（{@code toolNames}）、独立 context（每次调用全新 request，不与主 loop 共享历史）。
 * 通过 {@link SubAgentToolCallback} 适配成工具喂给主 loop —— 即 agent-as-tool。
 *
 * @param name          作为工具暴露的名字（snake_case，如 consult_psychologist）
 * @param description   工具描述（主 loop 据此判断何时调用）
 * @param systemPrompt  子代理 system prompt（专家人设 + 技能正文）
 * @param toolNames     允许使用的工具名子集（从主 loop 全量工具里按名过滤；空=无工具纯推理）
 * @param maxIterations 子代理循环上限
 */
public record SubAgent(
        String name,
        String description,
        String systemPrompt,
        List<String> toolNames,
        int maxIterations
) {
}
