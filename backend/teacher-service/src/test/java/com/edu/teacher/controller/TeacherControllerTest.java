package com.edu.teacher.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import com.edu.teacher.entity.Teacher;
import com.edu.teacher.service.TeacherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherControllerTest {

    private static final String AUTH = "Bearer tok";

    private TeacherService teacherService;
    private JwtUtil jwtUtil;
    private TeacherController controller;

    @BeforeEach
    void setUp() {
        teacherService = mock(TeacherService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new TeacherController(teacherService, new AccessGuard(jwtUtil));
        when(jwtUtil.getSubject("tok")).thenReturn("7");
    }

    private void asRole(String role) {
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of(role));
    }

    @Test
    void list_staffAllowed() {
        asRole("teacher");
        when(teacherService.list(1, 10, null, null, null)).thenReturn(new Page<>());
        assertThat(controller.list(1, 10, null, null, null, AUTH).getCode()).isEqualTo(200);
        verify(teacherService).list(1, 10, null, null, null);
    }

    @Test
    void list_studentDeniedWithoutServiceCall() {
        asRole("student");
        Result<Page<Teacher>> result = controller.list(1, 10, null, null, null, AUTH);
        assertThat(result.getCode()).isEqualTo(403);
        verify(teacherService, never()).list(any(), any(), any(), any(), any());
    }

    @Test
    void get_internalAllowed() {
        when(teacherService.getById(3L)).thenReturn(new Teacher());
        assertThat(controller.getById(3L, null).getCode()).isEqualTo(200);
        verify(teacherService).getById(3L);
    }

    @Test
    void save_adminAllowed() {
        asRole("admin");
        Teacher teacher = new Teacher();
        assertThat(controller.save(teacher, AUTH).getCode()).isEqualTo(200);
        verify(teacherService).save(teacher);
    }

    @Test
    void save_teacherDenied() {
        asRole("teacher");
        Teacher teacher = new Teacher();
        assertThat(controller.save(teacher, AUTH).getCode()).isEqualTo(403);
        verify(teacherService, never()).save(any());
    }

    @Test
    void update_adminAllowedAndUsesPathId() {
        asRole("admin");
        Teacher teacher = new Teacher();
        assertThat(controller.update(9L, teacher, AUTH).getCode()).isEqualTo(200);
        assertThat(teacher.getId()).isEqualTo(9L);
        verify(teacherService).update(teacher);
    }

    @Test
    void delete_adminAllowed() {
        asRole("admin");
        assertThat(controller.delete(9L, AUTH).getCode()).isEqualTo(200);
        verify(teacherService).delete(9L);
    }
}
