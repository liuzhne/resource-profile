---
description: 按 issue 描述定位并修复一个 bug（含定位、修复、测试、提交）
argument-hint: "<issue 号或一句话描述>"
---

请修复以下问题：$ARGUMENTS

流程：
1. **定位**：在 `backend/` 或 `ai-inference-service/` 或 `frontend/` 里搜出相关代码，先读懂上下文（必要时读 `docs/educare/EXECUTION_PLAN.md` 了解背景）。
2. **复现/确认根因**：说清问题出在哪、为什么。
3. **最小修复**：改动面尽量小，遵循 `.claude/rules/code-style.md` 与既有分层模式。
4. **测试**：补/改单测；跑 `mvn -pl <module> test`（Java）或 `python3 -m unittest`（Python）确认绿。
5. **提交**：在当前 feature 分支提交（非 main），提交信息说明根因与修复，结尾带
   `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`。

不要 push，不改与本问题无关的代码。
