package com.edu.mental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.*;
import com.edu.mental.entity.*;
import com.edu.mental.mapper.*;
import com.edu.mental.service.MentalAssessmentService;
import com.edu.mental.service.MentalAnswerContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MentalAssessmentServiceImpl implements MentalAssessmentService {
    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionMapper questionMapper;
    private final MentalResponseMapper responseMapper;
    private final MentalAssessmentMapper assessmentMapper;
    private final StudentLookupMapper studentLookupMapper;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Override public List<Map<String, Object>> listForStudent(Long userId) {
        Long studentId = resolveStudentId(userId);
        var all = questionnaireMapper.selectList(new LambdaQueryWrapper<Questionnaire>()
                .eq(Questionnaire::getStatus, 1).orderByDesc(Questionnaire::getCreateTime));
        Set<Long> answered = new HashSet<>();
        if (studentId != null) responseMapper.selectList(new LambdaQueryWrapper<MentalResponse>()
                .eq(MentalResponse::getStudentId, studentId)).forEach(r -> answered.add(r.getQuestionnaireId()));
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Questionnaire q : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",q.getId()); row.put("title",q.getTitle()); row.put("type",q.getType());
            row.put("description",q.getDescription()); row.put("questions",q.getQuestions());
            row.put("startTime",q.getStartTime()); row.put("endTime",q.getEndTime());
            boolean valid = (q.getStartTime()==null || !today.isBefore(q.getStartTime()))
                    && (q.getEndTime()==null || !today.isAfter(q.getEndTime()));
            row.put("inWindow",valid); row.put("answered",answered.contains(q.getId())); result.add(row);
        }
        return result;
    }

    @Override @Transactional public MentalAssessment submit(SubmitAnswerRequest req) {
        if (req==null || req.getUserId()==null || req.getQuestionnaireId()==null || req.getAnswers()==null)
            throw new BusinessException(400,"提交参数不完整");
        Long studentId = resolveStudentId(req.getUserId());
        if (studentId==null) throw new BusinessException(409,"当前账号未关联学生档案，请联系管理员绑定");
        Questionnaire q = questionnaireMapper.selectById(req.getQuestionnaireId());
        if (q==null) throw new BusinessException(404,"问卷不存在");
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (!Integer.valueOf(1).equals(q.getStatus())) throw new BusinessException(409,"问卷当前未开放");
        if (q.getStartTime()!=null && today.isBefore(q.getStartTime())) throw new BusinessException(409,"问卷尚未开始");
        if (q.getEndTime()!=null && today.isAfter(q.getEndTime())) throw new BusinessException(409,"问卷已结束");
        if (responseMapper.selectCount(new LambdaQueryWrapper<MentalResponse>()
                .eq(MentalResponse::getStudentId,studentId).eq(MentalResponse::getQuestionnaireId,q.getId()))>0)
            throw new BusinessException(409,"您已提交过该问卷");
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getQuestionnaireId,q.getId()).orderByAsc(Question::getSortOrder).orderByAsc(Question::getId));
        int score = MentalAnswerContract.score(questions,req.getAnswers());
        List<LevelRule> rules = MentalAnswerContract.levels(q.getLevelRules());
        LevelRule rule = rules.stream().filter(r -> r.getMinScore()<=score)
                .max(Comparator.comparingInt(LevelRule::getMinScore))
                .orElseThrow(() -> new BusinessException(400,"等级规则未覆盖本次得分，问卷配置需要修正"));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        MentalResponse response = new MentalResponse(); response.setStudentId(studentId);
        response.setQuestionnaireId(q.getId()); response.setScore(score); response.setStatus(1); response.setCreateTime(now);
        try {
            response.setAnswers(objectMapper.writeValueAsString(req.getAnswers()));
            response.setQuestionnaireSnapshot(objectMapper.writeValueAsString(MentalAnswerContract.respondentSnapshot(q,questions)));
        } catch (Exception e) { throw new BusinessException(400,"答卷格式无法保存"); }
        try { responseMapper.insert(response); }
        catch(DuplicateKeyException e) { throw new BusinessException(409,"您已提交过该问卷"); }
        MentalAssessment assessment = new MentalAssessment(); assessment.setStudentId(studentId);
        assessment.setQuestionnaireId(q.getId()); assessment.setResponseId(response.getId()); assessment.setScore(score);
        assessment.setLevel(rule.getLevel()); assessment.setSuggestion(rule.getSuggestion());
        assessment.setResult("总分 "+score+"，评级："+rule.getLevel()); assessment.setStatus(1);
        assessment.setWarningStatus("OPEN"); assessment.setCreateTime(now); assessmentMapper.insert(assessment);
        return assessment;
    }

    @Override public List<MentalAssessment> myHistory(Long userId) {
        Long studentId=resolveStudentId(userId);
        if(studentId==null) return List.of();
        var rows=assessmentMapper.selectList(new LambdaQueryWrapper<MentalAssessment>()
                .eq(MentalAssessment::getStudentId,studentId).orderByDesc(MentalAssessment::getCreateTime).orderByDesc(MentalAssessment::getId));
        for(var row:rows) {
            Questionnaire q=questionnaireMapper.selectById(row.getQuestionnaireId());
            if(q!=null) {row.setQuestionnaireTitle(q.getTitle());row.setQuestionnaireType(q.getType());}
            Questionnaire snapshot = snapshotMetadata(row.getResponseId(), row.getStudentId(), row.getQuestionnaireId());
            if(snapshot != null) { row.setQuestionnaireTitle(snapshot.getTitle()); row.setQuestionnaireType(snapshot.getType()); }
        }
        return rows;
    }

    @Override public List<MentalHistoryDto> historyForStudent(Long studentId) {
        if(studentId==null || studentLookupMapper.countActiveStudent(studentId)!=1)
            throw new BusinessException(404,"学生档案不存在");
        List<MentalHistoryDto> rows=assessmentMapper.selectHistoryByStudentId(studentId);
        for (var row : rows) {
            Questionnaire snapshot = snapshotMetadata(row.getResponseId(), row.getStudentId(), row.getQuestionnaireId());
            if(snapshot != null) { row.setQuestionnaireTitle(snapshot.getTitle()); row.setQuestionnaireType(snapshot.getType()); }
        }
        return rows;
    }

    @Override public Map<String,Object> getMyAssessmentDetail(Long userId,Long assessmentId) {
        Long studentId=resolveStudentId(userId); MentalAssessment a=assessmentMapper.selectById(assessmentId);
        if(a==null || studentId==null || !studentId.equals(a.getStudentId())) throw new BusinessException(403,"无权查看该评估");
        MentalResponse r=a.getResponseId()==null ? null : responseMapper.selectById(a.getResponseId());
        if(r!=null && (!studentId.equals(r.getStudentId()) || !a.getQuestionnaireId().equals(r.getQuestionnaireId())))
            throw new BusinessException(500,"答卷关联错误，请联系管理员");
        Map<String,Object> out=new LinkedHashMap<>(); out.put("assessment",a);
        if(r!=null && r.getQuestionnaireSnapshot()!=null) {
            try {
                QuestionnaireFullDto snapshot=objectMapper.readValue(r.getQuestionnaireSnapshot(),QuestionnaireFullDto.class);
                // Stored snapshot is already safe, defensively strip again for any migrated snapshot.
                var safe=MentalAnswerContract.respondentSnapshot(snapshot.getQuestionnaire(),snapshot.getQuestions());
                out.put("questionnaire",safe.getQuestionnaire()); out.put("questions",safe.getQuestions());
                out.put("historySnapshotAvailable",true); out.put("answersJson",r.getAnswers());
            } catch(Exception e) { throw new BusinessException(500,"历史问卷快照损坏，请联系管理员"); }
        } else {
            Questionnaire current=questionnaireMapper.selectById(a.getQuestionnaireId());
            if(current!=null) { current.setLevelRules(null); current.setCreatorId(null); }
            out.put("questionnaire",current); out.put("questions",List.of()); out.put("answersJson",r==null?null:r.getAnswers());
            out.put("historySnapshotAvailable",false);
            out.put("historyNotice","历史记录没有提交时题目快照，无法保证题干及选项回显。评估摘要仍按权限提供。");
        }
        return out;
    }
    @Override public List<Map<String,Object>> listCompletion(Long questionnaireId) {
        return responseMapper.selectCompletionByQuestionnaire(questionnaireId);
    }
    private Questionnaire snapshotMetadata(Long responseId, Long studentId, Long questionnaireId) {
        if(responseId == null) return null;
        MentalResponse response = responseMapper.selectById(responseId);
        if(response == null || !Objects.equals(studentId, response.getStudentId())
                || !Objects.equals(questionnaireId, response.getQuestionnaireId()))
            throw new BusinessException(500, "答卷关联错误，请联系管理员");
        if(response.getQuestionnaireSnapshot() == null) return null;
        try {
            QuestionnaireFullDto snapshot = objectMapper.readValue(response.getQuestionnaireSnapshot(), QuestionnaireFullDto.class);
            if(snapshot.getQuestionnaire() == null || !Objects.equals(questionnaireId, snapshot.getQuestionnaire().getId()))
                throw new IllegalArgumentException("snapshot identity mismatch");
            return snapshot.getQuestionnaire();
        } catch(Exception e) { throw new BusinessException(500, "历史问卷快照损坏，请联系管理员"); }
    }
    private Long resolveStudentId(Long userId) {
        if(userId==null) return null;
        List<Long> ids=studentLookupMapper.selectStudentIdsByUserId(userId);
        if(ids.size()>1) throw new BusinessException(409,"当前账号关联多个学生档案，请联系管理员明确绑定后再作答或查看本人记录");
        return ids.isEmpty()?null:ids.get(0);
    }
}
