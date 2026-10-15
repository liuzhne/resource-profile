package com.edu.auth.controller;

import com.edu.auth.dto.ProfileUpdateRequest;
import com.edu.auth.dto.PasswordChangeRequest;
import com.edu.auth.dto.UserInfoResponse;
import com.edu.auth.service.AccountService;
import com.edu.auth.service.AuthService;
import com.edu.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AccountController {
    private final AuthService auth;
    private final AccountService accounts;

    @PutMapping("/profile")
    public Result<UserInfoResponse> update(@RequestHeader("Authorization") String header,
                                          @Valid @RequestBody ProfileUpdateRequest request) {
        Long id = auth.requireSession(AuthController.accessToken(header));
        accounts.update(id, request);
        return Result.success(auth.getUserInfo(id));
    }

    @PostMapping("/password")
    public Result<Void> password(@RequestHeader("Authorization") String header,
            @Valid @RequestBody PasswordChangeRequest request, HttpServletRequest http, HttpServletResponse response) {
        Long id = auth.requireSession(AuthController.accessToken(header));
        accounts.changePassword(id, request);
        AuthController.refreshCookie(http, response, null);
        return Result.success();
    }
}
