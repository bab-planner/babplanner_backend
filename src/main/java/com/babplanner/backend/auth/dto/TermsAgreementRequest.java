package com.babplanner.backend.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * OpenAPI TermsAgreement.
 *
 * <p>계약상 두 값 모두 true여야 하므로 존재 여부만 검증하고, true 여부는
 * 서비스가 판단해 도메인 오류로 돌려준다.
 */
public record TermsAgreementRequest(@NotNull Boolean serviceTerms, @NotNull Boolean privacyPolicy) {}
