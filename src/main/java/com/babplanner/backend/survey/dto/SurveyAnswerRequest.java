package com.babplanner.backend.survey.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * OpenAPI SurveyResponseRequest.answers[] 항목.
 *
 * <p>질문 종류별로 쓰는 필드가 다르다. 어떤 조합이 유효한지는 서비스가
 * 질문 type을 보고 판단한다.
 */
public record SurveyAnswerRequest(
        @NotNull Long questionId, List<String> selectedOptions, Integer scaleValue, String textValue) {

    public List<String> selectedOptionsOrEmpty() {
        return selectedOptions == null ? List.of() : selectedOptions;
    }
}
