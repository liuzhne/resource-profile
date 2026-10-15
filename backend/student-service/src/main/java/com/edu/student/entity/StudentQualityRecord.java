package com.edu.student.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("student_quality_record")
public class StudentQualityRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private String kind;
    private String title;
    private java.time.LocalDate eventDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableLogic private Integer deleted;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
