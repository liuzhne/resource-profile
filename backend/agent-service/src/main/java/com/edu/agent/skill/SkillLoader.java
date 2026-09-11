package com.edu.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H-3.5：Skill 加载器。
 *
 * <p>按需把 classpath {@code skills/<name>.md} 解析为 {@link SkillDefinition} 并注入 AgentLoop 的
 * system prompt（打包进 jar，加载一次后缓存）。
 *
 * <p>"按需"通过 {@code educare.agent.skills.active}（逗号分隔、有序）选择当前任务注入哪些技能，
 * 避免把全部技能无脑塞进 prompt。
 */
@Slf4j
@Component
public class SkillLoader {

    @Value("${educare.agent.skills.enabled:true}")
    private boolean enabled;

    /** 当前激活、按序注入的技能名。 */
    @Value("${educare.agent.skills.active:risk-assessment,psychological-screening,intervention-design,compliance-audit}")
    private String activeCsv;

    /** name → def，classpath 加载后缓存。 */
    private final Map<String, SkillDefinition> cache = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    /** 配置中激活的技能名（有序、去空白）。 */
    public List<String> activeNames() {
        List<String> names = new ArrayList<>();
        for (String n : activeCsv.split(",")) {
            String t = n.strip();
            if (!t.isEmpty()) {
                names.add(t);
            }
        }
        return names;
    }

    /** 读取单个技能（classpath skills/&lt;name&gt;.md）；不存在或解析失败返回 empty。 */
    public Optional<SkillDefinition> getSkill(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = name.strip();
        SkillDefinition cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        ClassPathResource res = new ClassPathResource("skills/" + key + ".md");
        if (!res.exists()) {
            log.warn("H-3：技能 {} 不存在（classpath skills/{}.md 缺失）", key, key);
            return Optional.empty();
        }
        try (InputStream in = res.getInputStream()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            SkillDefinition def = parse(key, raw);
            cache.put(key, def);
            return Optional.of(def);
        } catch (IOException e) {
            log.warn("H-3：读取技能 {} 失败: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 当前激活的技能定义（按配置顺序，跳过缺失的）。 */
    public List<SkillDefinition> listActive() {
        List<SkillDefinition> out = new ArrayList<>();
        for (String name : activeNames()) {
            getSkill(name).ifPresent(out::add);
        }
        return out;
    }

    /**
     * 把激活技能拼成可注入 system prompt 的一段 markdown。
     * 关闭或无激活技能时返回空串（调用方按"无技能"处理，保持原 prompt 字节稳定）。
     */
    public String composeActiveSkillsPrompt() {
        if (!enabled) {
            return "";
        }
        List<SkillDefinition> skills = listActive();
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("# 技能手册（按需运用）\n");
        sb.append("以下是本次任务可运用的专业技能，请在相应阶段遵循其方法与红线：\n\n");
        for (SkillDefinition s : skills) {
            sb.append(s.toPromptBlock()).append('\n');
        }
        return sb.toString();
    }

    /** 解析 frontmatter（--- 包裹的 key: value）+ 正文。frontmatter 缺失时整体作正文。 */
    static SkillDefinition parse(String name, String raw) {
        String content = raw == null ? "" : raw.replace("\r\n", "\n");
        String description = "";
        String whenToUse = "";
        String body = content;

        String trimmed = content.stripLeading();
        if (trimmed.startsWith("---")) {
            int firstNl = trimmed.indexOf('\n');
            int end = trimmed.indexOf("\n---", firstNl);
            if (firstNl > 0 && end > firstNl) {
                String front = trimmed.substring(firstNl + 1, end);
                Map<String, String> fm = parseFrontmatter(front);
                description = fm.getOrDefault("description", "");
                whenToUse = fm.getOrDefault("when_to_use", "");
                int bodyStart = trimmed.indexOf('\n', end + 1);
                body = bodyStart >= 0 ? trimmed.substring(bodyStart + 1) : "";
            }
        }
        return new SkillDefinition(name, description, whenToUse, body.strip());
    }

    private static Map<String, String> parseFrontmatter(String front) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : Arrays.asList(front.split("\n"))) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String k = line.substring(0, colon).strip();
                String v = line.substring(colon + 1).strip();
                if (!k.isEmpty()) {
                    map.put(k, v);
                }
            }
        }
        return map;
    }
}
