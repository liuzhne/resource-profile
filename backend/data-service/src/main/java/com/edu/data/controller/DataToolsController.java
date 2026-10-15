package com.edu.data.controller;

import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.data.dto.*;
import com.edu.data.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
public class DataToolsController {
    private final DashboardService service;
    private final AccessGuard guard;

    @GetMapping("/search")
    public Result<List<SearchResultDto>> search(@RequestParam String q,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!guard.hasAnyRole(auth, Roles.STAFF_VIEW)) return Result.error(403, "无权搜索师生档案");
        return Result.success(service.search(q, limit));
    }

    @GetMapping("/notifications")
    public Result<TodoPageDto> notifications(@RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!guard.hasAnyRole(auth, Roles.STAFF_VIEW)) return Result.error(403, "无权查看待处理通知");
        return Result.success(service.getTodos(limit));
    }
}
