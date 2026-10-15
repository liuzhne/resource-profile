# Groq 请求协议修复

日期 / 标识：2026-10-11 / `GROQ-SCHEMA-FIX-20261011`，承接 `AI-PROD-FIX-20261011` 的 AI-01。

用户明确授权发送 MCP 工具定义及系统提示的协议诊断，最多2次。本轮实际发送2次，不含师生资料，不执行任何工具，不创建Agent业务任务；三名学生的新完整验收仍未授权。

## 原因及修改

第一请求使用生产两个MCP的实际7个tools/list定义、现有完整系统提示及公开查询诊断提示，由现有Spring AI客户端序列化。Groq返回HTTP400、error.type=invalid_request_error、code=null，明确指出 `/properties/action/anyOf` 第0分支缺少properties或additionalProperties:false。原生成器将7个工具对象再包成一个仅含anyOf的分支，Groq严格schema编译不接受这个包装。

[AgentLoopOutputSchema](../../../backend/agent-service/src/main/java/com/edu/agent/core/AgentLoopOutputSchema.java)将直接action分支展平为7个具体工具对象、并行数组及null。保持strict=true、全部required、additionalProperties=false、工具参数及最终产物校验，未关闭严格输出或变更模型/额度/重试策略。

第二请求只改变这一schema结构，返回HTTP200、1,127ms、stop。实际输出四个顶层字段，action=search_policies、公开查询参数和top_k=1、final_answer=null，Draft202012Validator验证通过。6088输入+135输出=6223token；没有执行返回的工具调用。首次诊断检查要求查询字面完全相等，将合法空格改写误判失败；本地按空白规范化后复核通过，没有因此发送第三条请求。重新编译的Java代码通过本机HTTP fixture生成的请求与第二请求完整JSON相等。

[LlmResponseErrorHandler](../../../backend/agent-service/src/main/java/com/edu/agent/config/LlmResponseErrorHandler.java)补充HTTP400且固定schema拒绝前缀的安全分类，解决code=null时只能显示PROVIDER_ERROR的问题。仅保存PROVIDER_JSON_SCHEMA/400，不保存路径、错误正文或failed_generation；64KiB读取限制及认证/限流优先级不变。

这些证据证明当前生产请求中存在可复现的首轮协议阻断及最小修复有效；旧task3/4/5的确切HTTP日志仍不可恢复，不猜测回填历史错误字段。HTTP200的公开单轮协议生成不能替代带学生资料的完整Agent、审核、PDF/反馈验收。

## 验证与发布

- schema新回归修复前单/多工具2项失败，修复后通过；空工具/并行数组/参数可空默认值保持。
- 新安全分类8项及完整受影响后端verify通过：common80、agent307，总387，0失败/错误/跳过，JaCoCo门通过。
- 真实协议证据（本地测试产物，不入库：`./GROQ_PROTOCOL_DIAGNOSIS.json`）包含两次请求摘要、HTTP/用量及本地响应验证，没有师生资料、密钥或原始模型thought。
- 生产发布及只读复验（本地测试产物，不入库：`./GROQ_SCHEMA_DEPLOYMENT.json`）通过：Agent已更新为prod-ai-fix-20261011-v3，只替换2个class及对应源码，实际运行JAR与审核制品摘要相等；17/17服务/Pod Ready、0重启，登录200、匿名task/SSE401。旧3个FAILED及结束时间保持、导出仍0；无新增数据库迁移、初始化、知识清理或收费资源。
- 完整学生Agent、新业务trace关联、成功PDF/反馈：待新具体学生资料授权；本轮外发协议授权已用完。R-5.2/R-5.4/R-6/B-2不勾选。

回滚保留当前v2镜像及两份原源码；schema修复v3未变更数据库，必要时回滚agent image至v2即可，但v2仍存在已复现的schema阻断。真实镜像回滚尚未演练，不宣称已验证。

同步维护：[ARCHITECTURE](../../../ARCHITECTURE.md)、[DECISIONS](../../../DECISIONS.md)、[RUNBOOK](../../../RUNBOOK.md)、[执行计划](../../educare/EXECUTION_PLAN.md)。
