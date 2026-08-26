package com.babplanner.backend.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** OpenAPI SurveyResponseRequest. */
public record SurveySubmitRequest(@NotNull @Valid List<SurveyAnswerRequest> answers) {}
