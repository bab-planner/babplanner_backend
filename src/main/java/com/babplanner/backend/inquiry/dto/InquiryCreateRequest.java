package com.babplanner.backend.inquiry.dto;

import com.babplanner.backend.inquiry.domain.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * OpenAPI InquiryCreateRequest.
 *
 * <p>작성자는 JWT가 식별한다. userId를 담지 않는다.
 */
public record InquiryCreateRequest(
        @NotNull InquiryCategory category, @NotBlank String title, @NotBlank String content) {}
