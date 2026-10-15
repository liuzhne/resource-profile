package com.edu.teacher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.teacher.entity.Teacher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {
    @Select("SELECT DISTINCT dept_name FROM teacher_info WHERE deleted=0 AND dept_name IS NOT NULL AND TRIM(dept_name)<>'' ORDER BY dept_name")
    List<String> departments();
    @Select("SELECT id, username FROM sys_user WHERE deleted=0 AND status=1 AND user_type=1 ORDER BY username")
    List<Map<String, Object>> availableAccounts();
    @Select("SELECT id FROM sys_user WHERE id=#{userId} AND deleted=0 AND status=1 AND user_type=1 FOR UPDATE")
    Long lockValidAccount(@Param("userId") Long userId);
    @Select("SELECT DISTINCT title FROM teacher_info WHERE deleted=0 AND title IS NOT NULL AND TRIM(title)<>'' ORDER BY title")
    List<String> titles();

    @Select("SELECT COUNT(*) FROM teacher_info WHERE employee_id=#{code} AND id<>#{excludeId}")
    Long countCodeAnyRow(@Param("code") String code, @Param("excludeId") Long excludeId);

}
