package com.edu.mental.dto;

import com.edu.mental.entity.Questionnaire;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDate;

/** Distinguish absent dates from an explicit JSON null (clear). */
public class QuestionnaireUpdateRequest extends Questionnaire {
    private boolean startTimePresent;
    private boolean endTimePresent;
    @Override @JsonSetter("startTime") public void setStartTime(LocalDate value) {
        startTimePresent = true; super.setStartTime(value);
    }
    @Override @JsonSetter("endTime") public void setEndTime(LocalDate value) {
        endTimePresent = true; super.setEndTime(value);
    }
    @JsonIgnore public boolean isStartTimePresent() { return startTimePresent; }
    @JsonIgnore public boolean isEndTimePresent() { return endTimePresent; }
}
