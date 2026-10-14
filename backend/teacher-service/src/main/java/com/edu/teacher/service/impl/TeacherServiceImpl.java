package com.edu.teacher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.teacher.entity.Teacher;
import com.edu.teacher.mapper.TeacherMapper;
import com.edu.teacher.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private final TeacherMapper teacherMapper;

    @Override
    public Page<Teacher> list(Integer page, Integer size, String name, String dept, String title) {
        Page<Teacher> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(name)) {
            wrapper.like(Teacher::getName, name);
        }
        if (StringUtils.hasText(dept)) {
            wrapper.eq(Teacher::getDeptName, dept);
        }
        if (StringUtils.hasText(title)) {
            wrapper.eq(Teacher::getTitle, title);
        }

        wrapper.orderByDesc(Teacher::getCreateTime);
        return teacherMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Teacher getById(Long id) {
        return teacherMapper.selectById(id);
    }

    @Override
    public Teacher getByUserId(Long userId) {
        return teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                .eq(Teacher::getUserId, userId)
                .last("LIMIT 1"));
    }

    @Override
    public void save(Teacher teacher) {
        teacherMapper.insert(teacher);
    }

    @Override
    public void update(Teacher teacher) {
        teacherMapper.updateById(teacher);
    }

    @Override
    public void delete(Long id) {
        teacherMapper.deleteById(id);
    }
}
