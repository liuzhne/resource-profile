# 生产接口性能优化：PERF-500MS-20260916

日期：2026-09-16。原始不可用功能诊断见 [REPORT.md](./REPORT.md)。本次修复代码与配置，生产部署及500ms全量验收尚在进行，不能视为全部达标。

## 修复

- AI/PDF同对象@Async失效：显式后台排队，队列满载不在HTTP线程执行，标记失败/业务503。
- MCP429导致Agent启动失败：Render后台完整工具集连接/重连，普通读取不被握手阻塞，工具未就绪明确失败。
- 首请求数据库/Servlet初始化：启动SELECT1、eager Bean/Servlet、2条空闲数据库连接及keepalive。
- 缺失Actuator：common提供真实探针；auth只公开health三个精确路径，其他权限保持。
- 统计4次数据库往返合并；趋势create_time谓词去DATE函数；没有生产DDL或敏感数据跨角色缓存。
- gateway DEBUG改INFO；生产SQL stdout关闭、压缩开启；Server-Timing显示应用/网关耗时。

## 验证

完整Java回归和安全覆盖率、新增异步/MCP失败恢复/DB预热测试通过；Python28项通过；前端构建、bundle体积门、61条既有逻辑断言通过；ESLint零错误、一个既有格式警告。Render Blueprint有效（12项计划动作，未据此创建资源）。

## 测量口径

[bench-production-api.py](../../../scripts/bench-production-api.py)使用持久requests.Session、顺序读取、完整响应墙钟时间。第一笔包含新连接，休眠唤醒独立保存。只记录状态码/耗时，不记录个体数据、密码或token。429、业务503或超时即使很快也失败；AI/PDF提交与最终生成、SSE握手与长连接时长分别判断。

生产前基线保存 performance-before*.json。首次登录15.79s超时、重试18.43s返回200；随后同连接auth/userInfo约400/406ms。原逐次新连接curl约0.73–1.86s不能直接与持久连接最佳值比较。

免费实例15分钟休眠与客户端跨区域网络尚未消除；本次不增加费用。所有接口含冷启动、推理/大文件完整传输均500ms的硬要求尚未满足，不能用代码承诺。部署后实测、剩余慢路径和限制将追加本报告。

## 导入补充修复

Excel逐题数据库写入改为每100题参数化批量INSERT，同一事务保持替换/题目数/计分JSON/排序/必答/时间戳语义。205题单测证实三批100/100/5；实际MyBatis SQL在本地H2验证中文/引号/JSON/null绑定及整体回滚。未更改生产问卷记录，完整文件上传/解析500ms仍待带夹具验收。

PR#15/main44a1074的完整CI已通过，首轮代码发布正在进行。生产服务仍跟随main；main与已验证PR源码tree相同。本次配置变化与原值分别保存render-performance-applied.json和render-performance-plan.json，回滚优先使用原始plan（配置网络超时后幂等重试的applied可能已无原值）。
