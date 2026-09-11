package com.edu.auth.service.impl;

import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.LoginResponse;
import com.edu.auth.dto.UserInfoResponse;
import com.edu.auth.entity.Role;
import com.edu.auth.entity.User;
import com.edu.auth.mapper.RoleMapper;
import com.edu.auth.mapper.UserMapper;
import com.edu.auth.service.AuthService;
import com.edu.common.exception.BusinessException;
import com.edu.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            // 用户不存在与密码错误返回同一 401 消息，避免用户名枚举
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 检查用户状态
        if (user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用");
        }

        // 生成 JWT Token
        Map<String, Object> claims = buildClaims(user);

        String accessToken = jwtUtil.generateAccessToken(user.getId().toString(), claims);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

        // 存入 Redis
        redisTemplate.opsForValue().set(
                "token:" + user.getId(),
                accessToken,
                24,
                TimeUnit.HOURS
        );

        LoginResponse response = new LoginResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(24 * 60 * 60L);

        return response;
    }

    /**
     * G-2.2-b：生成 JWT claims，含 {@code roles} 字符串数组，供
     * {@link com.edu.common.security.FieldPermissionAdvice} 决策。
     * 把这步集中到一个方法，避免 login / refreshToken 两处不一致导致刷新后角色丢失。
     */
    private Map<String, Object> buildClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("userType", user.getUserType());
        List<String> roleCodes = roleMapper.selectRolesByUserId(user.getId()).stream()
                .map(Role::getCode)
                .collect(Collectors.toList());
        claims.put("roles", roleCodes);
        return claims;
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 获取用户角色
        List<Role> roles = roleMapper.selectRolesByUserId(userId);
        List<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());

        UserInfoResponse response = new UserInfoResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setUserType(user.getUserType());
        response.setRoles(roleCodes);

        return response;
    }

    @Override
    public void logout(Long userId) {
        redisTemplate.delete("token:" + userId);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        // 解析 refresh token
        String userId = jwtUtil.getSubject(refreshToken);

        // 生成新的 access token
        User user = userMapper.selectById(Long.valueOf(userId));
        Map<String, Object> claims = buildClaims(user);

        String newAccessToken = jwtUtil.generateAccessToken(userId, claims);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        // 更新 Redis
        redisTemplate.opsForValue().set(
                "token:" + userId,
                newAccessToken,
                24,
                TimeUnit.HOURS
        );

        LoginResponse response = new LoginResponse();
        response.setToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(24 * 60 * 60L);

        return response;
    }
}
