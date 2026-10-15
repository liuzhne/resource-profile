package com.edu.mental.service;

import com.edu.mental.dto.SubmitAnswerRequest;
import com.edu.mental.entity.MentalAssessment;
import com.edu.mental.dto.MentalHistoryDto;

import java.util.List;
import java.util.Map;

public interface MentalAssessmentService {

    /** 学生可见的问卷列表（含我是否已答标记） */
    List<Map<String, Object>> listForStudent(Long userId);

    /** 学生提交答卷：计分 → 评级 → 写入 mental_response + mental_assessment */
    MentalAssessment submit(SubmitAnswerRequest request);

    /** 学生我的评估历史 */
    List<MentalAssessment> myHistory(Long userId);

    List<MentalHistoryDto> historyForStudent(Long studentId);

    /** 单次评估详情（含原始答卷） */
    Map<String, Object> getMyAssessmentDetail(Long userId, Long assessmentId);

    /** 教师/管理员：某问卷的完成情况列表 */
    List<Map<String, Object>> listCompletion(Long questionnaireId);
}
