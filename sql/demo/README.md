# 显式生成本科院校演示数据

交付标识：`DEMO-DATA-20261009`。本目录不加入 `sql/init` 自动初始化，不提供包含账号口令或生产备份的公开 SQL。

用户确认当前生产库只有演示/测试资料，并选定 1,200 名学生、80 名教师、4 个年级、8 个专业。生成器使用中文全名、固定种子和模拟校历；所有人口/能力/测评分布都是明确的演示假设，不是全国统计、真实人员档案或医学结论。联系方式使用保留 `.example` 域，电话和无法确定的政治面貌允许 NULL。

## 工具

- [只读预检](../../scripts/demo_data/audit.py)：读取部署的实际列、全表计数、最大 ID 与异常计数，不输出业务行或密码。
- [密码哈希](../../scripts/demo_data/HashPasswords.java)：为新增 1,278 个独立账户分别生成随机强密码的 bcrypt 哈希；明文立即丢弃，不使用默认/共享密码。
- [生成器](../../scripts/generate_demo_data.py)：完全离线、确定性生成；现有 `teacher`/`student` 各绑定一个新档案，凭据不变，仅修改两个演示账号的姓名/学院等显示资料。管理员、授权、迁移和真实操作历史保留。
- [独立校验](../../scripts/validate_demo_data.py)与[破坏性输入测试](../../scripts/test_demo_data.py)：核算日期、唯一绑定、课程、GPA/学分、考勤日历、答卷/评分、实际数据库字段；注入错误应拒绝。
- [安全应用工具](../../scripts/demo_data/apply.py)：VM 私有备份、隔离 MySQL 实际恢复和试导入、停止 writer/HPA、同连接事务与精确读回、提交内批次标记及原运行状态恢复。
- [只读生产冒烟](../../scripts/demo_data/smoke.py)：只做真实登录/注销及业务读取，不触发模型、向量或 PDF。

## 生成（本地，私有输出目录）

在生产 VM 上先运行只读预检并把安全元数据报告带回本地；备份和原始业务资料不带回。JDK 21 与 Maven 缓存的 Spring Security crypto 6.2.4 可用于哈希生成，使用实际 JDK 路径：

```bash
java -cp "$HOME/.m2/repository/org/springframework/security/spring-security-crypto/6.2.4/spring-security-crypto-6.2.4.jar" scripts/demo_data/HashPasswords.java .local-run/demo-data-20261009/password-hashes.json
python3 scripts/generate_demo_data.py --audit-before .local-run/demo-data-20261009/audit-before.json --password-hashes .local-run/demo-data-20261009/password-hashes.json --output-dir .local-run/demo-data-20261009/generated --as-of 2026-10-09
python3 scripts/test_demo_data.py --dataset .local-run/demo-data-20261009/generated/dataset.json --audit-before .local-run/demo-data-20261009/audit-before.json
```

哈希文件目标必须尚不存在。`dataset.json` 含完整合成资料及哈希，保留在权限 0700/0600 的 `.local-run`，不提交 Git；`generation-report.json` 只保留脱敏计数、范围和摘要。参考日固定为 2026-10-09，业务时区 `Asia/Shanghai`，事件截止 2026-10-08；问卷截止日期和预计毕业日期作为计划值可以晚于参考日。

## 分布与真实性边界

每年级 300、每专业 150，32 个班每班 36/39 人。2023/2024/2025 级分别有 6/4/2 个结课学期，每学期 8 门约 20 学分；2026 级无结课成绩，GPA 为未知，已获学分为 0。演示绩点政策为不及格 0，否则 `min(4,1+(score-60)/10)`；档案 GPA 按学分加权、通过课程累积学分。成绩、考勤和测评的随机因素彼此不强行绑定，不把敏感分类解释为成绩下降的确定原因。

教师教授/副教授/讲师/助教为 16/28/30/6，博士/硕士为 60/20；年龄、入职与职称相互约束。评教回答中“是否满意”未答为 NULL，满意比例只对实际作答的演示评价计算。活动是校内/院内合成经历，不虚构国家奖项、论文 DOI、基金批准号或真实证书。

三轮自编筛查各 20 题、每题 0–5、总分 100，高分表示支持较充分；历史参与 90%，当前参与 75%。正常/轻度/中度/重度的 80/15/4/1 比例仅用于展示。每条评估都具有匹配答卷、题目快照和等级规则，预警处置明确标为离线合成。未生成虚构 Agent COMPLETED、干预反馈有效性、PDF DONE、审计通过、RAG 命中、模型 token/耗时或 Langfuse 记录。

应用是有维护窗口的生产数据操作，不是无条件重放初始化。必须先通过隔离恢复和试导入；禁止 TRUNCATE、重置自增、关闭外键或全库/Redis 清空。详细回滚与验证见根目录 RUNBOOK 同标识条目和[生产交付报告](../../docs/production-tests/2026-10-09/DEMO_DATA_REPORT.md)。
