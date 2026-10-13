package com.edu.mcp.student.security;

/**
 * AUTHZ-MCP-20261013：身份缺失/非法或越权时抛出。MCP server 把工具异常转成 {@code isError=true} 的
 * tool 结果，模型只看到拒绝原因，拿不到任何数据（fail-closed）。
 */
public class McpAccessDeniedException extends RuntimeException {
    public McpAccessDeniedException(String message) {
        super(message);
    }
}
