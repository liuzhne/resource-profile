package com.edu.user.controller;
import com.edu.user.service.RoleDirectoryService;
import com.edu.user.dto.RoleWriteRequest;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.common.result.Result;
import com.edu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/user/roles") @RequiredArgsConstructor
public class RoleController {
    private final RoleDirectoryService service;
    private final AccessGuard guard;
    private void require(String auth) { if(!guard.allowSelfRoleOrInternal(auth,null,Roles.ADMIN)) throw new BusinessException(403,"无权管理角色"); }
    @GetMapping public Result<List<Map<String,Object>>> list(@RequestHeader(value="Authorization",required=false) String auth) { require(auth); return Result.success(service.list()); }
    @GetMapping("/permissions") public Result<List<Map<String,Object>>> permissions(@RequestHeader(value="Authorization",required=false) String auth) { require(auth); return Result.success(service.permissions()); }
    @GetMapping("/{id}/permissions") public Result<List<Long>> selected(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String auth) { require(auth); return Result.success(service.selected(id)); }
    @PutMapping("/{id}/permissions") public Result<Void> assign(@PathVariable Long id,@RequestBody List<Long> ids,@RequestHeader(value="Authorization",required=false) String auth) { require(auth); service.setPermissions(id,ids); return Result.success(); }
    @PostMapping public Result<Void> create(@Valid @RequestBody RoleWriteRequest request,@RequestHeader(value="Authorization",required=false) String auth) { require(auth); service.save(null,request); return Result.success(); }
    @PutMapping("/{id}") public Result<Void> update(@PathVariable Long id,@Valid @RequestBody RoleWriteRequest request,@RequestHeader(value="Authorization",required=false) String auth) { require(auth); service.save(id,request); return Result.success(); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String auth) { require(auth); service.delete(id); return Result.success(); }
}
