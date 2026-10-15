# FIELD-CONTRACT-FIX-20261007 修复验收记录

日期：2026-10-07。原[61项审计基线](./FIELD_CONTRACT_AUDIT_2026_10_07.md)保留为历史证据。以下逐项对应实现。完成于 2026-10-07：61组修复已部署至 Kamatera K3s，外部认证按用户要求置灰；生产验证仍有两项存量身份归属阻塞和一项问卷覆盖跳过，不能声明完整冒烟通过。

| 编号 | 审计问题 | 处理结果 |
|---|---|---|
| ROOT-D01 | 面板“今日问卷完成”实际统计启用问卷模板数 | todayCompletionCount queries actual mental_response rows within school business-day half-open [00:00,next00:00), joining undeleted student/profile and questionnaire. Does not count enabled questionnaire templates. API returns business date/timezone; UI shows school-zone meaning. |
| ROOT-D02 | “师生分布/在册总人数”实际统计登录账号 | distribution reads active undeleted teacher_info and student_info, same population as teacher/student statistics cards. Graph and total labels identify in-service/in-school profile counts; sys_user account population is removed. |
| ROOT-D03 | 本周/本月/全年标签对应最近7天/30天/12个月 | rolling labels now 近7天/近30天/近12个月, explicit school Clock for boundaries, future rows excluded, YYYY-MM labels across years. Trend title clarifies newly created profile events. |
| ROOT-D04 | 待处理事项展示固定的预警/科研/异常登录/作答事件 | GET /data/dashboard/todos returns real OPEN/IN_PROGRESS 中度/重度/高危 assessments (undeleted students) and undeleted FAILED/REJECTED Agent tasks (undeleted students), with sourceId/status/eventTime and real total; bounded 1–20 results, count/read in read-only transaction. Dashboard no longer has fabricated names/events/relative times. |
| ROOT-D05 | 最近登录表有读取接口但登录链不写入记录 | 登录链记录实际成功及已知账号失败事件；新库不生成虚构登录日志。 |
| ROOT-D06 | remember 参数与“7天内免登录”未落实 | remember=false使用sessionStorage且不签刷新令牌；true使用HttpOnly/SameSite cookie并旋转刷新，保留首次登录最多7天绝对期限。 |
| ROOT-D07 | refreshToken API helper 缺少后端必填请求头 | refresh helper携带同源cookie，可选X-Refresh-Token兼容；刷新密钥不进入JavaScript或响应体。 |
| ROOT-D08 | userInfo DTO 的 deptName/permissions 从未填充 | sys_user增加dept_name/bio；userInfo真实填充部门、简介及启用角色权限。 |
| ROOT-D09 | 用户列表 role 查询参数被接受但不生效 | 真实关联sys_user_role/sys_role的角色查询与前端筛选，分页total与相同条件一致。 |
| ROOT-D10 | 用户菜单“系统设置”没有操作或反馈 | 系统设置跳转/profile?tab=security，详情路由fullPath绑定当前组件身份。 |
| ROOT-G01 | 短信、统一身份认证、扫码登录未交付 | 按用户明确选择，将短信、SSO和扫码入口置灰并说明未配置外部认证服务。 |
| ROOT-G02 | 忘记密码与联系管理员开通没有流程 | 开通/找回申请实际保存至sys_account_request，管理员查看及人工核验处置；不自动重置密码或假称已发送邮件。 |
| ROOT-G03 | 使用条款和隐私政策没有实际内容入口 | 登录页提供实际使用和隐私说明，描述账号保护、数据访问、会话与AI服务边界。 |
| ROOT-G04 | 全局搜索与通知没有服务入口 | GET /data/search validates 2–50 character literal keyword, searches only profile name/employee/student code, excludes deleted rows, max20 and minimal DTO; GET /data/notifications reuses real source pending summaries. Canonical STAFF_VIEW plus signed JWT request data:read enforced; students/anonymous/role elevation claims rejected. Navbar actual search results, notification badge/modal and role+permission entry visibility. Settings routes to /profile?tab=security, existing logout code/style kept. |
| ROOT-G05 | 用户新增/编辑/删除/状态调整未在前端开放 | 管理员真实账号新增/编辑/删除/状态/角色接口与前端表单，bcrypt、强口令、唯一用户名、绑定删除限制、自身/最后管理员保护及会话撤销。 |
| ROOT-G06 | 心理概览“查看全部/处理”仅提示未开放 | GET /mental/warnings分页状态筛选及PATCH处置，OPEN/IN_PROGRESS/RESOLVED，note专业角色可见。 |
| TS-D01 | 学生详情课程成绩未接已有 API，页面声称成绩表/接口缺失 | 学生课程成绩接 GET /student/{id}/academic，实际 term 选项，NULL 成绩未出分、0 成绩保留；分区错误可重试 |
| TS-D02 | 学生列表将缺失/脱敏 GPA 的 null 显示为真实 0.00 | 列表 GPA null/undefined/空值显示 —；真实 0 显示 0.00 |
| TS-D03 | 学生详情将合法零 GPA 隐藏为 — | 详情和课程绩点使用同一 NULL/0 格式契约 |
| TS-C01 | 教师/学生列表筛选字典写死，无法覆盖数据库允许的新取值 | 学院/教师职称/学生年级由非删除真实档案字典动态查询 |
| TS-G01 | 教师政治面貌缺少持久字段及返回契约 | teacher_info 新增 nullable political，typed DTO 持久化，HIGH 字段按既有 role advice 脱敏 |
| TS-G02 | 教师详情电话/邮箱没有教师 API 投影，前端固定占位 | teacher_info 个人 phone/email nullable 持久化；不连接共享 sys_user 联系方式，HIGH 权限 |
| TS-G03 | 教师教学成果标签缺少记录表及 API | teacher_portrait_record TEACHING 实际新增/编辑/软删/读取，admin 写、staff 读 |
| TS-G04 | 教师科研项目标签缺少项目记录表及 API | teacher_portrait_record RESEARCH 实际项目新增/编辑/软删/读取 |
| TS-G05 | 教师综合评分/评价次数/满意度及评教标签缺少来源 | teacher_evaluation 实际记录 0–100 评分与 nullable 满意回答，均值/满意度只来源于真实记录 |
| TS-G06 | 学生专业排名固定占位且未定义计算/返回契约 | 同学院、专业、年级且在读、未删除、非 NULL GPA cohort；按 GPA 竞赛排名，同分并列；缺条件明示无排名 |
| TS-G07 | 学生综合素质：竞赛/荣誉/志愿服务记录缺少来源 | student_quality_record 竞赛/荣誉/志愿服务真实 CRUD，admin/teacher 写，owner/staff/internal 读 |
| TS-G08 | 学生详情教职工侧心理历史没有学生范围查询契约 | 学生详情心理历史接 GET /mental/students/{studentId}/assessments，只显示title/type/level/date，不显示原分/结果/建议 |
| TS-G09 | 教师新增/编辑前端尚未连接已有写接口 | 教师新增/编辑 typed DTO 实际写接口与表单，account 类型/启用状态、编号重复、字段范围验证 |
| TS-G10 | 学生新增/编辑前端尚未连接已有写接口 | 学生新增/编辑 typed DTO 实际写接口与表单；有效学生账号、新重复绑定拒绝，旧共享 unchanged 兼容；teacher 无权写 HIGH 出生日期，隐藏 omitted 保留、admin explicit null 可清除 |
| MENTAL-01 | “今日完成问卷”实际是最近 30 天评估记录数 | 今日完成量按 Asia/Shanghai 当日答卷时间区间计数，另保留近30天测评总数作为比例分母。 |
| MENTAL-02 | “各学院心理状况分布”丢失学院维度 | 分析学院图保留学院分组，堆叠展示三类真实测评记录数。 |
| MENTAL-03 | 心理分析“导出报告”按钮没有实现 | 真实 CSV 附件导出，staff鉴权、BOM/RFC转义/公式防注入。 |
| MENTAL-04 | 题目数可任意填写，不等于实际题库数量 | 新增草稿实际题数0，列表/detail按实际题目派生，题库写入与迁移同步缓存题数。 |
| MENTAL-05 | 数据库支持量表字段，设计页、作答和计分没有实现量表契约 | scale authoring/作答/回显/API实现；服务器范围整数、映射完整性、JSON不截断浮点。 |
| MENTAL-06 | scoringRules 有字段、有写入，但当前提交计分不读取 | 统一options[].score及旧scoringRules[value/index]来源，冲突/缺失拒绝；真实服务器求和及等级。 |
| MENTAL-07 | Excel 题目导入只存在 Service，没有 API 或前端入口 | 受MENTAL_WRITE控制的Excel预览/确认替换真实API；预览校验只读，错误文件不替换旧题库。 |
| MENTAL-08 | required 字段只在浏览器校验；提交 API 允许缺题、重复题或选项 | 服务器必答、所属问卷、重复题/选项、单选、多选范围、类型、长度、唯一答卷、绑定校验。 |
| MENTAL-09 | 历史“我的作答”使用当前题目及选项，缺少提交时快照 | 每次新提交保存剥离计分信息的题目快照；历史详情及title/type基于该快照。 |
| MENTAL-10 | 停用或删除问卷后，已有结果不能回显题目与作答 | 历史查看独立于当前问卷删除/停用；无旧快照明确notice且不假装当前题干属于历史。 |
| MENTAL-11 | 本人结果详情返回未裁剪的 questionnaire.levelRules | 本人详情/作答模板不包含levelRules、scoringRules、选项score/value；权限边界未扩张。 |
| MENTAL-12 | 完成情况按 rows.length 计人数，一条答卷可被多条评估扩成多行 | assessment.response_id唯一明确关联，完成情况JOIN按response_id；迁移仅回填无歧义历史对。 |
| MENTAL-13 | 编辑问卷清空开始/结束日期，无法保存为不限日期 | 实际JSON区分日期字段遗漏与显式null；后者清除SQL值。 |
| MENTAL-PERM-01 | 学生历史/结果直接展示被权限层置 null 的分数、等级、建议 | 按角色显示原分/级别/建议，缺值不解释为0或正常；页面明确权限空态及加载错误。 |
| AP-01 | 已返回的联系方式仍固定显示为空，来源说明过期 | 本人邮箱、电话、部门从auth/userInfo真实展示。 |
| AP-02 | 资料保存与修改密码未接线，输入仅保留在组件内 | 本人资料更新与验证原密码的改密接口，输入校验、显式null清空、密码变更撤销访问及刷新会话。 |
| AP-03 | 角色目录及用户数量为固定假数据 | 角色与真实账号数由sys_role/sys_user_role/sys_user读取，移除固定角色数据。 |
| AP-04 | 角色及权限操作按钮没有实际读写链路 | canonical角色CRUD及真实权限目录读取/选中/保存；角色为权限上限，JWT权限进一步限制业务请求与前端菜单；修改撤销相关会话；目录allowedRoles对齐真实端点，越界保存拒绝；管理员固定全选不可配置，学生本人能力独立于教职工管理目录。 |
| AP-05 | 初始化心理角色码与实际授权角色码不同 | 统一psychologist等6个角色码，保留历史mental_teacher关联并仅迁移有效角色绑定；不赋予teacher心理原分权限。 |
| AP-06 | 合法ReAct期限被显示为—天，固定7天标题也与期限不一致 | ReAct字符串deadline及legacy deadline_days规范为deadline |
| AP-07 | 长期跟进内容与当前schema完全错配 | goal/milestones/horizon与legacy phase/goals/metrics完整保留；metrics不冒充milestones |
| AP-08 | 资源字符串列表被当对象表格展示 | ReAct字符串resource规范为具名资源；旧对象type/name/contact保留 |
| AP-09 | 引用读错层级，合法主路径引用全部丢失 | 全局引用及行动引用去重保留并在UI/PDF呈现 |
| AP-10 | 主路径知识检索结果未留存，页面会误标未执行 | 主/子Agent真实search工具调用快照finally落既有JSON列；未执行/失败不记成功；真实SpringAI MCP content数组回归 |
| AP-11 | 敏感指标只在UI隐藏，任务API及PDF缺少相同字段门 | list/detail风险String JSON服务器allowlist裁剪全部自由文本/未知嵌套字段，普通staff不见原始心理指标；PDF三端点仅登录admin/psychologist |
| AP-12 | 同组件详情标签切换未同步路由身份，可能读取或操作旧实体 | Layout以route.fullPath重建详情，Agent/心理/师生详情另有迟到响应保护。 |
| AP-13 | Cloud构建未传入追踪页面URL，后端可观测配置不能驱动此页 | Langfuse日本区控制台URL由构建参数注入，安全新窗口入口，移除强制iframe；无前端密钥。 |
| AP-14 | 反馈服务未校验任务已有方案/终态，契约仅靠前端可见条件 | 反馈必须COMPLETED/REJECTED且有实际行动或长期目标/里程碑；拒绝空、坏JSON与无行动待补全占位，1000字校验 |
| SC-D01 | MCP 心理指标读取的 JSON 键与真实心理历史 API 不一致，造成已有风险等级丢失 | MCP读取真实questionnaireType/level/score，保留assessmentLevel并白名单归一风险；无因子来源时为空，不伪造分数。 |
| SC-D02 | MCP 学业平均分/平均绩点把未出分课程学分加入分母，将未知成绩当零分 | 分数与绩点分别只用有值课程学分作分母；未知为null，实际0保留，读取失败不冒充空历史。 |
| SC-D03 | MCP 心理查询绕经共享 userId 后丢失请求 studentId，LIMIT 1 与公开种子可造成混档 | MCP按studentId直接读取历史并检查全部返回行归属；自助查询多学生绑定返回409，新绑定并发串行拒绝重复，既有真实绑定不迁移。 |

## 数据库交付

全量新库执行 `sql/init/01、02、03、04、05、06、07、09`；新增初始化脚本为[画像契约](../../sql/init/06_portrait_contract.sql)、[心理契约](../../sql/init/07_mental_contract.sql)、[身份契约](../../sql/init/09_identity_contract.sql)。已有库只执行[画像迁移](../../sql/migrations/20261007_01_portrait_contract.sql)、[心理迁移](../../sql/migrations/20261007_02_mental_contract.sql)、[身份迁移](../../sql/migrations/20261007_04_identity_contract.sql)。[统一执行器](../../scripts/apply-sql-migrations.py)校验基础表、显式选库、SHA-256迁移版本，已执行文件变化时拒绝。Docker/K3s已有库部署会在MySQL就绪后、新应用镜像上线前迁移。

新增教师political/phone/email、账号dept_name/bio、答卷快照和测评response_id/预警处置字段；新增教学科研记录、教师评价、学生素质记录、账号申请表。排名、统计、JSON展示不重复造列。不存在资料的值保持null或空记录。

新库额外示例学生分别绑定默认停用的独立账号，不能使用默认密码登录；开通必须设置新密码。已有库不重置口令、不移动学生记录、不增加user_id唯一约束、不重放默认种子。

## 验证与边界

独立MySQL测试库：完整初始化、旧库升级、再次迁移、记录数保留、有效旧角色绑定转换、权限后来编辑保留均通过。Java全reactor verify及覆盖率门通过（472项、零失败/错误/跳过）；前端113项回归与角色权限契约、AI启用生产构建、体积门与lint（0/0）通过；部署安全及迁移启动门38项通过。目录默认权限校正后的MySQL新库/升级/重复/冲突/强制失败回滚复验通过。生产三份增量迁移已通过；26表274列与297实体持久字段逐项匹配，新增字段类型/nullable和JDBC投影均通过，业务记录数量保留。10个变更应用镜像已更新为 `prod-contract-20261007-v2`；全栈17个Deployment和17个Pod均Ready。发布前数据库备份保留在服务器，原运行配置、文件系统层、字体、TLS与PVC已核对。

生产只读冒烟实际执行79项：76通过、0失败、2阻塞、1跳过。注销及刷新后的旧令牌拒绝、分页total、角色权限与新增字段读取通过。两项阻塞是 `student_questionnaire_self` 和 `student_history_self`：既有学生账号绑定多个档案，API业务码409且不返回混档资料。`respondent_template_omits_scoring` 因没有当前开放问卷跳过。覆盖未完整，不把阻塞/跳过算通过；本次业务写入、模型请求、Agent执行均为0。脱敏证据见生产验收JSON（本地测试产物，不入库：`./FIELD_CONTRACT_PRODUCTION_VALIDATION_2026_10_07.json`）和数据库字段比对（本地测试产物，不入库：`./SCHEMA_CONTRACT_VALIDATION_2026_10_07.json`）。

浏览器已确认短信、统一认证、扫码入口置灰，默认账号提示消失，使用说明弹窗正确显示且隐私入口有实际内容。外部SMS/SSO/扫码未配置，按用户要求置灰。

旧无题目快照答卷无法还原过去题干；不伪造回显。

既有重复student userId绑定需管理员核实后调整；自助返回409。

新增画像记录和个人联系字段不回填虚构资料。

真实模型端到端验收未运行；不重复使用一次性学生数据外发授权。

部署/回滚命令与证据见[RUNBOOK](../../RUNBOOK.md)。初始化脚本不能用于覆盖已有业务库。
