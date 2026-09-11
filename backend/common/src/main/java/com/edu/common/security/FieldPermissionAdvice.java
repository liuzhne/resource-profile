package com.edu.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * G-2.2-a：字段级权限切面。
 *
 * <p>对所有 {@code @RestController} 返回的响应体，递归遍历对象图，遇到带
 * {@link SensitiveField} 注解的字段时，按 {@link RequestContext#getRoles()} 与
 * docs/educare/FIELD_PERMISSION.md §4 的矩阵决定是否置 {@code null}。
 *
 * <p><b>启用</b>：{@code educare.field-permission.enabled} 默认开；置 false 可关。
 * 仅对<b>带 token 的端用户请求</b>按角色脱敏；内网 Feign 匿名直调（无 token）放行不脱敏
 * （{@link RequestContext#isAuthenticated()}），故默认开不破坏 AI 取数链路。
 *
 * <p><b>不支持的场景</b>（显式落档）：
 * <ul>
 *   <li>行级权限（teacher 只看本班学生）—— 留 Phase I-4 合规框架。</li>
 *   <li>资源所有人（"本人"）判定 —— 一阶段不实现。</li>
 *   <li>{@code @ResponseBody} 之外的渲染路径（如直接 write 到 HttpServletResponse 的）。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnProperty(value = "educare.field-permission.enabled", havingValue = "true", matchIfMissing = true)
public class FieldPermissionAdvice implements ResponseBodyAdvice<Object> {

    /** 不进入对象内部递归的"叶子"包前缀，避免反射 JDK / Spring 内部类。 */
    private static final List<String> SKIP_PACKAGE_PREFIXES = List.of(
            "java.", "javax.", "jakarta.", "sun.", "com.sun.",
            "org.springframework.", "com.fasterxml.", "org.slf4j.",
            "com.baomidou.mybatisplus.core.toolkit." // MP 内部 helper
    );

    /** Field 反射结果缓存。null 值表示该类没有任何带 @SensitiveField 的字段（仍需递归子对象）。 */
    private static final Map<Class<?>, FieldDescriptor[]> CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) return null;
        // 内网 Feign 匿名直调（无 token）→ 可信，不脱敏（与 AccessGuard 内网放行一致），
        // 保证 mcp-student-data / agent 等 AI 取数链路拿到完整画像。仅对端用户请求按角色脱敏。
        if (!RequestContext.isAuthenticated()) {
            return body;
        }
        Set<String> roles = RequestContext.getRoles();
        // 端用户但无角色：保守走最低权限（只 PUBLIC），不抛错以免破坏链路
        if (roles.isEmpty()) {
            log.debug("FieldPermissionAdvice: authenticated but empty roles, applying PUBLIC-only filter on {}", body.getClass().getName());
        }
        try {
            walk(body, roles, Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (Exception e) {
            log.warn("FieldPermissionAdvice 处理失败（已透传原 body）: {}", e.getMessage());
        }
        return body;
    }

    // ---------------- 内部 ----------------

    private void walk(Object obj, Set<String> roles, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) return;
        Class<?> cls = obj.getClass();

        // 容器（Collection/Map/数组）类身处 java.*，但其元素可能是需脱敏的业务对象 —— 必须先于
        // isLeaf 递归，否则 isLeaf 把整个集合当叶子跳过，导致 list/page 响应（Result<List<X>> /
        // Page<X>）漏脱敏（单对象响应不受影响，因其 data 是业务类）。
        if (obj instanceof Collection<?> coll) {
            visited.add(obj);
            for (Object el : coll) walk(el, roles, visited);
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            visited.add(obj);
            for (Object v : map.values()) walk(v, roles, visited);
            return;
        }
        if (cls.isArray()) {
            visited.add(obj);
            int n = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < n; i++) walk(java.lang.reflect.Array.get(obj, i), roles, visited);
            return;
        }

        if (isLeaf(cls)) return;
        visited.add(obj);

        FieldDescriptor[] fields = CACHE.computeIfAbsent(cls, FieldPermissionAdvice::scan);
        for (FieldDescriptor fd : fields) {
            try {
                Object value = fd.field.get(obj);
                if (fd.sensitivity != null && !canSee(roles, fd.sensitivity)) {
                    fd.field.set(obj, null);
                    continue;
                }
                walk(value, roles, visited);
            } catch (IllegalAccessException ignored) {
                // setAccessible(true) 已开，不应到这里
            }
        }
    }

    private static FieldDescriptor[] scan(Class<?> cls) {
        // 走全继承链
        List<FieldDescriptor> out = new java.util.ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                f.setAccessible(true);
                SensitiveField ann = f.getAnnotation(SensitiveField.class);
                Sensitivity s = ann == null ? null : ann.value();
                out.add(new FieldDescriptor(f, s));
            }
        }
        return out.toArray(new FieldDescriptor[0]);
    }

    private static boolean isLeaf(Class<?> cls) {
        if (cls.isPrimitive() || cls.isEnum()) return true;
        String name = cls.getName();
        return SKIP_PACKAGE_PREFIXES.stream().anyMatch(name::startsWith);
    }

    /**
     * 角色 × 分级 决策（docs/educare/FIELD_PERMISSION.md §4 的列级部分）。
     * teacher / student 在本期暂归入与 academic_advisor 相近的列级判定；
     * 行级（teacher 只看本班、student 本人）留 Phase I-4。
     */
    static boolean canSee(Set<String> roles, Sensitivity tier) {
        if (roles.contains("admin")) return true;
        return switch (tier) {
            case PUBLIC -> true;
            case MEDIUM -> Stream.of("psychologist", "counselor", "academic_advisor", "teacher")
                                  .anyMatch(roles::contains);
            case HIGH -> Stream.of("psychologist", "counselor").anyMatch(roles::contains);
            case EXTREME -> roles.contains("psychologist");
        };
    }

    private record FieldDescriptor(Field field, Sensitivity sensitivity) {}
}
