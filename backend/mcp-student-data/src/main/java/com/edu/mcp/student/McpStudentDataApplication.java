package com.edu.mcp.student;

import com.edu.mcp.student.tool.StudentDataTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.edu.mcp.student.feign")
public class McpStudentDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpStudentDataApplication.class, args);
    }

    /**
     * Spring AI 1.1.6 GA：MCP server starter 不会自动扫描 @Tool 方法，
     * 必须手动通过 MethodToolCallbackProvider.toolObjects(...) 显式登记。
     * 这里把 StudentDataTools 暴露的 4 个 @Tool 方法注册为 MCP tool。
     */
    @Bean
    public ToolCallbackProvider studentDataToolCallbacks(StudentDataTools studentDataTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(studentDataTools)
                .build();
    }
}
