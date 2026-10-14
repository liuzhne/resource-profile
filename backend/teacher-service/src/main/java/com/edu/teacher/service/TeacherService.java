package com.edu.teacher.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.teacher.entity.Teacher;

public interface TeacherService {

    Page<Teacher> list(Integer page, Integer size, String name, String dept, String title);

    Teacher getById(Long id);

    /** AUTHZ-MCP-20261013：按 sys_user.id 查教师档案（MCP 判定"教师只能看本院系学生"用）。 */
    Teacher getByUserId(Long userId);

    void save(Teacher teacher);

    void update(Teacher teacher);

    void delete(Long id);
}
