package com.babplanner.backend.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** OpenAPI SurveyWriteRequest. */
public record SurveyWriteRequest(
        @NotBlank String title,
        String description,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotEmpty @Valid List<SurveyQuestionWriteRequest> questions) {}
