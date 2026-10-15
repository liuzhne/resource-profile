package com.edu.teacher.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.edu.common.exception.BusinessException;
import com.edu.teacher.dto.*;
import com.edu.teacher.entity.*;
import com.edu.teacher.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
@Service
@RequiredArgsConstructor
public class TeacherPortraitService {
    private final TeacherMapper teacherMapper;
    private final TeacherRecordMapper recordMapper;
    private final TeacherEvaluationMapper evaluationMapper;
    public Map<String, List<String>> options() { return Map.of("departments", teacherMapper.departments(), "titles", teacherMapper.titles()); }
    public List<Map<String, Object>> accounts() { return teacherMapper.availableAccounts(); }
    public void requireTeacher(Long id) { if (teacherMapper.selectById(id) == null) throw new BusinessException(404, "教师不存在"); }
    public TeacherPortraitDto portrait(Long id) {
        requireTeacher(id);
        List<TeacherRecord> records = recordMapper.selectList(Wrappers.<TeacherRecord>lambdaQuery().eq(TeacherRecord::getTeacherId, id).orderByDesc(TeacherRecord::getEventDate).orderByDesc(TeacherRecord::getId));
        List<TeacherEvaluation> evaluations = evaluationMapper.selectList(Wrappers.<TeacherEvaluation>lambdaQuery().eq(TeacherEvaluation::getTeacherId, id).orderByDesc(TeacherEvaluation::getEventDate).orderByDesc(TeacherEvaluation::getId));
        return summarize(records, evaluations);
    }
    public static TeacherPortraitDto summarize(List<TeacherRecord> records, List<TeacherEvaluation> evaluations) {
        TeacherPortraitDto dto = new TeacherPortraitDto();
        dto.setTeaching(records.stream().filter(x -> "TEACHING".equals(x.getKind())).toList());
        dto.setResearch(records.stream().filter(x -> "RESEARCH".equals(x.getKind())).toList());
        dto.setEvaluations(evaluations); dto.setEvaluationCount((long) evaluations.size());
        List<BigDecimal> scores = evaluations.stream().map(TeacherEvaluation::getScore).filter(Objects::nonNull).toList();
        dto.setEvaluationScore(scores.isEmpty() ? null : scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
        long answered = evaluations.stream().filter(x -> x.getSatisfied() != null).count();
        long satisfied = evaluations.stream().filter(x -> Boolean.TRUE.equals(x.getSatisfied())).count();
        dto.setSatisfaction(answered == 0 ? null : BigDecimal.valueOf(satisfied * 100).divide(BigDecimal.valueOf(answered), 2, RoundingMode.HALF_UP));
        return dto;
    }
    @Transactional
    public TeacherRecord saveRecord(Long teacherId, Long recordId, TeacherRecordRequest request) {
        requireTeacher(teacherId); TeacherRecord value = request.validatedEntity(); value.setTeacherId(teacherId);
        if (recordId == null) recordMapper.insert(value);
        else { TeacherRecord old = recordMapper.selectById(recordId); if (old == null || !teacherId.equals(old.getTeacherId())) throw new BusinessException(404, "记录不存在"); value.setId(recordId); recordMapper.updateById(value); }
        return recordMapper.selectById(value.getId());
    }
    @Transactional
    public void deleteRecord(Long teacherId, Long recordId) { requireTeacher(teacherId); TeacherRecord old = recordMapper.selectById(recordId); if (old == null || !teacherId.equals(old.getTeacherId())) throw new BusinessException(404, "记录不存在"); recordMapper.deleteById(recordId); }
    @Transactional
    public TeacherEvaluation saveEvaluation(Long teacherId, Long recordId, TeacherEvaluationRequest request) {
        requireTeacher(teacherId); TeacherEvaluation value = request.validatedEntity(); value.setTeacherId(teacherId);
        if (recordId == null) evaluationMapper.insert(value);
        else { TeacherEvaluation old = evaluationMapper.selectById(recordId); if (old == null || !teacherId.equals(old.getTeacherId())) throw new BusinessException(404, "评价不存在"); value.setId(recordId); evaluationMapper.updateById(value); }
        return evaluationMapper.selectById(value.getId());
    }
    @Transactional
    public void deleteEvaluation(Long teacherId, Long recordId) { requireTeacher(teacherId); TeacherEvaluation old = evaluationMapper.selectById(recordId); if (old == null || !teacherId.equals(old.getTeacherId())) throw new BusinessException(404, "评价不存在"); evaluationMapper.deleteById(recordId); }
}
