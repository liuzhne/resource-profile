package com.edu.teacher.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.teacher.entity.Teacher;
import com.edu.teacher.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final AccessGuard accessGuard;

    @GetMapping("/list")
    public Result<Page<Teacher>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String title,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权浏览教师列表");
        }
        Page<Teacher> result = teacherService.list(page, size, name, dept, title);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Teacher> getById(@PathVariable Long id,
                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.STAFF_VIEW)) {
            return Result.error(403, "无权访问教师档案");
        }
        Teacher teacher = teacherService.getById(id);
        return Result.success(teacher);
    }

    /**
     * AUTHZ-MCP-20261013：按 sys_user.id 查教师档案。只允许本人或已验证的内部调用（mcp-student-data 用它取
     * 教师院系，判定"教师只能看本院系学生"）；其他人（含其他教职工）一律 403。
     */
    @GetMapping("/by-user/{userId}")
    public Result<Teacher> getByUserId(@PathVariable Long userId,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, userId)) {
            return Result.error(403, "无权访问教师档案");
        }
        return Result.success(teacherService.getByUserId(userId));
    }

    @PostMapping
    public Result<Void> save(@RequestBody Teacher teacher,
                             @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权新增教师");
        }
        teacherService.save(teacher);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Teacher teacher,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权修改教师");
        }
        teacher.setId(id);
        teacherService.update(teacher);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权删除教师");
        }
        teacherService.delete(id);
        return Result.success();
    }
}
