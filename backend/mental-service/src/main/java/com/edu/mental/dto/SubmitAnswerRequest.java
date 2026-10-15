package com.edu.mental.dto;

import lombok.Data;

import java.util.List;

@Data
public class SubmitAnswerRequest {

    private Long userId;
    private Long questionnaireId;
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private List<Integer> optionIndices;
        private String text;
        private Integer scaleValue;
    }
}
