package com.edu.student.service;

import com.edu.student.entity.Attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AttendanceService {

    /** 区间内逐日明细 */
    List<Attendance> listByStudentId(Long studentId, LocalDate from, LocalDate to);

    /** 区间内 aggregate：出勤率、缺勤次数、迟到次数、请假次数、总天数 */
    Map<String, Object> summary(Long studentId, LocalDate from, LocalDate to);
}
