package com.babplanner.backend.survey.dto;

import com.babplanner.backend.survey.domain.Survey;
import java.util.List;

/** OpenAPI Survey. */
public record SurveyResponseView(
        Long id,
        String title,
        String description,
        String startsAt,
        String endsAt,
        List<SurveyQuestionResponse> questions) {

    public static SurveyResponseView from(Survey survey) {
        return new SurveyResponseView(
                survey.getId(),
                survey.getTitle(),
                survey.getDescription(),
                survey.getStartsAt().toString(),
                survey.getEndsAt().toString(),
                survey.getQuestions().stream().map(SurveyQuestionResponse::from).toList());
    }
}
