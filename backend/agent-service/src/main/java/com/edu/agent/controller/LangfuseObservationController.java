package com.edu.agent.controller;

import com.edu.agent.dto.LangfuseObservationPage;
import com.edu.agent.service.LangfuseObservationService;
import com.edu.common.exception.BusinessException;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin JWT only, including direct service access; internal credentials cannot authorize this page. */
@RestController
@RequestMapping("/agent/api/v1/observability")
@RequiredArgsConstructor
public class LangfuseObservationController {
    private final AccessGuard accessGuard;
    private final LangfuseObservationService observationService;

    @GetMapping("/observations")
    public Result<LangfuseObservationPage> observations(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String traceId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!accessGuard.hasAnyRole(authHeader, Roles.ADMIN)) return Result.error(403, "仅管理员可查看追踪记录");
        try {
            return Result.success(observationService.observations(from, to, limit, cursor, traceId));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }
}
