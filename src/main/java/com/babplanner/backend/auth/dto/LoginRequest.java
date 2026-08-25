package com.babplanner.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** OpenAPI LoginRequest. */
public record LoginRequest(
        @NotBlank @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
                String phone,
        @NotBlank String password) {}
