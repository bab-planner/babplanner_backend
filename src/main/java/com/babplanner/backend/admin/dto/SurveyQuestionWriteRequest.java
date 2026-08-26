package com.babplanner.backend.admin.dto;

import com.babplanner.backend.survey.domain.SurveyQuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 설문 문항 하나.
 *
 * <p>계약이 읽기용 SurveyQuestion 스키마를 쓰기에도 재사용해서 id가 들어 있다.
 * 여기서는 받지 않는다. 문항 id는 DB가 정하는 값이고, 클라이언트가 PK를
 * 지정하게 하면 다른 설문의 문항을 덮어쓸 수 있다.
 */
public record SurveyQuestionWriteRequest(
        @NotBlank String text,
        @NotNull SurveyQuestionType type,
        Boolean required,
        List<String> options) {

    public boolean requiredOrDefault() {
        return Boolean.TRUE.equals(required);
    }

    public List<String> optionsOrEmpty() {
        return options == null ? List.of() : options;
    }
}
