package com.babplanner.backend.auth.dto;

import com.babplanner.backend.user.domain.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** OpenAPI SignupRequest. */
public record SignupRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
                String phone,
        @NotBlank @Size(max = 20) String studentId,
        Gender gender,
        @NotBlank @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다.") String password,
        @NotNull @Valid TermsAgreementRequest agreements,
        @NotBlank String verificationToken) {}
