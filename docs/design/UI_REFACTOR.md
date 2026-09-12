# 前端毛玻璃改版 · 交付说明

原型来源：Claude Design 项目 `8a6f4bda-84ca-4617-9e73-6f93171596ba` / `新设计 · 毛玻璃.dc.html`（覆盖 10 屏）。

- **Stage 1**：设计地基 + 登录页 + 数据面板
- **Stage 2**：其余 8 屏 + 全量旧色板清理

原型 10 屏已全部落地。

## 0. 拍板决策

| 议题 | 结论 | 说明 |
|---|---|---|
| 配色 | **按原型**（iOS 色板） | 覆盖了任务书中「配色沿用现有规范」一条，经确认后改按原型 |
| 字体 | **不引入 Barlow** | 原型用 Google Fonts，与「不引入新依赖」冲突；改用系统字体 + `tabular-nums` |
| 节奏 | 分阶段 | 本次 Stage 1，其余 8 屏待 Stage 2 |
| 产品名 | 「见微 · 师生资源画像平台」 | 侧边栏与登录页同步 |

**未新增任何 npm 依赖。** 图标复用已有 `@element-plus/icons-vue`（原型的 `#ep-*` sprite 即 Element Plus 图标集）；图表继续用已有 ECharts（原型的手绘 SVG 只是 mock 手法）；毛玻璃为纯 CSS。

## 1. 改动 / 新增文件清单

### 新增（3）
| 文件 | 作用 |
|---|---|
| `frontend/src/styles/glass.scss` | 毛玻璃复用类：`.glass-panel` `.glass-card` `.seg-control` `.pill` `.icon-chip` `.icon-btn` `.tnum`，含 `@supports` 降级 |
| `frontend/src/views/dashboard/components/ChartState.vue` | 统一收敛 loading / 空态 / 错误态（错误态带重试） |
| `frontend/src/components/BrandLogo.vue` | 「见微」同心圆标识，侧边栏与登录页共用 |

### 修改（13）
| 文件 | 改动要点 |
|---|---|
| `styles/variables.scss` | 全量替换为原型色板 + 玻璃/圆角/阴影 token；侧边栏 208→**224px**，顶栏 64→**60px** |
| `styles/element-plus.scss` | `$colors`/`$border-radius` 重映射；**删除深色侧边栏菜单块**（原型为浅色玻璃）；菜单项 40px/圆角 10px；表格、卡片、弹层玻璃化 |
| `styles/index.scss` | 引入 `glass`；暖白底 + 三层 radial 光晕；原型字体栈；滚动条 8px |
| `styles/layout.scss` | 去掉深色侧边栏与不透明内容底；内容区 padding 改 `16px 20px 24px` |
| `Layout/Sidebar.vue` | 浅色玻璃；「见微」logo（`BrandLogo`）+ 副标；菜单容器 padding `6px 10px 16px` |
| `Layout/SidebarItem.vue` | 子菜单左侧竖线 + 缩进；箭头旋转过渡（仅加样式块，逻辑未动） |
| `Layout/Navbar.vue` | 60px 玻璃条；搜索/通知图标位；头像改渐变胶囊 |
| `Layout/Breadcrumb.vue` | 分隔符弱化，末级加粗为当前页 |
| `Layout/TagsView.vue` | 改药丸标签；当前页白底 + 轻投影 |
| `views/login/index.vue` | 左右分栏重写（品牌渐变栏 + 表单栏），< 920px 折叠 |
| `views/dashboard/index.vue` | Grid 布局重排；玻璃面板；分段控件；ECharts 重新配色；补三态 |
| `dashboard/components/StatisticCard.vue` | 重写为玻璃卡（图标胶囊 + 34px 数值 + 环比行） |
| `dashboard/components/CountTo.vue` | 新增可选 `separator`（千分位），对齐原型的 `1,286` |

## 2. 数据字段 ↔ 接口映射

**接口零改动**：`src/api/*.js`、`utils/request.js`、`store/`、`router/` 的逻辑均未修改，鉴权与错误处理沿用原封装。

### 登录页 → `POST /auth/login`（`api/auth.js: login`）
| 原型元素 | 字段 | 来源 |
|---|---|---|
| 账号输入框 | `username` | 原 `loginForm.username`，未变 |
| 密码输入框 | `password` | 原 `loginForm.password`，未变 |
| 记住我 | `remember` | 原 `loginForm.remember`，未变 |

提交链路仍为 `userStore.loginAction(loginForm)`，逐字保留。

### 数据面板
| 面板区块 | 接口 | 字段 |
|---|---|---|
| 4 张统计卡 | `GET /data/dashboard/statistics` | `teacherCount` `studentCount` `questionnaireCount` `warningCount` |
| 增长趋势 | `GET /data/dashboard/trend?period=` | `days[]` `teacherData[]` `studentData[]`；`period` ∈ `week`/`month`/`year` |
| 师生分布 | `GET /data/dashboard/distribution` | `data[].name` `data[].value`；中心总数由前端求和 |
| 最近登录 | `GET /data/dashboard/recentLogins` | `username` `role` `time` `ip` |

## 2.5 Stage 2 改动（其余 8 屏 + 旧色板清理）

### 新增（1）
| 文件 | 作用 |
|---|---|
| `frontend/src/utils/avatar.js` | 列表页文字头像：`initialOf()` 取姓名首字、`avatarBg()` 按行序轮转底色 |

### 共用样式扩充（`styles/glass.scss`）
新增列表页与详情页共用件，避免在 4 个列表页 / 2 个详情页里复制同一套样式：
`.page-head` `.filter-bar` `.table-scroll` `.plain-table` `.name-cell` `.badge` `.table-foot`
`.detail-head` `.back-btn` `.detail-grid` `.profile-card` `.mini-stats` `.info-rows` `.detail-main` `.no-source`。
`.plain-table` 原先定义在数据面板的 scoped 样式里，已提升为全局类并从面板移除，避免两处定义漂移。
`styles/element-plus.scss` 追加 `.el-pagination` 的 30px 药丸分页样式。

### 改造页面（8 屏）
| 文件 | 改动要点 |
|---|---|
| `teacher/list.vue` | 玻璃面板 + 页头计数 + 紧凑筛选条 + 原生表格 + 文字头像；补空态/错误态重试 |
| `student/list.vue` | 同上，另加 GPA 进度条（按 4.0 折算）与状态徽标；阈值 3.5/2.5 沿用改版前 |
| `teacher/detail.vue` | `300px 1fr` 两栏；档案卡 + 分段控件切 4 个 tab；缺口字段就地标注 |
| `student/detail.vue` | 同上；**并接通此前从未调用的 `getStudentDetail`** |
| `mental/overview.vue` | 4 张带进度条的玻璃统计卡；预警表改原生表格；趋势图由折线改柱状（对齐原型） |
| `agent/Warning.vue` | 仅换外壳与样式，**SSE / 轮询 / 触发对话框逻辑一行未动**；保留 `el-table`（见差异说明） |
| `profile/index.vue` | 两栏重排；**改读 store 中的真实登录用户**，替代原先写死的「管理员」 |
| `admin/users.vue` | 玻璃面板 + 原生表格；顶部显式声明「本页为静态演示数据」 |

### 旧色板清理
改造前有 **20+ 处硬编码 Ant 色值**散落在 6 个文件，会与新的 iOS 暖色系直接冲突。现已清零：

| 文件 | 处理 |
|---|---|
| `mental/overview.vue` | 4 张卡的 `#f6ffed`/`#fff7e6`/`#fff1f0`/`#e6f7ff` 底色与前景色 → 随重写一并移除 |
| `mental/analysis.vue` | 饼图/柱图/性别色 5 处 → 换新色板十六进制（ECharts 选项不支持 CSS 变量） |
| `agent/Warning.vue` | `.high-risk-row` 的 `#fff1f0` → `var(--error-tint)`；连接状态点 `#67c23a` → `var(--success-color)` |
| `agent/ReportDetail.vue` | `#faad14` 左边框 → `var(--warning-color)` |
| `teacher/detail.vue`、`student/detail.vue` | `#52c41a` → 随重写移除 |
| `error/404.vue` | `#1890ff` → `var(--primary-color)` |

校验：`grep -rn "#1890ff\|#52c41a\|#faad14\|#f5222d\|#001529\|#667eea" src/` 无输出。

### 本次发现的历史遗留问题
改造前有 **3 个页面是纯静态假数据**，与后端完全无关（非本次引入）：

| 页面 | 原状 | 现状 |
|---|---|---|
| `student/detail.vue` | 完全不调接口，列表传入的 `:id` 被忽略，永远显示「张三」 | **已接通** `getStudentDetail(id)`（接口一直存在，只是没被调用） |
| `profile/index.vue` | 写死「管理员 / admin@edu.edu.cn」 | **已改读** `userStore.userInfo` |
| `admin/users.vue` | 6 条写死的用户数据 | **仍为静态**：`src/api/` 无用户模块，后端亦无 `/user` CRUD 接口，已在页面顶部显式声明 |

前两项属于「复用已有接口」而非新增接口，故一并修复；第三项无接口可用，只做视觉改造并标注。

## 3. 与原型的差异及原因

### 3.1 缺少后端支撑，做占位处理

| 原型元素 | 现状处理 | 原因 |
|---|---|---|
| 统计卡「+2.4% 较上月」 | **不渲染该行**（`delta` 有值才显示） | `/data/dashboard/statistics` 只返回 4 个计数，无环比。不伪造数字 |
| 登录页「手机验证码」tab | 保留版式，输入框与按钮**全部禁用** + 文案提示 | `api/auth.js` 无短信接口 |
| 登录页「忘记密码？」 | 可点击，提示「暂未开放」 | 无对应接口 |
| 登录页「统一身份认证 / 扫码登录」 | 同上 | 无对应接口 |
| 登录页「联系管理员开通 / 使用条款 / 隐私政策」 | 同上 | 无对应页面 |
| 顶栏搜索、通知 | 保留图标位，点击提示「暂未开放」；**不画通知红点** | 无搜索与通知接口，红点会暗示不存在的未读状态 |
| 待处理事项列表 | 沿用原有静态数据（改为原型的圆点样式） | 改版前即为静态 mock，后端无对应接口，本次不扩大范围 |
| 学生详情「专业排名」 | 显示「—」 | `student_info` 表无排名字段 |
| 学生详情「联系电话 / 电子邮箱」 | 不展示（改为班级 / 预计毕业 / 状态） | 表中无这两列，改为展示表中确实有的字段 |
| 学生详情「学业成绩 / 综合素质」tab | 保留版式，显示缺口说明 | 无成绩表、无综合素质表，接口不返回 |
| 学生详情「心理健康」tab | 保留版式，显示缺口说明 | 心理数据在 `/mental/student/**` 下按 `userId` 查询，教师侧按学生 `id` 查看的接口未提供 |
| 教师详情「政治面貌」 | 显示「—」并标注 | `teacher_info` 表无该列 |
| 教师详情「联系电话 / 电子邮箱」 | 显示「—」 | 表中无这两列（改版前即为 `-`） |
| 教师详情「教学成果 / 科研项目 / 教学评价」tab | 保留版式，显示缺口说明 | 无对应表与接口；左栏三项评教统计同为占位 |
| 我的画像「邮箱 / 电话 / 部门」 | 显示「—」并标注 | `/auth/userInfo` 不返回这些字段 |
| 我的画像「保存资料 / 修改密码」 | 按钮保留，点击提示暂未开放 | `api/auth.js` 只有 login / userInfo / logout / refresh |
| 用户管理整页 | 静态演示数据 + 页面顶部声明 | `src/api/` 无用户模块，后端无 `/user` CRUD |

### 3.2 主动的实现差异

| 项 | 原型 | 实现 | 原因 |
|---|---|---|---|
| 数字字体 | Barlow（Google Fonts） | 系统字体 + `font-variant-numeric: tabular-nums` | 不引入外部依赖；内网部署下 CDN 不可达 |
| 图表 | 手绘 SVG path | ECharts 重新配色 | 手绘 SVG 是原型的 mock 手法；ECharts 已是既有依赖，且带 tooltip/resize 能力 |
| 环形图中心总数 | 写死 19,718 | 前端对 `distribution` 求和 | 跟随真实数据 |
| 背景光晕 | 容器内绝对定位遮罩 | `body` 上 `background-attachment: fixed` | 等效且覆盖登录页；注意 `#app` 必须透明，否则会盖住光晕 |
| 趋势图 X 轴 | 固定 12 个日期 | 由 `days[]` 决定 | 跟随接口返回 |
| 响应式断点 | 仅登录页 920px | 登录页 920px + 面板/心理页 1100px + 详情页 1000px 降为单列 | 原型未给窄屏稿，按内容最小宽度补 |
| AI 预警任务表 | 原生轻量表格 | 保留 `el-table` | 该表依赖 `row-class-name`（高危行高亮）、`fixed` 列与 SSE 驱动的行内状态；换原生表会牺牲这些能力。已通过全局 `.el-table` 样式贴近原型观感 |
| 心理概览趋势图 | 分组柱状图 | 柱状图（改版前为折线） | 按原型改为柱状 |
| 列表页「新增 / 编辑」 | 可点击 | 提示暂未开放 | `createTeacher`/`updateTeacher`/`createStudent`/`updateStudent` 接口存在，但原型未给表单稿，不自行设计 |

### 3.3 降级说明

`backdrop-filter` 不可用时（旧浏览器 / 关闭硬件加速），`glass.scss` 与各组件的 `@supports` 分支会把半透明底替换为 `rgba(255,255,255,0.96)` 不透明白底，避免文字叠在光晕上对比度不足。

## 4. 验证记录

| 项 | 结果 |
|---|---|
| `npm run lint:check` | 改动文件 **0 error 0 warning**（仅余 `vite.config.js` 一条改动前既有的格式警告，未动） |
| `npm run build` | 通过 |
| `npm run size:check` | total **2002.0 KiB**，较 Stage 1 的 2011.9 KiB 略降（样式去重 + 部分页面去掉 el-table） |
| SCSS 独立编译 | 通过 |
| Vite SFC 转换 | 全部 22 个页面组件均 200，dev 日志无错误 |
| 旧色板扫描 | 清零 |
| 产物 CSS 抽查 | `backdrop-filter`、`#007aff`、`#f6f4f1`、`--sidebar-width:224px`、`tabular-nums` 均已生成 |

**尚未做：浏览器实机视觉比对。** 自动化检查能证明「能编译、能加载、样式进了产物」，但证明不了「好不好看、版式对不对」。上线前必须人工打开 `http://localhost:5173` 把 22 个页面过一遍，重点看：

1. 登录页窄屏（< 920px）品牌栏折叠是否正常
2. 侧边栏折叠态、子菜单弹出层的玻璃效果
3. 三个 ECharts 图表（面板趋势/分布、心理趋势）在真实数据下的坐标轴密度与换行
4. 表格在窄屏下的横向滚动
5. Safari 与 Chrome 各看一次 `backdrop-filter`；关闭硬件加速时应走 `@supports` 的不透明白底降级

后端未启动时，数据区会走错误态并显示「重新加载」，属预期。

## 5. 上线前仍需处理

- **人工视觉验收**（见上）——这是当前唯一的硬缺口。
- **用户管理页仍是静态数据**：页面已显式声明，但若不希望生产环境出现演示数据，应在上线前隐藏该菜单项（`router/index.js` 中 `/admin/users` 路由），或补齐后端 `/user` 接口。
- `admin/roles.vue`、`admin/trace.vue`、`student-mental/*`（4 页）、`mental/questionnaire*`（3 页）、`error/404.vue` 原型未覆盖，仅通过新 token 获得配色统一，版式仍是旧的。功能不受影响。

## 6. 后端若要补齐原型全部能力，需新增

| 能力 | 缺什么 |
|---|---|
| 统计卡环比 | `/data/dashboard/statistics` 增加环比字段 |
| 学生成绩 | 成绩表 + 按学生查询接口 |
| 学生综合素质 | 竞赛/荣誉/志愿服务记录表 + 接口 |
| 教师教学成果 / 科研项目 / 教学评价 | 三张表 + 接口 |
| 教师政治面貌 | `teacher_info` 增加字段 |
| 师生联系方式 | 电话 / 邮箱字段（或从 `sys_user` 关联返回） |
| 用户管理 | `/user` 增删改查 + 启停 |
| 个人资料 | 更新资料 / 修改密码接口 |
| 短信登录 | 发送验证码 + 校验登录接口 |
| 全局搜索、通知 | 对应接口 |
| 教师侧查看学生心理 | 按学生 `id` 的心理测评查询接口 |
