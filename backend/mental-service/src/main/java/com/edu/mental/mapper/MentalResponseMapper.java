package com.edu.mental.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.mental.entity.MentalResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MentalResponseMapper extends BaseMapper<MentalResponse> {

    @Select("SELECT mr.id, mr.student_id AS studentId, si.name, si.student_id AS studentNo, " +
            "si.dept_name AS deptName, si.grade, si.class_name AS className, " +
            "mr.score, ma.level, mr.create_time AS submitTime " +
            "FROM mental_response mr " +
            "LEFT JOIN student_info si ON mr.student_id = si.id " +
            "LEFT JOIN mental_assessment ma ON ma.response_id = mr.id " +
            "WHERE mr.questionnaire_id = #{questionnaireId} " +
            "ORDER BY mr.create_time DESC")
    List<Map<String, Object>> selectCompletionByQuestionnaire(Long questionnaireId);
}
