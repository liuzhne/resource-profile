package com.edu.teacher.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("teacher_evaluation")
public class TeacherEvaluation {
    @TableId(type = IdType.AUTO) private Long id;
    private Long teacherId;
    private String title;
    private java.time.LocalDate eventDate;
    private java.math.BigDecimal score;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean satisfied;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String comment;
    @TableLogic private Integer deleted;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
