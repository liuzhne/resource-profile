package com.edu.student.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.student.entity.AcademicRecord;
import com.edu.student.entity.Student;
import com.edu.student.service.AcademicRecordService;
import com.edu.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * H-1.2：成绩查询端点，供 mcp-student-data 的 get_academic_history tool 调用（内网 Feign 匿名直调放行）。
 * 端用户须本人或教职工角色，否则 403。
 */
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentAcademicController {

    private final AcademicRecordService academicRecordService;
    private final StudentService studentService;
    private final AccessGuard accessGuard;

    @GetMapping("/{id}/academic")
    public Result<List<AcademicRecord>> listAcademic(
            @PathVariable("id") Long studentId,
            @RequestParam(required = false) String term,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, ownerUserId(studentId), Roles.STAFF_VIEW)) {
            return Result.error(403, "无权访问该学生成绩");
        }
        return Result.success(academicRecordService.listByStudentId(studentId, term));
    }

    /** 由 studentId 反查其 userId（用于"本人"判定）；学生不存在返回 null。 */
    private Long ownerUserId(Long studentId) {
        Student s = studentService.getById(studentId);
        return s == null ? null : s.getUserId();
    }
}
