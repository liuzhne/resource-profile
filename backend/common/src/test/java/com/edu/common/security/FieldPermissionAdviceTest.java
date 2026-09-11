package com.edu.common.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FieldPermissionAdvice.canSee 角色×分级矩阵单测（纯静态方法，无 Spring 上下文）。
 * 对齐 docs/educare/FIELD_PERMISSION.md §4 列级矩阵。
 */
class FieldPermissionAdviceTest {

    @Test
    void admin_seesEverything() {
        Set<String> admin = Set.of("admin");
        for (Sensitivity t : Sensitivity.values()) {
            assertThat(FieldPermissionAdvice.canSee(admin, t)).as("admin %s", t).isTrue();
        }
    }

    @Test
    void psychologist_seesUpToExtreme() {
        Set<String> r = Set.of("psychologist");
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.PUBLIC)).isTrue();
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.MEDIUM)).isTrue();
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.HIGH)).isTrue();
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.EXTREME)).isTrue();
    }

    @Test
    void counselor_seesUpToHigh_notExtreme() {
        Set<String> r = Set.of("counselor");
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.HIGH)).isTrue();
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.MEDIUM)).isTrue();
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.EXTREME)).isFalse();
    }

    @Test
    void teacher_seesUpToMedium_notHigh() {
        Set<String> r = Set.of("teacher");
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.MEDIUM)).isTrue();
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.HIGH)).isFalse();
        assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.EXTREME)).isFalse();
    }

    @Test
    void student_or_empty_seesPublicOnly() {
        for (Set<String> r : new Set[]{Set.of("student"), Set.<String>of()}) {
            assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.PUBLIC)).isTrue();
            assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.MEDIUM)).isFalse();
            assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.HIGH)).isFalse();
            assertThat(FieldPermissionAdvice.canSee(r, Sensitivity.EXTREME)).isFalse();
        }
    }
}
