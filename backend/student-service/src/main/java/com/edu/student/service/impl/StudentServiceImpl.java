package com.edu.student.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.student.entity.Student;
import com.edu.student.mapper.StudentMapper;
import com.edu.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import com.edu.common.exception.BusinessException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;

    @Override
    public Page<Student> list(Integer page, Integer size, String name, String dept, String grade) {
        Page<Student> pageParam = new Page<>(Math.max(1, page), Math.max(1, Math.min(100, size)));
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(name)) {
            wrapper.like(Student::getName, name);
        }
        if (StringUtils.hasText(dept)) {
            wrapper.eq(Student::getDeptName, dept);
        }
        if (StringUtils.hasText(grade)) {
            wrapper.eq(Student::getGrade, grade);
        }

        wrapper.orderByDesc(Student::getCreateTime).orderByDesc(Student::getId);
        return studentMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Student getById(Long id) {
        return studentMapper.selectById(id);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void save(Student student) {
        validateAccount(student, null);
        if (studentMapper.countCodeAnyRow(student.getStudentId(), 0L) > 0) throw new BusinessException(409, "档案编号已存在");
        try { studentMapper.insert(student); }
        catch (DuplicateKeyException conflict) { throw new BusinessException(409, "档案编号已存在"); }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(Student student) {
        Student current = studentMapper.selectById(student.getId());
        if (current == null) throw new BusinessException(404, "档案不存在");
        validateAccount(student, current);
        // A field hidden from teacher writers is omitted, so retain it; explicit admin null clears it.
        if (student.getBirthDate() == null && !student.isClearBirthDate()) student.setBirthDate(current.getBirthDate());
        if (studentMapper.countCodeAnyRow(student.getStudentId(), student.getId()) > 0) throw new BusinessException(409, "档案编号已存在");
        try { studentMapper.updateById(student); }
        catch (DuplicateKeyException conflict) { throw new BusinessException(409, "档案编号已存在"); }
    }

    @Override
    public void delete(Long id) {
        studentMapper.deleteById(id);
    }

    @Override
    public List<Long> listActiveIds() {
        return studentMapper.selectList(
                Wrappers.<Student>lambdaQuery()
                        .select(Student::getId)
                        .eq(Student::getStatus, 1)
        ).stream().map(Student::getId).toList();
    }
    private void validateAccount(Student value, Student current) {
        if (value.getUserId() == null || studentMapper.lockValidAccount(value.getUserId()) == null) throw new BusinessException(400, "关联账号必须是启用的学生账号");
        // Existing shared bindings may be corrected separately: unchanged ownership does not create a new duplicate.
        if ((current == null || !value.getUserId().equals(current.getUserId())) && studentMapper.countOtherOwners(value.getUserId(), current == null ? 0L : current.getId()) > 0) throw new BusinessException(409, "该学生账号已关联其他档案，请先校正绑定");
    }

}
