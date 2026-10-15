package com.edu.teacher.dto;
import com.edu.teacher.entity.TeacherRecord;
import com.edu.teacher.entity.TeacherEvaluation;
import lombok.Data;
import java.util.List;
import java.math.BigDecimal;
@Data
public class TeacherPortraitDto {
    private List<TeacherRecord> teaching;
    private List<TeacherRecord> research;
    private List<TeacherEvaluation> evaluations;
    private BigDecimal evaluationScore;
    private Long evaluationCount;
    private BigDecimal satisfaction;
}
