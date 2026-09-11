package com.edu.student.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.student.entity.Student;
import com.edu.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生档案端点。越权(IDOR)控制经 {@link AccessGuard#allowSelfRoleOrInternal}：
 * 端用户（经网关，必带 token）须本人或教职工角色；mcp-student-data 等内网 Feign 匿名直调放行。
 */
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final AccessGuard accessGuard;

    /** 列全部学生：仅教职工或内网。学生不得浏览全体。 */
    @GetMapping("/list")
    public Result<Page<Student>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String grade,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权浏览学生列表");
        }
        return Result.success(studentService.list(page, size, name, dept, grade));
    }

    /** EduCare 定时扫描用：在读学生 ID 列表（status=1）。仅返回 id（无 PII），由内网扫描直调。 */
    @GetMapping("/ids")
    public Result<List<Long>> listActiveIds() {
        return Result.success(studentService.listActiveIds());
    }

    /** 单个学生档案：本人、教职工或内网可见，否则 403。 */
    @GetMapping("/{id}")
    public Result<Student> getById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Student student = studentService.getById(id);
        Long ownerUserId = student == null ? null : student.getUserId();
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, ownerUserId, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权访问该学生档案");
        }
        return Result.success(student);
    }

    /** 新增学生：仅 admin/teacher（或内网）。 */
    @PostMapping
    public Result<Void> save(
            @RequestBody Student student,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STUDENT_WRITE)) {
            return Result.error(403, "无权新增学生");
        }
        studentService.save(student);
        return Result.success();
    }

    /** 修改学生：仅 admin/teacher（或内网）。 */
    @PutMapping("/{id}")
    public Result<Void> update(
            @PathVariable Long id,
            @RequestBody Student student,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STUDENT_WRITE)) {
            return Result.error(403, "无权修改学生");
        }
        student.setId(id);
        studentService.update(student);
        return Result.success();
    }

    /** 删除学生：仅 admin/teacher（或内网）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STUDENT_WRITE)) {
            return Result.error(403, "无权删除学生");
        }
        studentService.delete(id);
        return Result.success();
    }
}
