package com.edu.agent.core;

import com.edu.agent.skill.SkillDefinition;
import com.edu.agent.skill.SkillLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * J-2.1：子代理注册表。把班主任 / 心理咨询师 / 学业导师三类专家视角构建为 agent-as-tool。
 *
 * <p>每个专家 = 一个 {@link SubAgent}（专家人设 + 对应技能正文做 system prompt + 工具子集），
 * 经 {@link SubAgentToolCallback} 适配后由主 loop 当工具调用。默认关闭（不扰动既有默认链路），
 * {@code educare.agent.subagents.enabled=true} 开启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentRegistry {

    private final AgentLoop agentLoop;
    private final SkillLoader skillLoader;

    @Value("${educare.agent.subagents.enabled:false}")
    private boolean enabled;

    @Value("${educare.agent.subagents.max-iterations:4}")
    private int maxIterations;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 构建子代理工具列表。{@code allTools} 提供主 loop 全量工具供子代理按名过滤。
     * 关闭时返回空。
     */
    public List<ToolCallback> subAgentTools(Supplier<List<ToolCallback>> allTools) {
        if (!enabled) {
            return List.of();
        }
        return List.of(
                build("consult_head_teacher",
                        "班主任视角：评估学业表现、出勤与班级融入，给出关注与帮扶建议。",
                        "intervention-design",
                        List.of("get_academic_history", "get_attendance"), allTools),
                build("consult_psychologist",
                        "心理咨询师视角：心理风险初筛、预警分级与转介建议（不做临床诊断）。",
                        "psychological-screening",
                        List.of("get_mental_indicators", "search_psychology"), allTools),
                build("consult_academic_advisor",
                        "学业导师视角：选课/补考/学业规划与学习方法辅导建议。",
                        "risk-assessment",
                        List.of("get_academic_history", "search_cases"), allTools));
    }

    private ToolCallback build(String name, String desc, String skillName,
                               List<String> toolNames, Supplier<List<ToolCallback>> allTools) {
        String skillBody = skillLoader.getSkill(skillName)
                .map(SkillDefinition::body).orElse("");
        String systemPrompt = "你是" + desc + "\n只就你的专业视角给出**简要**结论与依据，"
                + "基于工具返回的事实推理，遵守相关红线。\n\n# 专业方法\n" + skillBody;
        SubAgent spec = new SubAgent(name, desc, systemPrompt, toolNames, maxIterations);
        return new SubAgentToolCallback(spec, agentLoop, allTools);
    }
}
