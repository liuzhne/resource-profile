package com.edu.student.dto;
import lombok.Data;
import com.edu.common.security.SensitiveField;
import com.edu.common.security.Sensitivity;
@Data
public class StudentRankDto {
    private boolean available;
    @SensitiveField(Sensitivity.MEDIUM) private Long rank;
    @SensitiveField(Sensitivity.MEDIUM) private Long cohortSize;
    private String scope;
    private String reason;
}
