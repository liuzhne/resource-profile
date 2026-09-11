package com.edu.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.user.entity.User;
import com.edu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccessGuard accessGuard;

    @GetMapping("/list")
    public Result<Page<User>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权管理用户");
        }
        Page<User> result = userService.list(page, size, username, role, status);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id,
                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权管理用户");
        }
        User user = userService.getById(id);
        return Result.success(user);
    }

    @PostMapping
    public Result<Void> save(@RequestBody User user,
                             @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权管理用户");
        }
        userService.save(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody User user,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权管理用户");
        }
        user.setId(id);
        userService.update(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.allowSelfRoleOrInternal(authHeader, null, Roles.ADMIN)) {
            return Result.error(403, "无权管理用户");
        }
        userService.delete(id);
        return Result.success();
    }
}
