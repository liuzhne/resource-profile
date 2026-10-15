package com.edu.teacher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.teacher.entity.Teacher;
import com.edu.teacher.mapper.TeacherMapper;
import com.edu.teacher.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import com.edu.common.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private final TeacherMapper teacherMapper;

    @Override
    public Page<Teacher> list(Integer page, Integer size, String name, String dept, String title) {
        Page<Teacher> pageParam = new Page<>(Math.max(1, page), Math.max(1, Math.min(100, size)));
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

        wrapper.orderByDesc(Teacher::getCreateTime).orderByDesc(Teacher::getId);
        return teacherMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Teacher getById(Long id) {
        return teacherMapper.selectById(id);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void save(Teacher teacher) {
        validateAccount(teacher, null);
        if (teacherMapper.countCodeAnyRow(teacher.getEmployeeId(), 0L) > 0) throw new BusinessException(409, "档案编号已存在");
        try { teacherMapper.insert(teacher); }
        catch (DuplicateKeyException conflict) { throw new BusinessException(409, "档案编号已存在"); }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(Teacher teacher) {
        Teacher current = teacherMapper.selectById(teacher.getId());
        if (current == null) throw new BusinessException(404, "档案不存在");
        validateAccount(teacher, current);
        if (teacherMapper.countCodeAnyRow(teacher.getEmployeeId(), teacher.getId()) > 0) throw new BusinessException(409, "档案编号已存在");
        try { teacherMapper.updateById(teacher); }
        catch (DuplicateKeyException conflict) { throw new BusinessException(409, "档案编号已存在"); }
    }

    @Override
    public void delete(Long id) {
        teacherMapper.deleteById(id);
    }
    private void validateAccount(Teacher value, Teacher current) {
        if (value.getUserId() == null || teacherMapper.lockValidAccount(value.getUserId()) == null) throw new BusinessException(400, "关联账号必须是启用的教师账号");
    }

}
