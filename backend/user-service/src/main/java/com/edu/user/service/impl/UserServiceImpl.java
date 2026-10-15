package com.edu.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.user.entity.User;
import com.edu.user.mapper.UserMapper;
import com.edu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public Page<User> list(Integer page, Integer size, String username, Integer role, Integer status) {
        if (page == null || page < 1 || size == null || size < 1 || size > 100)
            throw new com.edu.common.exception.BusinessException(400, "分页参数无效");
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (username != null && !username.isEmpty()) {
            wrapper.like(User::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (role != null) wrapper.apply("EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=sys_user.id AND r.id={0} AND r.deleted=0 AND r.status=1)", role);

        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public void save(User user) {
        userMapper.insert(user);
    }

    @Override
    public void update(User user) {
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        userMapper.deleteById(id);
    }
}
