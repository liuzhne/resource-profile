package com.edu.teacher.dto;
import com.edu.common.exception.BusinessException;
import com.edu.teacher.entity.TeacherEvaluation;
import lombok.Data;
@Data
public class TeacherEvaluationRequest {
    private String title;
    private java.time.LocalDate eventDate;
    private java.math.BigDecimal score;
    private Boolean satisfied;
    private String comment;
    public TeacherEvaluation validatedEntity() {
        if (title == null || title.isBlank() || title.length() > 200) throw new BusinessException(400, "标题不能为空且最多 200 字");
        if (eventDate == null) throw new BusinessException(400, "请选择记录日期");
        if (score == null || score.signum() < 0 || score.compareTo(new java.math.BigDecimal("100")) > 0 || score.scale() > 2) throw new BusinessException(400, "评价分数须为 0–100，最多两位小数");
        if (comment != null && comment.length() > 2000) throw new BusinessException(400, "评价内容最多 2000 字");
        TeacherEvaluation value = new TeacherEvaluation();
        value.setTitle(title);
        value.setEventDate(eventDate);
        value.setScore(score);
        value.setSatisfied(satisfied);
        value.setComment(comment);
        value.setTitle(title.trim());
        return value;
    }
}
