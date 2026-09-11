package com.edu.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.user.entity.User;
import com.edu.user.mapper.UserMapper;
import com.edu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Retrieve a paginated list of users filtered by optional criteria.
     *
     * @param page     the current page number (1-based)
     * @param size     the maximum number of users per page
     * @param username optional substring to match against usernames (applies SQL LIKE)
     * @param role     optional user type filter
     * @param status   optional user status filter
     * @return a page of users matching the provided filters, ordered by creation time descending
     */
    @Override
    public Page<User> list(Integer page, Integer size, String username, Integer role, Integer status) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (username != null && !username.isEmpty()) {
            wrapper.like(User::getUsername, username);
        }
        if (role != null) {
            wrapper.eq(User::getUserType, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }

        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * Creates a new user record after validating and ensuring the user's password is encoded.
     *
     * The provided user's password must not be null or blank; the password will be encoded (unless
     * already bcrypt-formatted) before insertion.
     *
     * @param user the user to create; its `password` field must be non-null and non-blank
     * @throws RuntimeException if the user's password is null or blank
     */
    @Override
    public void save(User user) {
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new RuntimeException("密码不能为空");
        }
        user.setPassword(encodePassword(user.getPassword()));
        userMapper.insert(user);
    }

    /**
     * Updates an existing user record and processes the password field if present.
     *
     * <p>If the provided user's password is null, the stored password is left unchanged.
     * If the password is an empty or whitespace string, the password field is cleared (set to null)
     * to avoid updating it. If the password is a non-blank value, it is encoded before persisting.
     *
     * @param user the user entity containing updated fields; may include a password to be processed
     */
    @Override
    public void update(User user) {
        if (user.getPassword() != null) {
            if (user.getPassword().isBlank()) {
                user.setPassword(null);
            } else {
                user.setPassword(encodePassword(user.getPassword()));
            }
        }
        userMapper.updateById(user);
    }

    /**
     * Delete the user identified by the given primary key.
     *
     * @param id the primary key of the user to delete
     */
    @Override
    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    /**
     * Produce a bcrypt-encoded password or return the input unchanged if it is already bcrypt-formatted.
     *
     * @param rawPassword the plaintext password or an existing bcrypt hash; must not be null
     * @return the original `rawPassword` if it starts with "$2a$", "$2b$", or "$2y$"; otherwise the bcrypt-encoded form of `rawPassword`
     */
    private String encodePassword(String rawPassword) {
        if (rawPassword.startsWith("$2a$") || rawPassword.startsWith("$2b$") || rawPassword.startsWith("$2y$")) {
            return rawPassword;
        }
        return passwordEncoder.encode(rawPassword);
    }
}
