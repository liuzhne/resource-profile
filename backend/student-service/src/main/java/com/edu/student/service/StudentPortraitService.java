package com.edu.student.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.edu.common.exception.BusinessException;
import com.edu.student.dto.*;
import com.edu.student.entity.*;
import com.edu.student.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
@RequiredArgsConstructor
public class StudentPortraitService {
    private final StudentMapper studentMapper;
    private final StudentQualityRecordMapper recordMapper;
    public Map<String,List<String>> options() { return Map.of("departments",studentMapper.departments(),"grades",studentMapper.grades(),"majors",studentMapper.majors()); }
    public List<Map<String,Object>> accounts() { return studentMapper.availableAccounts(); }
    public Student requireStudent(Long id) { Student student = studentMapper.selectById(id); if (student == null) throw new BusinessException(404,"学生不存在"); return student; }
    public StudentRankDto rank(Long id) {
        Student student = requireStudent(id); StudentRankDto dto = new StudentRankDto(); dto.setScope("同学院、专业、年级的在读学生；GPA 降序，同分并列（竞赛排名）");
        if (student.getStatus() == null || student.getStatus() != 1 || student.getGpa() == null || blank(student.getDeptName()) || blank(student.getMajorName()) || blank(student.getGrade())) { dto.setReason("缺少完整排名条件或不是在读学生"); return dto; }
        var cohort = Wrappers.<Student>lambdaQuery().eq(Student::getStatus,1).eq(Student::getDeptName,student.getDeptName()).eq(Student::getMajorName,student.getMajorName()).eq(Student::getGrade,student.getGrade()).isNotNull(Student::getGpa);
        dto.setCohortSize(studentMapper.selectCount(cohort));
        dto.setRank(studentMapper.selectCount(cohort.clone().gt(Student::getGpa,student.getGpa()))+1);
        dto.setAvailable(true); return dto;
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    public List<StudentQualityRecord> records(Long id) { requireStudent(id); return recordMapper.selectList(Wrappers.<StudentQualityRecord>lambdaQuery().eq(StudentQualityRecord::getStudentId,id).orderByDesc(StudentQualityRecord::getEventDate).orderByDesc(StudentQualityRecord::getId)); }
    @Transactional
    public StudentQualityRecord saveRecord(Long id,Long recordId,StudentQualityRecordRequest request) {
        requireStudent(id); StudentQualityRecord value=request.validatedEntity(); value.setStudentId(id);
        if (recordId==null) recordMapper.insert(value); else { StudentQualityRecord old=recordMapper.selectById(recordId); if(old==null || !id.equals(old.getStudentId())) throw new BusinessException(404,"记录不存在"); value.setId(recordId); recordMapper.updateById(value); }
        return recordMapper.selectById(value.getId());
    }
    @Transactional
    public void deleteRecord(Long id,Long recordId) { requireStudent(id); StudentQualityRecord old=recordMapper.selectById(recordId); if(old==null || !id.equals(old.getStudentId())) throw new BusinessException(404,"记录不存在"); recordMapper.deleteById(recordId); }
}
