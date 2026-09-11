---
name: security-review
description: 对本项目改动做安全审查 —— 鉴权/字段权限、Prompt 注入、敏感数据、未成年人合规、SQL/越权。当用户要求"安全审查/security review"，或改动触及 auth/jwt、mental(心理数据)、agent LLM 调用、MCP 工具、字段权限时使用。
---

# 安全审查工作流（EduCare）

对当前改动按下列维度逐项核查，输出「命中点 + 风险等级 + 整改建议」。

## 1. 鉴权与会话
- JWT 校验是否绕过；`jwt.secret` 是否硬编码/弱密钥（HS256 需 ≥256 bit）。
- 新端点是否落在鉴权链内；`/auth/**` 之外是否要求 token。

## 2. 字段级权限（敏感数据）
- 心理量表（MentalAssessment：score/result/suggestion=EXTREME）、生日/邮箱/电话(HIGH) 等是否经 `@SensitiveField` + `FieldPermissionAdvice` 控制。
- `User.password` 是否 `@JsonIgnore` 永不返回。
- 参 `docs/educare/FIELD_PERMISSION.md` 角色矩阵。

## 3. Prompt 注入（LLM / Agent）
- 用户可控文本进 LLM 前是否经 `PromptSanitizer`（清洗 + `<tag>` 包裹只读声明）。
- AgentLoop 工具返回值是否当只读数据；system prompt 是否声明忽略注入指令。
- **工具守卫**：敏感工具（如 get_mental_indicators）是否经 `ToolGuard` 按角色门控。

## 4. 数据访问 / 越权
- MyBatis-Plus Wrapper 是否拼接用户输入（注解 SQL 注入面）。
- 跨学生/跨角色访问是否校验归属。

## 5. 未成年人 / 合规
- 干预方案/研判是否含歧视性标签；敏感标签（单亲/留守/经济困难）是否仅中立背景使用。
- 合规审核（P4）路径是否保留。

## 6. 配置与密钥
- `.env` / `application-prod.yml` / 云端 API key 是否误提交；是否走 Nacos 加密配置。

只读审查；除非用户要求，不直接改代码。
