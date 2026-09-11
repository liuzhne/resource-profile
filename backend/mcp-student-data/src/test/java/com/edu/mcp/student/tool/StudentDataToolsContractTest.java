package com.edu.mcp.student.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定跨语言 MCP 公共契约，避免 Java 方法名再次泄漏成 camelCase。
 */
class StudentDataToolsContractTest {

    @Test
    void exposesCanonicalSnakeCaseToolNames() {
        StudentDataTools tools = new StudentDataTools(null, null);

        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build()
                .getToolCallbacks();

        Set<String> names = Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
                "get_student_profile",
                "get_academic_history",
                "get_mental_indicators",
                "get_attendance");
    }
}
