package com.edu.auth.controller;

import com.edu.auth.dto.AccountRequest;
import com.edu.common.result.Result;
import com.edu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

@RestController @RequestMapping("/auth/support") @RequiredArgsConstructor
public class AccountRequestController {
    private final JdbcTemplate db;
    private final StringRedisTemplate redis;
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AccountRequest request, HttpServletRequest http) {
        // One request per minute per actual peer; Redis failures fail closed. No account-existence disclosure.
        String key="support-limit:"+http.getRemoteAddr();
        if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,"1",60,TimeUnit.SECONDS))) throw new BusinessException(429,"提交过于频繁，请稍后再试");
        db.update("INSERT INTO sys_account_request(type,username,contact,reason) VALUES(?,?,?,?)",request.getType(),request.getUsername(),request.getContact(),request.getReason());
        return Result.success();
    }
}
