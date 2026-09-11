package com.edu.agent.core;

/**
 * J-1.3：final_answer 校验器。AgentLoop 拿到 final_answer 后，可选地用它校验语义/schema；
 * 不合格时返回纠错说明，AgentLoop 把说明作为 observation 喂回 LLM 触发"修复轮"，而非直接判失败。
 *
 * <p>校验标准是任务相关的（如 EduCare 的 risk_analysis + intervention_plan 双 JSON），
 * 故由调用方按需传入，AgentLoop 保持通用。
 */
@FunctionalInterface
public interface FinalAnswerValidator {

    Result validate(String finalAnswer);

    /** 校验结果。valid=false 时 correction 给 LLM 看的纠错指引。 */
    record Result(boolean valid, String correction) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result invalid(String correction) {
            return new Result(false, correction);
        }
    }
}
