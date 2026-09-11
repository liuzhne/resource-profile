package com.edu.student.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.edu.student.entity.Attendance;
import com.edu.student.mapper.AttendanceMapper;
import com.edu.student.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceMapper attendanceMapper;

    @Override
    public List<Attendance> listByStudentId(Long studentId, LocalDate from, LocalDate to) {
        return attendanceMapper.selectList(
                Wrappers.<Attendance>lambdaQuery()
                        .eq(Attendance::getStudentId, studentId)
                        .ge(from != null, Attendance::getAttendDate, from)
                        .le(to != null, Attendance::getAttendDate, to)
                        .orderByAsc(Attendance::getAttendDate)
        );
    }

    @Override
    public Map<String, Object> summary(Long studentId, LocalDate from, LocalDate to) {
        List<Attendance> records = listByStudentId(studentId, from, to);
        int total = records.size();
        int present = 0, late = 0, absent = 0, leave = 0;
        for (Attendance a : records) {
            switch (a.getStatus() == null ? -1 : a.getStatus()) {
                case 0 -> present++;
                case 1 -> late++;
                case 2 -> absent++;
                case 3 -> leave++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("present", present);
        result.put("late", late);
        result.put("absent", absent);
        result.put("leave", leave);
        // 出勤率 = present / total （没有记录时 1.0，避免除零让 LLM 拿到 NaN）
        double rate = total == 0 ? 1.0 : (double) present / total;
        result.put("attendanceRate", Math.round(rate * 1000) / 1000.0);
        return result;
    }
}
