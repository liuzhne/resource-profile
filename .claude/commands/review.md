---
description: 审查当前分支改动（正确性 + 复用/简化 + 本项目约定）
argument-hint: "[可选: PR 号或分支名]"
---

请审查当前改动 $ARGUMENTS（缺省则审 `git diff` 工作区 + 暂存区）。

步骤：
1. 跑 `git diff`（或对指定 PR/分支）拿到改动面。
2. 对照 `.claude/rules/`（code-style / testing / api-conventions）逐条核对。
3. 重点看：
   - **正确性**：空指针、并发、事务边界、`Result<T>` 错误码是否规范。
   - **复用/简化**：是否重复造轮子、能否复用既有工具类（如 `PromptSanitizer`、`JwtUtil`、`HistoryCompactor`）。
   - **测试**：新逻辑是否有单测；是否 `mvn -pl <module> test` 可跑通。
   - **安全**：敏感数据/注入/字段权限（参 `FIELD_PERMISSION.md`）。
4. 用「文件:行号」给出可点击定位，按 严重/建议/可选 分级，给最小修复方案。

只读审查，不直接改代码（除非我明确要求）。
