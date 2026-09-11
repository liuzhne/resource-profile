package com.edu.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FieldPermissionAdvice.beforeBodyWrite 端到端单测（headless 替代五角色手测）：
 * 对带 @SensitiveField 的真实对象按角色脱敏，断言字段实际置空 / 保留 + 内网放行语义。
 */
class FieldPermissionAdviceWalkTest {

    private final FieldPermissionAdvice advice = new FieldPermissionAdvice();

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    /** 测试 POJO：覆盖 PUBLIC(未标) / MEDIUM / HIGH / EXTREME 四档。 */
    static class Profile {
        String name = "张三";                                          // PUBLIC（未标注）
        @SensitiveField(Sensitivity.MEDIUM) Double gpa = 3.5;
        @SensitiveField(Sensitivity.HIGH) String phone = "13800000000";
        @SensitiveField(Sensitivity.EXTREME) Integer mentalScore = 88;
    }

    private Profile filterAs(boolean authenticated, Set<String> roles) {
        Profile p = new Profile();
        RequestContext.setAuthenticated(authenticated);
        RequestContext.setRoles(roles);
        advice.beforeBodyWrite(p, null, null, null, null, null);
        return p;
    }

    @Test
    void teacher_seesMedium_masksHighAndExtreme() {
        Profile p = filterAs(true, Set.of("teacher"));
        assertThat(p.name).isEqualTo("张三");   // PUBLIC 保留
        assertThat(p.gpa).isEqualTo(3.5);       // MEDIUM 保留
        assertThat(p.phone).isNull();           // HIGH 脱敏
        assertThat(p.mentalScore).isNull();     // EXTREME 脱敏
    }

    @Test
    void counselor_seesHigh_masksExtreme() {
        Profile p = filterAs(true, Set.of("counselor"));
        assertThat(p.gpa).isEqualTo(3.5);
        assertThat(p.phone).isEqualTo("13800000000"); // HIGH 保留
        assertThat(p.mentalScore).isNull();           // EXTREME 脱敏
    }

    @Test
    void psychologist_seesExtreme() {
        Profile p = filterAs(true, Set.of("psychologist"));
        assertThat(p.mentalScore).isEqualTo(88);
        assertThat(p.phone).isEqualTo("13800000000");
    }

    @Test
    void admin_seesEverything() {
        Profile p = filterAs(true, Set.of("admin"));
        assertThat(p.gpa).isEqualTo(3.5);
        assertThat(p.phone).isEqualTo("13800000000");
        assertThat(p.mentalScore).isEqualTo(88);
    }

    @Test
    void student_or_emptyRoles_seePublicOnly() {
        Profile p = filterAs(true, Set.of("student"));
        assertThat(p.name).isEqualTo("张三");   // PUBLIC 保留
        assertThat(p.gpa).isNull();
        assertThat(p.phone).isNull();
        assertThat(p.mentalScore).isNull();
    }

    @Test
    void internalCall_notAuthenticated_keepsAllFields() {
        // 内网 Feign 无 token → 放行不脱敏（保 AI 取数链路完整），即便无角色
        Profile p = filterAs(false, Set.of());
        assertThat(p.gpa).isEqualTo(3.5);
        assertThat(p.phone).isEqualTo("13800000000");
        assertThat(p.mentalScore).isEqualTo(88);
    }

    @Test
    void nestedCollection_isAlsoFiltered() {
        // 验证对象图递归：List 内的 Profile 也按角色脱敏
        RequestContext.setAuthenticated(true);
        RequestContext.setRoles(Set.of("teacher"));
        List<Profile> list = List.of(new Profile(), new Profile());
        advice.beforeBodyWrite(list, null, null, null, null, null);
        assertThat(list).allSatisfy(p -> {
            assertThat(p.gpa).isEqualTo(3.5);
            assertThat(p.mentalScore).isNull();
        });
    }
}
