package com.edu.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.student.entity.Attendance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {
}
