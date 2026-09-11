package com.edu.agent.skill;

/**
 * H-3：一个 Skill 的解析结果。
 *
 * <p>对应一份 markdown 文件（frontmatter + 正文）：
 * <pre>
 * ---
 * name: risk-assessment
 * description: ...
 * when_to_use: ...
 * ---
 * &lt;正文&gt;
 * </pre>
 *
 * @param name       技能名（kebab-case，= 文件名去掉 .md）
 * @param description 一句话描述（用于"按需选择"时给 LLM 判断相关性）
 * @param whenToUse  适用场景
 * @param body       frontmatter 之后的正文 markdown
 */
public record SkillDefinition(String name, String description, String whenToUse, String body) {

    /** 注入 system prompt 的格式化块。 */
    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Skill: ").append(name).append('\n');
        if (description != null && !description.isBlank()) {
            sb.append("> ").append(description).append('\n');
        }
        sb.append('\n').append(body == null ? "" : body.strip()).append('\n');
        return sb.toString();
    }
}
