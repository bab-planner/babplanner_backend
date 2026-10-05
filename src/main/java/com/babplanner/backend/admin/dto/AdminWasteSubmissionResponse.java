package com.babplanner.backend.admin.dto;

import com.babplanner.backend.waste.repository.AdminWasteSubmissionRow;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 관리자 잔반 검토 항목.
 *
 * <p>전화번호는 마스킹한 값만 내보낸다. earnedPoints와 couponIssued는 PENDING에서 null이다.
 */
public record AdminWasteSubmissionResponse(
        Long id,
        String userName,
        String userPhoneMasked,
        Long menuId,
        String menuName,
        LocalDate serviceDate,
        String status,
        Integer earnedPoints,
        Boolean couponIssued,
        Instant createdAt,
        Instant capturedAt,
        Instant reviewedAt) {

    public static AdminWasteSubmissionResponse from(AdminWasteSubmissionRow row) {
        return new AdminWasteSubmissionResponse(
                row.id(),
                row.userName(),
                maskPhone(row.userPhone()),
                row.menuId(),
                row.menuName(),
                row.serviceDate(),
                row.status().name(),
                row.earnedPoints(),
                row.couponIssued(),
                row.createdAt(),
                row.capturedAt(),
                row.reviewedAt());
    }

    /** 01012345678 → 010-****-5678. 짧은 값은 전부 가린다. */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 3) + "-****-" + phone.substring(phone.length() - 4);
    }
}
