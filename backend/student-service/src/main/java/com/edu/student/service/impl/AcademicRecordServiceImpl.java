package com.edu.student.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.edu.student.entity.AcademicRecord;
import com.edu.student.mapper.AcademicRecordMapper;
import com.edu.student.service.AcademicRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicRecordServiceImpl implements AcademicRecordService {

    private final AcademicRecordMapper academicRecordMapper;

    @Override
    public List<AcademicRecord> listByStudentId(Long studentId, String term) {
        return academicRecordMapper.selectList(
                Wrappers.<AcademicRecord>lambdaQuery()
                        .eq(AcademicRecord::getStudentId, studentId)
                        .eq(StringUtils.hasText(term), AcademicRecord::getTerm, term)
                        .orderByDesc(AcademicRecord::getTerm)
                        .orderByAsc(AcademicRecord::getCourseCode)
        );
    }
}
