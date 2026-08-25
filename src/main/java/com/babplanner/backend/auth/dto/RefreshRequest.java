package com.babplanner.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** OpenAPI RefreshRequest. */
public record RefreshRequest(@NotBlank String refreshToken) {}
