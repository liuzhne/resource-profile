# GROQ-400-CAUSE-20261011 原因验证与修复结论

> 后续生产业务复测（2026-10-11，AI-PROD-RETEST-20261011）：v6下新task9/10/11仍首轮JSON_GENERATION400，经纠错成功读取档案后第三轮再400，完整AI未恢复。以下A–P仍是虚构ID0、未执行工具的工程历史结果，不能替代业务结论；见[最新复测](./retest/REPORT.md)。

日期：2026-10-11。以下记录本轮 **16 次实际 Groq 工程诊断**，截至 UTC 12:09:54 的 P 请求。全部使用虚构 studentId=0，不含师生档案、不执行返回的工具 action；没有创建完整学生 Agent、PDF 导出或反馈。本轮后续工程请求已有新的明确用户授权。

## 当前结论

**应用侧首轮400修复已通过真实工程请求复验并部署：严格 ReAct 模式不再把完整工具元数据重复写进 system，保留正式七工具响应 Schema。** 原 A 请求失败；L 保留其 user、七工具正式 Schema、模型和全部选项，仅移除 system 的“可用工具”名称/描述/raw inputSchema 整块后，实际 HTTP200 且严格四字段、profile action、ID=0、final_answer=null 全部校验通过。

该变化从原 A 至 L 已有严格对照，真实最终编译请求 M 与 L 逐值相同，M/N 两次200；原 A 字节重复请求 O 仍400。v6 已上线，部署后工程请求 P 再次200。**没有查明 Groq 内部生成或约束解码失败的精确机理**，也不证明工具 Schema 非法或供应商 Bug。这里验证的是初始 ReAct 输出协议；没有执行业务工具，不能写成完整 AI、后续 RAG/P4、PDF 或反馈验收通过。

## 全部实测

表内时间均为 2026-10-11 UTC；token 为成功请求实际返回的 prompt + completion，失败请求不补猜消耗量。

| 请求 | 对照内容 | 时间UTC | HTTP / 判据 | 耗时 | token |
|---|---|---|---|---|---|
| A | 原完整system/user、七工具正式Schema | 11:07:56 | 400 / JSON_GENERATION | 973ms | 未返回 |
| B | A仅把action工具候选缩为profile | 11:09:19 | 400 / JSON_GENERATION | 905ms | 未返回 |
| C | B只把system/user替换为短工程消息 | 11:17:04 | 200 / 严格协议通过 | 597ms | 805 + 94 |
| D | C仅将Schema简化为单profile action、plan/final=null | 11:17:26 | 200 / 严格协议通过 | 667ms | 305 + 74 |
| E | B完整system＋C短user | 11:20:16 | 400 / JSON_GENERATION | 897ms | 未返回 |
| F | C短system＋B原user | 11:20:46 | 200 / 严格协议通过 | 631ms | 872 + 61 |
| G | E仅删除skills块 | 11:22:45 | 400 / JSON_GENERATION | 758ms | 未返回 |
| H | E仅删除整个工具文本元数据块 | 11:23:06 | 200 / 严格协议通过 | 689ms | 4358 + 64 |
| I | E仅删除七条raw inputSchema行 | 11:25:47 | 400 / JSON_GENERATION | 956ms | 未返回 |
| J | I仅恢复原A七工具正式Schema | 11:27:23 | 200 / 严格协议通过 | 935ms | 6021 + 63 |
| K | 真实编译类捕获；原A仅删除七条raw inputSchema行 | 11:31:58 | 400 / JSON_GENERATION | 983ms | 未返回 |
| L | 原A仅删除整个工具文本元数据块 | 11:38:02 | 200 / 严格协议通过 | 755ms | 5556 + 69 |
| M | 最终真实编译类/MCP/Spring AI捕获；逐值与L相同 | 11:49:32 | 200 / 严格协议通过 | 761ms | 5556 + 69 |
| N | M原请求字节独立重复 | 11:50:31 | 200 / 严格协议通过 | 607ms | 5556 + 69 |
| O | 原A完整字节独立复测 | 11:51:28 | 400 / JSON_GENERATION | 759ms | 未返回 |
| P | v6部署后使用已核对编译协议M复验 | 12:09:54 | 200 / 严格协议通过 | 919ms | 5556 + 69 |

16 次均为 `openai/gpt-oss-120b`、`json_schema`、`strict=true`、非流式，无原生 `tools` 参数；max_tokens=2400、reasoning_effort=low 等请求选项保持一致。9 个 HTTP200 结果均经过实际请求 Schema 的 Draft202012 校验，并验证完整四字段、档案 action、整数 ID0 和空 final_answer；未执行 action。7 个 HTTP400 均为 `json_validate_failed / JSON_GENERATION`，Schema 编译拒绝前缀均未出现；没有 HTTP429。完整 SHA、固定结构标记、时间、用量与独立离线差异证明见安全证据（本地测试产物，不入库：`./GROQ_400_CAUSE_EVIDENCE.json`）。

A 的 failed_generation 严格解析失败，但未记录长度或根类型。B/E/G/I/K/O 的该字段实测均为空字符串，字符数和 UTF-8 字节数为0；没有可检查的生成正文，也没有函数标签或代码围栏的固定前缀。不能把这些空样本描述成“四字段缺失”，更不能反推旧 task6/7/8 的准确形态。没有保存提供商原始错误、生成文本、提示原文、凭据或学生内容到公开证据。

## 对照能说明什么

- **A→B：七工具缩为一个仍失败。** C 与原 B 正式 Schema 相同，仅更换短消息就通过；J/L 也使用原七工具 Schema 通过。故不能归因于“七工具数量过多”或称此正式 Schema 始终不可编译。
- **C/E/F：故障随完整 system 组合重现。** C 短 system/短 user 通过；E 恢复原 system、保留短 user 失败；F 短 system/原 user 通过。用户消息本身不足以解释故障，仍有 system 与消息组合影响。
- **E→G/H：删除 skills 不够，删除工具元数据块通过。** G 只删 skills 仍400；H 只删完整工具元数据块为200。原业务基础提示、四个 skills、安全边界和严格协议可以保留。
- **I/J/K：只删除 raw inputSchema 行不足。** I 使用 profile-only Schema 仍400；J 恢复七工具正式 Schema、配短 user 为200；真实编译类和实际 MCP adapter 捕获的 K 保留原 A user/正式 Schema，仅删七行 raw inputSchema，实际仍400。J 与 K 的 system 和 Schema 相同，只有 user 不同，故不能把 J 一次成功当成这个中间代码版本已经修好。
- **A→L：保留原 user 和正式七工具 Schema，仅删除全部工具文本元数据块，通过。** 这是保持原失败请求其它值不变的严格对照，已由真实编译M/N和原A重复O进一步复验。该候选同时改变被删文本内容和长度，一次通过不能证明普遍稳定或定位其中某个字符串/字段。G 比 H/L 更短仍失败，也不支持简单按总字数阈值解释。

D 同时将 action 改为单对象、plan/final_answer 改为 null；其200不能单独证明最终答案分支有问题。以上各请求没有自动重试，结果保留原样，不把失败样本从对照表删除。

## Schema 语义核对

原 MCP 工具提示与 `AgentLoopOutputSchema.closeObjects` 生成的正式严格 Schema 有有意的表示差异：`get_attendance.from/to`、`get_academic_history.term`、三个 RAG 工具的 `top_k` 原来可省略，正式严格 Schema 把它们设为必填且允许 null；三个 RAG 对象还明确关闭额外字段。全部非空子 Schema、原必填字段类型和默认注释保持不变。

既有 [`AgentLoopOutputSchema.toolArguments`](../../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoopOutputSchema.java) 会在调用 MCP 前删掉原可选字段的 null，恢复省略与默认值语义。这两种表示都可合法存在；文本重复本身不是非法 Schema，尚无证据证明上述六个字段或特定 JSON 键导致本次400。官方公开规则支持该模型的严格结构化输出和 anyOf；真实错误仍以本轮证据为准，不能以官方保证反推准确平台内部故障。[Groq Structured Outputs](https://console.groq.com/docs/structured-outputs)。

## 最终修复、部署与验收边界

最终改动仅作用于 [`AgentLoop.composeSystemPrompt`](../../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoop.java) 的结构化 ReAct 分支：整个工具元数据块从文本提示中省去，实际 MCP 工具、正式严格响应 Schema、守卫、最终答案本地校验、可选参数默认恢复、轮次/截止和有界纠错仍保留。普通兼容模式及原生工具模式保持原来的必要工具定义；[提示与参数边界回归](../../../backend/agent-service/src/test/java/com/edu/agent/core/AgentLoopPromptContractTest.java)核对严格模式删除目录、兼容模式保留完整目录，以及实际回调参数仍拒绝错误类型。不能永久缩为一个工具、关闭 strict、宽松化 args 或伪造报告。

中间版本“仅删 raw inputSchema 行”的 K 真实400已否定其足够性，未作为最终修复发布。最终版本另行执行 `maven-v6-exact-verify.log` 对应 verify，common80 + agent372 共 **452项通过，0失败/错误/跳过，BUILD SUCCESS**。本机实际 MCP adapter＋Spring AI 的 M 捕获证明：相对原 A 只精确删除2413字符工具元数据块，没有插入其它字符；user、正式七工具 Schema、strict及全部选项保持相同，M 与 L 的 JSON 值完全相同，字节 SHA 差异仅为序列化格式。10个使用类均与最终验证JAR相等。M 在UTC11:49:32为200、N同字节独立重复为200，O在UTC11:51:28使用原A完整原字节/相同SHA仍400空样本；因此没有凭一次候选成功结束验证。

Agent镜像 `edu-portrait-kamatera-agent-service:prod-ai-accept-20261011-v6` 于UTC11:52:40发布。审核包仅更换 `AgentLoop.class`、对应1个源文件，无新增JAR entry；不改API、数据库、密钥、原生工具模式或非结构化兼容模式。UTC12:07:08只读核验：17/17 Deployment、17/17 Pod Ready，0重启；实际运行JAR SHA256 `3ecbf71001e7f7e20213798d3c3cb6bbe24da86dff4e3a732f58ea394856471d` 与审核发布制品一致，源文件符合manifest，10个运行类均与已通过M使用类一致。公开登录HTTP200、未登录访问受保护Agent接口HTTP401。

部署后UTC12:09:54使用已核对运行类的工程协议 P 再次HTTP200，919ms、5556＋69tokens，完整四字段与正式七工具Schema、profile ID0 action、final_answer=null全部通过；没有执行这个action。这确认发布后的初始协议修复，尚未覆盖真实工具取数、后续分析/检索/审核或完整产物。

本轮没有新的学生资料/完整任务授权，旧[完整 Agent 验收](./AI_FULL_ACCEPTANCE_REPORT.md)中的 task6/7/8 均保持 FAILED，不修改历史终态。完整 Agent、真实成功 PDF 和反馈仍待新的相应授权与验收；R-5/R-6/B-2不能据工程 action 验证关闭。

历史审批记录：初始 A/B 两次额度结束后，第三次 C 的进程曾被自动审批拒绝，理由为超出两次工程外发范围、额外内部 Schema/提示发送缺明确授权。该次没有执行；随后用户明确允许后续 Groq 工程验证，范围仍无学生业务数据。本报告的 C 至 P 为新授权后的实际请求，不延用旧的待审批状态。
