package com.edu.agent.skill;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-3.5：SkillLoader 单测。
 *
 * <p>parse() 纯逻辑直接验；classpath 加载用 src/main/resources/skills/ 下真实的 4 个技能文件
 * （test 运行时在 classpath 上）。
 */
class SkillLoaderTest {

    private SkillLoader loader(boolean enabled, String active) {
        SkillLoader l = new SkillLoader();
        ReflectionTestUtils.setField(l, "enabled", enabled);
        ReflectionTestUtils.setField(l, "activeCsv", active);
        return l;
    }

    @Test
    void parseExtractsFrontmatterAndBody() {
        String raw = "---\nname: x\ndescription: 一句话描述\nwhen_to_use: 某场景\n---\n# 正文\n内容行\n";
        SkillDefinition d = SkillLoader.parse("x", raw);
        assertThat(d.name()).isEqualTo("x");
        assertThat(d.description()).isEqualTo("一句话描述");
        assertThat(d.whenToUse()).isEqualTo("某场景");
        assertThat(d.body()).contains("# 正文").contains("内容行");
        assertThat(d.body()).doesNotContain("description:");
    }

    @Test
    void parseWithoutFrontmatterKeepsWholeAsBody() {
        String raw = "# 没有 frontmatter\n直接正文";
        SkillDefinition d = SkillLoader.parse("y", raw);
        assertThat(d.description()).isEmpty();
        assertThat(d.body()).contains("没有 frontmatter").contains("直接正文");
    }

    @Test
    void getSkillLoadsFromClasspath() {
        SkillLoader l = loader(true, "risk-assessment");
        Optional<SkillDefinition> d = l.getSkill("risk-assessment");
        assertThat(d).isPresent();
        assertThat(d.get().description()).isNotBlank();
        assertThat(d.get().body()).contains("风险");
    }

    @Test
    void unknownSkillReturnsEmpty() {
        SkillLoader l = loader(true, "risk-assessment");
        assertThat(l.getSkill("does-not-exist")).isEmpty();
    }

    @Test
    void composeActiveIncludesAllConfiguredSkills() {
        SkillLoader l = loader(true,
                "risk-assessment,psychological-screening,intervention-design,compliance-audit");
        String prompt = l.composeActiveSkillsPrompt();
        assertThat(prompt)
                .contains("Skill: risk-assessment")
                .contains("Skill: psychological-screening")
                .contains("Skill: intervention-design")
                .contains("Skill: compliance-audit");
        assertThat(l.listActive()).hasSize(4);
    }

    @Test
    void disabledComposesEmpty() {
        SkillLoader l = loader(false, "risk-assessment");
        assertThat(l.composeActiveSkillsPrompt()).isEmpty();
    }

    @Test
    void activeNamesTrimsAndSkipsBlanks() {
        SkillLoader l = loader(true, " risk-assessment , , intervention-design ");
        assertThat(l.activeNames()).containsExactly("risk-assessment", "intervention-design");
    }
}
