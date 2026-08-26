package com.babplanner.backend.waste.dto;

import com.babplanner.backend.waste.domain.WasteSubmission;
import java.time.Instant;

/**
 * OpenAPI WasteSubmission 스키마.
 *
 * <p>계약에 있는 키만 담는다. imageStorageKey, 서버 경로, userId, capturedAt은
 * 응답에 넣지 않는다.
 */
public record WasteSubmissionResponse(
        Long id,
        Long menuId,
        String status,
        Double wasteRate,
        Double confidence,
        Integer earnedPoints,
        Boolean couponIssued,
        Instant createdAt) {

    public static WasteSubmissionResponse from(WasteSubmission submission) {
        return new WasteSubmissionResponse(
                submission.getId(),
                submission.getMenuId(),
                submission.getStatus().name(),
                submission.getWasteRate(),
                submission.getConfidence(),
                submission.getEarnedPoints(),
                submission.getCouponIssued(),
                submission.getCreatedAt());
    }
}
