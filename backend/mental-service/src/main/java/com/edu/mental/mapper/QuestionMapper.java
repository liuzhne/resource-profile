package com.edu.mental.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.mental.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
    /** One parameterized round trip per bounded batch, inside the import transaction. */
    @Insert("""
        <script>
        INSERT INTO mental_question
          (id, questionnaire_id, sort_order, content, question_type, `options`, scoring_rules,
           scale_min, scale_max, scale_labels, required, deleted, create_time, update_time)
        VALUES
        <foreach collection="questions" item="q" separator=",">
          (#{q.id}, #{q.questionnaireId}, #{q.sortOrder}, #{q.content}, #{q.questionType},
           #{q.options}, #{q.scoringRules}, #{q.scaleMin}, #{q.scaleMax}, #{q.scaleLabels},
           #{q.required}, COALESCE(#{q.deleted}, 0), #{q.createTime}, #{q.updateTime})
        </foreach>
        </script>
        """)
    int insertBulk(@Param("questions") List<Question> questions);
}
