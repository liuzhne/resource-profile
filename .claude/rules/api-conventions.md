# API 约定（全局生效）

## 统一返回体
- 所有 controller 返回 `Result<T>`（common 模块）：
  - `Result.success(data)` → code 200
  - `Result.error(message)` → code 500
  - `Result.error(code, message)` → 自定义码（如 400 参数错、404 不存在、429 限流）
- 校验失败 / 业务异常走 `GlobalExceptionHandler`（`@RestControllerAdvice`）。

## 网关与路由
- 网关 Spring Cloud Gateway（8080），`lb://{service}` + `StripPrefix=0`：
  `/auth /user /teacher /student /mental /data /agent → 对应服务`。
- 端口：gateway 8080 / auth 8081 / user 8082 / teacher 8083 / student 8084 / mental 8085 / data 8086 / agent 8087。
- MCP server：mcp-student-data 8094 / knowledge-rag 8095（Streamable HTTP，单端点 `/mcp`；均绑 127.0.0.1）。

## 鉴权
- JWT（HS256）：access 24h / refresh 7d；`Authorization: Bearer <token>`。
- `/auth/**` 公开，其余需鉴权；前端 401 自动登出。
- 字段级权限：`@SensitiveField` + `FieldPermissionAdvice`（参 `docs/educare/FIELD_PERMISSION.md`），默认关 `educare.field-permission.enabled`。

## Nacos 配置
- 每服务 import `{service}.yml` + `common.yml`（`optional:nacos:...`）。
- 共享密钥（如 `jwt.secret`）放 `common.yml`；勿明文写进代码。

## 新增端点检查清单
1. 返回 `Result<T>`，错误码规范；2. 路径挂在该服务命名空间下；3. 是否需鉴权/字段权限；
4. 入参校验 + 异常交给 GlobalExceptionHandler；5. 对应前端 `src/api/*.js` 同步。
