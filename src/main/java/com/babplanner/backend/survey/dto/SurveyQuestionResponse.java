package com.babplanner.backend.survey.dto;

import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** OpenAPI SurveyQuestion. 계약 키는 required다. */
public record SurveyQuestionResponse(
        Long id,
        String text,
        SurveyQuestionType type,
        @JsonProperty("required") boolean required,
        List<String> options) {

    public static SurveyQuestionResponse from(SurveyQuestion question) {
        return new SurveyQuestionResponse(
                question.getId(),
                question.getText(),
                question.getType(),
                question.isRequired(),
                List.copyOf(question.getOptions()));
    }
}
