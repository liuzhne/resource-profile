package com.edu.knowledge;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
public class KnowledgeTools {
    private final RagService service;
    public KnowledgeTools(RagService service) { this.service=service; }
    @Tool(name="search_cases",description="检索教育介入案例库。返回 chunk_id/doc_id/source/source_url/title/content/score。演示案例为虚构支持流程，不能视为真实个案或已验证结局。")
    public Map<String,Object> cases(String query,@ToolParam(required=false,description="返回条数，默认5，上限20") Integer top_k) {return service.search(query,"case",top_k);}
    @Tool(name="search_policies",description="检索校规/教育政策库，返回原始出处和片段；缺失来源不得编造。")
    public Map<String,Object> policies(String query,@ToolParam(required=false,description="返回条数，默认5，上限20") Integer top_k) {return service.search(query,"policy",top_k);}
    @Tool(name="search_psychology",description="检索心理支持知识库及原始出处；不替代专业诊断。")
    public Map<String,Object> psychology(String query,@ToolParam(required=false,description="返回条数，默认5，上限20") Integer top_k) {return service.search(query,"psychology",top_k);}
}
