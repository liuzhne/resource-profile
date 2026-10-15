package com.edu.agent.controller;
import com.alibaba.fastjson2.JSON;
import com.edu.agent.service.AgentGenerationService;
import com.edu.common.security.InternalCallCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
/** Direct compatibility/evaluation API. Gateway blocks _internal; this controller independently checks credentials. */
@RestController
@RequestMapping("/_internal/ai")
public class InternalGenerationController {
    private final AgentGenerationService generation;
    private final InternalCallCredential credential;
    private final String model;
    private final DiagnosticsController diagnostics;
    public InternalGenerationController(AgentGenerationService generation, InternalCallCredential credential, DiagnosticsController diagnostics,
            @Value("${spring.ai.openai.chat.options.model}") String model) {this.generation=generation;this.credential=credential;this.model=model;this.diagnostics=diagnostics;}
    @GetMapping("/metrics")
    public Map<String,Object> metrics(@RequestHeader(value="X-Internal-Token",required=false) String token) {
        if(!credential.matches(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Internal credential required");
        return diagnostics.llmMetrics().getData();
    }
    @PostMapping("/{route:risk|plan|audit|chat}")
    public Map<String,Object> generate(@PathVariable String route,@RequestHeader(value="X-Internal-Token",required=false) String token,
            @RequestBody Map<String,Object> request) {
        if(!credential.matches(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Internal credential required");
        if(route.equals("chat")) {
            if(!(request.get("message") instanceof String message) || message.isBlank() || message.length()>8000)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Invalid message");
            return Map.of("content",generation.chat(String.valueOf(request.getOrDefault("system_prompt","你是一个教育分析师")),message),"model",model);
        }
        if(!(request.get("student_profile") instanceof Map<?,?>)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"student_profile required");
        if(route.equals("plan") && !(request.get("risk_analysis") instanceof Map<?,?>)
                || route.equals("audit") && !(request.get("intervention_plan") instanceof Map<?,?>))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Missing generation context");
        return JSON.parseObject(switch(route) {case "risk"->generation.analyzeRisk(request);case "plan"->generation.generatePlan(request);default->generation.complianceAudit(request);});
    }
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<?> requestFailure(ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).body(Map.of("detail","Invalid internal generation request"));
    }
    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<?> generationFailure(Exception error) {
        return org.springframework.http.ResponseEntity.status(503).body(Map.of("detail","Generation unavailable"));
    }
}
