# 字段级权限设计（G-2.1）

> **目标**：把"哪个角色能看到哪些字段"从前端展示层下沉到后端 REST 层，关闭 IMPROVEMENT v1.1 §G-2 提到的"前端假脱敏漏洞"。
>
> **范围**：student-service / teacher-service / mental-service / user-service 的 controller 返回；agent-service 已有的 `DataMasker` 不动，作为本方案的样板。
>
> **不在本期范围**：行级权限（同一角色看哪些学生子集）—— 留待 Phase I 合规框架（I-4）统一处理。
>
> **2026-09-14 修订**：调用方如何被归类为「端用户 / 已验证内部调用 / 匿名」以 §11 为准，取代旧的「无 token = 内网放行不脱敏」口径。
>
> **2026-10-05 修订**：心理管理端点与学生作答响应按 §12 执行；内部凭证不授予所有心理管理权限。

---

## 1. 角色清单（来源 + 编码）

| 角色码 | 来源 | 后端常量 | 前端 meta |
|--------|------|----------|-----------|
| `admin` | `sys_role` 表 | `AgentSecurityContext.ROLE_ADMIN` | `'admin'` |
| `psychologist` | 同上 | `ROLE_PSYCHOLOGIST` | `'psychologist'` |
| `counselor` | 同上 | `ROLE_COUNSELOR` | `'counselor'` |
| `academic_advisor` | 同上 | `ROLE_ACADEMIC_ADVISOR` | `'academic_advisor'` |
| `teacher` | `User.userType=2` 派生 | （新增）`ROLE_TEACHER` | `'teacher'` |
| `student` | `User.userType=3` 派生 | （新增）`ROLE_STUDENT` | `'student'` |

G-2.1 设计时 JWT 仅含 `userType`（数字）；G-2.2 已在登录/刷新链写入 `roles` claim（字符串数组），当前权限校验读取该 claim。原设计步骤保留为历史。

---

## 2. 敏感度分级（沿用 `DataMasker` 三档 + 新增 PUBLIC）

| 级别 | 定义 | 处理 |
|------|------|------|
| `PUBLIC` | 无敏感性，任何已登录用户可见（姓名、学号、专业、年级） | 直出 |
| `MEDIUM` | 涉及个人能力 / 表现（GPA、考勤率、不及格科目、奖项） | 仅 `admin / academic_advisor / counselor / 本人 / 班主任` 可见原值；其他角色见摘要文本 |
| `HIGH` | 涉及家庭 / 经济 / 联系方式（手机号、住址、家庭经济水平、是否单亲） | 仅 `admin / counselor / 本人` 可见；其他角色 → 字段置 `null` 或星号脱敏 |
| `EXTREME` | 心理量表原始分、咨询记录、罪错处分、医疗记录 | 仅 `admin / psychologist / 本人（部分字段限制）` 可见；其他角色 → `null` |

> 注：`本人` 指 `request.userId == 资源.studentId` 时的自访问；放在 §6 二阶段实现。

---

## 3. 字段总表（首版按现有 entity 字段填）

> 实施时若发现遗漏，按本表的 schema 新增字段并把分级写在新增字段的 `@SensitiveField` 注解里（§5），同步更新此表。

### student-service / `Student`
| 字段 | 类型 | 分级 |
|------|------|------|
| `id`, `studentId`, `name`, `grade`, `major`, `className` | String/Long | PUBLIC |
| `birthDate` | LocalDate | HIGH |
| `gpa`, `credits` | BigDecimal/Integer | MEDIUM |
| `phone`, `address`, `emergencyContact` | String | HIGH |

### teacher-service / `Teacher`
| 字段 | 类型 | 分级 |
|------|------|------|
| `id`, `employeeId`, `name`, `title`, `school`, `major`, `researchArea` | — | PUBLIC |
| `birthDate`, `phone`, `email` | — | HIGH |
| `education` | String | MEDIUM |

### mental-service / `MentalAssessment`
| 字段 | 类型 | 分级 |
|------|------|------|
| `id`, `studentId`, `assessmentDate`, `assessmentType` | — | PUBLIC |
| `level` （等级文本如 "中度"） | String | MEDIUM |
| `score`, `result`, `suggestion`, `rawAnswers`（若有） | — | EXTREME |

### user-service / `User`
| 字段 | 类型 | 分级 |
|------|------|------|
| `id`, `username`, `userType`, `status` | — | PUBLIC |
| `email`, `phone` | — | HIGH |
| `password`（即便 hash） | String | **永不返回**（用专门 DTO 排除，不走分级） |

---

## 4. 角色 × 分级 矩阵（决策表）

| 角色 \ 分级 | PUBLIC | MEDIUM | HIGH | EXTREME |
|-------------|--------|--------|------|---------|
| admin | ✅ | ✅ | ✅ | ✅ |
| psychologist | ✅ | ✅ | ✅ | ✅ |
| counselor | ✅ | ✅ | ✅ | ⛔ |
| academic_advisor | ✅ | ✅ | ⛔ | ⛔ |
| teacher | ✅ | ✅（仅所授班级，行级，留 Phase I） | ⛔ | ⛔ |
| student（本人） | ✅ | ✅ 本人字段 | ✅ 本人字段 | ⛔（只见 `level` 等摘要） |
| student（他人） | ✅（仅有限字段：姓名 / 班级 / 专业） | ⛔ | ⛔ | ⛔ |

**违规返回值**：被禁字段统一置 `null`（不是空串、不是 `"****"`），让前端能据此选择"隐藏 vs 显示占位"。前端"假脱敏"页面（`v-permission`）保留作 UX 优化，不再作为安全防线。

---

## 5. 落地手段：`ResponseBodyAdvice` + `@SensitiveField` 注解

**为什么不选 Jackson `@JsonView`**：JsonView 需要在 controller 方法注解视图类，每个角色一个视图类组合数爆炸；且无法表达"分级 + 角色矩阵"。

**为什么不选 DTO 映射层**：每个 entity 出一个 DTO 反而增加维护成本，且现有 controller 直返 entity，迁移成本大。

**选定方案**：

```java
// common/src/main/java/com/edu/common/security/SensitiveField.java
@Target(FIELD)
@Retention(RUNTIME)
public @interface SensitiveField {
    Sensitivity value();
    String maskedAs() default "";  // 可选：被脱敏时的替换值（仅 HIGH 可用）
}

public enum Sensitivity { PUBLIC, MEDIUM, HIGH, EXTREME }
```

```java
// common/src/main/java/com/edu/common/security/FieldPermissionAdvice.java
@RestControllerAdvice
public class FieldPermissionAdvice implements ResponseBodyAdvice<Object> {
    // 1. 从当前 Request 上下文拿 roles（来自 JWT roles claim）
    // 2. 递归走 body 对象的 declared fields，遇到 @SensitiveField 时按 §4 矩阵判定
    // 3. 决策 → 反射 set null 或替换值
}
```

注解写在 entity 字段上：
```java
public class Student {
    @SensitiveField(Sensitivity.HIGH)
    private LocalDate birthDate;

    @SensitiveField(Sensitivity.MEDIUM)
    private BigDecimal gpa;
    // ...
}
```

**性能**：用 `ConcurrentHashMap<Class, FieldDescriptor[]>` 缓存反射结果（首次扫描，后续直接读）。一次响应平均 < 0.5 ms。

**集合处理**：返回 `Page<Student>` / `List<Teacher>` 时递归遍历 `getContent()` / 元素。

---

## 6. 实施路径（拆到 G-2.2 子任务）

| 子步 | 范围 | 产物 |
|------|------|------|
| G-2.2-a | 在 `common` 模块加 `SensitiveField` 注解 + `Sensitivity` 枚举 + `FieldPermissionAdvice` | `common/src/main/java/com/edu/common/security/*.java` |
| G-2.2-b | 改 JWT 登录流：登录成功后把 `roles: List<String>` 塞进 token claim；`JwtUtil.parseRoles()` 提供解析 | `auth-service/.../AuthServiceImpl.java` + `common/.../JwtUtil.java` |
| G-2.2-c | 在 gateway / 各 service 加一个 `RoleContextFilter`，把 JWT roles 解到 `ThreadLocal<RequestContext>`，供 advice 读取 | `common/.../security/RequestContext.java` + `WebFilter` |
| G-2.2-d | 给 4 个 entity 加 `@SensitiveField` 注解（按 §3） | student/teacher/mental/user entity |
| G-2.2-e | `FieldPermissionAdvice` 注册到 `common-autoconfig`，所有 service 自动启用 | spring.factories / `@AutoConfiguration` |
| G-2.3 | 前端去除假脱敏：信任后端返回，把 `v-permission` 改为纯 UX 隐藏（折叠/占位"无权限查看"） | `frontend/src/views/...` |

**自包验证（G-2.3 收尾）**：用 admin / counselor / teacher / student 四个测试账号分别拉同一学生详情，diff 返回 JSON 字段集，断言矩阵符合 §4。

---

## 7. 已知风险与折中

| 风险 | 处理 |
|------|------|
| `@SensitiveField` 漏标 → 字段裸奔 | CI 中跑 `FieldPermissionLinter`：扫所有 entity，凡 `private` 非 `id/createdAt/updatedAt/deleted` 字段无注解 → 编译警告。本期先靠 code review；G-2 收尾前补 linter。 |
| 集合（List / Page）深递归性能 | Page 走 `getContent()` 单次；嵌套 DTO 限制递归深度 3 层。 |
| 老接口仍直返 entity，灰度风险 | `FieldPermissionAdvice` 默认对所有 `@RestController` 生效；可通过 `@SkipFieldPermission` 类注解临时关闭（仅 admin 端点用）。 |
| 行级权限（teacher 只看自己班学生）缺失 | 显式标注：本设计仅覆盖**列级**；行级权限走 MyBatis Plus 的 `TenantHandler` 或 service 层的 `studentIds` 过滤，留 Phase I-4 合规框架。 |
| JWT roles claim 增加 token 大小 | 角色数 ≤ 6，整体影响 < 50 bytes，可忽略。 |
| 自访问（"本人"）判定 | 一阶段不实现；advice 看不到 path 变量。二阶段如需，加 `@ResourceOwner("#studentId")` 注解 + SpEL 提取。 |

---

## 8. 与 audit_log 的关系

本设计**不**记录 "who saw what field"（避免每个响应一行日志，量爆炸）。

- **粗粒度审计**：在 gateway 已有的访问日志里记 `userId / role / endpoint / responseTime` 足够；
- **细粒度审计**：仅对 EXTREME 字段的 **访问尝试** 记一条（无论命中拒绝），写入 Phase I-4 规划的 `audit_log` 表；
- 当前阶段（G-2）只记 EXTREME 字段被脱掉的次数（Micrometer counter），用于排查越权迹象。

---

## 9. 后续动作（G-2.2 / G-2.3 跟踪）

完成此设计后回到 `EXECUTION_PLAN.md`：
- 把 §3 字段表 + §4 矩阵作为 G-2.2 落地的 "规格"，不再讨论"该不该脱敏"，只讨论"怎么实现"
- 若实施中发现矩阵需要调整 → 改本文件 §4，在文件底部追加 "## 10. 变更记录" 一行

---

## 10. 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-05-13 | 初版 | G-2.1 设计产出，作为 G-2.2/G-2.3 实施规格 |
| 2026-09-14 | 新增 §11：内部调用改为出示 `X-Internal-Token` 正向凭证；无 token 且无凭证的请求不再视为内网 —— 服务入口 401、端点拒绝、字段只留 PUBLIC | Render 上下游服务各有公网 URL，「无 token = 内网」不成立，匿名直连可拿到未脱敏的心理数据 |

---

## 11. 调用方分类与内部凭证（2026-09-14 修订）

> 本节取代此前「无 token 的内网 Feign 调用放行不脱敏」的口径。

**背景**：旧实现把「请求不带 `Authorization`」等同于「内网 Feign 调用」，前提是下游服务端口不对公网发布、外部流量必经网关。Render 部署（`render.yaml`）下这个前提不成立：student / mental / data / teacher / user / agent / mcp-student 都是 `type: web` 免费服务，各有公网 `https://edu-portrait-<name>.onrender.com`；免费实例又收不到私网流量，内部 Feign 只能走公网 HTTPS。结果是任何人不带 token 直连下游，`AccessGuard` 放行、本 advice 不脱敏，拿到的是含未成年人心理数据在内的完整响应。

**现行判定**（`ServiceAuthFilter` → `AccessGuard` → `RoleContextFilter`/`FieldPermissionAdvice` 顺序一致，带 token 优先）：

| 请求 | 服务入口 `ServiceAuthFilter` | 端点授权 `AccessGuard.allowSelfRoleOrInternal` | 字段（本 advice） |
|------|------|------|------|
| 带 `Authorization: Bearer`，JWT 合法 | 放行 | 本人或授权角色 | 按 §4 矩阵脱敏 |
| 带 Bearer 但 JWT 非法 / 过期 | 401 | 拒绝 | —— |
| 无 token，`X-Internal-Token` 比对通过 | 放行 | 仅接受内部调用的端点放行（心理管理例外见 §12） | **不脱敏**（AI 取数链路需要完整画像） |
| 无 token，内部凭证缺失或错误 | 401 | 拒绝 | 只留 PUBLIC（fail-closed 兜底） |

- 带 token 时一律按端用户处理，同时附上内部凭证头也不提权。
- **凭证**：共享密钥 `EDUCARE_INTERNAL_TOKEN`（或 `educare.internal.token`）。`InternalCallCredential` 对两边取 SHA-256 摘要后用 `MessageDigest.isEqual` 比较，耗时与入参无关。出站由 `InternalCallFeignConfig` 按客户端挂载：agent-service 的 student/mental/data 客户端、mcp-student-data 的 student/mental 客户端；不发给 ai-inference。
- **未配置 = 不承认任何内部调用**（所有 profile 一致）：无 JWT 的请求一律 401 / 只留 PUBLIC，AI 取数链路会断，但不会泄露数据。本地起栈用 docker-compose / RUNBOOK §4.4 的开发默认值；生产由 Render `generateValue` 或 `scripts/preflight-prod.sh`（≥32 字符、拒绝开发默认值）保证。
- **防伪造**：网关 `InternalHeaderStripFilter` 排在所有 filter 之前，剥掉客户端自带的 `X-Internal-Token`（含 `/auth/**` 公开路径），经网关无法伪造。
- **服务入口门**：开关 `educare.service-auth.enabled`（默认开）。auth-service（`/auth/**` 本就公开）、agent-service（已有只认 JWT 的 `AgentSelfAuthFilter`）、mcp-student-data（`/mcp` 由 `X-MCP-Token` 把守）显式关闭。入口门覆盖缺少端点授权的历史路由；`MentalController` 现已增加 §12 的角色门，`/student/ids` 等内部取数路由仍需服务入口准入保护。健康检查 `/actuator/health` 豁免，原始 URI 与规范化路径须同时匹配才豁免。

**残余风险（本次未解决）**：
1. 下游入口门只验 JWT 签名 + 过期，不查 Redis 会话白名单（多数下游不连 Redis）：已登出 / 改密但未过期的 token 仍能直连下游公网 URL，最长 24h。根治需让下游不可公网访问（Render 付费私网服务），或下游也查会话。
2. 单一共享密钥：泄露后所有内部调用都可被冒充；轮换需所有服务一起重新部署（Render 改 env group 值后重部署）。
3. ~~`MentalController` 缺角色授权~~ 已由 MENTAL-AUTHZ-20261005 / ADR-030 收口，规则见 §12；真实 HTTP/浏览器角色验收仍待验证。
4. `X-Internal-Token` 在服务入口仍是共享准入凭证；心理管理端点已限制为仅 analysis 接受内部调用，其余服务尚未普遍实施按调用方/端点的细粒度内部授权。

## 12. 心理问卷管理与作答响应（MENTAL-AUTHZ-20261005）

身份准入之后继续执行端点角色授权，不能以合法内部凭证代替所有心理管理权限。
当前实现见 [MentalController](../../backend/mental-service/src/main/java/com/edu/mental/controller/MentalController.java)、
[StudentMentalController](../../backend/mental-service/src/main/java/com/edu/mental/controller/StudentMentalController.java) 与
[QuestionServiceImpl](../../backend/mental-service/src/main/java/com/edu/mental/service/impl/QuestionServiceImpl.java)。

| 端点或字段 | 授权/响应规则 |
|---|---|
| 心理概览、问卷与题目设计读取、完成情况 | 合法 JWT 的 `STAFF_VIEW`；内部凭证不代替角色 |
| 问卷、题目、等级规则写入 | 合法 JWT 的 `MENTAL_WRITE`：admin/psychologist |
| `/mental/analysis` | `STAFF_VIEW` 或已验证内部调用；带学生 JWT 时附内部头不提权 |
| 完成情况 Map 行的 `score` | 仅 `EXTREME_VIEW`：admin/psychologist 保留，其余教职工置 null |
| `/mental/student/questionnaires/{id}` | 必须合法 JWT；状态 0/null 拒绝，状态 1/2 返回去分值模板 |
| 作答模板计分信息 | 移除 options 中的 score、scoringRules 与 levelRules；非法/非数组 options 不下发 |

模板裁剪只修改响应，提交测评仍读取数据库原规则计分；个人测评记录继续按本人/已验证内部调用授权。
管理端角色拒绝返回 JSON 业务码 403，HTTP 可能为 200；入口缺失/非法凭证返回 HTTP 401，验收须同时检查。
前端隐藏写按钮只提供操作提示，后端承担最终授权。验证命令、判据与回滚见根目录 RUNBOOK 同名条目；
历史分支自动测试通过；2026-10-05 合并后 JDK 21 全后端 267 例与 JaCoCo 门通过，见 RUNBOOK 的
MERGE-MAIN-20261005。真实 HTTP 和浏览器角色矩阵仍待验证。
