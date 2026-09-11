package com.edu.student.service;

import com.edu.student.entity.AcademicRecord;

import java.util.List;

public interface AcademicRecordService {

    /** 列出某学生历次成绩；term 为空表示全部学期 */
    List<AcademicRecord> listByStudentId(Long studentId, String term);
}
