package com.edu.mental.dto;

import com.edu.common.security.SensitiveField;
import com.edu.common.security.Sensitivity;
import lombok.Data;
import java.time.LocalDateTime;

/** Student-record scope, not an account-wide or guessed LIMIT 1 history. No invented factor scores. */
@Data
public class MentalHistoryDto {
    private Long id;
    private Long studentId;
    private Long questionnaireId;
    private Long responseId;
    private String questionnaireTitle;
    private String questionnaireType;
    @SensitiveField(Sensitivity.EXTREME) private Integer score;
    @SensitiveField(Sensitivity.MEDIUM) private String level;
    @SensitiveField(Sensitivity.EXTREME) private String result;
    @SensitiveField(Sensitivity.EXTREME) private String suggestion;
    private LocalDateTime createTime;
}
