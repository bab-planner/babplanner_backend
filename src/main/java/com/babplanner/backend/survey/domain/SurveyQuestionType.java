package com.babplanner.backend.survey.domain;

/**
 * OpenAPI v3.1 SurveyQuestion.type 열거값.
 *
 * <p>문자열이 Flutter SurveyQuestionType.fromJson과 정확히 같아야 한다.
 */
public enum SurveyQuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    SCALE,
    TEXT
}
