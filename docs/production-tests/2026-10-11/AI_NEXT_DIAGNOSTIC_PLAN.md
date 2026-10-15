# AI-FULL-ACCEPT-20261011 下一轮诊断候选（2026-10-11）

> 后续工程更新（2026-10-11 / GROQ-400-CAUSE-20261011）：原始A/O生成400，移除strict重复工具文字目录的L/M/N/P严格200；452回归及最小Agent v6发布、运行制品一致/17健康通过。见[原因验证与修复结论](./GROQ_400_CAUSE_REPORT.md)。本文原task6/7/8失败记录保持不变，完整学生/P4/RAG/PDF/反馈没有重新验收。


当前验收仍失败，本轮3次完整任务及此前2次工程诊断授权均已使用。本文件仅说明已准备、尚未外发的下一步，不能作为新授权或验收通过证据。

## 可审阅的两份候选

| 项目 | A | B |
|---|---|---|
| action工具候选 | 既有7工具 | 仅get_student_profile |
| system/skills及user消息 | 相同 | 相同 |
| 虚构诊断studentId | 0 | 0 |
| final_answer schema | 保持现值 | 保持现值 |
| response_format | json_schema / strict=true | json_schema / strict=true |
| 输出预算 / reasoning | 2400 / low | 2400 / low |
| 请求大小 | 24868 bytes | 20248 bytes |

完整序列化请求唯一差异为 `/response_format/json_schema/schema/properties/action/anyOf`。两份消息均为9689 UTF-16字符，小于18000预算；完整四字段的档案action、plan=null、final_answer=null示例均通过Draft202012 schema验证。无原生tools参数、非流式、无cache_prompt。

精确请求与元数据保存在忽略目录 `.local-run/ai-accept-20261011/next-protocol-request-A.json`、`next-protocol-request-B.json`、`next-protocol-candidates-metadata.json`。使用实际MCP定义和v5对应的11类/6资源/4源文件/193依赖，摘要一致且无不匹配。仅执行2次127.0.0.1 HTTP stub捕获，Groq请求、MCP连接/传输/反序列化、业务工具执行、部署及JAR修改均为0。

## 待新授权的执行及通过判据

若获授权，每份只发送1个Groq请求，最多2次；不自动重试，不执行返回action，不查询学生0或生成业务报告。只发送工程MCP定义、系统/技能提示和虚构ID0的诊断用户提示，不发送真实或合成学生档案。实际发送前核对上述候选摘要；结果仅保留固定HTTP/错误类别、schema校验、首轮档案action及studentId=0的布尔判据，不保存提供商失败正文。

HTTP200且完整四字段严格校验通过、action为约定档案工具、studentId=0、final_answer=null，才表示该候选协议通过；400/401/429或不合schema均据实失败。单次A/B结果不足以证明普遍稳定性或精确根因，不据此自动关闭完整Agent故障。该诊断不改变生产配置，无需应用回滚。

随后若另获最多3次完整任务授权，仍使用346/750/175三名已确认合成学生；只对真实成功报告验PDF，并对真实方案记录演示反馈。资料发送目标仍为Groq生成、Cloudflare RAG和Langfuse日本区安全指标。所有新模型请求及完整/PDF/反馈结果均为待验证；不扩展当前已用完的授权次数、不伪造报告或成功终态。
