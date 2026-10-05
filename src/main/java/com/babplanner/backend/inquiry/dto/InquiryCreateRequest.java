package com.babplanner.backend.inquiry.dto;

import com.babplanner.backend.inquiry.domain.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * OpenAPI InquiryCreateRequest.
 *
 * <p>작성자는 JWT가 식별한다. userId를 담지 않는다.
 */
public record InquiryCreateRequest(
        @NotNull InquiryCategory category,
        // DB 컬럼 title VARCHAR(200). 초과 시 500이 아니라 400으로 거절한다.
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content) {}
