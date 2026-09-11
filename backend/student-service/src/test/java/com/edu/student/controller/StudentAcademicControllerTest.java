package com.edu.student.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.util.JwtUtil;
import com.edu.student.entity.AcademicRecord;
import com.edu.student.entity.Student;
import com.edu.student.service.AcademicRecordService;
import com.edu.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StudentAcademicController 越权(IDOR)单测：成绩按 studentId→userId 反查做属主校验。
 */
class StudentAcademicControllerTest {

    private static final String AUTH = "Bearer tok";

    private AcademicRecordService academicRecordService;
    private StudentService studentService;
    private JwtUtil jwtUtil;
    private StudentAcademicController controller;

    @BeforeEach
    void setUp() {
        academicRecordService = mock(AcademicRecordService.class);
        studentService = mock(StudentService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new StudentAcademicController(academicRecordService, studentService, new AccessGuard(jwtUtil));
    }

    private Student studentOwnedBy(long userId) {
        Student s = new Student();
        s.setId(1L);
        s.setUserId(userId);
        return s;
    }

    @Test
    void listAcademic_otherStudent_forbidden_andNoServiceCall() {
        when(studentService.getById(1L)).thenReturn(studentOwnedBy(7L));
        when(jwtUtil.getSubject("tok")).thenReturn("9");
        when(jwtUtil.parseRoles("tok")).thenReturn(Set.of("student"));

        Result<List<AcademicRecord>> r = controller.listAcademic(1L, null, AUTH);

        assertThat(r.getCode()).isEqualTo(403);
        verify(academicRecordService, never()).listByStudentId(any(), any());
    }

    @Test
    void listAcademic_self_ok() {
        when(studentService.getById(1L)).thenReturn(studentOwnedBy(7L));
        when(jwtUtil.getSubject("tok")).thenReturn("7");
        when(academicRecordService.listByStudentId(1L, null)).thenReturn(List.of(new AcademicRecord()));

        Result<List<AcademicRecord>> r = controller.listAcademic(1L, null, AUTH);

        assertThat(r.getCode()).isEqualTo(200);
        verify(academicRecordService).listByStudentId(1L, null);
    }

    @Test
    void listAcademic_internalNoToken_ok() {
        when(academicRecordService.listByStudentId(1L, null)).thenReturn(List.of(new AcademicRecord()));

        Result<List<AcademicRecord>> r = controller.listAcademic(1L, null, null);

        assertThat(r.getCode()).isEqualTo(200);
        verify(academicRecordService).listByStudentId(1L, null);
    }
}
