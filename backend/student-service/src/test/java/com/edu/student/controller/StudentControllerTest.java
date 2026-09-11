package com.edu.student.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import com.edu.student.entity.Student;
import com.edu.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StudentController 越权(IDOR)单测：真实 AccessGuard + mock JwtUtil。
 * 读端点本人/教职工/内网可见；写/列端点限 admin/teacher；学生越权 403。
 */
class StudentControllerTest {

    private static final String AUTH = "Bearer tok";

    private StudentService studentService;
    private JwtUtil jwtUtil;
    private StudentController controller;

    @BeforeEach
    void setUp() {
        studentService = mock(StudentService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new StudentController(studentService, new AccessGuard(jwtUtil));
    }

    private Student studentOwnedBy(long userId) {
        Student s = new Student();
        s.setId(1L);
        s.setUserId(userId);
        return s;
    }

    @Test
    void getById_self_ok() {
        when(studentService.getById(1L)).thenReturn(studentOwnedBy(7L));
        when(jwtUtil.getSubject("tok")).thenReturn("7");

        Result<Student> r = controller.getById(1L, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isNotNull();
    }

    @Test
    void getById_otherStudent_forbidden() {
        when(studentService.getById(1L)).thenReturn(studentOwnedBy(7L));
        when(jwtUtil.getSubject("tok")).thenReturn("9");          // 非本人
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student")); // 非教职工

        Result<Student> r = controller.getById(1L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
    }

    @Test
    void getById_staff_ok() {
        when(studentService.getById(1L)).thenReturn(studentOwnedBy(7L));
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("teacher"));

        Result<Student> r = controller.getById(1L, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void getById_internalNoToken_ok() {
        when(studentService.getById(1L)).thenReturn(studentOwnedBy(7L));

        Result<Student> r = controller.getById(1L, null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void list_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<?> r = controller.list(1, 10, null, null, null, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(studentService, never()).list(anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void delete_student_forbidden_andNoServiceCall() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<Void> r = controller.delete(5L, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(studentService, never()).delete(anyLong());
    }

    @Test
    void update_staff_ok() {
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("admin"));

        Result<Void> r = controller.update(5L, new Student(), AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        verify(studentService).update(any(Student.class));
    }
}
