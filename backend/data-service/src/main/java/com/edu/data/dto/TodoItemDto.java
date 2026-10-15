package com.edu.data.dto;

import com.edu.common.security.SensitiveField;
import com.edu.common.security.Sensitivity;
import lombok.Data;
import java.time.LocalDateTime;

/** Existing actionable source state; never includes assessment scores or provider JSON. */
@Data
public class TodoItemDto {
    private String kind;
    private Long sourceId;
    private Long studentId;
    private String title;
    private String status;
    @SensitiveField(Sensitivity.MEDIUM)
    private String level;
    private LocalDateTime eventTime;
}
