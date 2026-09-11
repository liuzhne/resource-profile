package com.edu.student.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.student.entity.Attendance;
import com.edu.student.entity.Student;
import com.edu.student.service.AttendanceService;
import com.edu.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * H-1.2：考勤查询端点，供 mcp-student-data 的 get_attendance tool 调用（内网 Feign 匿名直调放行）。
 * 端用户须本人或教职工角色，否则 403。
 */
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentAttendanceController {

    private final AttendanceService attendanceService;
    private final StudentService studentService;
    private final AccessGuard accessGuard;

    @GetMapping("/{id}/attendance")
    public Result<List<Attendance>> listAttendance(
            @PathVariable("id") Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, ownerUserId(studentId), Roles.STAFF_VIEW)) {
            return Result.error(403, "无权访问该学生考勤");
        }
        return Result.success(attendanceService.listByStudentId(studentId, from, to));
    }

    @GetMapping("/{id}/attendance/summary")
    public Result<Map<String, Object>> attendanceSummary(
            @PathVariable("id") Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, ownerUserId(studentId), Roles.STAFF_VIEW)) {
            return Result.error(403, "无权访问该学生考勤");
        }
        return Result.success(attendanceService.summary(studentId, from, to));
    }

    /** 由 studentId 反查其 userId（用于"本人"判定）；学生不存在返回 null。 */
    private Long ownerUserId(Long studentId) {
        Student s = studentService.getById(studentId);
        return s == null ? null : s.getUserId();
    }
}
