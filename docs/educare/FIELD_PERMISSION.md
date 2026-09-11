# 字段级权限设计（G-2.1）

> **目标**：把"哪个角色能看到哪些字段"从前端展示层下沉到后端 REST 层，关闭 IMPROVEMENT v1.1 §G-2 提到的"前端假脱敏漏洞"。
>
> **范围**：student-service / teacher-service / mental-service / user-service 的 controller 返回；agent-service 已有的 `DataMasker` 不动，作为本方案的样板。
>
> **不在本期范围**：行级权限（同一角色看哪些学生子集）—— 留待 Phase I 合规框架（I-4）统一处理。

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

**当前 JWT 仅含 `userType`**（数字），需要在登录后查 `sys_role` 写入 token 的 `roles` claim（字符串数组），避免每个请求查库。这是 G-2.2 的前置改造。

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
