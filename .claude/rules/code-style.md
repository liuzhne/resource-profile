# 代码风格（全局生效）

## 通用
- 写出与周围代码一致的代码：匹配既有命名、注释密度与惯用法。
- 中文是 user-facing 文字的默认语言；代码标识符、路径保持英文。
- 注释解释「为什么」，不复述「做了什么」。

## 后端（Java / Spring Boot 3.2.5 · Java 17）
- 分层固定：`entity / mapper / service(+impl) / controller / dto / config`。
- ORM 用 **MyBatis-Plus**：Mapper 继承 `BaseMapper<Entity>`，**无 XML**，SQL 走注解或 Wrapper。
- 逻辑删除字段 `deleted`（1=删 / 0=未删）；ID `auto`；`map-underscore-to-camel-case: true`。
- 实体用 Lombok `@Data`；构造注入用 `@RequiredArgsConstructor`（final 字段）。
- 自动填充走 `MyMetaObjectHandler`（createdAt/updatedAt）。
- 可选/未就绪依赖用 `@Autowired(required=false)` 或 `ObjectProvider<>` 优雅降级，**不要**让缺失依赖阻断启动。
- 异步走自定义线程池 `@Async("agentExecutor")`（见 `AsyncConfig`），不要新建裸线程。

## 前端（Vue 3 + Vite · Element Plus · Pinia）
- API 层每个后端服务一个 `src/api/*.js`，统一用 `@/utils/request` 实例。
- 状态管理用 Pinia Composition API（`defineStore` + `ref`/`computed`）。
- 组件/Element Plus 走 `unplugin-auto-import` 自动导入，别手动 import 常用 API。
- `@` 别名指向 `src`。

## 提交
- 改动只在 feature 分支（非 main）；用户要求才提交/推送。
- commit message 结尾：`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`。
