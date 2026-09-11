package com.edu.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 entity / DTO 字段的敏感度。
 *
 * <p>由 {@link FieldPermissionAdvice} 在响应体序列化前反射读取，
 * 按 {@link RequestContext#getRoles()} 决定是否置 {@code null}（参见
 * docs/educare/FIELD_PERMISSION.md §4 的角色 × 分级矩阵）。
 *
 * <p>未标注的字段被视为 {@link Sensitivity#PUBLIC}。建议为 entity 中所有
 * 非 id/审计字段显式标注，避免遗漏（CI Linter 在 Phase G 收尾会强制检查）。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveField {
    Sensitivity value();
}
