package com.babplanner.backend.inquiry.dto;

import com.babplanner.backend.inquiry.domain.Inquiry;
import com.babplanner.backend.inquiry.domain.InquiryCategory;
import com.babplanner.backend.inquiry.domain.InquiryStatus;

/** OpenAPI Inquiry. */
public record InquiryResponse(
        Long id,
        InquiryCategory category,
        String title,
        String content,
        InquiryStatus status,
        String adminAnswer,
        String createdAt,
        String answeredAt) {

    public static InquiryResponse from(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getCategory(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getStatus(),
                inquiry.getAdminAnswer(),
                inquiry.getCreatedAt().toString(),
                inquiry.getAnsweredAt() == null ? null : inquiry.getAnsweredAt().toString());
    }
}
