# 前端毛玻璃改版 · Stage 1 交付说明

原型来源：Claude Design 项目 `8a6f4bda-84ca-4617-9e73-6f93171596ba` / `新设计 · 毛玻璃.dc.html`（覆盖 10 屏）。
本次交付 **Stage 1**：设计地基 + 登录页 + 数据面板。

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

### 3.2 主动的实现差异

| 项 | 原型 | 实现 | 原因 |
|---|---|---|---|
| 数字字体 | Barlow（Google Fonts） | 系统字体 + `font-variant-numeric: tabular-nums` | 不引入外部依赖；内网部署下 CDN 不可达 |
| 图表 | 手绘 SVG path | ECharts 重新配色 | 手绘 SVG 是原型的 mock 手法；ECharts 已是既有依赖，且带 tooltip/resize 能力 |
| 环形图中心总数 | 写死 19,718 | 前端对 `distribution` 求和 | 跟随真实数据 |
| 背景光晕 | 容器内绝对定位遮罩 | `body` 上 `background-attachment: fixed` | 等效且覆盖登录页；注意 `#app` 必须透明，否则会盖住光晕 |
| 趋势图 X 轴 | 固定 12 个日期 | 由 `days[]` 决定 | 跟随接口返回 |
| 响应式断点 | 仅登录页 920px | 登录页 920px + 面板 1100px 降为单列 | 原型未给面板的窄屏稿，按内容最小宽度补 |

### 3.3 降级说明

`backdrop-filter` 不可用时（旧浏览器 / 关闭硬件加速），`glass.scss` 与各组件的 `@supports` 分支会把半透明底替换为 `rgba(255,255,255,0.96)` 不透明白底，避免文字叠在光晕上对比度不足。

## 4. 验证记录

| 项 | 结果 |
|---|---|
| `npm run lint:check` | 改动文件 **0 error 0 warning**（仅余 `vite.config.js` 一条改动前既有的格式警告，未动） |
| `npm run build` | 通过，530ms |
| `npm run size:check` | total 2011.9 KiB，无异常膨胀 |
| SCSS 独立编译 | 通过 |
| Vite SFC 转换 | 5 个改动组件均 200，dev 日志无错误 |
| 产物 CSS 抽查 | `backdrop-filter` 17 处、`#007aff` 10 处、`#f6f4f1`、`--sidebar-width:224px`、`tabular-nums` 均已生成 |

**尚未做**：浏览器实机视觉比对（需人工打开 `http://localhost:5173/login` 与 `/dashboard` 核对）。后端未启动时，面板三个数据区会走错误态并显示「重新加载」，属预期。

## 5. Stage 2 待办

原型剩余 8 屏：教师列表/详情、学生列表/详情、心理概览、AI 预警、我的画像、用户管理。

原型未覆盖、本次也未改的页面：`student-mental/*`（4 页）、`mental/questionnaire*`（3 页）、`admin/roles`、`admin/trace`、`error/404` —— 这些页面会通过新 token 自动获得部分观感改善，但版式仍是旧的。

Stage 2 已知需标注的缺口（详见原型分析）：教师详情的「政治面貌」列、「教学成果 / 科研项目 / 教学评价」三个 tab、学生详情的「学业成绩 / 综合素质」两个 tab，在 `teacher_info` / `student_info` 表及现有接口中**均无对应数据**。
