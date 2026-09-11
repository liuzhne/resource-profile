package com.edu.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.edu.agent.config.LangfuseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H-2.1：think → tool → observe 循环骨架。
 *
 * <p>协议为 ReAct JSON：每轮 LLM 必须输出形如：
 * <pre>{"thought":"...","action":{"tool":"name","args":{...}}}</pre>
 * 或：
 * <pre>{"thought":"...","final_answer":"..."}</pre>
 *
 * <p>循环自行解析、自行调度 ToolCallback、自行决定 break。**不**使用 Spring AI 1.1.6
 * `ChatClient.prompt().tools(...)` 的隐式内部循环，目的是让 thought / action / observation
 * 三类事件个体可见、可单测、可（H-2.2 起）接 Langfuse trace。
 *
 * <p>本类不接 MCP client；`AgentLoopRequest.tools()` 是 ToolCallback 列表的入参，调用方负责注入。
 * H-2.2 接 MCP 时由 `ToolCallbackProvider` 提供。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private static final int TOOL_RESULT_MAX_LEN = 4096;
    private static final int CONSECUTIVE_PARSE_ERROR_LIMIT = 2;
    /** J-1.3：final_answer 校验失败时最多触发几次"修复轮"，防模型反复给坏答案打转。 */
    private static final int MAX_FINAL_ANSWER_REPAIRS = 2;

    private final ChatClient chatClient;
    private final LangfuseClient langfuseClient;

    /** J-1.2：工具守卫。null（如单测 new AgentLoop(...)）→ 全放行，行为不变。 */
    @Autowired(required = false)
    private ToolGuard toolGuard;

    /** J-2.4：并行工具执行器。null（单测）→ 退化为顺序执行（结果一致，仅不并发）。 */
    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("agentExecutor")
    private java.util.concurrent.Executor parallelToolExecutor;

    /** J-3.1：生命周期钩子（流式/检查点/审计）。无注册 bean → null。 */
    @Autowired(required = false)
    private List<AgentLoopHook> hooks;

    /** J-3.4：工具协议。react（默认，可见 thought/observation 细粒度）或 native（Spring AI 原生 tool-calling）。 */
    @org.springframework.beans.factory.annotation.Value("${educare.agent.loop.protocol:react}")
    private String protocol;

    private void recordTrace(AgentLoopRequest req, List<AgentTrace> traces, AgentTrace t) {
        traces.add(t);
        fireHooks(h -> h.onIteration(req.taskTag(), t));
    }

    private void fireHooks(java.util.function.Consumer<AgentLoopHook> action) {
        if (hooks == null) {
            return;
        }
        for (AgentLoopHook h : hooks) {
            try {
                action.accept(h);
            } catch (Exception e) {
                log.debug("AgentLoopHook 异常（已忽略）: {}", e.getMessage());
            }
        }
    }

    public AgentLoopResult run(AgentLoopRequest rawReq) {
        return run(rawReq, null);
    }

    /**
     * J-1.3：带 final_answer 校验的 run。validator 非空时，final_answer 不合格会喂纠错 observation
     * 触发最多 {@link #MAX_FINAL_ANSWER_REPAIRS} 次修复轮；修复预算耗尽则接受当前答案返回（由调用方裁决）。
     */
    public AgentLoopResult run(AgentLoopRequest rawReq, FinalAnswerValidator validator) {
        AgentLoopRequest req = rawReq.normalized();
        Instant runStart = Instant.now();
        // J-3.4：双轨 —— native 走 Spring AI 原生 tool-calling 的隐式内部循环
        if ("native".equalsIgnoreCase(protocol)) {
            return runNative(req, validator, runStart);
        }
        String systemPrompt = composeSystemPrompt(req);
        List<AgentTrace> traces = new ArrayList<>(req.maxIterations());
        int consecutiveParseError = 0;
        int repairsLeft = MAX_FINAL_ANSWER_REPAIRS;
        String lastThought = null;
        String agentPlan = null;  // J-2.3：首轮规划
        fireHooks(h -> h.onStart(req.taskTag(), req));  // J-3.1

        for (int i = 1; i <= req.maxIterations(); i++) {
            long t0 = System.currentTimeMillis();
            String userPrompt = composeUserPromptWithHistory(req, traces);

            String raw;
            try {
                raw = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
            } catch (Exception e) {
                log.error("[AgentLoop][{}] LLM 调用异常 iter={}", req.taskTag(), i, e);
                recordTrace(req, traces, new AgentTrace(i, "", null, null, null, null,
                        System.currentTimeMillis() - t0, "llm-call-failed: " + e.getMessage()));
                return finishRun(req, new AgentLoopResult(AgentLoopStatus.TOOL_ERROR, null, traces, i), runStart);
            }

            ParsedTurn parsed = parseLlmJson(raw);

            // J-2.3：捕获首个 plan，记日志（也随 rawLlmOutput 进 Langfuse trace），便于长任务可观测/可steer
            if (parsed.plan != null && agentPlan == null) {
                agentPlan = parsed.plan;
                log.info("[AgentLoop][{}] plan: {}", req.taskTag(), abbreviate(agentPlan));
            }

            if (parsed.finalAnswer != null) {
                // J-1.3：final_answer 校验 + 自纠错。不合格且还有修复预算 → 喂纠错 observation，继续循环。
                if (validator != null && repairsLeft > 0) {
                    FinalAnswerValidator.Result vr = validator.validate(parsed.finalAnswer);
                    if (!vr.valid()) {
                        repairsLeft--;
                        recordTrace(req, traces, new AgentTrace(i, raw, parsed.thought, null, null,
                                "FINAL_ANSWER_INVALID: " + vr.correction(),
                                System.currentTimeMillis() - t0, "final-answer-invalid"));
                        log.warn("[AgentLoop][{}] iter={} final_answer 不合格，触发修复轮（剩余 {}）：{}",
                                req.taskTag(), i, repairsLeft, vr.correction());
                        lastThought = parsed.thought;
                        continue;
                    }
                }
                recordTrace(req, traces, new AgentTrace(i, raw, parsed.thought, null, null, null,
                        System.currentTimeMillis() - t0, null));
                log.info("[AgentLoop][{}] iter={} COMPLETED finalAnswer={}",
                        req.taskTag(), i, abbreviate(parsed.finalAnswer));
                return finishRun(req, new AgentLoopResult(AgentLoopStatus.COMPLETED, parsed.finalAnswer, traces, i), runStart);
            }

            if (parsed.parseError != null) {
                consecutiveParseError++;
                recordTrace(req, traces, new AgentTrace(i, raw, null, null, null, null,
                        System.currentTimeMillis() - t0, parsed.parseError));
                if (consecutiveParseError >= CONSECUTIVE_PARSE_ERROR_LIMIT) {
                    log.warn("[AgentLoop][{}] iter={} 连续 {} 轮 parse error，退出",
                            req.taskTag(), i, consecutiveParseError);
                    return finishRun(req, new AgentLoopResult(AgentLoopStatus.PARSE_ERROR, null, traces, i), runStart);
                }
                continue;
            }
            consecutiveParseError = 0;

            // J-2.4：并行多工具批 —— 守卫 + 调用各工具，合并 observation。
            if (parsed.parallelCalls != null && !parsed.parallelCalls.isEmpty()) {
                String merged = executeParallel(req, parsed.parallelCalls);
                recordTrace(req, traces, new AgentTrace(i, raw, parsed.thought,
                        "[parallel:" + parsed.parallelCalls.size() + "]", null,
                        truncate(merged), System.currentTimeMillis() - t0, null));
                log.info("[AgentLoop][{}] iter={} 并行 {} 工具完成",
                        req.taskTag(), i, parsed.parallelCalls.size());
                lastThought = parsed.thought;
                continue;
            }

            if (parsed.toolName != null) {
                // J-1.2：工具守卫 —— 被拒不崩，转结构化 TOOL_DENIED observation 喂回 LLM，循环继续。
                if (toolGuard != null) {
                    ToolGuard.GuardDecision decision = toolGuard.check(parsed.toolName, parsed.toolArgs);
                    if (!decision.allowed()) {
                        String denied = "TOOL_DENIED: " + decision.reason();
                        recordTrace(req, traces, new AgentTrace(i, raw, parsed.thought, parsed.toolName, parsed.toolArgs,
                                denied, System.currentTimeMillis() - t0, "tool-denied"));
                        log.warn("[AgentLoop][{}] iter={} tool={} 被守卫拒绝: {}",
                                req.taskTag(), i, parsed.toolName, decision.reason());
                        lastThought = parsed.thought;
                        continue;
                    }
                }
                String observation;
                try {
                    observation = invokeTool(req.tools(), parsed.toolName, parsed.toolArgs);
                } catch (Exception first) {
                    log.warn("[AgentLoop][{}] iter={} tool={} 首次失败，重试一次",
                            req.taskTag(), i, parsed.toolName, first);
                    try {
                        observation = invokeTool(req.tools(), parsed.toolName, parsed.toolArgs);
                    } catch (Exception second) {
                        recordTrace(req, traces, new AgentTrace(i, raw, parsed.thought, parsed.toolName, parsed.toolArgs,
                                "ERROR: " + second.getMessage(),
                                System.currentTimeMillis() - t0,
                                "tool-failed-after-retry"));
                        log.error("[AgentLoop][{}] iter={} tool={} 重试仍失败，终止",
                                req.taskTag(), i, parsed.toolName, second);
                        return finishRun(req, new AgentLoopResult(AgentLoopStatus.TOOL_ERROR, null, traces, i), runStart);
                    }
                }
                String truncated = truncate(observation);
                recordTrace(req, traces, new AgentTrace(i, raw, parsed.thought, parsed.toolName, parsed.toolArgs,
                        truncated, System.currentTimeMillis() - t0, null));
                log.info("[AgentLoop][{}] iter={} tool={} → {} bytes",
                        req.taskTag(), i, parsed.toolName,
                        observation == null ? 0 : observation.length());
                lastThought = parsed.thought;
                continue;
            }

            // 既无 action 也无 final_answer：合法但循环不推进，记 thought 后下一轮
            lastThought = parsed.thought;
            recordTrace(req, traces, new AgentTrace(i, raw, parsed.thought, null, null, null,
                    System.currentTimeMillis() - t0, null));
            log.info("[AgentLoop][{}] iter={} thought-only，继续", req.taskTag(), i);
        }

        log.warn("[AgentLoop][{}] 达到 maxIterations={}，强制退出",
                req.taskTag(), req.maxIterations());
        return finishRun(req,
                new AgentLoopResult(AgentLoopStatus.MAX_ITERATIONS, lastThought, traces, req.maxIterations()),
                runStart);
    }

    /**
     * H-2.2：所有终止路径汇集到这里上报 Langfuse 顶层 trace。
     *
     * <p>每轮 LLM call 仍由 {@code LlmMetricsInterceptor} 各自上报独立 {@code llm.chat} generation trace，
     * 两类 trace 并列、不嵌套（嵌套留 H-2.4 eval 调优时扩展）。
     * LangfuseClient 未配 key 时整体 no-op，失败 fail-soft。
     */
    private AgentLoopResult finishRun(AgentLoopRequest req, AgentLoopResult result, Instant runStart) {
        Instant runEnd = Instant.now();
        fireHooks(h -> h.onFinish(req.taskTag(), result));  // J-3.1
        try {
            String tracesSummary = JSON.toJSONString(result.traces());
            if (tracesSummary.length() > 2048) {
                tracesSummary = tracesSummary.substring(0, 2048) + "...[truncated]";
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("task_tag", req.taskTag());
            metadata.put("iterations", result.iterations());
            metadata.put("status", result.status().name());
            metadata.put("traces_summary", tracesSummary);
            langfuseClient.traceGeneration(
                    "agent.loop",
                    "unknown",
                    req.userPrompt(),
                    result.finalAnswer(),
                    0L,
                    0L,
                    metadata,
                    runStart,
                    runEnd);
        } catch (Exception e) {
            log.debug("[AgentLoop][{}] Langfuse trace 上报触发异常（已忽略）: {}",
                    req.taskTag(), e.getMessage());
        }
        return result;
    }

    /**
     * J-3.4：native 协议 —— 交给 Spring AI 原生 tool-calling，模型内部完成 tool 调用循环，返回最终文本。
     * 失去 thought/observation 细粒度（单条 trace），但对大模型/云端更省协议开销。system prompt 直接用
     * req.systemPrompt()（不追加 ReAct 协议）；validator 仅做一次校验记日志（native 不做修复轮）。
     */
    private AgentLoopResult runNative(AgentLoopRequest req, FinalAnswerValidator validator, Instant runStart) {
        fireHooks(h -> h.onStart(req.taskTag(), req));
        long t0 = System.currentTimeMillis();
        List<AgentTrace> traces = new ArrayList<>(1);
        String content;
        try {
            content = chatClient.prompt()
                    .system(req.systemPrompt() == null ? "" : req.systemPrompt())
                    .user(req.userPrompt())
                    .toolCallbacks(guardNativeTools(req.tools()))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[AgentLoop][{}] native 调用异常", req.taskTag(), e);
            recordTrace(req, traces, new AgentTrace(1, "", null, null, null, null,
                    System.currentTimeMillis() - t0, "native-call-failed: " + e.getMessage()));
            return finishRun(req, new AgentLoopResult(AgentLoopStatus.TOOL_ERROR, null, traces, 1), runStart);
        }
        if (validator != null) {
            FinalAnswerValidator.Result vr = validator.validate(content);
            if (!vr.valid()) {
                recordTrace(req, traces, new AgentTrace(1, content, null, null, null, null,
                        System.currentTimeMillis() - t0, "final-answer-invalid: " + vr.correction()));
                log.warn("[AgentLoop][{}] native final_answer 不合格，终止为 VALIDATION_ERROR：{}",
                        req.taskTag(), vr.correction());
                return finishRun(req,
                        new AgentLoopResult(AgentLoopStatus.VALIDATION_ERROR, content, traces, 1), runStart);
            }
        }
        recordTrace(req, traces, new AgentTrace(1, content, null, null, null, null,
                System.currentTimeMillis() - t0, null));
        log.info("[AgentLoop][{}] native COMPLETED finalAnswer={}", req.taskTag(), abbreviate(content));
        return finishRun(req, new AgentLoopResult(AgentLoopStatus.COMPLETED, content, traces, 1), runStart);
    }

    /**
     * native 协议由 Spring AI 执行隐式工具循环，因此必须包装每个 callback，
     * 让实际调用（而非仅工具注册）仍经过与 ReAct 相同的 ToolGuard。
     */
    List<ToolCallback> guardNativeTools(List<ToolCallback> tools) {
        if (toolGuard == null || tools == null || tools.isEmpty()) {
            return tools == null ? List.of() : tools;
        }
        return tools.stream().<ToolCallback>map(delegate -> new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return guardedCall(delegate, toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return guardedCall(delegate, toolInput, toolContext);
            }
        }).toList();
    }

    private String guardedCall(ToolCallback delegate, String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        ToolGuard.GuardDecision decision = toolGuard.check(toolName, toolInput);
        if (!decision.allowed()) {
            log.warn("[AgentLoop][native] tool={} 被守卫拒绝: {}", toolName, decision.reason());
            return "TOOL_DENIED: " + decision.reason();
        }
        return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
    }

    // -------- 拼接 prompt --------

    private String composeSystemPrompt(AgentLoopRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            sb.append(req.systemPrompt()).append("\n\n");
        }
        sb.append("# 可用工具\n");
        if (req.tools().isEmpty()) {
            sb.append("（本次无可用工具，请直接给出最终答案）\n");
        } else {
            for (ToolCallback cb : req.tools()) {
                ToolDefinition def = cb.getToolDefinition();
                sb.append("- name: ").append(def.name()).append('\n');
                sb.append("  description: ").append(def.description()).append('\n');
                String schema = def.inputSchema();
                if (schema != null && !schema.isBlank()) {
                    sb.append("  inputSchema: ").append(schema).append('\n');
                }
            }
        }
        sb.append("\n# 输出协议（ReAct JSON，严格遵守）\n");
        sb.append("每一轮必须只输出一个 JSON 对象，**不要**写任何解释文字或 Markdown 代码围栏。\n");
        sb.append("有两种合法形态，二选一：\n");
        sb.append("1. 调用工具：{\"thought\":\"<本轮思考>\",\"action\":{\"tool\":\"<工具名>\",\"args\":{<参数对象>}}}\n");
        sb.append("2. 给出最终答案：{\"thought\":\"<本轮思考>\",\"final_answer\":\"<最终答复字符串>\"}\n");
        sb.append("收到 Observation 后必须基于其内容继续推理或直接给出 final_answer，不要重复同一个 action。\n");
        sb.append("（可选）首轮可在 JSON 里附 \"plan\":[\"步骤1\",\"步骤2\",...] 列出你的计划，便于追踪，但不影响上述两种形态。\n");
        sb.append("（可选并行）当需要同时取多份**互不依赖**的数据时，\"action\" 可写成数组：");
        sb.append("\"action\":[{\"tool\":\"t1\",\"args\":{}},{\"tool\":\"t2\",\"args\":{}}]，将并行执行并合并 Observation。\n");
        return sb.toString();
    }

    private String composeUserPromptWithHistory(AgentLoopRequest req, List<AgentTrace> traces) {
        // J-1.1：委托 HistoryCompactor 做窗口压缩，避免每轮重放全量历史导致 prompt 无界增长。
        return HistoryCompactor.compose(req.userPrompt(), traces);
    }

    // -------- 解析 + 工具调度 --------

    /** ReAct JSON 解析；trim Markdown fence + 提第一个 {...} 容忍 LLM 输出闲话。 */
    ParsedTurn parseLlmJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParsedTurn.error("empty-llm-output");
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int fenceEnd = s.lastIndexOf("```");
            if (fenceEnd >= 0) {
                s = s.substring(0, fenceEnd);
            }
            s = s.trim();
        }
        int lb = s.indexOf('{');
        int rb = s.lastIndexOf('}');
        if (lb < 0 || rb < lb) {
            return ParsedTurn.error("no-json-object");
        }
        String jsonStr = s.substring(lb, rb + 1);
        JSONObject obj;
        try {
            obj = JSON.parseObject(jsonStr);
        } catch (Exception e) {
            return ParsedTurn.error("json-parse-failed: " + e.getClass().getSimpleName());
        }
        if (obj == null) {
            return ParsedTurn.error("json-null");
        }

        String thought = obj.getString("thought");
        String finalAnswer = obj.getString("final_answer");
        String plan = extractPlan(obj);  // J-2.3：可选规划字段

        if (finalAnswer != null) {
            return new ParsedTurn(thought, finalAnswer, null, null, null, plan, null);
        }

        Object actionRaw = obj.get("action");
        // J-2.4：action 为数组 → 并行多工具批
        if (actionRaw instanceof com.alibaba.fastjson2.JSONArray arr) {
            java.util.List<ToolCall> calls = new java.util.ArrayList<>();
            for (Object o : arr) {
                if (o instanceof JSONObject a) {
                    String tn = a.getString("tool");
                    if (tn != null && !tn.isBlank()) {
                        Object ag = a.get("args");
                        calls.add(new ToolCall(tn, ag == null ? "{}" : JSON.toJSONString(ag)));
                    }
                }
            }
            if (calls.isEmpty()) {
                return ParsedTurn.error("action-array-empty");
            }
            return new ParsedTurn(thought, null, null, null, null, plan, calls);
        }
        // 单工具
        if (actionRaw instanceof JSONObject action) {
            String toolName = action.getString("tool");
            if (toolName == null || toolName.isBlank()) {
                return ParsedTurn.error("action-missing-tool");
            }
            Object argsObj = action.get("args");
            String argsJson = argsObj == null ? "{}" : JSON.toJSONString(argsObj);
            return new ParsedTurn(thought, null, toolName, argsJson, null, plan, null);
        }
        return new ParsedTurn(thought, null, null, null, null, plan, null);
    }

    /** J-2.3：抽 plan 字段（可为数组/对象/字符串），序列化为字符串；无则 null。 */
    static String extractPlan(JSONObject obj) {
        Object p = obj.get("plan");
        if (p == null) {
            return null;
        }
        return (p instanceof String) ? (String) p : JSON.toJSONString(p);
    }

    String invokeTool(List<ToolCallback> tools, String name, String argsJson) {
        for (ToolCallback cb : tools) {
            if (name.equals(cb.getToolDefinition().name())) {
                return cb.call(argsJson == null ? "{}" : argsJson);
            }
        }
        throw new IllegalArgumentException("未知工具: " + name);
    }

    /** J-2.4：并行（有 executor）或顺序（无）执行多个工具，合并 observation；单个失败/被拒不影响其它。 */
    String executeParallel(AgentLoopRequest req, List<ToolCall> calls) {
        List<java.util.concurrent.CompletableFuture<String>> futures = new ArrayList<>();
        for (ToolCall c : calls) {
            java.util.function.Supplier<String> task =
                    () -> "Observation[" + c.name() + "]: " + invokeOneGuarded(req, c);
            if (parallelToolExecutor != null) {
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(task, parallelToolExecutor));
            } else {
                futures.add(java.util.concurrent.CompletableFuture.completedFuture(task.get()));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.concurrent.CompletableFuture<String> f : futures) {
            try {
                sb.append(f.join()).append('\n');
            } catch (Exception e) {
                sb.append("Observation[error]: ").append(e.getMessage()).append('\n');
            }
        }
        return sb.toString().strip();
    }

    /** 单工具：守卫 + 调用 + 一次重试，异常/拒绝转字符串结果（不抛，供并行批合并）。 */
    private String invokeOneGuarded(AgentLoopRequest req, ToolCall c) {
        if (toolGuard != null) {
            ToolGuard.GuardDecision d = toolGuard.check(c.name(), c.args());
            if (!d.allowed()) {
                return "TOOL_DENIED: " + d.reason();
            }
        }
        try {
            return invokeTool(req.tools(), c.name(), c.args());
        } catch (Exception first) {
            try {
                return invokeTool(req.tools(), c.name(), c.args());
            } catch (Exception second) {
                return "ERROR: " + second.getMessage();
            }
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= TOOL_RESULT_MAX_LEN) {
            return s;
        }
        return s.substring(0, TOOL_RESULT_MAX_LEN) + "...[truncated " + (s.length() - TOOL_RESULT_MAX_LEN) + "]";
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /** 一次工具调用（名 + JSON 参数）。J-2.4 并行批用。 */
    record ToolCall(String name, String args) {
    }

    /**
     * 解析结果：互斥字段 {finalAnswer / toolName(单工具) / parallelCalls(多工具) / 仅 thought / parseError}
     * + 可选 plan（J-2.3）。
     */
    record ParsedTurn(String thought, String finalAnswer, String toolName, String toolArgs, String parseError,
                      String plan, java.util.List<ToolCall> parallelCalls) {
        static ParsedTurn error(String err) {
            return new ParsedTurn(null, null, null, null, err, null, null);
        }
    }
}
