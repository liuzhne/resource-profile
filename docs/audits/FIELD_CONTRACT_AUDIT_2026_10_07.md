# 页面、接口与数据库契约审计

> 日期：2026-10-07 · 标识：FIELD-CONTRACT-AUDIT-20261007

本次覆盖全部 22 个路由视图（含404页）、7 个前端 API 模块，并核对生产 MySQL 的20张表、220列及相关实体/DTO/mapper/SQL。按独立问题组去重，共记录 **61 项**。例如同一详情路由问题涉及7页只计1项，评教三指标归为1项；期限、长期计划、资源、引用的独立结构错配分别列项。该数量是审计条目数，不是缺失字段数。

这些记录分为实际契约缺陷、已明确未开放的能力、权限响应的页面适配。它们不是全部“数据库缺列”：政治面貌等页面能力确实没有当前来源；学业明细、账号联系方式等已有来源却没有接入；其他问题出在参数、JSON结构、统计口径或安全投影。

本次完成检查与汇总，没有修改业务代码、部署、业务记录或数据库结构，没有调用模型，也没有读取真实师生行。生产只读查询 information_schema.COLUMNS 的表名、列名、类型和可空性。运行时问题按代码条件标注，不能把本报告当作每个生产页面都已实际复现。

## 1 汇总

| 分类 | 数量 | 含义 |
|---|---:|---|
| 契约缺陷或语义错配 | 42 | 已有页面/接口/处理链与实际来源、形态或规则不一致，包括潜伏且未被当前UI调用的API问题。 |
| 已声明未开放或能力缺口 | 18 | 显式占位、禁用或暂无来源的未交付能力；不当作保存成功或现网运行故障。 |
| 权限响应UI适配 | 1 | 合法脱敏后的空值被页面误解释；不是缺少数据库字段。 |

| 模块 | 问题组数 |
|---|---:|
| 面板、认证与通用入口 | 16 |
| 教师与学生画像 | 14 |
| 心理问卷与测评 | 14 |
| Agent报告、个人资料与管理 | 14 |
| MCP与数据库映射 | 3 |

优先级按影响建议排序：P1为安全边界、身份/角色或关键分析内容的完整性；P2为展示、计分、回显或操作契约；P3为较低影响的字典、潜伏参数和明确未开放能力。它不是已经发生事故的证明。

## 2 最接近政治面貌的来源缺口

| 页面能力 | 当前结论 | 清单项 |
|---|---|---|
| 教师政治面貌 | teacher_info、Teacher及教师详情返回均没有该项 | TS-G01 |
| 教师电话、邮箱 | sys_user 有账号联系方式；teacher API 无投影，页面固定为空，账号与教师身份关系需先定义 | TS-G02 |
| 教学成果、科研项目、教学评价及三项统计 | 缺记录/返回契约；研究方向文本不等于项目明细 | TS-G03～05 |
| 学生专业排名、竞赛/荣誉/志愿记录 | 排名未定义计算口径，综合素质记录没有来源 | TS-G06～07 |
| 教职工侧学生心理历史 | 数据表存在；没有供该详情页使用且权限适当的学生范围摘要查询 | TS-G08 |
| 个人部门、资料简介、权限 | deptName/permissions DTO不填；bio无持久/返回契约，自助保存未交付 | ROOT-D08、AP-02 |
| MCP心理类型、风险等级、总分、因子分 | 历史API已有部分信息但tool读取键错配；因子分本身无结构化来源 | SC-D01 |

已经存在而被页面误说为“没有来源”的两项应先接现有契约：学生课程成绩 TS-D01、本人账号邮箱/电话 AP-01。不应为它们重复造表。

## 3 完整清单

### 面板、认证与通用入口

| 编号 | 优先级 | 类型 | 问题 |
|---|---|---|---|
| ROOT-D01 | P2 | 契约缺陷或语义错配 | 面板“今日问卷完成”实际统计启用问卷模板数 |
| ROOT-D02 | P2 | 契约缺陷或语义错配 | “师生分布/在册总人数”实际统计登录账号 |
| ROOT-D03 | P3 | 契约缺陷或语义错配 | 本周/本月/全年标签对应最近7天/30天/12个月 |
| ROOT-D04 | P2 | 契约缺陷或语义错配 | 待处理事项展示固定的预警/科研/异常登录/作答事件 |
| ROOT-D05 | P2 | 契约缺陷或语义错配 | 最近登录表有读取接口但登录链不写入记录 |
| ROOT-D06 | P2 | 契约缺陷或语义错配 | remember 参数与“7天内免登录”未落实 |
| ROOT-D07 | P2 | 契约缺陷或语义错配 | refreshToken API helper 缺少后端必填请求头 |
| ROOT-D08 | P3 | 契约缺陷或语义错配 | userInfo DTO 的 deptName/permissions 从未填充 |
| ROOT-D09 | P3 | 契约缺陷或语义错配 | 用户列表 role 查询参数被接受但不生效 |
| ROOT-D10 | P3 | 契约缺陷或语义错配 | 用户菜单“系统设置”没有操作或反馈 |
| ROOT-G01 | P3 | 已声明未开放或能力缺口 | 短信、统一身份认证、扫码登录未交付 |
| ROOT-G02 | P3 | 已声明未开放或能力缺口 | 忘记密码与联系管理员开通没有流程 |
| ROOT-G03 | P3 | 已声明未开放或能力缺口 | 使用条款和隐私政策没有实际内容入口 |
| ROOT-G04 | P3 | 已声明未开放或能力缺口 | 全局搜索与通知没有服务入口 |
| ROOT-G05 | P3 | 已声明未开放或能力缺口 | 用户新增/编辑/删除/状态调整未在前端开放 |
| ROOT-G06 | P3 | 已声明未开放或能力缺口 | 心理概览“查看全部/处理”仅提示未开放 |

#### ROOT-D01 面板“今日问卷完成”实际统计启用问卷模板数

questionnaireCount 来自 mental_questionnaire，不是 mental_response，也没有今日时间条件。

建议：先统一“今日有效提交数”的去重、时区和时间范围，再查询答卷；或将现有卡片改名为启用问卷数。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/dashboard/index.vue:193](../../frontend/src/views/dashboard/index.vue#L193)（标签与 questionnaireCount）；[backend/data-service/src/main/resources/mapper/DashboardMapper.xml:8](../../backend/data-service/src/main/resources/mapper/DashboardMapper.xml#L8)（COUNT mental_questionnaire status=1）。

#### ROOT-D02 “师生分布/在册总人数”实际统计登录账号

图表读取 sys_user；教师/学生总数卡片读取 teacher_info/student_info。账号与人员档案并非一一对应，图表不能表达同一在册人口。

建议：明确该图是账号类型分布还是师生档案分布；按选定口径统一数据源、标签和去重规则。

边界：本次没有读取生产人员或账号行；基数不一致由不同数据源及非唯一 user_id 契约确认，不推断当前业务人数。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/dashboard/index.vue:75](../../frontend/src/views/dashboard/index.vue#L75)（在册总人数）；[backend/data-service/src/main/resources/mapper/DashboardMapper.xml:68](../../backend/data-service/src/main/resources/mapper/DashboardMapper.xml#L68)（selectUserDistribution 统计 sys_user）；[backend/data-service/src/main/resources/mapper/DashboardMapper.xml:6](../../backend/data-service/src/main/resources/mapper/DashboardMapper.xml#L6)（卡片统计档案表）。

#### ROOT-D03 本周/本月/全年标签对应最近7天/30天/12个月

滚动窗口与自然周、自然月、自然年不同；跨月/跨年时用户看到的期间并非标签所指。

建议：改用“近7天/近30天/近12个月”，或实现以业务时区计算的自然期间。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/dashboard/index.vue:160](../../frontend/src/views/dashboard/index.vue#L160)（三个时间段标签）；[backend/data-service/src/main/java/com/edu/data/service/impl/DashboardServiceImpl.java:32](../../backend/data-service/src/main/java/com/edu/data/service/impl/DashboardServiceImpl.java#L32)（period 切换 7/30/12）；[backend/data-service/src/main/java/com/edu/data/service/impl/DashboardServiceImpl.java:45](../../backend/data-service/src/main/java/com/edu/data/service/impl/DashboardServiceImpl.java#L45)（now minus days）；[backend/data-service/src/main/java/com/edu/data/service/impl/DashboardServiceImpl.java:72](../../backend/data-service/src/main/java/com/edu/data/service/impl/DashboardServiceImpl.java#L72)（now minus months）。

#### ROOT-D04 待处理事项展示固定的预警/科研/异常登录/作答事件

四条内容和“10分钟前”等时间为硬编码，没有接口或事件来源；页面未标注为演示，可被误认作实时事项。

建议：当前先去除或明确演示标签；交付真实待办后必须带来源ID、状态、时间及权限范围。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/dashboard/index.vue:129](../../frontend/src/views/dashboard/index.vue#L129)（以待处理事项展示）；[frontend/src/views/dashboard/index.vue:239](../../frontend/src/views/dashboard/index.vue#L239)（静态四条记录）。

#### ROOT-D05 最近登录表有读取接口但登录链不写入记录

sys_login_log 只有初始化 INSERT，当前后端登录只生成令牌及写 Redis；新的正常登录不会由本仓库链路产生最近登录记录。

建议：在认证成功/失败的明确位置记录最小登录审计，并处理IP可信代理和保留期限；不要拿种子记录代表线上实时日志。

边界：全 backend/sql 搜索只有表定义、种子写入、entity/mapper与读取SQL；不排除部署外部存在其他日志系统，但本应用没有接入其数据。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[backend/data-service/src/main/resources/mapper/DashboardMapper.xml:82](../../backend/data-service/src/main/resources/mapper/DashboardMapper.xml#L82)（读取 sys_login_log）；[backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java:48](../../backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java#L48)（完整 login 链无登录日志写入）；[sql/init/01_init.sql:239](../../sql/init/01_init.sql#L239)（初始化示例记录）。

#### ROOT-D06 remember 参数与“7天内免登录”未落实

登录 DTO 接收 remember，服务不读取；无论勾选与否都将 access token 存 localStorage，只有24小时访问会话，前端未使用 refreshToken。

建议：定义记住我和非记住我的存储/刷新策略；实现受控刷新、失效和撤销契约后再保留7天文案。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/login/index.vue:131](../../frontend/src/views/login/index.vue#L131)（7天文案）；[backend/auth-service/src/main/java/com/edu/auth/dto/LoginRequest.java:17](../../backend/auth-service/src/main/java/com/edu/auth/dto/LoginRequest.java#L17)（remember）；[backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java:73](../../backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java#L73)（令牌签发与24小时会话）；[frontend/src/store/modules/user.js:29](../../frontend/src/store/modules/user.js#L29)（始终 localStorage）；[frontend/src/store/modules/user.js:43](../../frontend/src/store/modules/user.js#L43)（只取 token）。

#### ROOT-D07 refreshToken API helper 缺少后端必填请求头

前端 POST /auth/refresh 没有参数/头；AuthController 必须读取 X-Refresh-Token，直接使用该 helper 会被拒绝。

建议：明确传入刷新令牌并按后端契约发送；配合上一项设计刷新令牌校验和撤销，不仅补一个头就宣称免登录完成。

边界：该 helper 当前没有调用方，是潜伏的接口错配；不声称当前页面频繁请求该接口。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/api/auth.js:25](../../frontend/src/api/auth.js#L25)（无参数 POST）；[backend/auth-service/src/main/java/com/edu/auth/controller/AuthController.java:71](../../backend/auth-service/src/main/java/com/edu/auth/controller/AuthController.java#L71)（必填 X-Refresh-Token）。

#### ROOT-D08 userInfo DTO 的 deptName/permissions 从未填充

UserInfoResponse 声明两字段，但 getUserInfo 只填账号字段和 roles。部门来源需定义；权限关联表存在但未聚合。

建议：收敛为已兑现的返回 DTO，或在明确身份归属/权限策略后补返回；当前路由和 v-permission 依 roles，不将空 permissions 等同鉴权失效。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[backend/auth-service/src/main/java/com/edu/auth/dto/UserInfoResponse.java:17](../../backend/auth-service/src/main/java/com/edu/auth/dto/UserInfoResponse.java#L17)（deptName/permissions）；[backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java:133](../../backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java#L133)（响应赋值列表未含二者）；[sql/init/01_init.sql:42](../../sql/init/01_init.sql#L42)（角色/权限关联表）。

#### ROOT-D09 用户列表 role 查询参数被接受但不生效

控制器接收 role 并传入 Service；查询只过滤 username/status，不使用 role。

建议：删除未交付参数，或按 sys_user_role/sys_role 明确定义角色ID/编码后实现真实过滤。

边界：当前用户页面已移除该筛选，不重复报告前轮已修复的分页total。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[backend/user-service/src/main/java/com/edu/user/controller/UserController.java:20](../../backend/user-service/src/main/java/com/edu/user/controller/UserController.java#L20)（接收 role）；[backend/user-service/src/main/java/com/edu/user/service/impl/UserServiceImpl.java:18](../../backend/user-service/src/main/java/com/edu/user/service/impl/UserServiceImpl.java#L18)（未使用 role 的 query）。

#### ROOT-D10 用户菜单“系统设置”没有操作或反馈

goToSettings 仅有注释，点击不会跳转、提交或提示。

建议：明确标注未开放或移除入口；若交付设置功能先定义API和作用域。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/components/Layout/Navbar.vue:35](../../frontend/src/components/Layout/Navbar.vue#L35)（菜单入口）；[frontend/src/components/Layout/Navbar.vue:79](../../frontend/src/components/Layout/Navbar.vue#L79)（空函数）。

#### ROOT-G01 短信、统一身份认证、扫码登录未交付

短信输入及获取按钮禁用，SSO/扫码只提示未开放；没有相应认证契约。

建议：保留时明确可用状态；规划独立认证能力后再接入。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/login/index.vue:111](../../frontend/src/views/login/index.vue#L111)（短信禁用）；[frontend/src/views/login/index.vue:151](../../frontend/src/views/login/index.vue#L151)（SSO/扫码 notReady）。

#### ROOT-G02 忘记密码与联系管理员开通没有流程

两处链接只弹未开放提示，没有密码恢复或管理员联系/开户入口。

建议：提供有效联系渠道；密码恢复需独立验证和审计，不能复用管理员任意改账号。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/login/index.vue:44](../../frontend/src/views/login/index.vue#L44)（联系管理员 notReady）；[frontend/src/views/login/index.vue:99](../../frontend/src/views/login/index.vue#L99)（忘记密码 notReady）。

#### ROOT-G03 使用条款和隐私政策没有实际内容入口

两个链接仅调用 notReady，无法查看相应文档。

建议：发布经过确认的实际政策文档并接链接。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/login/index.vue:35](../../frontend/src/views/login/index.vue#L35)（两个 notReady 链接）。

#### ROOT-G04 全局搜索与通知没有服务入口

两个图标只提示未开放，没有搜索结果或通知数据。

建议：标注能力状态；实现时各自定义数据范围和角色访问规则。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/components/Layout/Navbar.vue:16](../../frontend/src/components/Layout/Navbar.vue#L16)（搜索/通知占位）；[frontend/src/components/Layout/Navbar.vue:71](../../frontend/src/components/Layout/Navbar.vue#L71)（notReady）。

#### ROOT-G05 用户新增/编辑/删除/状态调整未在前端开放

用户页已接真实查询分页，但CRUD/状态控件禁用；后端存在管理员CRUD，frontend api/user.js只封装查询。

建议：如需交付管理写流程，先用受验证的请求DTO明确密码、角色、状态更新规则，再接管理操作。

边界：本次作为能力清单，不推翻前轮只读交付决策，也不把按钮禁用误报为提交成功。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/admin/users.vue:31](../../frontend/src/views/admin/users.vue#L31)（只读说明）；[frontend/src/views/admin/users.vue:74](../../frontend/src/views/admin/users.vue#L74)（禁用状态/编辑/删除）；[frontend/src/api/user.js:1](../../frontend/src/api/user.js#L1)（仅查询helper）；[backend/user-service/src/main/java/com/edu/user/controller/UserController.java:45](../../backend/user-service/src/main/java/com/edu/user/controller/UserController.java#L45)（管理员CRUD）。

#### ROOT-G06 心理概览“查看全部/处理”仅提示未开放

概览行动入口没有完整预警处置流程。

建议：明确所指是量表预警还是Agent任务，定义处置状态再接真实流程。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/mental/overview.vue:169](../../frontend/src/views/mental/overview.vue#L169)（notReady）。

### 教师与学生画像

| 编号 | 优先级 | 类型 | 问题 |
|---|---|---|---|
| TS-D01 | P2 | 契约缺陷或语义错配 | 学生详情课程成绩未接已有 API，页面声称成绩表/接口缺失 |
| TS-D02 | P2 | 契约缺陷或语义错配 | 学生列表将缺失/脱敏 GPA 的 null 显示为真实 0.00 |
| TS-D03 | P2 | 契约缺陷或语义错配 | 学生详情将合法零 GPA 隐藏为 — |
| TS-C01 | P3 | 契约缺陷或语义错配 | 教师/学生列表筛选字典写死，无法覆盖数据库允许的新取值 |
| TS-G01 | P3 | 已声明未开放或能力缺口 | 教师政治面貌缺少持久字段及返回契约 |
| TS-G02 | P3 | 已声明未开放或能力缺口 | 教师详情电话/邮箱没有教师 API 投影，前端固定占位 |
| TS-G03 | P3 | 已声明未开放或能力缺口 | 教师教学成果标签缺少记录表及 API |
| TS-G04 | P3 | 已声明未开放或能力缺口 | 教师科研项目标签缺少项目记录表及 API |
| TS-G05 | P3 | 已声明未开放或能力缺口 | 教师综合评分/评价次数/满意度及评教标签缺少来源 |
| TS-G06 | P3 | 已声明未开放或能力缺口 | 学生专业排名固定占位且未定义计算/返回契约 |
| TS-G07 | P3 | 已声明未开放或能力缺口 | 学生综合素质：竞赛/荣誉/志愿服务记录缺少来源 |
| TS-G08 | P3 | 已声明未开放或能力缺口 | 学生详情教职工侧心理历史没有学生范围查询契约 |
| TS-G09 | P3 | 已声明未开放或能力缺口 | 教师新增/编辑前端尚未连接已有写接口 |
| TS-G10 | P3 | 已声明未开放或能力缺口 | 学生新增/编辑前端尚未连接已有写接口 |

#### TS-D01 学生详情课程成绩未接已有 API，页面声称成绩表/接口缺失

教师/管理员查看学生详情，学业成绩签永远显示占位，已有课程成绩不会展示。

当前来源：GET /student/{id}/academic -> List<AcademicRecord>；student_academic_record

建议：封装并接入现有 academic API，展示课程/学期/学分/成绩/绩点；删除过期无接口提示。

边界：该接口允许本人或 STAFF_VIEW，成绩字段不是由角色脱敏导致消失；仅做源码审计，没有读取生产学生行。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student/detail.vue:88](../../frontend/src/views/student/detail.vue#L88)；[frontend/src/views/student/detail.vue:132](../../frontend/src/views/student/detail.vue#L132)；[frontend/src/views/student/detail.vue:155](../../frontend/src/views/student/detail.vue#L155)；[frontend/src/api/student.js:1](../../frontend/src/api/student.js#L1)；[backend/student-service/src/main/java/com/edu/student/controller/StudentAcademicController.java:28](../../backend/student-service/src/main/java/com/edu/student/controller/StudentAcademicController.java#L28)；[backend/student-service/src/main/java/com/edu/student/service/impl/AcademicRecordServiceImpl.java:20](../../backend/student-service/src/main/java/com/edu/student/service/impl/AcademicRecordServiceImpl.java#L20)；[backend/student-service/src/main/java/com/edu/student/entity/AcademicRecord.java:19](../../backend/student-service/src/main/java/com/edu/student/entity/AcademicRecord.java#L19)；[sql/init/04_student_extras.sql:9](../../sql/init/04_student_extras.sql#L9)。

#### TS-D02 学生列表将缺失/脱敏 GPA 的 null 显示为真实 0.00

字段为 null 时仍显示 0.00，容易把未记录或不可见的成绩误判为零绩点。

当前来源：GET /student/list -> Page<Student>.records[].gpa；student_info.gpa

建议：先判 null/undefined/空字符串，再格式化数值；不可见和零值分别展示。

边界：合成值本地复现 Number(null)=0；不是把角色正常脱敏本身归为 bug。

验证：源码表达式与虚构 null/0 值离线复核。

证据：[frontend/src/views/student/list.vue:78](../../frontend/src/views/student/list.vue#L78)；[frontend/src/views/student/list.vue:205](../../frontend/src/views/student/list.vue#L205)；[backend/student-service/src/main/java/com/edu/student/entity/Student.java:56](../../backend/student-service/src/main/java/com/edu/student/entity/Student.java#L56)；[sql/init/01_init.sql:115](../../sql/init/01_init.sql#L115)；[backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java:27](../../backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java#L27)。

#### TS-D03 学生详情将合法零 GPA 隐藏为 —

真实 gpa=0 时详情不显示 0.00，且同一值列表显示 0.00、详情显示 —。

当前来源：GET /student/{id} -> Student.gpa；student_info.gpa

建议：使用显式空值判定；合法 0 显示 0.00，未知值显示明确空态。

边界：合成值本地复现；实际无成绩的默认 0 与已获得零绩点尚无单独状态口径，不能把所有种子 0 认作真实成绩。

验证：源码表达式与虚构 null/0 值离线复核。

证据：[frontend/src/views/student/detail.vue:30](../../frontend/src/views/student/detail.vue#L30)；[frontend/src/views/student/detail.vue:169](../../frontend/src/views/student/detail.vue#L169)；[backend/student-service/src/main/java/com/edu/student/entity/Student.java:56](../../backend/student-service/src/main/java/com/edu/student/entity/Student.java#L56)；[sql/init/01_init.sql:115](../../sql/init/01_init.sql#L115)。

#### TS-C01 教师/学生列表筛选字典写死，无法覆盖数据库允许的新取值

年级只能选 2020–2024（不能选 2025/2026）；教师学院只有 4 项、职称只有 3 项，其他合法取值无法通过该筛选控件查询。

当前来源：GET /teacher/list?dept=&title= ; GET /student/list?dept=&grade=；teacher_info.dept_name/title ; student_info.dept_name/grade

建议：用真实字典/后端取值接口提供筛选选项；年级范围避免固定在2024年。

边界：后端确实执行这些筛选，不能说筛选参数无效；仅证明选项范围有限，不声称当前生产存在额外年级/学院/职称。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/teacher/list.vue:22](../../frontend/src/views/teacher/list.vue#L22)；[frontend/src/views/teacher/list.vue:109](../../frontend/src/views/teacher/list.vue#L109)；[frontend/src/views/student/list.vue:22](../../frontend/src/views/student/list.vue#L22)；[frontend/src/views/student/list.vue:124](../../frontend/src/views/student/list.vue#L124)；[backend/teacher-service/src/main/java/com/edu/teacher/service/impl/TeacherServiceImpl.java:26](../../backend/teacher-service/src/main/java/com/edu/teacher/service/impl/TeacherServiceImpl.java#L26)；[backend/student-service/src/main/java/com/edu/student/service/impl/StudentServiceImpl.java:29](../../backend/student-service/src/main/java/com/edu/student/service/impl/StudentServiceImpl.java#L29)；[sql/init/01_init.sql:85](../../sql/init/01_init.sql#L85)；[sql/init/01_init.sql:108](../../sql/init/01_init.sql#L108)。

#### TS-G01 教师政治面貌缺少持久字段及返回契约

政治面貌始终 —，无法从现有教师接口查看或保存。

当前来源：GET /teacher/{id} -> Teacher（不含 political）；teacher_info（无政治面貌列）

建议：先决定是否需要采集政治面貌及权限；需要时同步迁移、DTO、接口和UI，否则移除字段。

边界：用户给出的例子；页面已明确声明占位，属于已知能力缺口而非新发现运行时错误。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/teacher/detail.vue:89](../../frontend/src/views/teacher/detail.vue#L89)；[frontend/src/views/teacher/detail.vue:194](../../frontend/src/views/teacher/detail.vue#L194)；[frontend/src/views/teacher/detail.vue:235](../../frontend/src/views/teacher/detail.vue#L235)；[backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java:13](../../backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java#L13)；[backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java:35](../../backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java#L35)；[sql/init/01_init.sql:77](../../sql/init/01_init.sql#L77)。

#### TS-G02 教师详情电话/邮箱没有教师 API 投影，前端固定占位

即使相关账号有联系方式，教师详情也不读取，两个字段始终 —。

当前来源：GET /teacher/{id} -> Teacher（不含 phone/email）；GET /user/{id} -> User（仅 ADMIN）；teacher_info.user_id -> sys_user.id；sys_user.phone/email

建议：先定义教师身份与账号联系方式的关系、唯一性和可见角色，再由教师返回 DTO 投影；不得直接复用管理员用户查询。

边界：并非全系统无电话/邮箱。它们是账号级 HIGH 字段，候选关联来源不等于每位教师个人联系方式：公开种子三位教师共用 user_id=2；不可未经业务/权限确认直接复用管理员 user API。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/teacher/detail.vue:54](../../frontend/src/views/teacher/detail.vue#L54)；[frontend/src/views/teacher/detail.vue:235](../../frontend/src/views/teacher/detail.vue#L235)；[backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java:18](../../backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java#L18)；[backend/teacher-service/src/main/java/com/edu/teacher/service/impl/TeacherServiceImpl.java:38](../../backend/teacher-service/src/main/java/com/edu/teacher/service/impl/TeacherServiceImpl.java#L38)；[sql/init/01_init.sql:18](../../sql/init/01_init.sql#L18)；[backend/user-service/src/main/java/com/edu/user/controller/UserController.java:35](../../backend/user-service/src/main/java/com/edu/user/controller/UserController.java#L35)；[backend/user-service/src/main/java/com/edu/user/entity/User.java:27](../../backend/user-service/src/main/java/com/edu/user/entity/User.java#L27)；[sql/init/01_init.sql:213](../../sql/init/01_init.sql#L213)。

#### TS-G03 教师教学成果标签缺少记录表及 API

教学成果标签无法展示成果记录。

当前来源：GET /teacher/{id} -> Teacher（只有档案字段）；teacher_info；现有 SQL 无教师教学成果记录表

建议：定义教学成果记录及查询契约，或保持清晰未交付状态。

边界：显式占位；不能把 teacher_info 内研究方向/学历当成果明细。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/teacher/detail.vue:95](../../frontend/src/views/teacher/detail.vue#L95)；[backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java:13](../../backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java#L13)；[backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java:20](../../backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java#L20)；[sql/init/01_init.sql:77](../../sql/init/01_init.sql#L77)。

#### TS-G04 教师科研项目标签缺少项目记录表及 API

科研项目标签始终无项目数据。

当前来源：GET /teacher/{id}.researchArea 只为研究方向文本，没有项目列表；teacher_info.research_area；现有 SQL 无科研项目记录表

建议：定义项目实体与教师关联/查询，不把 researchArea 文本当项目列表。

边界：researchArea 存在且在基本信息中正常展示，不能误报为 researchArea 字段缺失；缺的是项目明细能力。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/teacher/detail.vue:107](../../frontend/src/views/teacher/detail.vue#L107)；[backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java:47](../../backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java#L47)；[backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java:20](../../backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java#L20)；[sql/init/01_init.sql:90](../../sql/init/01_init.sql#L90)。

#### TS-G05 教师综合评分/评价次数/满意度及评教标签缺少来源

三项统计永远 —，教学评价标签也无法展示真实评价。

当前来源：GET /teacher/{id} -> Teacher（无这 3 项）；teacher_info；现有 SQL 无评教记录表

建议：定义评价记录、评分/满意度算法、统计期间和来源后再交付评教模块。

边界：同一评教能力聚为 1 项问题，避免将 3 个展示字段和标签重复累计。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/teacher/detail.vue:28](../../frontend/src/views/teacher/detail.vue#L28)；[frontend/src/views/teacher/detail.vue:116](../../frontend/src/views/teacher/detail.vue#L116)；[frontend/src/views/teacher/detail.vue:239](../../frontend/src/views/teacher/detail.vue#L239)；[backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java:13](../../backend/teacher-service/src/main/java/com/edu/teacher/entity/Teacher.java#L13)；[sql/init/01_init.sql:77](../../sql/init/01_init.sql#L77)。

#### TS-G06 学生专业排名固定占位且未定义计算/返回契约

专业排名永远 —。

当前来源：GET /student/{id} -> Student（无排名字段）；student_info（只有 gpa/major_name 等；无排名字段）

建议：先定义专业/年级/学期与同分排名规则，再提供派生统计；未必需要增加排名列。

边界：排名可设计成派生数据，不代表必需新加数据库列；当前服务没有排名计算，也未定义年级/专业/学期排名口径。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student/detail.vue:33](../../frontend/src/views/student/detail.vue#L33)；[backend/student-service/src/main/java/com/edu/student/entity/Student.java:14](../../backend/student-service/src/main/java/com/edu/student/entity/Student.java#L14)；[backend/student-service/src/main/java/com/edu/student/service/impl/StudentServiceImpl.java:40](../../backend/student-service/src/main/java/com/edu/student/service/impl/StudentServiceImpl.java#L40)；[sql/init/01_init.sql:100](../../sql/init/01_init.sql#L100)。

#### TS-G07 学生综合素质：竞赛/荣誉/志愿服务记录缺少来源

综合素质标签只显示无来源说明，无法展示三类记录。

当前来源：GET /student/{id} -> Student（无相关列表）；student_info；现有 SQL 无对应素质记录表

建议：定义竞赛/荣誉/志愿服务的记录与证据来源，或移除未交付标签。

边界：同一综合素质模块聚为 1 项；不把每个示例记录类型拆成重复问题。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student/detail.vue:99](../../frontend/src/views/student/detail.vue#L99)；[backend/student-service/src/main/java/com/edu/student/entity/Student.java:14](../../backend/student-service/src/main/java/com/edu/student/entity/Student.java#L14)；[backend/student-service/src/main/java/com/edu/student/controller/StudentController.java:27](../../backend/student-service/src/main/java/com/edu/student/controller/StudentController.java#L27)；[sql/init/01_init.sql:100](../../sql/init/01_init.sql#L100)。

#### TS-G08 学生详情教职工侧心理历史没有学生范围查询契约

教职工在学生详情无法查看该生心理历史，标签固定说明不展示。

当前来源：GET /mental/student/assessments?userId= 只允许本人或内部；管理 API 为聚合/问卷完成列表；mental_assessment / mental_response 已存在

建议：如需教职工查看历史，新增按学生档案授权的摘要 DTO 与端点；保持本人接口鉴权及心理脱敏。

边界：数据库有心理数据，不能说不存在数据表；现有本人接口是明确安全边界，不应放开为任意教师读他人原始测评。需要另行定义允许字段/角色的管理契约，不构成当前鉴权 bug。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student/detail.vue:110](../../frontend/src/views/student/detail.vue#L110)；[backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java:28](../../backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java#L28)；[backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java:71](../../backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java#L71)；[backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java:47](../../backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java#L47)；[backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java:216](../../backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java#L216)。

#### TS-G09 教师新增/编辑前端尚未连接已有写接口

点击新增或编辑只提示该功能暂未开放，不能保存教师档案。

当前来源：POST /teacher ; PUT /teacher/{id}（ADMIN）；teacher_info

建议：先核对后端写入 DTO/身份关联及字段验证，再开放教师新增/编辑；当前明确提示未开放。

边界：明确未开放的操作，不宣称有保存成功后数据丢失。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/teacher/list.vue:10](../../frontend/src/views/teacher/list.vue#L10)；[frontend/src/views/teacher/list.vue:75](../../frontend/src/views/teacher/list.vue#L75)；[frontend/src/views/teacher/list.vue:175](../../frontend/src/views/teacher/list.vue#L175)；[frontend/src/api/teacher.js:11](../../frontend/src/api/teacher.js#L11)；[backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java:45](../../backend/teacher-service/src/main/java/com/edu/teacher/controller/TeacherController.java#L45)。

#### TS-G10 学生新增/编辑前端尚未连接已有写接口

点击新增或编辑只提示该功能暂未开放，不能保存学生档案。

当前来源：POST /student ; PUT /student/{id}（STUDENT_WRITE）；student_info

建议：先核对后端写入 DTO/身份关联及字段验证，再开放学生新增/编辑；当前明确提示未开放。

边界：明确未开放的操作，不宣称有保存成功后数据丢失。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student/list.vue:10](../../frontend/src/views/student/list.vue#L10)；[frontend/src/views/student/list.vue:90](../../frontend/src/views/student/list.vue#L90)；[frontend/src/views/student/list.vue:201](../../frontend/src/views/student/list.vue#L201)；[frontend/src/api/student.js:11](../../frontend/src/api/student.js#L11)；[backend/student-service/src/main/java/com/edu/student/controller/StudentController.java:60](../../backend/student-service/src/main/java/com/edu/student/controller/StudentController.java#L60)。

### 心理问卷与测评

| 编号 | 优先级 | 类型 | 问题 |
|---|---|---|---|
| MENTAL-01 | P2 | 契约缺陷或语义错配 | “今日完成问卷”实际是最近 30 天评估记录数 |
| MENTAL-02 | P2 | 契约缺陷或语义错配 | “各学院心理状况分布”丢失学院维度 |
| MENTAL-03 | P2 | 契约缺陷或语义错配 | 心理分析“导出报告”按钮没有实现 |
| MENTAL-04 | P2 | 契约缺陷或语义错配 | 题目数可任意填写，不等于实际题库数量 |
| MENTAL-05 | P1 | 契约缺陷或语义错配 | 数据库支持量表字段，设计页、作答和计分没有实现量表契约 |
| MENTAL-06 | P1 | 契约缺陷或语义错配 | scoringRules 有字段、有写入，但当前提交计分不读取 |
| MENTAL-07 | P2 | 已声明未开放或能力缺口 | Excel 题目导入只存在 Service，没有 API 或前端入口 |
| MENTAL-08 | P1 | 契约缺陷或语义错配 | required 字段只在浏览器校验；提交 API 允许缺题、重复题或选项 |
| MENTAL-09 | P1 | 契约缺陷或语义错配 | 历史“我的作答”使用当前题目及选项，缺少提交时快照 |
| MENTAL-10 | P2 | 契约缺陷或语义错配 | 停用或删除问卷后，已有结果不能回显题目与作答 |
| MENTAL-11 | P1 | 契约缺陷或语义错配 | 本人结果详情返回未裁剪的 questionnaire.levelRules |
| MENTAL-12 | P2 | 契约缺陷或语义错配 | 完成情况按 rows.length 计人数，一条答卷可被多条评估扩成多行 |
| MENTAL-13 | P2 | 契约缺陷或语义错配 | 编辑问卷清空开始/结束日期，无法保存为不限日期 |
| MENTAL-PERM-01 | P2 | 权限响应UI适配 | 学生历史/结果直接展示被权限层置 null 的分数、等级、建议 |

#### MENTAL-01 “今日完成问卷”实际是最近 30 天评估记录数

今日无提交但此前 30 天有记录时仍显示完成数；其来源是 mental_assessment，而不是今日提交的 mental_response。

建议：统一今日提交/今日评估的定义、业务时区和去重方式，再修 SQL 或卡片名称。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/mental/overview.vue:148](../../frontend/src/views/mental/overview.vue#L148)（卡片标签“今日完成问卷”，读取 overviewData.todayCompleted）；[frontend/src/api/mental.js:4](../../frontend/src/api/mental.js#L4)（GET /mental/overview）；[backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java:55](../../backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java#L55)（sum selectRecentStats count，写入 todayCompleted）；[backend/mental-service/src/main/java/com/edu/mental/mapper/MentalAssessmentMapper.java:14](../../backend/mental-service/src/main/java/com/edu/mental/mapper/MentalAssessmentMapper.java#L14)（mental_assessment WHERE create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)）。

#### MENTAL-02 “各学院心理状况分布”丢失学院维度

后端按 dept 分组，但前端把所有学院 good/attention/intervention 求和成全校单个饼图，无法比较各学院。

建议：保留 dept 维度绘制各学院图；若只展示全校统计则改名。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/mental/analysis.vue:13](../../frontend/src/views/mental/analysis.vue#L13)（标题各学院心理状况分布）；[frontend/src/views/mental/analysis.vue:63](../../frontend/src/views/mental/analysis.vue#L63)（只累加 item.good/attention/intervention；不用 item.dept）；[backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java:87](../../backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java#L87)（deptDistribution）；[backend/mental-service/src/main/java/com/edu/mental/mapper/MentalAssessmentMapper.java:34](../../backend/mental-service/src/main/java/com/edu/mental/mapper/MentalAssessmentMapper.java#L34)（SELECT si.dept_name as dept ... GROUP BY si.dept_name）。

#### MENTAL-03 心理分析“导出报告”按钮没有实现

按钮可点击但没有事件、下载或接口调用；mental 控制器和 api/mental.js 无心理分析导出契约。

建议：明确未开放或移除按钮；交付导出时定义授权、格式及下载接口。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/mental/analysis.vue:7](../../frontend/src/views/mental/analysis.vue#L7)（el-button 只有 type，无 @click/disabled）；[frontend/src/api/mental.js:3](../../frontend/src/api/mental.js#L3)（仅分析 GET 与问卷/作答接口，无导出函数）；[backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java:79](../../backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java#L79)（analysis 仅返回 JSON，控制器无 export 路由）。

#### MENTAL-04 题目数可任意填写，不等于实际题库数量

管理和学生列表显示 questions 字段；新建/编辑手工题数不生成题目。默认种子显示 20/15/15 题，但初始化未插入任何 mental_question。题目增删才同步真实 count，随后编辑元数据仍可覆盖。

建议：题数由活跃题目集合派生，禁止元数据任意覆盖；发布前校验题库非空。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/mental/questionnaire.vue:19](../../frontend/src/views/mental/questionnaire.vue#L19)（显示 prop=questions）；[frontend/src/views/mental/questionnaire.vue:70](../../frontend/src/views/mental/questionnaire.vue#L70)（手工编辑 formData.questions）；[frontend/src/views/student-mental/list.vue:27](../../frontend/src/views/student-mental/list.vue#L27)（共 item.questions 题）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java:45](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java#L45)（直接 insert/updateById 元数据，不生成或校验题目）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java:145](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java#L145)（仅题目增删批量流程调用 syncQuestionCount）；[sql/init/01_init.sql:226](../../sql/init/01_init.sql#L226)（种子 questions=20/15/15；所有 init SQL 无 mental_question INSERT）。

#### MENTAL-05 数据库支持量表字段，设计页、作答和计分没有实现量表契约

Question 有 scaleMin/scaleMax/scaleLabels，设计页无量表创建选项及字段；已有 scale 编辑仍走普通选择题编辑，作答只输入无范围校验的字符串，后端无 scale 分支且 optionIndices 为空，结果计 0 分。

建议：统一量表的数值、范围、标签、必答与计分 DTO，贯通设计/作答/服务器计算。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[sql/init/01_init.sql:163](../../sql/init/01_init.sql#L163)（question_type 含 scale；scale_min/max/labels 已建列）；[backend/mental-service/src/main/java/com/edu/mental/entity/Question.java:32](../../backend/mental-service/src/main/java/com/edu/mental/entity/Question.java#L32)（三个 scale 字段映射）；[frontend/src/views/mental/questionnaire-design.vue:52](../../frontend/src/views/mental/questionnaire-design.vue#L52)（只提供单选/多选/简答）；[frontend/src/views/mental/questionnaire-design.vue:69](../../frontend/src/views/mental/questionnaire-design.vue#L69)（所有非 text 走 optionList，不用 scale 参数）；[frontend/src/views/student-mental/take.vue:65](../../frontend/src/views/student-mental/take.vue#L65)（量表按 text 回退处理，仅展示不计分）；[frontend/src/views/student-mental/take.vue:155](../../frontend/src/views/student-mental/take.vue#L155)（只有单选/多选构造 optionIndices，量表发 text）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:197](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L197)（除 text 外按 optionIndices 计分；为空就 0）。

#### MENTAL-06 scoringRules 有字段、有写入，但当前提交计分不读取

Excel 服务写 options=[{label,value}] 与 scoringRules={A:score}；活跃提交链仅读取 options[].score。该格式选择题将全部计 0 分；设计页读取无 score 的选项也显示 0，编辑会丢弃 value。另一个读取 scoringRules 的旧 service 没有控制器调用，不能证明当前链已支持。

建议：选定并版本化一种计分结构，迁移/兼容旧题；未识别格式拒绝发布，不能静默当零分。

边界：当前 UI 创建的 options[].score 格式可计分；不能误报所有单选题失效。Excel 导入当前不可达，影响也涵盖任何经题目 CRUD 写入既有 scoringRules 格式的题目。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[sql/init/01_init.sql:164](../../sql/init/01_init.sql#L164)（options 与 scoring_rules JSON 列）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java:115](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java#L115)（options 仅 label/value；分值进 scoringRules）；[frontend/src/views/mental/questionnaire-design.vue:209](../../frontend/src/views/mental/questionnaire-design.vue#L209)（把 o.score 缺失归为 0；只留下 label/score）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:212](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L212)（只用 opts.get(idx).get(score)，从未 getScoringRules）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireResponseServiceImpl.java:50](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireResponseServiceImpl.java#L50)（另一服务读取 scoringRules；全代码扫描无控制器引用该服务）。

#### MENTAL-07 Excel 题目导入只存在 Service，没有 API 或前端入口

importQuestionsFromExcel 支持题目、计分规则、量表参数解析，但没有控制器映射、api 函数、管理页上传入口；不算已交付给用户的导入功能。

建议：如保留导入能力，先解决计分结构并提供授权上传入口、校验预览和回滚；否则标记为未交付。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[backend/mental-service/src/main/java/com/edu/mental/service/QuestionnaireService.java:19](../../backend/mental-service/src/main/java/com/edu/mental/service/QuestionnaireService.java#L19)（接口声明 importQuestionsFromExcel）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java:61](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java#L61)（完整导入实现）；[frontend/src/api/mental.js:1](../../frontend/src/api/mental.js#L1)（无 multipart/import endpoint）；[frontend/src/views/mental/questionnaire-design.vue:13](../../frontend/src/views/mental/questionnaire-design.vue#L13)（操作仅编辑等级规则/新增题目）；[backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java:94](../../backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java#L94)（CRUD 路由无 MultipartFile/import）。

#### MENTAL-08 required 字段只在浏览器校验；提交 API 允许缺题、重复题或选项

前端声明必答，后端未检查必答覆盖、题目归属错误直接 continue，同一 questionId 或 multiple_choice 同一索引重复可多次累积分数。API 契约未落实题目 required 和集合唯一性，造成空/畸形答案仍持久化并评级。

建议：服务器校验必答覆盖、题目归属、题目/选项唯一性及单选数量；非法输入拒绝且不得落库。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student-mental/take.vue:127](../../frontend/src/views/student-mental/take.vue#L127)（仅客户端校验 required）；[backend/mental-service/src/main/java/com/edu/mental/dto/SubmitAnswerRequest.java:8](../../backend/mental-service/src/main/java/com/edu/mental/dto/SubmitAnswerRequest.java#L8)（DTO 无 @Valid/约束）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:113](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L113)（遍历提交项计分；不存在的 questionId continue；不按完整题集校验 required）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:212](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L212)（重复 index 重复累加；single 多选也只取 first，不拒绝）；[sql/init/01_init.sql:169](../../sql/init/01_init.sql#L169)（required 真实存在）。

#### MENTAL-09 历史“我的作答”使用当前题目及选项，缺少提交时快照

答案仅存 questionId/optionIndices/text；结果页再请求当前题库。具有 MENTAL_WRITE 权限的管理员/心理咨询师可修改/删除题目及选项，旧索引会映射到新的文字，历史回显可失真或消失。

建议：保存题库版本或提交时题干/选项快照；旧答案从同一版本回显。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student-mental/result.vue:71](../../frontend/src/views/student-mental/result.vue#L71)（当前 opts[idx].label 与历史 optionIndices 拼接）；[frontend/src/views/student-mental/result.vue:109](../../frontend/src/views/student-mental/result.vue#L109)（取 assessment detail 后再次 GET 当前 respondent template）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:133](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L133)（原样序列化 answers，不写题干/选项快照）；[backend/mental-service/src/main/java/com/edu/mental/dto/SubmitAnswerRequest.java:15](../../backend/mental-service/src/main/java/com/edu/mental/dto/SubmitAnswerRequest.java#L15)（答案 DTO 无版本/题干/标签）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java:97](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java#L97)（允许修改/删除题目，无已提交保护）；[sql/init/01_init.sql:177](../../sql/init/01_init.sql#L177)（mental_response 无题库版本或内容快照列）。

#### MENTAL-10 停用或删除问卷后，已有结果不能回显题目与作答

结果页面复用当前作答模板；状态改为 0 时该接口返回 403；逻辑删除后问卷为空/不存在。历史 assessment/answers 仍在，但界面没能独立回显。

建议：历史结果读取与当前开放作答状态解耦，并以历史快照展示已提交内容。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student-mental/result.vue:109](../../frontend/src/views/student-mental/result.vue#L109)（当前 questionnaire 有 id 才继续模板请求；失败仅 console.error）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java:62](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java#L62)（0/null 状态拒绝 respondent template）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:170](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L170)（按当前问卷 selectById，答案与结果仍返回）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java:50](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java#L50)（允许更新状态与逻辑删除）。

#### MENTAL-11 本人结果详情返回未裁剪的 questionnaire.levelRules

学生模板接口明确剥离等级阈值，但本人结果接口直接返回完整 Questionnaire；levelRules 没有 @SensitiveField，会被序列化。页面虽不展示，该客户端响应包含原定不向作答者提供的计分阈值。

建议：本人结果使用专用返回 DTO，裁剪未授权计分阈值；保持模板和结果安全契约一致。

边界：这是客户端响应安全契约问题，不是数据库漏字段。只读代码确认；未提取生产学生资料。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/api/mental.js:38](../../frontend/src/api/mental.js#L38)（本人详情 GET /mental/student/assessments/{id}）；[backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java:81](../../backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java#L81)（本人授权通过后 getMyAssessmentDetail）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:170](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L170)（out.put(questionnaire,q)，无 levelRules 裁剪）；[backend/mental-service/src/main/java/com/edu/mental/entity/Questionnaire.java:32](../../backend/mental-service/src/main/java/com/edu/mental/entity/Questionnaire.java#L32)（levelRules 映射，未标敏感字段）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java:69](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java#L69)（作答模板明确 setLevelRules(null)；作为对照）。

#### MENTAL-12 完成情况按 rows.length 计人数，一条答卷可被多条评估扩成多行

mental_response 对 student/questionnaire 唯一；mental_assessment 没有唯一约束，completion 按这两列 LEFT JOIN。种子已有评估但无答卷，后续合法提交会另 insert 新评估，导致一条答卷两行、重复姓名/不同等级、人数重复，平均分可能被重复权重扭曲。

建议：用答卷/评估明确关联或确定唯一最新评估，按提交实体计人数；先核对存量重复再迁移。

边界：唯一约束依据初始化DDL；本次information_schema.COLUMNS不含索引，未核验生产实际索引或重复记录。触发样例需在问卷开放时间内，或用隔离夹具设置可提交状态；本次未提交答卷。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/mental/questionnaire-result.vue:11](../../frontend/src/views/mental/questionnaire-result.vue#L11)（rows.length 显示已完成人数）；[backend/mental-service/src/main/java/com/edu/mental/mapper/MentalResponseMapper.java:14](../../backend/mental-service/src/main/java/com/edu/mental/mapper/MentalResponseMapper.java#L14)（LEFT JOIN mental_assessment ON student_id + questionnaire_id，无选最新/唯一）；[sql/init/01_init.sql:177](../../sql/init/01_init.sql#L177)（response 唯一；assessment 无唯一）；[sql/init/01_init.sql:232](../../sql/init/01_init.sql#L232)（已有 questionnaire=1 的评估种子；初始化无 mental_response INSERT）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:105](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L105)（重复只查 response）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:140](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L140)（总是 insert 新 assessment）。

#### MENTAL-13 编辑问卷清空开始/结束日期，无法保存为不限日期

DatePicker 可清空为 null，但 Questionnaire 日期字段未设置 ALWAYS 更新策略、全局无覆盖；updateById 采用 MyBatis-Plus 3.5.11 默认 NOT_NULL，会跳过 null 日期，接口仍返回成功而数据库保留原日期。

建议：采用显式更新 DTO 区分“不改”和“清空”，只对允许清空字段设置正确更新策略。

边界：已只读 javap 核对本地缓存 mybatis-plus-core-3.5.11 GlobalConfig$DbConfig：updateStrategy=FieldStrategy.NOT_NULL；未修改生产日期。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/mental/questionnaire.vue:73](../../frontend/src/views/mental/questionnaire.vue#L73)（DatePicker 默认为 clearable；v-model dates）；[frontend/src/views/mental/questionnaire.vue:240](../../frontend/src/views/mental/questionnaire.vue#L240)（clone formData 调用 update）；[backend/mental-service/src/main/java/com/edu/mental/entity/Questionnaire.java:24](../../backend/mental-service/src/main/java/com/edu/mental/entity/Questionnaire.java#L24)（日期 TableField 仅列名，无 updateStrategy）；[backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java:50](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionnaireServiceImpl.java#L50)（updateById）；[backend/mental-service/src/main/resources/application.yml:51](../../backend/mental-service/src/main/resources/application.yml#L51)（全局无 update-strategy 覆盖）。

#### MENTAL-PERM-01 学生历史/结果直接展示被权限层置 null 的分数、等级、建议

学生界面总分/评级为空；建议被置 null 却显示“无建议”，混淆不允许看与无数据。FieldPermissionAdvice 当前还不放行 student 的 MEDIUM，因此 level 也会空。

建议：UI区分不可见、未测评与真实零值；如需本人摘要，另定义审核后的摘要权限，不关闭脱敏。

边界：字段在 DB/实体/API 都存在；排除于“缺字段”统计。可作为 UI 兼容及本人摘要策略另列；绝不可建议直接关闭脱敏。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/student-mental/history.vue:15](../../frontend/src/views/student-mental/history.vue#L15)（score/level/suggestion 未按权限隐藏或标注）；[frontend/src/views/student-mental/result.vue:11](../../frontend/src/views/student-mental/result.vue#L11)（score/level 直展示；suggestion || 无建议）；[backend/mental-service/src/main/java/com/edu/mental/entity/MentalAssessment.java:23](../../backend/mental-service/src/main/java/com/edu/mental/entity/MentalAssessment.java#L23)（score/result/suggestion EXTREME，level MEDIUM）；[backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java:158](../../backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java#L158)（student 不在 MEDIUM/HIGH/EXTREME 放行名单）。

### Agent报告、个人资料与管理

| 编号 | 优先级 | 类型 | 问题 |
|---|---|---|---|
| AP-01 | P2 | 契约缺陷或语义错配 | 已返回的联系方式仍固定显示为空，来源说明过期 |
| AP-02 | P2 | 已声明未开放或能力缺口 | 资料保存与修改密码未接线，输入仅保留在组件内 |
| AP-03 | P1 | 契约缺陷或语义错配 | 角色目录及用户数量为固定假数据 |
| AP-04 | P2 | 契约缺陷或语义错配 | 角色及权限操作按钮没有实际读写链路 |
| AP-05 | P1 | 契约缺陷或语义错配 | 初始化心理角色码与实际授权角色码不同 |
| AP-06 | P2 | 契约缺陷或语义错配 | 合法ReAct期限被显示为—天，固定7天标题也与期限不一致 |
| AP-07 | P1 | 契约缺陷或语义错配 | 长期跟进内容与当前schema完全错配 |
| AP-08 | P2 | 契约缺陷或语义错配 | 资源字符串列表被当对象表格展示 |
| AP-09 | P2 | 契约缺陷或语义错配 | 引用读错层级，合法主路径引用全部丢失 |
| AP-10 | P2 | 契约缺陷或语义错配 | 主路径知识检索结果未留存，页面会误标未执行 |
| AP-11 | P1 | 契约缺陷或语义错配 | 敏感指标只在UI隐藏，任务API及PDF缺少相同字段门 |
| AP-12 | P1 | 契约缺陷或语义错配 | 同组件详情标签切换未同步路由身份，可能读取或操作旧实体 |
| AP-13 | P2 | 契约缺陷或语义错配 | Cloud构建未传入追踪页面URL，后端可观测配置不能驱动此页 |
| AP-14 | P2 | 契约缺陷或语义错配 | 反馈服务未校验任务已有方案/终态，契约仅靠前端可见条件 |

#### AP-01 已返回的联系方式仍固定显示为空，来源说明过期

sys_user邮箱/电话有值时仍显示—，编辑框也不会读入该值；页面宣称接口不返回与真实DTO/赋值相反。

当前来源：GET /auth/userInfo → AuthServiceImpl.getUserInfo → sys_user.email/phone

建议：从 userInfo 读取 email/phone并正确区分空值，删除错误无来源说明；部门另按 ROOT-D08 定义。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/profile/index.vue:27](../../frontend/src/views/profile/index.vue#L27)（邮箱、电话均为固定—，未绑定userInfo）；[frontend/src/views/profile/index.vue:34](../../frontend/src/views/profile/index.vue#L34)（称/auth/userInfo不返回邮箱电话）；[frontend/src/views/profile/index.vue:131](../../frontend/src/views/profile/index.vue#L131)（form.email/phone初始化为空）；[backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java:138](../../backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java#L138)（setEmail/setPhone实际赋值）；[backend/auth-service/src/main/java/com/edu/auth/dto/UserInfoResponse.java:14](../../backend/auth-service/src/main/java/com/edu/auth/dto/UserInfoResponse.java#L14)（声明email/phone）；[sql/init/01_init.sql:18](../../sql/init/01_init.sql#L18)（sys_user含email、phone列）。

#### AP-02 资料保存与修改密码未接线，输入仅保留在组件内

两个可点击按钮只提示功能暂未开放，不发API、不改store、不落库；属于页面已明确标注的未实现功能，不是保存成功后的数据丢失。

当前来源：前端reactive form/passwordForm；auth无自助更新/改密端点；/user/{id} PUT仅管理员管理接口

建议：定义自助资料/改密 DTO 和安全流程后接入；bio 若需要需新增持久/返回契约；不借用管理员任意更新接口。

边界：nickname/email/phone 有 sys_user 字段；bio 无现有持久/返回字段。两个操作已明确未开放，不是接口返回保存成功后丢数据。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/profile/index.vue:72](../../frontend/src/views/profile/index.vue#L72)（保存@click=notReady）；[frontend/src/views/profile/index.vue:95](../../frontend/src/views/profile/index.vue#L95)（修改密码@click=notReady）；[frontend/src/views/profile/index.vue:144](../../frontend/src/views/profile/index.vue#L144)（notReady只ElMessage.info）；[frontend/src/api/auth.js:7](../../frontend/src/api/auth.js#L7)（仅登录/userInfo/退出/刷新helper）；[backend/auth-service/src/main/java/com/edu/auth/controller/AuthController.java:25](../../backend/auth-service/src/main/java/com/edu/auth/controller/AuthController.java#L25)（控制器仅login/userInfo/logout/refresh）；[backend/user-service/src/main/java/com/edu/user/controller/UserController.java:55](../../backend/user-service/src/main/java/com/edu/user/controller/UserController.java#L55)（update仅Roles.ADMIN授权）。

#### AP-03 角色目录及用户数量为固定假数据

无论真实数据库有哪些角色/多少用户，页面总显示固定4行与2、128、2456、5人，创建时间全部2025-01-01；管理员无法从此页获得真实权限目录或成员数。

当前来源：roleList直接ref字面量；真实目录在sys_role，人数需来自sys_user_role，当前页面无任何API导入/加载

建议：读取真实角色目录、关联人数和创建时间；交付前去除假记录或显式演示标识。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/admin/roles.vue:52](../../frontend/src/views/admin/roles.vue#L52)（硬编码roleList开始）；[frontend/src/views/admin/roles.vue:58](../../frontend/src/views/admin/roles.vue#L58)（固定用户数量2）；[frontend/src/views/admin/roles.vue:66](../../frontend/src/views/admin/roles.vue#L66)（固定用户数量128）；[frontend/src/views/admin/roles.vue:74](../../frontend/src/views/admin/roles.vue#L74)（固定用户数量2456）；[frontend/src/views/admin/roles.vue:82](../../frontend/src/views/admin/roles.vue#L82)（固定用户数量5）；[sql/init/01_init.sql:29](../../sql/init/01_init.sql#L29)（sys_role真实表）；[sql/init/01_init.sql:42](../../sql/init/01_init.sql#L42)（sys_user_role关联表）；[backend/auth-service/src/main/java/com/edu/auth/mapper/RoleMapper.java:13](../../backend/auth-service/src/main/java/com/edu/auth/mapper/RoleMapper.java#L13)（只按用户查询角色join，不是此页面目录接口）。

#### AP-04 角色及权限操作按钮没有实际读写链路

新增/编辑/删除/确定没有handler；权限设置仅打开对话框，忽略被点击角色，不读取当前勾选、不回显、不保存，树为固定节点。不存在此页面调用到sys_role/sys_role_permission的API链路。

当前来源：permissionData静态ref；handlePermission(_row)只设置visible；SQL已有角色/权限关联表但无页面接线

建议：先提供真实角色/权限读取及受控写入契约，按选中角色回显、保存；未交付控件应禁用并说明。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/admin/roles.vue:7](../../frontend/src/views/admin/roles.vue#L7)（新增按钮无@click）；[frontend/src/views/admin/roles.vue:21](../../frontend/src/views/admin/roles.vue#L21)（编辑/删除无@click）；[frontend/src/views/admin/roles.vue:40](../../frontend/src/views/admin/roles.vue#L40)（确定无@click）；[frontend/src/views/admin/roles.vue:87](../../frontend/src/views/admin/roles.vue#L87)（静态permissionData）；[frontend/src/views/admin/roles.vue:124](../../frontend/src/views/admin/roles.vue#L124)（忽略_row，只打开dialog）；[sql/init/01_init.sql:51](../../sql/init/01_init.sql#L51)（sys_permission）；[sql/init/01_init.sql:68](../../sql/init/01_init.sql#L68)（sys_role_permission）。

#### AP-05 初始化心理角色码与实际授权角色码不同

如果给用户分配初始化的mental_teacher角色，JWT/userInfo保持原码；当前心理管理/EXTREME/STAFF_VIEW与前端菜单只认psychologist等码，心理教师仍被隐藏或403。仅此角色未被默认分配，不能宣称当前某真实用户已受影响。

当前来源：sys_role.code → RoleMapper join → buildClaims/userInfo角色；common Roles/前端STAFF_VIEW_ROLES固定授权集合

建议：统一 canonical 角色码，并对存量角色/绑定作受控迁移；不得为兼容旧码扩大普通教师权限。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/admin/roles.vue:80](../../frontend/src/views/admin/roles.vue#L80)（心理健康教师code=mental_teacher）；[sql/init/01_init.sql:134](../../sql/init/01_init.sql#L134)（初始化同名角色仍用mental_teacher）；[backend/auth-service/src/main/java/com/edu/auth/mapper/RoleMapper.java:13](../../backend/auth-service/src/main/java/com/edu/auth/mapper/RoleMapper.java#L13)（取原角色记录，不映射码）；[backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java:113](../../backend/auth-service/src/main/java/com/edu/auth/service/impl/AuthServiceImpl.java#L113)（原roleCodes写claims）；[backend/common/src/main/java/com/edu/common/security/Roles.java:14](../../backend/common/src/main/java/com/edu/common/security/Roles.java#L14)（实际心理专业码psychologist）；[backend/common/src/main/java/com/edu/common/security/Roles.java:19](../../backend/common/src/main/java/com/edu/common/security/Roles.java#L19)（STAFF_VIEW不含mental_teacher）；[backend/common/src/main/java/com/edu/common/security/Roles.java:34](../../backend/common/src/main/java/com/edu/common/security/Roles.java#L34)（MENTAL_WRITE仅admin/psychologist）。

#### AP-06 合法ReAct期限被显示为—天，固定7天标题也与期限不一致

当前合法deadline=2周内/1个月内会显示—天。根因是frontend/PDF仍读旧Python方案deadline_days，而主路径schema只允许deadline字符串。

当前来源：AgentLoop final_answer.intervention_plan → task.interventionPlan JSON列；legacy Python plan另用deadline_days

建议：在服务边界统一/版本化 ReAct 与 legacy 方案 DTO，同时修页面和PDF，不强制字符串期限转换为天数。

验证：虚构对象通过当前 strict schema，离线核对当前页面投影；不是浏览器视觉验收。

证据：[backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json:27](../../backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json#L27)（必填deadline字符串，closed object禁止deadline_days）；[backend/agent-service/src/main/resources/prompts/agent-loop.system.md:33](../../backend/agent-service/src/main/resources/prompts/agent-loop.system.md#L33)（期限示例1周/2周/1个月）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java:366](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java#L366)（planJson原样写interventionPlan）；[frontend/src/views/agent/ReportDetail.vue:172](../../frontend/src/views/agent/ReportDetail.vue#L172)（固定7天标题）；[frontend/src/views/agent/ReportDetail.vue:177](../../frontend/src/views/agent/ReportDetail.vue#L177)（读取row.deadline_days || —）；[ai-inference-service/app/prompts/plan.system.md:10](../../ai-inference-service/app/prompts/plan.system.md#L10)（legacy字段deadline_days）；[backend/agent-service/src/main/resources/templates/report.html:155](../../backend/agent-service/src/main/resources/templates/report.html#L155)（PDF也读取deadline_days）。

#### AP-07 长期跟进内容与当前schema完全错配

合法长期计划有goal/milestones/horizon，页面只显示默认阶段1，不显示目标、里程碑、周期；PDF同步漏展示。不是SQL缺字段，也不是角色脱敏。

当前来源：task.interventionPlan JSON → ReAct closed schema goal:string、milestones:string[]、horizon:string；旧Python形态phase/goals/metrics

建议：按统一方案 DTO 展示目标、里程碑和周期，并兼容有版本的旧方案；同步PDF模板。

验证：虚构对象通过当前 strict schema，离线核对当前页面投影；不是浏览器视觉验收。

证据：[backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json:32](../../backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json#L32)（必填goal/milestones/horizon）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java:371](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java#L371)（planJson落库无形态转换）；[frontend/src/views/agent/ReportDetail.vue:200](../../frontend/src/views/agent/ReportDetail.vue#L200)（标题读phase）；[frontend/src/views/agent/ReportDetail.vue:203](../../frontend/src/views/agent/ReportDetail.vue#L203)（目标读goals数组）；[frontend/src/views/agent/ReportDetail.vue:207](../../frontend/src/views/agent/ReportDetail.vue#L207)（衡量指标读metrics数组）；[ai-inference-service/app/prompts/plan.system.md:13](../../ai-inference-service/app/prompts/plan.system.md#L13)（旧Pythonphase/goals/metrics）；[backend/agent-service/src/main/resources/templates/report.html:162](../../backend/agent-service/src/main/resources/templates/report.html#L162)（PDF同旧keys）。

#### AP-08 资源字符串列表被当对象表格展示

主路径合法resources=[字符串]时表格有行但类型/名称/联系为空。PDF ExportService parseArr尝试getJSONObject字符串并catch降级为空列表，可能整段不展示。

当前来源：ReAct resources:string[]；legacy Python resources为对象数组；task.intervention_plan存JSON

建议：统一资源条目类型；支持主路径字符串或在边界转为完整资源 DTO，PDF不得吞掉格式错误。

验证：虚构对象通过当前 strict schema，离线核对当前页面投影；不是浏览器视觉验收。

证据：[backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json:36](../../backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json#L36)（resources是string[]）；[frontend/src/views/agent/ReportDetail.vue:222](../../frontend/src/views/agent/ReportDetail.vue#L222)（表格data=plan.resources）；[frontend/src/views/agent/ReportDetail.vue:223](../../frontend/src/views/agent/ReportDetail.vue#L223)（读type/name/link_or_contact对象属性）；[ai-inference-service/app/prompts/plan.system.md:16](../../ai-inference-service/app/prompts/plan.system.md#L16)（legacy对象资源契约）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java:187](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java#L187)（resources通过parseArr）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java:259](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java#L259)（parseArr对每项getJSONObject）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java:263](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java#L263)（异常返回空列表）。

#### AP-09 引用读错层级，合法主路径引用全部丢失

主路径只允许plan顶层references，frontend仅循环action.references，closed行动对象禁止该key，因此引用列恒空；顶层references完全未渲染，PDF也无引用输出。

当前来源：ReAct plan.references:string[]；task JSON已存引用；旧Python额外有action.references

建议：展示全局 references；动作级关联如需交付必须有明确引用模型及 schema。

验证：虚构对象通过当前 strict schema，离线核对当前页面投影；不是浏览器视觉验收。

证据：[backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json:27](../../backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json#L27)（action closed required仅action/owner/deadline）；[backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json:37](../../backend/agent-service/src/main/resources/prompts/agent-final-answer.schema.json#L37)（顶层references:string[]）；[frontend/src/views/agent/ReportDetail.vue:182](../../frontend/src/views/agent/ReportDetail.vue#L182)（只循环row.references）；[ai-inference-service/app/prompts/plan.system.md:10](../../ai-inference-service/app/prompts/plan.system.md#L10)（legacy动作级references）；[ai-inference-service/app/prompts/plan.system.md:19](../../ai-inference-service/app/prompts/plan.system.md#L19)（legacy也有全局references）。

#### AP-10 主路径知识检索结果未留存，页面会误标未执行

即使Agent调用search_* knowledge-rag MCP，其返回chunks进入ReAct observation后没有写retrievedKnowledge；doExecuteAgentLoop仅写risk/plan并把知识/方案阶段当空状态标记。报告hasStage(2)只看数据库JSON列，所以不能展示实际检索来源。未执行真实模型，影响是静态调用链条件证明。

当前来源：Python rag_adapter返回chunks/reranked/fallback → MCP tool observation；主路径没有持久化该observation；legacy才setRetrievedKnowledge

建议：保存工具检索来源/引用的结构化快照，或调整主路径报告阶段语义，不能用缺持久字段断言未执行。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[ai-inference-service/app/mcp/rag_adapter.py:65](../../ai-inference-service/app/mcp/rag_adapter.py#L65)（实际MCP返回chunks metadata）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java:329](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java#L329)（主路径RAG内嵌工具，阶段2/3执行体为空）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java:364](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java#L364)（只存risk/level/plan）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java:383](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java#L383)（knowledge阶段仅status流转）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java:273](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java#L273)（legacy才持久化retrievedKnowledge）；[frontend/src/views/agent/ReportDetail.vue:369](../../frontend/src/views/agent/ReportDetail.vue#L369)（hasStage(2)只看retrievedKnowledge）；[frontend/src/views/agent/ReportDetail.vue:146](../../frontend/src/views/agent/ReportDetail.vue#L146)（无字段就显示未执行）；[sql/init/03_agent_init.sql:18](../../sql/init/03_agent_init.sql#L18)（retrieved_knowledge列确实存在）。

#### AP-11 敏感指标只在UI隐藏，任务API及PDF缺少相同字段门

老师/辅导员可走STAFF_VIEW获取任务list/detail，其中riskAnalysisResult是无敏感标注的原始JSON字符串，common advice不解析字符串内部key。页面注释要求普通辅导员隐藏心理原始指标，但v-permission只detach DOM。PDF API也允许全部staff，模板直接打印keyIndicators；UI导出按钮的admin/psychologist门不是服务器边界。仅静态潜在泄漏，不读取/证明某真实任务含敏感值。

当前来源：task.risk_analysis_result JSON（可能来自get_mental_indicators）→ AgentTask原样返回；PDF模板同源JSON；服务器只做staff行入口授权

建议：任务返回采用可分级的 DTO/JSON裁剪，PDF/创建/下载同步按授权投影；前端隐藏仅保留UX用途。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/agent/ReportDetail.vue:82](../../frontend/src/views/agent/ReportDetail.vue#L82)（注释心理原始分对普通辅导员隐藏）；[frontend/src/views/agent/ReportDetail.vue:85](../../frontend/src/views/agent/ReportDetail.vue#L85)（仅v-permission psychologist/admin）；[backend/agent-service/src/main/java/com/edu/agent/entity/AgentTask.java:30](../../backend/agent-service/src/main/java/com/edu/agent/entity/AgentTask.java#L30)（riskAnalysisResult无SensitiveField，类型String）；[backend/agent-service/src/main/java/com/edu/agent/controller/AgentTaskController.java:61](../../backend/agent-service/src/main/java/com/edu/agent/controller/AgentTaskController.java#L61)（详情STAFF_VIEW）；[backend/agent-service/src/main/java/com/edu/agent/controller/AgentTaskController.java:81](../../backend/agent-service/src/main/java/com/edu/agent/controller/AgentTaskController.java#L81)（列表STAFF_VIEW）；[backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java:113](../../backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java#L113)（leaf直接返回）；[backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java:139](../../backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java#L139)（仅取SensitiveField标注）；[backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java:147](../../backend/common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java#L147)（String/java.*叶子不解JSON）；[frontend/src/directives/permission.js:4](../../frontend/src/directives/permission.js#L4)（明确UX隐藏不是安全防线）；[backend/agent-service/src/main/java/com/edu/agent/controller/ExportController.java:42](../../backend/agent-service/src/main/java/com/edu/agent/controller/ExportController.java#L42)（导出允许全部STAFF_VIEW）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java:190](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/ExportServiceImpl.java#L190)（keyIndicators直接parseStringArr）；[backend/agent-service/src/main/resources/templates/report.html:95](../../backend/agent-service/src/main/resources/templates/report.html#L95)（PDF输出每个关键指标）。

#### AP-12 同组件详情标签切换未同步路由身份，可能读取或操作旧实体

报告、问卷设计/完成情况、学生作答/结果共5页首次捕获ID且仅onMounted；教师/学生详情2页读取当前ID但仅首次挂载加载。同组件A→B的标签切换复用实例，可能仍显示A档案，刷新/新增题目/等级规则/作答/导出/反馈仍使用A的ID。

当前来源：route.params.id → 非reactive const taskId → detail/export/feedback API；SQL task_id使用该旧值

建议：监听并校验路由ID后重置/重载状态，或用明确 route key 重建实例；取消旧请求并防旧响应覆盖；写操作绑定当前已加载实体。

边界：同一根因合并7页为1项；源码证明可达切换路径，未在真实业务页面操作，实机回归待验证。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/agent/ReportDetail.vue:347](../../frontend/src/views/agent/ReportDetail.vue#L347)（const taskId=route.params.id）；[frontend/src/views/agent/ReportDetail.vue:385](../../frontend/src/views/agent/ReportDetail.vue#L385)（getAgentTaskDetail(taskId)）；[frontend/src/views/agent/ReportDetail.vue:394](../../frontend/src/views/agent/ReportDetail.vue#L394)（只onMounted读取，没有watch params）；[frontend/src/views/agent/ReportDetail.vue:471](../../frontend/src/views/agent/ReportDetail.vue#L471)（exportReport(taskId)）；[frontend/src/views/agent/ReportDetail.vue:498](../../frontend/src/views/agent/ReportDetail.vue#L498)（反馈taskId=Number(taskId)）；[frontend/src/components/Layout/index.vue:18](../../frontend/src/components/Layout/index.vue#L18)（组件无route key）；[frontend/src/components/Layout/TagsView.vue:7](../../frontend/src/components/Layout/TagsView.vue#L7)（tag.path router-link直接跨报告路由）；[sql/init/05_intervention_feedback.sql:10](../../sql/init/05_intervention_feedback.sql#L10)（反馈持久化task_id）；[frontend/src/views/mental/questionnaire-design.vue:136](../../frontend/src/views/mental/questionnaire-design.vue#L136)（首次捕获ID；293仅onMounted；279规则保存用旧ID）；[frontend/src/views/mental/questionnaire-result.vue:58](../../frontend/src/views/mental/questionnaire-result.vue#L58)（首次捕获ID；95仅onMounted）；[frontend/src/views/student-mental/take.vue:89](../../frontend/src/views/student-mental/take.vue#L89)（首次捕获ID；180仅onMounted；154提交旧ID）；[frontend/src/views/student-mental/result.vue:52](../../frontend/src/views/student-mental/result.vue#L52)（首次捕获assessmentId；124仅onMounted）；[frontend/src/views/teacher/detail.vue:216](../../frontend/src/views/teacher/detail.vue#L216)（只在255 onMounted加载，无params watch）；[frontend/src/views/student/detail.vue:159](../../frontend/src/views/student/detail.vue#L159)（只在187 onMounted加载，无params watch）。

#### AP-13 Cloud构建未传入追踪页面URL，后端可观测配置不能驱动此页

trace页只读编译期VITE_LANGFUSE_URL，为空就显示未配置。Cloud Dockerfile/Compose只传VITE_AI_ENABLED/API/SSE，.env文件被Docker和source打包排除；后端LANGFUSE_HOST等runtime env不进入已构建SPA。因此当前标准Cloud构建链路无法显示已配置的Cloud追踪页。生产页面是否有人另行手工重构建未检查，不宣称已浏览实测。

当前来源：frontend编译期VITE_LANGFUSE_URL，独立于Java/Python runtime LANGFUSE_HOST；iframe直接外部Langfuse，不经业务SQL/API

建议：将非秘密的 Langfuse Cloud 控制台URL显式传入SPA构建参数；勿将public/secret key放前端；实际跳转/iframe另验。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/admin/trace.vue:48](../../frontend/src/views/admin/trace.vue#L48)（仅import.meta.env.VITE_LANGFUSE_URL）；[frontend/src/views/admin/trace.vue:19](../../frontend/src/views/admin/trace.vue#L19)（空URL显示未配置）；[docker/Dockerfile.frontend.cloud:8](../../docker/Dockerfile.frontend.cloud#L8)（ARG仅VITE_AI_ENABLED）；[docker/Dockerfile.frontend.cloud:9](../../docker/Dockerfile.frontend.cloud#L9)（ENV仅AI/API/SSE/PREWARM）；[docker/docker-compose.cloud.yml:473](../../docker/docker-compose.cloud.yml#L473)（build args仅AI）；[frontend/.dockerignore:4](../../frontend/.dockerignore#L4)（.env与.env.*排除）；[scripts/cloud_deploy.py:277](../../scripts/cloud_deploy.py#L277)（source打包拒绝.env文件）；[k8s/workloads.json:703](../../k8s/workloads.json#L703)（LANGFUSE_HOST只注入后端agent运行时）。

#### AP-14 反馈服务未校验任务已有方案/终态，契约仅靠前端可见条件

页面以COMPLETED/REJECTED显示反馈卡，但服务只检查task存在与score/outcome，任何存在任务（PENDING/FAILED或legacy低风险无方案）都可落干预反馈。当前默认ReAct一般会有plan；主要是API状态边界与legacy支持路径的记录完整性问题，未实际提交。

当前来源：POST /agent/api/v1/intervention/feedback → InterventionFeedbackServiceImpl.submit → intervention_feedback；服务不检查task.status/interventionPlan

建议：服务器定义并落实反馈允许的任务状态/方案存在条件；低风险无干预时有独立结果口径。

验证：源码静态确认；具体运行时表现未逐页实测。

证据：[frontend/src/views/agent/ReportDetail.vue:281](../../frontend/src/views/agent/ReportDetail.vue#L281)（反馈卡由canExport控制）；[frontend/src/views/agent/ReportDetail.vue:406](../../frontend/src/views/agent/ReportDetail.vue#L406)（canExport仅终态COMPLETED/REJECTED）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/InterventionFeedbackServiceImpl.java:48](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/InterventionFeedbackServiceImpl.java#L48)（任务仅存在/未删除检查）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/InterventionFeedbackServiceImpl.java:53](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/InterventionFeedbackServiceImpl.java#L53)（无状态/方案校验即insert）；[backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java:258](../../backend/agent-service/src/main/java/com/edu/agent/service/impl/AgentTaskServiceImpl.java#L258)（legacy低/无风险短路不生成plan）；[sql/init/05_intervention_feedback.sql:10](../../sql/init/05_intervention_feedback.sql#L10)（反馈task_id/score等不具备业务状态约束）。

### MCP与数据库映射

| 编号 | 优先级 | 类型 | 问题 |
|---|---|---|---|
| SC-D01 | P1 | 契约缺陷或语义错配 | MCP 心理指标读取的 JSON 键与真实心理历史 API 不一致，造成已有风险等级丢失 |
| SC-D02 | P2 | 契约缺陷或语义错配 | MCP 学业平均分/平均绩点把未出分课程学分加入分母，将未知成绩当零分 |
| SC-D03 | P1 | 契约缺陷或语义错配 | MCP 心理查询绕经共享 userId 后丢失请求 studentId，LIMIT 1 与公开种子可造成混档 |

#### SC-D01 MCP 心理指标读取的 JSON 键与真实心理历史 API 不一致，造成已有风险等级丢失

来源 API 含 level/score，但消费者使用其他键，类型/等级/分数未投影，因子分没有现有结构化来源。

当前来源：GET /mental/student/assessments?userId= -> List<MentalAssessment>；mental_assessment 与 mental_questionnaire

建议：统一心理指标 DTO，显式映射 level/类型/总分；没有因子分就明确标未提供，不能用总分冒充因子分。

边界：模拟内部成功响应直接输入方法，未经过角色脱敏且来源 level/score 非 null，仍无法映射，故不是正常脱敏或“无测评”情况。empty history -> found=false 为合法缺省；createTime -> assessedAt 正常。没有调用生产心理接口、模型或读取学生行。因子分目前无明确量表因子计算来源，是承诺/能力缺口；不声称现有总分可直接推导因子分。

验证：重新编译当前 Java tool 源码，动态代理注入虚构 API 返回，实际方法执行；0网络调用。

证据：[backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java:125](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java#L125)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java:207](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java#L207)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/dto/MentalIndicatorDto.java:17](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/dto/MentalIndicatorDto.java#L17)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/feign/MentalServiceClient.java:18](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/feign/MentalServiceClient.java#L18)；[backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java:71](../../backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java#L71)；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:153](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L153)；[backend/mental-service/src/main/java/com/edu/mental/entity/MentalAssessment.java:17](../../backend/mental-service/src/main/java/com/edu/mental/entity/MentalAssessment.java#L17)；[sql/init/01_init.sql:142](../../sql/init/01_init.sql#L142)；[sql/init/01_init.sql:189](../../sql/init/01_init.sql#L189)；[backend/agent-service/src/main/resources/prompts/agent-loop.system.md:5](../../backend/agent-service/src/main/resources/prompts/agent-loop.system.md#L5)；[backend/agent-service/src/main/resources/skills/psychological-screening.md:13](../../backend/agent-service/src/main/resources/skills/psychological-screening.md#L13)。

#### SC-D02 MCP 学业平均分/平均绩点把未出分课程学分加入分母，将未知成绩当零分

未出分课只有学分、无 score/gradePoint 时，分子不加但分母照加，真实已出成绩的均值被压低；全未出分时输出 0.00，误呈现已有零成绩，可能误导学业风险判定。两个均值还需要各自的有效分母，不能共用全部课程学分。

当前来源：GET /student/{id}/academic -> List<AcademicRecord>；student_academic_record.score/grade_point 生产 schema 允许 NULL，credit 非空

建议：score和gradePoint分别只使用有效出分课程的学分作分母；无有效成绩返回unknown，保留合法0。

边界：实际调用当前源码方法，虚构 score/gradePoint=null 符合生产 nullable 元数据；无脱敏介入，课程 gradePoint/score 本来也未标 SensitiveField。全出分对照计算正确，所以不是所有加权均值都失效。没有主表 GPA/credits 自动汇总定义，不将本问题扩张为主表必须同步。

验证：重新编译当前 Java tool 源码，动态代理注入虚构 API 返回，实际方法执行；0网络调用。

证据：[backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java:73](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java#L73)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java:85](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java#L85)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/dto/AcademicHistoryDto.java:17](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/dto/AcademicHistoryDto.java#L17)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/feign/StudentServiceClient.java:30](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/feign/StudentServiceClient.java#L30)；[backend/student-service/src/main/java/com/edu/student/controller/StudentAcademicController.java:28](../../backend/student-service/src/main/java/com/edu/student/controller/StudentAcademicController.java#L28)；[backend/student-service/src/main/java/com/edu/student/service/impl/AcademicRecordServiceImpl.java:20](../../backend/student-service/src/main/java/com/edu/student/service/impl/AcademicRecordServiceImpl.java#L20)；[backend/student-service/src/main/java/com/edu/student/entity/AcademicRecord.java:27](../../backend/student-service/src/main/java/com/edu/student/entity/AcademicRecord.java#L27)；[sql/init/04_student_extras.sql:15](../../sql/init/04_student_extras.sql#L15)。

#### SC-D03 MCP 心理查询绕经共享 userId 后丢失请求 studentId，LIMIT 1 与公开种子可造成混档

请求其他 studentId 可能拿到同账号下另一人的测评，或因所选任意学生没有记录而漏报；MCP 不检查响应记录 studentId，仍写请求 id 并 found=true，造成记录归属错误。风险等级当前丢失是 SC-D01 的独立问题，修字段映射不会消除本混档。

当前来源：student_info.user_id 与 student_info.id；mental_assessment.student_id

建议：内部心理查询保持请求studentId及授权范围，并校验返回归属；先确定一账号一学生还是多学生模型，再盘点重复绑定和历史归属，制定保留数据的迁移。不得直接加UNIQUE(user_id)或让LIMIT 1选任意学生。

边界：只依据源码/公开初始化 SQL 及固定虚构 API 响应复验。当前生产元数据仅列/type/nullable，不含索引，也未读取任何生产学生或评估行，所以不声称生产已存在共享账号或已发生真实混档。不要把 LIMIT 1 视作选择第一行的保证。

验证：公开SQL与当前源码核对；离线实际方法注入不同学生来源，证明未检验归属；生产重复绑定/索引/真实资料未查询。

证据：[backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java:128](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java#L128)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java:149](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/tool/StudentDataTools.java#L149)；[backend/mcp-student-data/src/main/java/com/edu/mcp/student/feign/MentalServiceClient.java:22](../../backend/mcp-student-data/src/main/java/com/edu/mcp/student/feign/MentalServiceClient.java#L22)；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:153](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L153)；[backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java:190](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/MentalAssessmentServiceImpl.java#L190)；[backend/mental-service/src/main/java/com/edu/mental/mapper/StudentLookupMapper.java:9](../../backend/mental-service/src/main/java/com/edu/mental/mapper/StudentLookupMapper.java#L9)；[sql/init/01_init.sql:100](../../sql/init/01_init.sql#L100)；[sql/init/01_init.sql:219](../../sql/init/01_init.sql#L219)；[sql/init/01_init.sql:232](../../sql/init/01_init.sql#L232)。

## 4 离线验证与生产结构核对

生产结构查询成功，20表/220列。22个 @TableName 实体的256个字段映射全部有对应生产列；这些实体覆盖15个不同表，其余5表没有现行实体映射。24个Java mapper、1个XML mapper、11个DTO文件（含嵌套共14类）和SQL的20个不同表已复核。SQL中两处CREATE IF NOT EXISTS的重复旧定义不被当作有效迁移；当前列以生产元数据和完整初始化/增量脚本交叉核对。没有发现映射缺列，不代表页面所有能力已交付。

JSON中的 deadline/goal/references 等是 agent_task 中JSON文档的属性，不要求每个属性有独立SQL列。派生排名、均值、统计别名也不能只靠表列名称判定缺失。

离线实际执行当前 Java MCP tool：虚构历史响应含 level、score，输出却是 riskLevel=null、assessmentType=null、factorScores={}；空历史正常 found=false。两门等学分课程一门100/4.0、一门未出分，工具算出50/2.0；两门均有分的对照为90/3.5，证明问题是空值口径。

身份归属对照：虚构请求学生701，虚构上游返回历史的studentId为700，工具仍输出studentId=701且found=true。源码和公开种子证明经共享userId与LIMIT 1会丢失原学生范围；本次没有查询生产是否实际有重复绑定。

离线构造合法 strict ReAct 方案：deadline="2周内"、goal、milestones、horizon、字符串资源和顶层引用均通过schema。页面读取旧键后期限成“— 天”，目标/里程碑/周期和引用消失，资源对象列为空。该探针是源码投影及schema验证，不是浏览器截图/PDF导出的实测。

证据文件：生产列元数据（本地测试产物，不入库：`./PRODUCTION_SCHEMA_2026_10_07.json`）、MCP离线执行结果（本地测试产物，不入库：`./MCP_OFFLINE_PROOF_2026_10_07.json`）、方案离线投影（本地测试产物，不入库：`./AGENT_PLAN_OFFLINE_PROOF_2026_10_07.json`）、完整结构化清单（本地测试产物，不入库：`./FIELD_CONTRACT_AUDIT_2026_10_07.json`）、CSV清单（本地测试产物，不入库：`./FIELD_CONTRACT_AUDIT_2026_10_07.csv`）。

## 5 不应误判与额外边界

- 正常空表、未填邮箱/电话、没有评估历史不等于数据库漏列。本文不读取业务行来判断某个实际人员是否填过这些资料。
- student本人/普通教职工的心理 score/result/suggestion 为null可来自明确的字段权限；作答模板隐藏分值/等级阈值是既有安全设计，不能关闭脱敏来填满页面。页面空态兼容见MENTAL-PERM-01，结果接口未裁剪阈值另见MENTAL-11。
- auth的角色数组与账号 userType 是不同概念；头像首字母、日期/状态格式、统计SQL alias和派生字段有实际来源，不需要单独建列。
- 当前用户列表已接真实 records/total，分页插件已补；注销令牌、学生主页和退出弹窗是前轮修复项，不用旧截图将其重复算作当前缺陷。
- 教师分页改变size未重置page存在静态控制流风险，但不属于本报告的字段/数据源主清单，见teacher/list.vue的分页绑定及fetchData；动态翻页复验待验证。
- 问卷status的SQL注释与UI生命周期词义、response.status新旧注释、MCP gender注释有文档漂移；现有代码并未把实际数值转换错误，未额外算成缺列或性别交换。
- trace帮助文本仍偏自托管，个人页只用userType显示粗角色，属于文字/标签局限；真实Cloud页面跳转及iframe限制待验证。
- 同表副本实体字段不完全相同不自动构成缺陷：data-service只返回聚合；不存在原始对象返回证据时不宣称副本实体泄露。

## 6 覆盖范围

| 视图 | 核对结论 |
|---|---|
| [frontend/src/views/admin/roles.vue](../../frontend/src/views/admin/roles.vue) | AP-03、AP-04、AP-05 |
| [frontend/src/views/admin/trace.vue](../../frontend/src/views/admin/trace.vue) | AP-13 |
| [frontend/src/views/admin/users.vue](../../frontend/src/views/admin/users.vue) | ROOT-G05 |
| [frontend/src/views/agent/ReportDetail.vue](../../frontend/src/views/agent/ReportDetail.vue) | AP-06、AP-07、AP-08、AP-09、AP-10、AP-11、AP-12、AP-14 |
| [frontend/src/views/agent/Warning.vue](../../frontend/src/views/agent/Warning.vue) | 字段、枚举、筛选和分页匹配；与ReportDetail共用任务返回的JSON安全边界见AP-11。 |
| [frontend/src/views/dashboard/index.vue](../../frontend/src/views/dashboard/index.vue) | ROOT-D01、ROOT-D02、ROOT-D03、ROOT-D04 |
| [frontend/src/views/error/404.vue](../../frontend/src/views/error/404.vue) | 静态错误页面，无数据库字段/业务API承诺。 |
| [frontend/src/views/login/index.vue](../../frontend/src/views/login/index.vue) | ROOT-D06、ROOT-G01、ROOT-G02、ROOT-G03 |
| [frontend/src/views/mental/analysis.vue](../../frontend/src/views/mental/analysis.vue) | MENTAL-02、MENTAL-03 |
| [frontend/src/views/mental/overview.vue](../../frontend/src/views/mental/overview.vue) | ROOT-G06、MENTAL-01 |
| [frontend/src/views/mental/questionnaire-design.vue](../../frontend/src/views/mental/questionnaire-design.vue) | MENTAL-05、MENTAL-06、MENTAL-07、AP-12 |
| [frontend/src/views/mental/questionnaire-result.vue](../../frontend/src/views/mental/questionnaire-result.vue) | MENTAL-12、AP-12 |
| [frontend/src/views/mental/questionnaire.vue](../../frontend/src/views/mental/questionnaire.vue) | MENTAL-04、MENTAL-13 |
| [frontend/src/views/profile/index.vue](../../frontend/src/views/profile/index.vue) | AP-01、AP-02 |
| [frontend/src/views/student-mental/history.vue](../../frontend/src/views/student-mental/history.vue) | MENTAL-PERM-01 |
| [frontend/src/views/student-mental/list.vue](../../frontend/src/views/student-mental/list.vue) | MENTAL-04 |
| [frontend/src/views/student-mental/result.vue](../../frontend/src/views/student-mental/result.vue) | MENTAL-09、MENTAL-10、MENTAL-PERM-01、AP-12 |
| [frontend/src/views/student-mental/take.vue](../../frontend/src/views/student-mental/take.vue) | MENTAL-05、MENTAL-08、AP-12 |
| [frontend/src/views/student/detail.vue](../../frontend/src/views/student/detail.vue) | TS-D01、TS-D03、TS-G06、TS-G07、TS-G08、AP-12 |
| [frontend/src/views/student/list.vue](../../frontend/src/views/student/list.vue) | TS-D02、TS-C01、TS-G10 |
| [frontend/src/views/teacher/detail.vue](../../frontend/src/views/teacher/detail.vue) | TS-G01、TS-G02、TS-G03、TS-G04、TS-G05、AP-12 |
| [frontend/src/views/teacher/list.vue](../../frontend/src/views/teacher/list.vue) | TS-C01、TS-G09 |

前端API模块：[frontend/src/api/user.js](../../frontend/src/api/user.js)、[frontend/src/api/auth.js](../../frontend/src/api/auth.js)、[frontend/src/api/teacher.js](../../frontend/src/api/teacher.js)、[frontend/src/api/mental.js](../../frontend/src/api/mental.js)、[frontend/src/api/student.js](../../frontend/src/api/student.js)、[frontend/src/api/dashboard.js](../../frontend/src/api/dashboard.js)、[frontend/src/api/agent.js](../../frontend/src/api/agent.js)。

| 生产表 | 列数 |
|---|---:|
| agent_export_task | 11 |
| agent_task | 14 |
| audit_log | 10 |
| intervention_feedback | 10 |
| intervention_report | 15 |
| knowledge_citation | 10 |
| mental_assessment | 9 |
| mental_question | 14 |
| mental_questionnaire | 14 |
| mental_response | 7 |
| student_academic_record | 11 |
| student_attendance | 9 |
| student_info | 20 |
| sys_login_log | 7 |
| sys_permission | 13 |
| sys_role | 8 |
| sys_role_permission | 4 |
| sys_user | 12 |
| sys_user_role | 4 |
| teacher_info | 18 |

## 7 修复顺序与验收条件

先处理服务器字段权限、角色/身份契约以及关键分析输入，再处理方案展示和计分历史，最后补齐需要明确业务口径的新能力。不要一次给所有占位增加可空列：这不能解决已有API未接、JSON形态分裂、参数忽略或权限边界。

| 批次 | 重点 | 通过判据 |
|---|---|---|
| 1 | AP-11、MENTAL-11、AP-05、AP-12、SC-D01/03 | 最小角色矩阵在API/PDF均一致；同组件A→B读写始终绑定B；心理查询保持请求学生归属；有测评的tool返回真实授权等级，禁止伪造因子分 |
| 2 | MENTAL-05/06/08/09/10/12/13、SC-D02 | 每题型、必答/非法答案拒绝、未知值均值、题库修改后的历史回显、清空日期/计人数均通过真实存储回归 |
| 3 | AP-06～10、TS-D01～03、AP-01、面板统计 | ReAct/legacy版本样例在UI/PDF一致；现有成绩/联系方式可展示；时间/人口/学院口径一致；移除假数据 |
| 4 | 登录刷新、目录管理、筛选及明确未开放能力 | 有安全和数据来源定义的能力再开放；未交付状态清楚，不增加无来源数字或绕过授权 |

这些验收属于后续修复，**待验证**。本次只完成审计清单、元数据核对及所述离线验证，不将新问题标记为已修复，不改变R-5/R-6完成状态。
