# 字段权限验收手册（G-2.3）

> **目标**：用 5 个测试角色账号访问相同接口，断言返回字段集与 `FIELD_PERMISSION.md §4` 矩阵一致。
>
> **前置**：G-2.2-a 至 e 已落地。本手测**不**涉及 mental-service 接口（受 §8 B-1 阻塞，等 mental 子团队补 `Question` 实体字段后再补测）。

---

## 1. 启用

每个待测 service 的 `application.yml`（或 Nacos `*.yml`）加：
```yaml
educare:
  field-permission:
    enabled: true
```

需启用的 service：`auth-service` / `user-service` / `student-service` / `teacher-service`（mental 暂略）。

启动后检查日志，应看到 `FieldPermissionAdvice` 与 `RoleContextFilter` 类被加载（autoconfig 命中）。若 `educare.field-permission.enabled` 为 false，advice 与 filter **均不注册**，验证仍能进行但不会有过滤。

---

## 2. 测试账号准备

在 `sys_user` + `sys_role` + `sys_user_role` 三张表里准备 5 个账号（数据库脚本或后台手建均可）：

| 用户名 | 密码（明文） | 绑定角色 code |
|--------|------------|---------------|
| `test_admin` | `Admin@123` | `admin` |
| `test_counselor` | `Counselor@123` | `counselor` |
| `test_acadv` | `Acadv@123` | `academic_advisor` |
| `test_teacher` | `Teacher@123` | `teacher` |
| `test_student` | `Student@123` | `student` |

> 也可直接在已有 admin 账号上插不同 `sys_role` 做组合测试。

---

## 3. 期望返回字段集（基于 `FIELD_PERMISSION.md §4` 列级矩阵）

测试接口：`GET /student/{id}`，被测目标为某存在的学生（建议测试库塞一条完整数据）。

| 角色 \ 字段 | birthDate (HIGH) | gpa (MEDIUM) | credits (MEDIUM) | 其他 (PUBLIC) |
|-------------|------------------|--------------|------------------|---------------|
| admin | 有值 | 有值 | 有值 | 有值 |
| counselor | 有值 | 有值 | 有值 | 有值 |
| academic_advisor | **null** | 有值 | 有值 | 有值 |
| teacher | **null** | 有值 | 有值 | 有值 |
| student | **null** | **null** | **null** | 有值 |

> 矩阵摘自 `FieldPermissionAdvice.canSee` —— admin/psychologist 全通；counselor MEDIUM+HIGH 通过；academic_advisor/teacher 仅 MEDIUM 通；其它角色仅 PUBLIC。

测试接口：`GET /teacher/{id}`

| 角色 \ 字段 | birthDate (HIGH) | education (MEDIUM) |
|-------------|------------------|--------------------|
| admin | 有值 | 有值 |
| counselor | 有值 | 有值 |
| academic_advisor | **null** | 有值 |
| teacher | **null** | 有值 |
| student | **null** | **null** |

测试接口：`GET /user/{id}`（user-service）

| 角色 \ 字段 | email (HIGH) | phone (HIGH) | password |
|-------------|--------------|--------------|----------|
| admin | 有值 | 有值 | **字段缺失**（@JsonIgnore） |
| counselor | 有值 | 有值 | 字段缺失 |
| academic_advisor | **null** | **null** | 字段缺失 |
| teacher | **null** | **null** | 字段缺失 |
| student | **null** | **null** | 字段缺失 |

---

## 4. 一键脚本（推荐）

```bash
# 拿 5 个角色的 token
for u in test_admin test_counselor test_acadv test_teacher test_student; do
  TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$u\",\"password\":\"${u/test_/}\"}" | jq -r '.data.token')
  echo "$u: $TOKEN" >> /tmp/tokens.txt
done

# 用每个 token 拉同一学生详情，diff 字段集
STU_ID=1
for u in test_admin test_counselor test_acadv test_teacher test_student; do
  TOKEN=$(grep "^$u:" /tmp/tokens.txt | cut -d' ' -f2)
  echo "===== $u ====="
  curl -s "http://localhost:8080/student/$STU_ID" \
    -H "Authorization: Bearer $TOKEN" \
    | jq '.data | with_entries(select(.value != null) | .key)'
done
```

期望：admin / counselor 输出包含 `birthDate / gpa / credits`；academic_advisor / teacher 输出**缺** `birthDate` 但含 `gpa / credits`；student 输出**只**含 PUBLIC 字段。

---

## 5. 调试要点

| 现象 | 原因 | 修复 |
|------|------|------|
| 所有角色都拿到全字段 | `educare.field-permission.enabled` 未开 / autoconfig 未生效 | 启动日志搜 `FieldPermissionAutoConfiguration`；检查 application.yml 拼写 |
| 全角色都被打成最低权限 | JWT 没带 `roles` claim（G-2.2-b 未生效）或 `RoleContextFilter` 没跑 | 解码 token 看 claims；检查 filter 注册（`/actuator/mappings` 或日志） |
| 部分字段被打 null 但理应可见 | 矩阵理解错 / `@SensitiveField` 等级填错 | 对照 `FIELD_PERMISSION.md §3` 字段表 |
| `password` 字段竟然返回了 | `@JsonIgnore` 漏加（G-2.2-d 没改全） | 检查两份 `User.java`（auth + user 都得有） |
| Filter 报 NPE on jwtUtil | autoconfig 在没有 JwtUtil bean 的 service 启动 | 确认 `common` 的 imports 文件里 `JwtUtil` 已登记 |

---

## 6. v-permission 行为再校

后端拿 null 后，前端原有 `v-permission` 仍按角色隐藏 UI 区块；G-2.2 之后这只是 UX 层的"少占位"，**不**是安全防线（参见 `permission.js` 顶部 JSDoc）。

手测时注意：
- `student-mental/result.vue` 中 `data.assessment.score` 在 student 视角下应为 `null`（mental-service 上线后），UI 当前会渲染空。后续可加 `|| '—'` 占位，UX 优化不属本期 G-2 范围。
- `student/detail.vue` 中 `phone / email` 当前未在实体里（参见 G-2.2-d 笔记），返回什么取决于 Student 实体后续是否补字段。

---

## 7. 通过标准

- [ ] 5 个角色 × 3 个接口（/student/{id}、/teacher/{id}、/user/{id}）的字段集 diff 全部符合 §3
- [ ] `password` 字段在所有角色下都不返回
- [ ] 关闭 `educare.field-permission.enabled` 时所有字段照旧返回（确认 advice 没硬编码）
- [ ] 至少 1 个角色（推荐 `student`）的请求在 Langfuse / 日志中能看到 `RoleContextFilter` 解 token 成功的痕迹（debug 日志 "FieldPermissionAdvice: empty roles..."（如果只有 PUBLIC）或 advice 处理 N 个字段的痕迹）

通过后，把本文件顶部加一行"验收通过：YYYY-MM-DD，执行人：xxx"，并把 `EXECUTION_PLAN.md` 的 G-2.3 勾选。

---

## 8. 后续优化（不阻塞 G-2.3 验收）

- UX 优化：把 `null` 渲染成 `'—'` 或 `'无权限查看'` —— 建议加 `<MaskedValue :value="..." />` 组件
- 行级权限（teacher 仅看本班）—— Phase I-4 合规框架
- CI Linter：扫 entity 漏标 `@SensitiveField` —— G-2 段收尾子任务
- mental-service B-1 解锁后补测心理评估字段矩阵
