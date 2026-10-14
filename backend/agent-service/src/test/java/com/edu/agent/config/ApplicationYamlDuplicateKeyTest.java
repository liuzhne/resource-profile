package com.edu.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 防止 application.yml 出现重复键（例如两个 PR 各自新增 educare.ai 块，git 无冲突合并后
 * Spring Boot 启动报 DuplicateKeyException）。这里用严格模式解析真实配置文件，让 CI 能发现。
 */
class ApplicationYamlDuplicateKeyTest {

    private static final String FILE = "application.yml";

    @Test
    void strictSnakeYamlRejectsNothing() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            for (Object doc : yaml.loadAll(in)) {
                assertNotNull(doc);
            }
        }
    }

    @Test
    void springYamlLoaderLoadsFile() {
        assertDoesNotThrow(() -> new YamlPropertySourceLoader().load(FILE, new ClassPathResource(FILE)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void educareAiIsSingleBlock() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            Map<String, Object> root = new Yaml(new SafeConstructor(options)).load(in);
            Map<String, Object> educare = (Map<String, Object>) root.get("educare");
            assertNotNull(educare, "缺少 educare 块");
            Object ai = educare.get("ai");
            assertInstanceOf(Map.class, ai, "educare.ai 应为单个映射块");
            Map<String, Object> aiMap = (Map<String, Object>) ai;
            for (String k : new String[]{"rag", "risk", "plan", "structured", "eval-endpoint", "audit"}) {
                assertTrue(aiMap.containsKey(k), "educare.ai 缺少子项 " + k);
            }
        }
    }
}
