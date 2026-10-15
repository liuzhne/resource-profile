package com.edu.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.student.entity.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
    @Select("SELECT DISTINCT dept_name FROM student_info WHERE deleted=0 AND dept_name IS NOT NULL AND TRIM(dept_name)<>'' ORDER BY dept_name")
    List<String> departments();
    @Select("SELECT id, username FROM sys_user WHERE deleted=0 AND status=1 AND user_type=2 ORDER BY username")
    List<Map<String, Object>> availableAccounts();
    @Select("SELECT COUNT(*) FROM sys_user WHERE id=#{userId} AND deleted=0 AND status=1 AND user_type=2")
    long countValidAccount(@Param("userId") Long userId);
    @Select("SELECT DISTINCT grade FROM student_info WHERE deleted=0 AND grade IS NOT NULL AND TRIM(grade)<>'' ORDER BY grade DESC")
    List<String> grades();
    @Select("SELECT DISTINCT major_name FROM student_info WHERE deleted=0 AND major_name IS NOT NULL AND TRIM(major_name)<>'' ORDER BY major_name")
    List<String> majors();
    @Select("SELECT COUNT(*) FROM student_info WHERE user_id=#{userId} AND deleted=0 AND id<>#{excludeId}")
    long countOtherOwners(@Param("userId") Long userId, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM student_info WHERE student_id=#{code} AND id<>#{excludeId}")
    Long countCodeAnyRow(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** Serialize new/rebound ownership across service replicas without changing legacy shared rows. */
    @Select("SELECT id FROM sys_user WHERE id=#{userId} AND deleted=0 AND status=1 AND user_type=2 FOR UPDATE")
    Long lockValidAccount(@Param("userId") Long userId);

}
