package com.edu.auth.service;

import com.edu.auth.dto.PasswordChangeRequest;
import com.edu.auth.dto.ProfileUpdateRequest;
import com.edu.auth.mapper.UserMapper;
import com.edu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserMapper users;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;

    public void update(Long id, ProfileUpdateRequest request) {
        if (users.updateProfile(id, request.getNickname(), request.getEmail(), request.getPhone(), request.getBio()) != 1)
            throw new BusinessException(404, "账号不存在或已停用");
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        var user = users.selectById(id);
        if (user == null || !encoder.matches(request.getOldPassword(), user.getPassword()))
            throw new BusinessException(400, "原密码不正确");
        if (request.getNewPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(400, "新密码UTF-8长度不能超过72字节");
        if (request.getOldPassword().equals(request.getNewPassword()))
            throw new BusinessException(400, "新密码不能与原密码相同");
        if (users.changePassword(id, user.getPassword(), encoder.encode(request.getNewPassword())) != 1)
            throw new BusinessException(409, "密码已变化，请重新登录");
        Long revoked = redis.delete(List.of("token:" + id, "refresh:" + id));
        // Zero is already revoked; no confirmation or a Redis exception rolls back the password UPDATE.
        if (revoked == null) throw new BusinessException(503, "会话撤销未确认，请重试");
    }
}
