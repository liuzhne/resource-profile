# 演示数据应用安全设计（2026-10-09）

修复/交付标识：`DEMO-DATA-20261009`。本文件是源代码审查与应用工具的验收约束，**不是生产执行报告**。规则已按最终生成/应用方案同步；执行证据统一见[生产交付报告](../production-tests/2026-10-09/DEMO_DATA_REPORT.md)，不能由本设计推定执行结果。

用户已确认目标为 1200 名学生、80 名教师、4 个年级、8 个专业，当前业务资料只有演示/测试数据。这一确认授权替换对应的测试业务资料，不表示每条现存记录都是仓库 SQL 原始种子，也不授权重置管理员、清理真实访问审计或伪造模型运行。后续应用应沿用已有授权，先把准确的分类、影响计数和回滚产物做成可审查结果。

## 1. 本次查阅的基线

- [`EXECUTION_PLAN.md`](../educare/EXECUTION_PLAN.md)：R-5.2、R-6、B-2 仍未完成；演示数据生成不能替代真实模型验收。
- [`01_init.sql`](../../sql/init/01_init.sql)：基础身份、画像、问卷及公开示例测评；仅查阅表结构和业务种子，未读取账户口令值。
- [`02_questionnaire_extension.sql`](../../sql/init/02_questionnaire_extension.sql)、[`03_agent_init.sql`](../../sql/init/03_agent_init.sql)、[`04_student_extras.sql`](../../sql/init/04_student_extras.sql)、[`05_intervention_feedback.sql`](../../sql/init/05_intervention_feedback.sql)：问卷、Agent、学业/考勤、反馈的逻辑关系与原始公开业务种子。
- [`06_portrait_contract.sql`](../../sql/init/06_portrait_contract.sql)、[`07_mental_contract.sql`](../../sql/init/07_mental_contract.sql)、[`09_identity_contract.sql`](../../sql/init/09_identity_contract.sql)：画像明细的物理外键、答卷快照/评估唯一关联、身份迁移保护边界。
- [`apply-sql-migrations.py`](../../scripts/apply-sql-migrations.py)、[`backup-mysql.sh`](../../scripts/backup-mysql.sh)、[`restore-mysql.sh`](../../scripts/restore-mysql.sh)、[`k3s-deploy.py`](../../scripts/k3s-deploy.py)：既有迁移、备份与部署能力。
- [`AsyncConfig.java`](../../backend/agent-service/src/main/java/com/edu/agent/config/AsyncConfig.java)、[`AgentTaskServiceImpl.java`](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java)、[`ExportServiceImpl.java`](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java)、[`DailyScanScheduler.java`](../../backend/agent-service/src/main/java/com/edu/agent/schedule/DailyScanScheduler.java)：异步工作、数据库状态和定时触发的真实实现。

## 2. 可证种子、已确认测试资料与未知资料

应用前生成 VM 私有的分类清单，覆盖目标表的**全部物理记录**，包括逻辑删除记录。公开输出仅含静态表名、类别、行数、摘要与通过/拒绝状态，不输出姓名、答卷、联系方式、口令哈希、自由文本审计或原始 SQL。

| 类别 | 证据 | 允许的处理 |
| --- | --- | --- |
| `PUBLIC_SEED_EXACT` | 与公开 SQL 中确定的业务值逐字段匹配，且关联父记录也匹配；规范化后的指纹一致 | 可按本次范围替换，并在清单记录源文件/种子版本 |
| `CONFIRMED_DEMO_OTHER` | 不能证明是原始种子，但用户已确认该业务库是演示/测试资料；具体表在替换白名单内 | 可替换；不得报告为“原始种子已删除” |
| `UNKNOWN_OUT_OF_SCOPE` | 白名单外的表/字段/关系，或新出现的无法解释 schema/批次记录 | 中止应用，保留原数据与预检结果；不扩大删除范围 |
| `PROTECTED_IDENTITY_OR_AUDIT` | 身份、权限、迁移、人工申请或真实运行审计 | 保留原始记录；必要的新增真实应用审计另行追加 |
| `BATCH_OWNED_DEMO` | 本批次清单、生成器版本、自然键命名空间、映射 ID 与载荷摘要共同证明 | 可验证重复应用或精确撤销；不能仅凭“DEMO”文本猜测所有权 |

不能用 `id=1`、姓名像“同学”、`student_id` 前缀、默认账户、创建日期、行数少或“看起来像测试”证明原始种子。公开 `04_student_extras.sql` 的学业与考勤记录依赖学生档案实际归属，分类不能只比较其 `student_id=1`。问卷后续迁移会重算题目数量、补等级规则及无歧义的 `response_id`；发生过合法修改的资料应归入 `CONFIRMED_DEMO_OTHER`，不伪造与原始种子一致的证据。

已存在的真实测试 Agent 执行、PDF 操作及登录事件，即使业务对象本身为演示学生，也仍是**真实操作历史**。若对应业务资料被替换，其原始产物在此次完整备份中保留；现有审计及 Langfuse 观测不删除、不改写为本批次生成历史。

## 3. 固定替换白名单与保留边界

### 3.1 可替换的 16 张业务表

`teacher_info`、`teacher_portrait_record`、`teacher_evaluation`、`student_info`、`student_academic_record`、`student_attendance`、`student_quality_record`、`mental_questionnaire`、`mental_question`、`mental_response`、`mental_assessment`、`agent_task`、`intervention_report`、`knowledge_citation`、`agent_export_task`、`intervention_feedback`。

应用工具须逐表声明“替换/生成/清空旧测试产物/保持”，不能对所有数据库表执行循环删除。未知表或新增外键不自动纳入范围。Agent 相关表可清理已确认的旧测试业务产物，但默认不插入未经真实执行的新 Agent/PDF/RAG 产物，见 §8。

### 3.2 原始记录必须保留

| 表或资产 | 保留规则 |
| --- | --- |
| `sys_user` | 管理员整行保持原值；全部既有账号的用户名、密码、类型、状态、删除标记、头像、创建日期和角色绑定保持。只允许清单明确声明的 teacher/student 两演示账户更新 nickname/dept_name/phone/email/bio/update_time 展示列；可添加清单声明的新演示账户 |
| `sys_user_role` | 既有角色绑定保持；可为新增演示账户追加已有规范角色，不修改现有账户的授权 |
| `sys_role`、`sys_permission`、`sys_role_permission` | 整表原值不变，不重放默认权限、不改变管理员及已经编辑的角色目录 |
| `sys_migration_log`、`sys_schema_migration` | 整表原值不变，不删除迁移校验和或身份迁移标记，不借演示数据重跑初始化 |
| `sys_login_log` | 不删除、不更新、不插入虚构登录；验收实际登录如追加真实日志，须单独记录该增量 |
| `audit_log` | 全部原始记录保持；仅可追加确实发生的应用/回滚操作审计。不能生成虚假模型或导出审计 |
| `sys_account_request` | 全部既有人工申请及处置记录保持，不据“演示库”推断申请也可清空 |
| Redis、Milvus、MinIO、TLS、PDF 文件、Langfuse | 不整体清空、不重建卷、不清理真实观测；文件与缓存处理必须另有明确对象清单 |

保护校验应比较“应用前的原始 ID 集”对应行的规范化内容摘要，而不是只比较行数。新增演示账户/角色绑定及真实操作审计可使整表行数增长，但原始受保护行的摘要必须不变。公开报告只包含摘要、计数及布尔结果。

## 4. 身份和标识规则

1. 每个新学生、教师档案绑定一个类型正确的独立账户；1200 名学生和 80 名教师不能共用少数 `user_id`。本批次新增账户启用，每个账户使用独立随机强密码的 bcrypt 哈希，明文立即丢弃且不提供默认/共享密码，规范角色通过角色编码查询，不假定固定角色 ID。
2. 用户名、学号、工号使用固定批次自然键命名空间；插入前检查全表冲突，包括逻辑删除行。任何与受保护既有账户冲突均中止，不能 `REPLACE INTO` 或 `ON DUPLICATE KEY UPDATE` 覆盖账户。
3. 本批次复用 teacher/student 两个代表账号且保留现有凭据；新增代表账号如需可登录，须由管理员独立设置新强口令，不公开或共享。现有管理员不改密，不读取/输出其口令。
4. 不执行 `TRUNCATE`、`ALTER ... AUTO_INCREMENT=1` 或数值 ID 复用。保留的审计 `target_id` 不能在替换后指向另一个人或任务；新记录使用新的主键，并将自然键到主键的映射写入批次清单。
5. 不根据旧共用账号猜测真实归属。此次用户已确认测试数据，可整体替换对应档案；若复用原 `teacher`/`student` 账户作为代表账户，需在清单明确映射到哪一个新演示档案，且该账户只绑定一个档案。
6. 对明确重绑的演示用户，可在提交后精确撤销其 `token:{id}`、`refresh:{id}`；管理员和其他既有账户的会话保持。禁止 Redis `FLUSHDB`/`FLUSHALL`，回滚也不恢复已撤销的旧令牌。

## 5. 维护窗口与异步工作门

仅把 gateway 缩为 0 不能证明数据库不再写入。当前 Agent 异步线程池、PDF 队列、直接服务入口及定时扫描仍可能写库。当前部署脚本对 `data-service` 配置 HPA，应用工具必须读取并保存实际部署/HPA 状态，不能假设所有副本都是 1，也不能依赖一个未实现的 `maintenance=true` 开关。

建议门序：

1. 记录原应用副本、HPA 规范及相关非敏感调度开关，保存到 VM 私有维护清单；禁止输出 Deployment 的完整环境变量或 Secrets。
2. 阻止公网新增请求与 Agent/导出触发。将定时扫描的**有效配置**关闭并确认生效；环境变量默认 false 不能替代可能覆盖它的 Nacos 有效配置。
3. 在当前 worker 仍存活时检查并等待数据库活跃状态归零，间隔复查两次：

   ```sql
   SELECT COUNT(*) FROM agent_task
    WHERE status IN ('PENDING','RISK_ANALYZING','KNOWLEDGE_RETRIEVING','PLAN_GENERATING','COMPLIANCE_CHECKING');
   SELECT COUNT(*) FROM agent_export_task
    WHERE status IN ('PENDING','PROCESSING');
   ```

   检查全部物理行，包括逻辑删除行，防止其进程仍在工作。应用工具不得把活跃记录批量改成 COMPLETED/FAILED 来制造“已排空”。超出限定等待时间仍有活跃任务，应中止该次应用，保留维护/排错证据，再恢复原运行状态。
4. 保存并暂时移除相关 HPA 控制器规范，停止所有会写入或可能触发写入的应用进程，确认实际 Pod 数为 0；只保留 MySQL/Redis/向量基础设施和必要的维护展示。应用成功后按保存的原状态恢复，不能把原来 0 的服务一律恢复为 1。
5. 停止 writer 后再次检查活跃状态为 0；确认无其他工具/运维会话写入目标库。申请单/登录日志也要求暂停对应入口，以使备份与保护指纹来自同一稳定状态。
6. 任何“停止到一半”的失败都执行明确的恢复分支；每一步有超时、退出码和 finally 清理。不能无条件在失败后打开网站，把尚未验证的部分数据暴露出去。

源码里的 Agent 默认总期限为 900 秒，锁 TTL 默认 1200 秒，线程池关闭等待为 60 秒；这些都是默认值，不是生产实测值。应用时须以实际有效设置决定等待上限，60 秒 Pod 退出并不自动证明长任务正常终结。Redis 锁只能在确认旧 worker 已退出且对应任务不活跃后处理；优先等待短期幂等键自然过期，不按宽泛前缀清空全部状态。

## 6. VM 内完整备份与隔离恢复验证

### 6.1 备份产物

- 每次应用使用新的 VM 私有目录，目录 0700、文件 0600、`umask 077`；保存完整逻辑 dump、gzip SHA256、生成器/载荷 SHA256、非敏感原运行状态、全表计数和受保护原始行摘要。
- 从 MySQL 容器内部环境/文件传入凭据，不输出密码、命令展开结果、dump 内容、表行或自由文本 SQL 错误。所有管道环节都必须检查返回码；gzip 非空并不证明 mysqldump 成功。
- 备份期间没有业务 writer 或 DDL；采用一致性事务备份，并包含恢复所需 schema、数据、触发器/例程等对象。备份成功后执行 gzip 完整性检查与 SHA256，不使用 `head` 截取管道作为唯一有效性判据。
- 产物只留 VM；不能上传到公开仓库、文档或下载到聊天。此次回滚备份在应用与验收收口前不得被一般 7 日自动保留策略删除。

### 6.2 必须验证“能恢复”

[`backup-mysql.sh`](../../scripts/backup-mysql.sh) 使用 `mysqldump --databases`，dump 含数据库创建/选择指令；[`restore-mysql.sh`](../../scripts/restore-mysql.sh) 直接恢复到所选 Docker 容器。**只把 mysql 客户端选库参数改为某个校验库，不能安全隔离该类 dump**，也不能简单文本替换数据库名，因为例程、触发器及业务文本可能含同名内容。

建议在同一 VM 使用同版本已有 MySQL 镜像启动一个临时恢复容器：

1. 独立的新空数据目录或临时卷，不挂载生产 PVC/数据目录；无宿主端口映射，`network=none`，有限的内存/CPU/等待期限，禁用事件调度和任何业务服务。
2. 使用临时私有凭据文件，不在命令行或日志打印；不访问公网、不拉取不明新镜像。
3. 原样导入完整 dump，使 dump 中同名 schema/触发器只存在于隔离实例。导入失败、对象缺失、gzip/指纹不符、计数不符均拒绝生产写入。
4. 验证所有表的精确行数、列/索引/FK/JSON 结构与必要对象，比较受保护行的规范化 SHA256。计数一致不能替代内容一致；`GROUP_CONCAT` 截断或省略 NULL 的拼接不可以作为摘要。
5. 可在这个恢复实例上先执行本批次应用并检查目标计数、保护边界、关联完整性、幂等性、故障回滚；均完成后才允许对生产库应用。
6. finally 删除恢复容器和专属临时目录/卷，仅保留脱敏检验报告和原备份。删除时必须验证目标属于该次恢复批次，不能按广泛名称模式清理其他容器/卷。

禁止对生产执行恢复演练，禁止把 dump 导入另一个实际业务库，禁止用 `FOREIGN_KEY_CHECKS=0` 掩盖恢复后的孤儿数据。当前安全审查未执行上述恢复验证。

## 7. 单连接事务、外键与批次幂等

### 7.1 事务与删除/插入顺序

应用业务 DML 使用**同一个 MySQL 会话**、InnoDB 事务及有限锁等待；工具需要全局应用互斥锁，并在同一会话保持到提交/回滚结束。若采用 MySQL advisory lock，不能在单独的 mysql 进程取得锁后用另一个进程进行应用。预检记录与实际应用之间不得放入会重新开启 writer 的操作。

业务事务中禁止 `TRUNCATE`、DDL、迁移/初始化重放或自动提交 SQL。所有删除为白名单内的精确范围删除；显式顺序：

1. 依赖 Agent 的 feedback/citation/report/export，再旧 `agent_task`。
2. 先 `mental_assessment` 与 `mental_response`，再 `mental_question` 与 `mental_questionnaire`。
3. 先学生 quality/academic/attendance，再学生档案；先教师 evaluation/portrait_record，再教师档案。
4. 如添加新演示账户，保留旧账户原行不变；先账户，再角色绑定，再画像，再业务明细/问卷/答卷/测评。

实际 schema 的全部 FK 应先读取并核对。当前脚本中三组物理 FK 为 `teacher_portrait_record.teacher_id`、`teacher_evaluation.teacher_id`→`teacher_info.id` 及 `student_quality_record.student_id`→`student_info.id`，其余大量关系只有逻辑 ID，**没有数据库 FK 不表示可以留下孤儿**。新增未知外键应阻止应用；整个会话保持 `FOREIGN_KEY_CHECKS=1`。

提交前验证零孤儿、目标计数、账户绑定唯一、数值范围、JSON 可解析/内容契约和保护指纹；任意 SQL/校验错误均显式 `ROLLBACK`，进程非零退出。不能把 mysql 的最后一条 SELECT 成功视为整个脚本成功。

### 7.2 推荐的批次凭据

批次至少记录 `batch_id`、生成器版本、固定随机种子、配置（1200/80/4/8）、业务时区/参考日、载荷完整 SHA256、每张表计划/实际数量、自然键→新 ID 映射、保护指纹、备份摘要。参考日固定才能保证重复运行不随日历改变。

优先不借用 schema 迁移表存业务种子完成标记。可在**同一业务事务内**向现有 `audit_log` 追加一个真实 `DEMO_DATA_APPLIED` 操作标记：固定批次 ID、载荷 SHA、计数与生成器版本，且通过应用互斥锁防止并发重复。此标记是本次确实发生的数据操作，不是虚构审计。也可采用经专门迁移创建的批次表，但其 DDL 必须独立完成并验证，不能混入可回滚的业务事务。

- 同 batch+同载荷：核对已提交标记、计数、映射和摘要后返回“已应用”，不新增/重写业务记录。
- 同 batch+不同载荷：拒绝；生成新批次，不能覆盖原批次标记。
- 自然键碰撞但无可信批次证据：拒绝，不把部分现有记录强行认领为本批次。
- 进程断开、提交结果未知：保持维护状态，根据事务内标记及精确读回确定“已提交/完全未提交”；出现部分或无法解释状态则中止。不能自动重放一遍，也不能直接恢复对外服务。
- VM 私有 receipt 应在提交后用原子文件替换持久化，并包含上述事务标记；只有外部文件、没有提交内证据时，不能把文件写成功当作数据库原子性证明。

## 8. 内容真实性与关联通过判据

- 人名采用不带占位符的完整中文姓名；bio、活动/评价标题、备注、问卷说明与批次清单明确合成来源；生成器不使用真实学生资料、电话号码/邮箱或网上抓取的个人资料。
- 1200/80 是有效档案的最终总数；4 年级/8 专业、学院/班级、GPA/学分必须与明细和业务枚举自洽。未知数据仍可为 NULL，不把“未发生”统统填 0；日期符合参考日、年级与在校时段。
- 每位学生的答卷是与该问卷匹配的真实合成作答，`questionnaire_snapshot` 与回答同批生成；`response_id` 正确关联且唯一，题目数、必填题、选项/量表范围、分数及等级规则相互一致。合成心理分数仅供演示，不描述为真实诊断。
- 预警状态和处置说明可作为明确的离线演示情景；须与演示专业账户、日期及状态一致，不伪称工作人员真实处置了真实学生。
- **不生成虚构 Langfuse 观测、模型/token/耗时、MCP 工具执行、Milvus 命中或真实 LLM 成功状态**。当前 Agent/PDF 记录没有独立的离线演示来源类型，直接插入 COMPLETED/DONE 会让界面误以为发生了真实执行；默认保持新批次 Agent/PDF 产物为空。若以后需要静态演示报告，应先实现端到端明确的“离线模板示例”来源与展示契约，不能仅在未显示的 JSON 中加一个标记。
- 不调用 Groq、Cloudflare 向量写入、Langfuse ingestion 或 Agent 触发来“填满页面”；真实模型验收需独立授权与执行证据，不复用此前一次性授权。
- `audit_log` 与 `sys_login_log` 是操作历史，不能为最近 30 日/仪表盘趋势编造事件。业务资料的创建/答卷日期可在演示情景中设定，但须在数据说明中区分场景时间和真实应用时间。

必须检测的逻辑关系：所有画像→账户；学业/考勤/素质→学生；教师记录/评价→教师；题目→问卷；答卷→学生/问卷；测评→学生/问卷及非空 `response_id`→同学生同问卷答卷；Agent task→学生；report/citation/export/feedback→任务，冗余学生 ID 与任务一致。保留的历史审计引用已移除对象可以保持原始证据，但不能指向复用 ID 的新对象。

## 9. 回滚与解除维护

提交前失败：同会话回滚，验证所有受保护记录/业务计数仍为原状态，再按原运行清单恢复应用、HPA 和调度。

提交后验收失败：继续保持维护，保存失败的非敏感证据，使用已通过隔离恢复验证的备份恢复**原目标库**。恢复前确认所有 writer 仍停止，恢复后比对全表计数、原始行摘要、schema/迁移/管理员角色，并追加真实回滚操作证据。若完整 dump 包含 schema 切换，恢复工具须验证固定生产容器与数据库，不接受任意外部地址或未审核 dump。

成功解除维护前需证明：

1. 原始受保护行不变；目标计数 1200/80/4/8、账号归属/关系与所有字段验证通过。
2. 批次提交标记及私有 receipt 可核对，备份存在且已经实际恢复验证。
3. 应用按原副本/HPA 配置恢复并 Ready，MySQL 与 Redis 正常，公开站点可访问。
4. read-only API 检查用户分页、画像、问卷、答卷/预警、仪表盘计数；实际测试登录另记真实审计增量，并核对旧共享账号问题不再出现。完整 Agent/模型调用不属于此轮静态演示数据验收。
5. 预检/应用/恢复/验收过程的 stdout 与公开报告只含计数、摘要、固定状态和静态标识；原业务数据、凭据、dump、完整环境及自由文本报错仍留 VM 私有空间。

安全工具审查时，以上每条应有明确函数/分支/测试或执行证据。未执行的门标为“待验证”；不能把生成 SQL 成功、gzip 非空、行数相同或 Pod Ready 单独当成整个数据应用已通过。
