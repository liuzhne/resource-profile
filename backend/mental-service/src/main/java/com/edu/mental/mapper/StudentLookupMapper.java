package com.edu.mental.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface StudentLookupMapper {

    @Select("SELECT id FROM student_info WHERE user_id = #{userId} AND deleted = 0 ORDER BY id")
    List<Long> selectStudentIdsByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM student_info WHERE id = #{studentId} AND deleted = 0")
    long countActiveStudent(Long studentId);
}
