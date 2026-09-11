package com.edu.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.edu.agent.core.AgentLoop;
import com.edu.agent.core.AgentLoopRequest;
import com.edu.agent.core.AgentLoopResult;
import com.edu.agent.core.AgentLoopStatus;
import com.edu.agent.core.FinalAnswerValidator;
import com.edu.agent.entity.AgentTask;
import com.edu.agent.enums.RiskLevel;
import com.edu.agent.enums.TaskStatus;
import com.edu.agent.feign.AiInferenceClient;
import com.edu.agent.mapper.AgentTaskMapper;
import com.edu.agent.service.AgentTaskService;
import com.edu.agent.service.RiskAnalyzeService;
import com.edu.agent.service.StudentPortraitAggregator;
import com.edu.agent.skill.SkillLoader;
import com.edu.agent.sse.WarningPublisher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskServiceImpl extends ServiceImpl<AgentTaskMapper, AgentTask> implements AgentTaskService {

    private final AgentTaskMapper agentTaskMapper;
    private final AiInferenceClient aiInferenceClient;          // P3 阶段接入 RAG / 方案 / 合规
    private final StudentPortraitAggregator portraitAggregator;   // P2-5：基于真实端点的画像聚合
    private final RiskAnalyzeService riskAnalyzeService;          // P2-5：真实 LLM 风险识别
    private final StringRedisTemplate redisTemplate;
    private final WarningPublisher warningPublisher;              // F-2：终态事件发布
    private final AgentLoop agentLoop;                            // H-2.3：AgentLoop 切流目标
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;  // H-2.3：MCP 工具列表（启动期 fail-fast 时 ObjectProvider 让单测能选择不注入）
    private final SkillLoader skillLoader;                        // H-3.5：技能加载器（按需注入 classpath skills 到 system prompt）
    private final com.edu.agent.core.SubAgentRegistry subAgentRegistry;  // J-2.1：子代理 agent-as-tool（默认关）

    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    @Value("${educare.idempotency.trigger-window-seconds:30}")
    private long triggerIdempotencyWindowSeconds;

    /** AgentLoop 主路径开关（默认 true）；false 回落旧 4 阶段流水线作 fallback。 */
    @Value("${educare.agent.loop.enabled:true}")
    private boolean agentLoopEnabled;

    /** H-2.3：AgentLoop system prompt，启动时一次性加载，保证字节稳定以命中 G-1 prompt cache。 */
    @Value("classpath:prompts/agent-loop.system.md")
    private Resource agentLoopSystemPromptResource;

    private String agentLoopSystemPrompt;

    private static final String LOCK_PREFIX = "agent:task:lock:";
    private static final String TRIGGER_IDEM_PREFIX = "edu:agent:trigger:";
    private static final long LOCK_EXPIRE_SECONDS = 120;
    private static final int AGENT_LOOP_MAX_ITERATIONS = 8;

    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(
            TaskStatus.PENDING,
            TaskStatus.RISK_ANALYZING,
            TaskStatus.KNOWLEDGE_RETRIEVING,
            TaskStatus.PLAN_GENERATING,
            TaskStatus.COMPLIANCE_CHECKING
    );

    @PostConstruct
    public void initAgentLoopPrompt() throws IOException {
        agentLoopSystemPrompt = agentLoopSystemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        log.info("AgentLoop 主路径 enabled={}，system prompt 加载 {} bytes",
                agentLoopEnabled,
                agentLoopSystemPrompt.getBytes(StandardCharsets.UTF_8).length);
    }

    // ==================== 0. 查询接口 ====================

    @Override
    public AgentTask getTaskDetail(Long taskId) {
        return agentTaskMapper.selectById(taskId);
    }

    @Override
    public IPage<AgentTask> listTasks(long page, long size, String status, String riskLevel) {
        LambdaQueryWrapper<AgentTask> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(AgentTask::getStatus, TaskStatus.valueOf(status));
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            qw.eq(AgentTask::getRiskLevel, RiskLevel.valueOf(riskLevel));
        }
        qw.orderByDesc(AgentTask::getCreatedAt);
        return agentTaskMapper.selectPage(new Page<>(page, size), qw);
    }

    // ==================== 1. 任务创建 ====================

    /**
     * D 阶段：幂等触发。
     * 1) Redis SETNX 占位（窗口 = educare.idempotency.trigger-window-seconds，默认 30s）
     * 2) 命中则查询该 studentId 最近的活跃任务并返回，不创建新任务也不再触发执行
     * 3) 未命中则正常 createTask + asyncExecute，返回新 taskId
     */
    @Override
    public Long triggerTask(Long studentId) {
        String idemKey = TRIGGER_IDEM_PREFIX + studentId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idemKey, "1", triggerIdempotencyWindowSeconds, TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(acquired)) {
            AgentTask existing = findLatestActiveByStudent(studentId);
            if (existing != null) {
                log.info("幂等命中：studentId={} 已有活跃任务 taskId={}，复用", studentId, existing.getId());
                return existing.getId();
            }
            log.info("幂等命中但未找到活跃任务，studentId={} 继续创建新任务", studentId);
        }

        Long taskId = createTask(studentId);
        asyncExecute(taskId);
        return taskId;
    }

    @Override
    @Transactional
    public Long createTask(Long studentId) {
        AgentTask task = new AgentTask();
        task.setStudentId(studentId);
        task.setStatus(TaskStatus.PENDING);
        agentTaskMapper.insert(task);
        log.info("创建 Agent 任务: taskId={}, studentId={}", task.getId(), studentId);
        return task.getId();
    }

    private AgentTask findLatestActiveByStudent(Long studentId) {
        return agentTaskMapper.selectOne(
                new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getStudentId, studentId)
                        .in(AgentTask::getStatus, ACTIVE_STATUSES)
                        .orderByDesc(AgentTask::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    // ==================== 2. 异步执行入口 ====================

    @Override
    @Async("agentExecutor")
    public void asyncExecute(Long taskId) {
        String lockKey = LOCK_PREFIX + taskId;
        String lockValue = UUID.randomUUID().toString();

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        log.info("分布式锁！");
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("任务 {} 正在执行中，跳过重复调度", taskId);
            return;
        }

        try {
            doExecute(taskId);
        } catch (Exception e) {
            log.error("任务 {} 执行异常", taskId, e);
            failTask(taskId);
            publishTerminal(taskId);
        } finally {
            log.info("释放锁！");
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lockKey), lockValue);
        }
    }

    // ==================== 3. 4 阶段状态机 ====================

    private void doExecute(Long taskId) {
        // AgentLoop 主路径（默认）：一次性 think→tool→observe 替换 P1+P3（风险识别 + 计划生成），
        // RAG 检索由 knowledge-rag MCP tool 自然完成；P4 合规审核仍走原 audit 端点。
        // educare.agent.loop.enabled=false 时回落旧 4 阶段流水线作 fallback。
        if (agentLoopEnabled) {
            doExecuteAgentLoop(taskId);
            return;
        }
        doExecuteLegacy(taskId);
    }

    private void doExecuteLegacy(Long taskId) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            log.warn("任务 {} 不存在或已删除", taskId);
            return;
        }

        log.info("任务 {} 开始执行（legacy 4 阶段），当前状态: {}", taskId, task.getStatus());

        // ========== 阶段 1: 风险识别（P2-5 真实接入）==========
        if (task.getStatus() == TaskStatus.PENDING) {
            transition(taskId, TaskStatus.PENDING, TaskStatus.RISK_ANALYZING);

            // 【关键修复】同步内存对象状态，防止 updateById 覆盖
            task.setStatus(TaskStatus.RISK_ANALYZING);

            // ① 聚合画像（调用既有真实端点）→ ② LLM 推理
            String riskJson = executeRiskAnalyze(task);
            task.setRiskAnalysisResult(riskJson);

            RiskLevel level = parseRiskLevel(riskJson);
            task.setRiskLevel(level);
//            agentTaskMapper.updateById(task);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getRiskAnalysisResult, task.getRiskAnalysisResult())
                    .set(AgentTask::getRiskLevel, task.getRiskLevel()));

            // 低风险/无风险 → 短路完成
            if (level == RiskLevel.NONE || level == RiskLevel.LOW) {
                transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.COMPLETED);
                completeTask(taskId);
                publishTerminal(taskId);
                log.info("任务 {} 风险等级 {}，直接完成", taskId, level);
                return;
            }
        }

        // ========== 阶段 2: RAG 检索 ==========
        if (task.getStatus() == TaskStatus.RISK_ANALYZING) {
            transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.KNOWLEDGE_RETRIEVING);
            task.setStatus(TaskStatus.KNOWLEDGE_RETRIEVING); // 同步内存，防止后续 updateById 回写

            String knowledge = executeKnowledgeRetrieve(task);
            task.setRetrievedKnowledge(knowledge);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getRetrievedKnowledge, knowledge));
        }

        // ========== 阶段 3: 方案生成 ==========
        if (task.getStatus() == TaskStatus.KNOWLEDGE_RETRIEVING) {
            transition(taskId, TaskStatus.KNOWLEDGE_RETRIEVING, TaskStatus.PLAN_GENERATING);
            task.setStatus(TaskStatus.PLAN_GENERATING);

            String plan = executePlanGenerate(task);
            task.setInterventionPlan(plan);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getInterventionPlan, plan));
        }

        // ========== 阶段 4: 合规审核 ==========
        if (task.getStatus() == TaskStatus.PLAN_GENERATING) {
            transition(taskId, TaskStatus.PLAN_GENERATING, TaskStatus.COMPLIANCE_CHECKING);
            task.setStatus(TaskStatus.COMPLIANCE_CHECKING);

            String audit = executeComplianceAudit(task);
            task.setComplianceAudit(audit);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getComplianceAudit, audit));

            boolean passed = parseAuditPassed(audit);
            if (!passed) {
                transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.REJECTED);
                publishTerminal(taskId);
                log.warn("任务 {} 合规审核未通过，转人工", taskId);
                return;
            }
        }

        // ========== 完成 ==========
        if (task.getStatus() == TaskStatus.COMPLIANCE_CHECKING) {
            transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.COMPLETED);
            completeTask(taskId);
            publishTerminal(taskId);
            log.info("任务 {} 全部完成", taskId);
        }
    }

    // ==================== 3.5 AgentLoop 流水线（H-2.3） ====================

    /**
     * H-2.3：AgentLoop 切流路径。
     *
     * <p>状态机仍流转 {@code PENDING → RISK_ANALYZING → KNOWLEDGE_RETRIEVING → PLAN_GENERATING
     * → COMPLIANCE_CHECKING → COMPLETED/REJECTED}，保证前端 polling/SSE 看到的中间态语义不变；
     * 但 RISK_ANALYZING 阶段由 {@link AgentLoop#run} 一次性产出 {@code risk_analysis} +
     * {@code intervention_plan} 两份 JSON，知识检索由 knowledge-rag MCP tool 内嵌完成，
     * KNOWLEDGE_RETRIEVING / PLAN_GENERATING 仅做状态流转标记（执行体为空，结果已由 AgentLoop 写入）。
     * 合规审核 P4 仍走旧 {@link #executeComplianceAudit}。
     */
    private void doExecuteAgentLoop(Long taskId) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            log.warn("任务 {} 不存在或已删除", taskId);
            return;
        }

        log.info("任务 {} 开始执行（AgentLoop 路径），当前状态: {}", taskId, task.getStatus());

        // ========== 阶段 1: AgentLoop 一次性完成风险识别 + 方案生成 ==========
        if (task.getStatus() == TaskStatus.PENDING) {
            transition(taskId, TaskStatus.PENDING, TaskStatus.RISK_ANALYZING);
            task.setStatus(TaskStatus.RISK_ANALYZING);

            AgentLoopResult result = runAgentLoopForTask(task);
            if (result.status() != AgentLoopStatus.COMPLETED || result.finalAnswer() == null) {
                log.error("任务 {} AgentLoop 未给出 final_answer，status={} iterations={}",
                        taskId, result.status(), result.iterations());
                failTask(taskId);
                publishTerminal(taskId);
                return;
            }

            AgentLoopParsed parsed = parseAgentLoopFinalAnswer(result.finalAnswer());
            if (parsed == null) {
                log.error("任务 {} AgentLoop final_answer 解析失败，原文: {}",
                        taskId, abbreviate(result.finalAnswer()));
                failTask(taskId);
                publishTerminal(taskId);
                return;
            }

            task.setRiskAnalysisResult(parsed.riskJson);
            task.setRiskLevel(parsed.riskLevel);
            task.setInterventionPlan(parsed.planJson);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getRiskAnalysisResult, parsed.riskJson)
                    .set(AgentTask::getRiskLevel, parsed.riskLevel)
                    .set(AgentTask::getInterventionPlan, parsed.planJson));

            // 低/无风险短路（与 legacy 一致）
            if (parsed.riskLevel == RiskLevel.NONE || parsed.riskLevel == RiskLevel.LOW) {
                transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.COMPLETED);
                completeTask(taskId);
                publishTerminal(taskId);
                log.info("任务 {} AgentLoop 路径风险等级 {}，直接完成", taskId, parsed.riskLevel);
                return;
            }
        }

        // ========== 阶段 2-3: 状态机连流（AgentLoop 内部已完成 RAG 与 plan，仅打卡） ==========
        if (task.getStatus() == TaskStatus.RISK_ANALYZING) {
            transition(taskId, TaskStatus.RISK_ANALYZING, TaskStatus.KNOWLEDGE_RETRIEVING);
            task.setStatus(TaskStatus.KNOWLEDGE_RETRIEVING);
        }
        if (task.getStatus() == TaskStatus.KNOWLEDGE_RETRIEVING) {
            transition(taskId, TaskStatus.KNOWLEDGE_RETRIEVING, TaskStatus.PLAN_GENERATING);
            task.setStatus(TaskStatus.PLAN_GENERATING);
        }

        // ========== 阶段 4: 合规审核（保留原逻辑） ==========
        if (task.getStatus() == TaskStatus.PLAN_GENERATING) {
            transition(taskId, TaskStatus.PLAN_GENERATING, TaskStatus.COMPLIANCE_CHECKING);
            task.setStatus(TaskStatus.COMPLIANCE_CHECKING);

            String audit = executeComplianceAudit(task);
            task.setComplianceAudit(audit);
            agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, taskId)
                    .set(AgentTask::getComplianceAudit, audit));

            boolean passed = parseAuditPassed(audit);
            if (!passed) {
                transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.REJECTED);
                publishTerminal(taskId);
                log.warn("任务 {} AgentLoop 路径合规审核未通过，转人工", taskId);
                return;
            }
        }

        // ========== 完成 ==========
        if (task.getStatus() == TaskStatus.COMPLIANCE_CHECKING) {
            transition(taskId, TaskStatus.COMPLIANCE_CHECKING, TaskStatus.COMPLETED);
            completeTask(taskId);
            publishTerminal(taskId);
            log.info("任务 {} AgentLoop 路径全部完成", taskId);
        }
    }

    private AgentLoopResult runAgentLoopForTask(AgentTask task) {
        // J-2.1：主 loop 工具 = MCP 工具 + （开启时）3 个专家子代理 agent-as-tool
        List<ToolCallback> tools = new java.util.ArrayList<>(resolveMcpTools());
        tools.addAll(subAgentRegistry.subAgentTools(this::resolveMcpTools));
        // H-3.5：按需把激活技能注入 system prompt（关闭/无技能时返回空串，prompt 字节稳定，仍命中 G-1 cache）
        String skillsBlock = skillLoader.composeActiveSkillsPrompt();
        String systemPrompt = skillsBlock.isEmpty()
                ? agentLoopSystemPrompt
                : agentLoopSystemPrompt + "\n\n" + skillsBlock;
        String userPrompt = String.format(
                "请对学生 ID = %d 完成一次风险识别 + 干预方案生成任务。\n"
                        + "请通过可用工具拉取该学生的画像/学业/心理/出勤数据，必要时检索案例、政策、心理学知识，"
                        + "并按 system 中规定的 final_answer JSON schema 给出最终答复。",
                task.getStudentId());
        AgentLoopRequest req = new AgentLoopRequest(
                systemPrompt,
                userPrompt,
                tools,
                AGENT_LOOP_MAX_ITERATIONS,
                "task-" + task.getId());
        log.info("任务 {} 启动 AgentLoop，tools={}, maxIter={}",
                task.getId(), tools.size(), AGENT_LOOP_MAX_ITERATIONS);
        // J-1.3：传入 final_answer 校验器 —— 双 JSON schema 不合格则触发修复轮，而非直接 FAILED。
        return agentLoop.run(req, AgentTaskServiceImpl::validateFinalAnswer);
    }

    /** J-1.3：final_answer 必须能解析为 risk_analysis + intervention_plan 双 JSON，否则给纠错指引。 */
    static FinalAnswerValidator.Result validateFinalAnswer(String finalAnswer) {
        return parseAgentLoopFinalAnswer(finalAnswer) != null
                ? FinalAnswerValidator.Result.ok()
                : FinalAnswerValidator.Result.invalid(
                "final_answer 必须是合法 JSON 字符串，且含 risk_analysis 与 intervention_plan 两个对象，"
                        + "risk_analysis.risk_level ∈ {high,medium,low,none}。请只输出该 JSON，勿加多余文字。");
    }

    private List<ToolCallback> resolveMcpTools() {
        ToolCallbackProvider provider = toolCallbackProviders.getIfAvailable();
        if (provider == null) {
            log.warn("ToolCallbackProvider 不可用，AgentLoop 将以零工具运行");
            return List.of();
        }
        ToolCallback[] arr = provider.getToolCallbacks();
        return (arr == null || arr.length == 0) ? List.of() : Arrays.asList(arr);
    }

    /** AgentLoop final_answer 解析结果。riskJson / planJson 已序列化为字符串落库；riskLevel 解析失败默认 MEDIUM。 */
    static record AgentLoopParsed(String riskJson, String planJson, RiskLevel riskLevel) { }

    /**
     * 解析 AgentLoop 的 final_answer：期望是合法 JSON 字符串，内含 {@code risk_analysis} 与
     * {@code intervention_plan} 两个对象。解析失败返回 null（由调用方判 FAILED）。
     *
     * <p>包级可见 + static：供集成测试直接验证"final_answer → risk/plan/level"链路（无需起 Spring 上下文）。
     */
    static AgentLoopParsed parseAgentLoopFinalAnswer(String finalAnswer) {
        try {
            JSONObject root = JSON.parseObject(finalAnswer);
            if (root == null) {
                return null;
            }
            JSONObject riskObj = root.getJSONObject("risk_analysis");
            JSONObject planObj = root.getJSONObject("intervention_plan");
            if (riskObj == null || planObj == null) {
                log.warn("AgentLoop final_answer 缺少 risk_analysis 或 intervention_plan 字段");
                return null;
            }
            RiskLevel level = parseRiskLevelFromObj(riskObj);
            return new AgentLoopParsed(riskObj.toJSONString(), planObj.toJSONString(), level);
        } catch (Exception e) {
            log.warn("AgentLoop final_answer JSON 解析异常: {}", e.getMessage());
            return null;
        }
    }

    static RiskLevel parseRiskLevelFromObj(JSONObject riskObj) {
        try {
            String level = riskObj.getString("risk_level");
            if (level == null || level.isBlank()) {
                return RiskLevel.MEDIUM;
            }
            return RiskLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            log.warn("AgentLoop risk_level 解析失败: {}", e.getMessage());
            return RiskLevel.MEDIUM;
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 300 ? s.substring(0, 300) + "...[truncated]" : s;
    }

    // ==================== 4. 各阶段执行方法 ====================

    /**
     * 阶段 1：真实风险识别
     * 调用既有服务真实端点（/student/{id} + /mental/analysis + /data/dashboard/statistics）
     */
    private String executeRiskAnalyze(AgentTask task) {
        Long studentId = task.getStudentId();
        log.info("任务 {} 开始风险识别，学生: {}", task.getId(), studentId);

        // ① 聚合画像（基于既有真实端点，mental/data 降级处理）
        String maskedProfile = portraitAggregator.buildMaskedProfile(String.valueOf(studentId));

        // ② 调用本地 llama.cpp
        return riskAnalyzeService.analyzeRisk(maskedProfile);
    }

    /**
     * 阶段 2：RAG 检索
     * 用风险类型 + 根因分析作为多路查询，调用 ai-inference-service 的 Milvus 召回。
     */
    private String executeKnowledgeRetrieve(AgentTask task) {
        try {
            JSONObject risk = JSON.parseObject(task.getRiskAnalysisResult());
            String riskType = risk == null ? null : risk.getString("primary_risk_type");
            String rootCause = risk == null ? null : risk.getString("root_cause_analysis");

            Map<String, Object> req = new HashMap<>();
            req.put("risk_type", riskType != null ? riskType : "综合风险");
            req.put("queries", List.of(
                    riskType != null ? riskType : "学生学业风险",
                    rootCause != null ? rootCause : "高校学生干预方法"
            ));
            req.put("top_k", 5);

            String response = aiInferenceClient.retrieveKnowledge(req);
            log.info("任务 {} RAG 检索完成，长度: {}", task.getId(), response == null ? 0 : response.length());
            return response != null ? response : fallbackKnowledge();
        } catch (Exception e) {
            log.error("任务 {} RAG 检索异常", task.getId(), e);
            return fallbackKnowledge();
        }
    }

    /**
     * 阶段 3：方案生成
     * 入参 = 脱敏画像 + 风险分析 + 召回知识 chunks
     */
    private String executePlanGenerate(AgentTask task) {
        try {
            String maskedProfile = portraitAggregator.buildMaskedProfile(String.valueOf(task.getStudentId()));

            Map<String, Object> req = new HashMap<>();
            req.put("student_profile", JSON.parseObject(maskedProfile));
            req.put("risk_analysis", JSON.parseObject(task.getRiskAnalysisResult()));

            JSONObject knowledge = JSON.parseObject(task.getRetrievedKnowledge());
            JSONArray chunks = knowledge != null ? knowledge.getJSONArray("chunks") : null;
            req.put("knowledge_chunks", chunks != null ? chunks : Collections.emptyList());

            String response = aiInferenceClient.generatePlan(req);
            log.info("任务 {} 方案生成完成", task.getId());
            return response != null ? response : fallbackPlan();
        } catch (Exception e) {
            log.error("任务 {} 方案生成异常", task.getId(), e);
            return fallbackPlan();
        }
    }

    /**
     * 阶段 4：合规审核
     * 失败时强制 audit_passed=false → 任务转 REJECTED 进入人工兜底。
     */
    private String executeComplianceAudit(AgentTask task) {
        try {
            String maskedProfile = portraitAggregator.buildMaskedProfile(String.valueOf(task.getStudentId()));

            Map<String, Object> req = new HashMap<>();
            req.put("student_profile", JSON.parseObject(maskedProfile));
            req.put("intervention_plan", JSON.parseObject(task.getInterventionPlan()));

            String response = aiInferenceClient.complianceAudit(req);
            log.info("任务 {} 合规审核完成", task.getId());
            return response != null ? response : fallbackAudit();
        } catch (Exception e) {
            log.error("任务 {} 合规审核异常", task.getId(), e);
            return fallbackAudit();
        }
    }

    private String fallbackKnowledge() {
        return "{\"chunks\":[],\"fallback\":true}";
    }

    private String fallbackPlan() {
        return "{\"report_title\":\"方案生成失败，转人工制定\",\"summary\":\"AI服务异常\","
                + "\"immediate_actions\":[],\"long_term_plan\":[],\"talk_outline\":\"请由辅导员主导\","
                + "\"resources\":[],\"references\":[]}";
    }

    private String fallbackAudit() {
        return "{\"audit_passed\":false,\"manual_review_required\":true,"
                + "\"audit_items\":[{\"dimension\":\"system\",\"passed\":false,\"issue\":\"审核服务异常\"}],"
                + "\"redacted_suggestions\":[\"请人工审核此方案\"]}";
    }

    // ==================== 5. 状态机工具方法 ====================

    private void transition(Long taskId, TaskStatus from, TaskStatus to) {
        int rows = agentTaskMapper.updateStatus(taskId, from.name(), to.name());
        if (rows == 0) {
            throw new IllegalStateException(
                    String.format("任务 %d 状态流转失败: %s -> %s", taskId, from, to));
        }
        log.info("任务 {} 状态流转: {} -> {}", taskId, from, to);
    }

    private void completeTask(Long taskId) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setCompletedAt(LocalDateTime.now());
        agentTaskMapper.updateById(update);
    }

    private void failTask(Long taskId) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(TaskStatus.FAILED);
        agentTaskMapper.updateById(update);
    }

    /**
     * F-2：终态（COMPLETED / REJECTED / FAILED）后向 Redis 发布事件，
     * 由 SSE 订阅器分发给所有已连接前端。重新查询任务以拿到最新 status / riskLevel。
     */
    private void publishTerminal(Long taskId) {
        try {
            AgentTask t = agentTaskMapper.selectById(taskId);
            if (t == null) return;
            String status = t.getStatus() == null ? "UNKNOWN" : t.getStatus().name();
            String riskLevel = t.getRiskLevel() == null ? null : t.getRiskLevel().name();
            warningPublisher.publishTerminal(taskId, status, riskLevel, t.getStudentId());
        } catch (Exception e) {
            log.warn("F-2：发布终态事件失败 taskId={} err={}", taskId, e.getMessage());
        }
    }

    // ==================== 6. JSON 解析辅助 ====================

    private RiskLevel parseRiskLevel(String json) {
        try {
            JSONObject map = JSON.parseObject(json);
            String level = map.getString("risk_level");
            return RiskLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            log.error("解析风险等级失败: {}", json, e);
            return RiskLevel.MEDIUM;
        }
    }

    private boolean parseAuditPassed(String json) {
        try {
            JSONObject map = JSON.parseObject(json);
            return Boolean.TRUE.equals(map.getBoolean("audit_passed"));
        } catch (Exception e) {
            log.error("解析合规审核结果失败: {}", json, e);
            return false;
        }
    }
}