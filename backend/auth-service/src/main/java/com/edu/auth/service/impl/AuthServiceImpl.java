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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final RedisScript<Long> ISSUE_SESSION = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', 86400)
            if ARGV[2] == '' then redis.call('DEL', KEYS[2])
            else redis.call('SET', KEYS[2], ARGV[2], 'EX', 604800) end
            return 1
            """, Long.class);

    // Redis executes the comparison and deletion atomically, so a delayed logout cannot
    // revoke a newer login for the same user between a separate GET and DEL.
    private static final RedisScript<Long> REVOKE_SESSION = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[2])
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SESSION = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            local ttl = redis.call('TTL', KEYS[1])
            if ttl <= 0 then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ttl)
            redis.call('SET', KEYS[2], ARGV[3], 'EX', math.min(ttl, 86400))
            return 1
            """, Long.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        long started = System.nanoTime();
        // 查询用户
        User user = userMapper.selectByUsername(request.getUsername());
        long lookupDone = System.nanoTime();
        if (user == null) {
            // 用户不存在与密码错误返回同一 401 消息，避免用户名枚举
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLogin(user, 0, "");
            throw new BusinessException(401, "用户名或密码错误");
        }
        long passwordDone = System.nanoTime();

        // 检查用户状态
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        // Identity mutations lock this same immutable admin-role row before target users.
        // BCrypt above holds neither lock. READ_COMMITTED makes the following role queries
        // see the mutation that completed while this login waited for the coordinator.
        List<Long> coordinator = roleMapper.lockIdentityAdminRole();
        if (coordinator == null || coordinator.size() != 1) {
            throw new BusinessException(503, "身份权限协调记录不可用");
        }
        User current = userMapper.selectByIdForUpdate(user.getId());
        if (current == null || !java.util.Objects.equals(user.getPassword(), current.getPassword())) {
            throw new BusinessException(401, "账号已变化，请重新登录");
        }
        if (!Integer.valueOf(1).equals(current.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        user = current;

        // 生成 JWT Token
        Map<String, Object> claims = buildClaims(user);
        long claimsDone = System.nanoTime();

        String accessToken = jwtUtil.generateAccessToken(user.getId().toString(), claims);
        String refreshToken = Boolean.TRUE.equals(request.getRemember())
                ? jwtUtil.generateRefreshToken(user.getId().toString()) : null;
        long jwtDone = System.nanoTime();

        // 存入 Redis
        Long issued = redisTemplate.execute(ISSUE_SESSION,
                List.of("token:" + user.getId(), "refresh:" + user.getId()),
                accessToken, refreshToken == null ? "" : refreshToken);
        if (!Long.valueOf(1).equals(issued)) throw new BusinessException(503, "登录会话创建未确认");
        recordLogin(user, 1, String.join(",", (List<String>) claims.get("roles")));
        long sessionDone = System.nanoTime();
        if (sessionDone - started >= TimeUnit.MILLISECONDS.toNanos(500)) {
            // Phase durations only: never include username, password, user id, tokens or SQL.
            log.info("Slow login phases lookupMs={} passwordMs={} rolesMs={} jwtMs={} sessionMs={}",
                    TimeUnit.NANOSECONDS.toMillis(lookupDone - started),
                    TimeUnit.NANOSECONDS.toMillis(passwordDone - lookupDone),
                    TimeUnit.NANOSECONDS.toMillis(claimsDone - passwordDone),
                    TimeUnit.NANOSECONDS.toMillis(jwtDone - claimsDone),
                    TimeUnit.NANOSECONDS.toMillis(sessionDone - jwtDone));
        }

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
        List<String> permissions = roleMapper.selectPermissionsByUserId(user.getId());
        claims.put("permissions", permissions == null ? List.of() : permissions);
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
        response.setDeptName(user.getDeptName());
        response.setBio(user.getBio());
        List<String> permissions = roleMapper.selectPermissionsByUserId(userId);
        response.setPermissions(permissions == null ? List.of() : permissions);

        return response;
    }

    @Override
    public void logout(Long userId, String accessToken) {
        if (userId == null || userId <= 0 || accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(401, "登录状态无效");
        }
        Long removed = redisTemplate.execute(REVOKE_SESSION, List.of("token:" + userId, "refresh:" + userId), accessToken);
        // 0 means the old session is already gone or replaced: logout remains idempotent.
        if (removed == null || (removed != 0 && removed != 1)) {
            throw new BusinessException(503, "会话撤销未确认，请重试");
        }
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || !jwtUtil.validateToken(refreshToken)
                || !"refresh".equals(jwtUtil.getClaim(refreshToken, "tokenUse"))) {
            throw new BusinessException(401, "刷新会话无效，请重新登录");
        }
        String userId = jwtUtil.getSubject(refreshToken);
        Long ttl = redisTemplate.getExpire("refresh:" + userId, TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) throw new BusinessException(401, "刷新会话已失效");
        // Capture both absolute deadlines before DB/role queries; their latency must not extend JWT/cookie expiry.
        long refreshDeadline = Math.min(jwtUtil.getExpiration(refreshToken).getTime(), System.currentTimeMillis() + ttl * 1000);

        // 生成新的 access token
        User user = userMapper.selectById(Long.valueOf(userId));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            redisTemplate.delete(List.of("token:" + userId, "refresh:" + userId));
            throw new BusinessException(401, "账号不可用");
        }
        Map<String, Object> claims = buildClaims(user);

        String newAccessToken = jwtUtil.generateAccessToken(userId, claims);
        long remainingMillis = refreshDeadline - System.currentTimeMillis();
        if (remainingMillis < 1000) throw new BusinessException(401, "刷新会话已失效");
        String newRefreshToken = jwtUtil.generateRefreshTokenUntil(userId, new java.util.Date(refreshDeadline));

        // 更新 Redis
        Long rotated = redisTemplate.execute(ROTATE_SESSION,
                List.of("refresh:" + userId, "token:" + userId), refreshToken, newRefreshToken, newAccessToken);
        if (!Long.valueOf(1).equals(rotated)) throw new BusinessException(401, "刷新会话已使用或撤销");

        LoginResponse response = new LoginResponse();
        response.setToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(Math.min(24 * 60 * 60L, remainingMillis / 1000));

        return response;
    }

    @Override
    public Long requireSession(String accessToken) {
        try {
            var claims = jwtUtil.parseToken(accessToken);
            Long id = Long.valueOf(claims.getSubject());
            if (!accessToken.equals(redisTemplate.opsForValue().get("token:" + id))) {
                throw new BusinessException(401, "登录状态已失效");
            }
            return id;
        } catch (BusinessException e) { throw e; }
        catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new BusinessException(401, "登录状态无效");
        }
    }

    private void recordLogin(User user, int status, String roles) {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String ip = attrs instanceof org.springframework.web.context.request.ServletRequestAttributes request
                ? request.getRequest().getRemoteAddr() : "unknown";
        userMapper.recordLogin(user.getId(), user.getUsername(), roles, ip, status);
    }
}
