package com.babplanner.backend.waste.repository;

import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.time.Instant;
import java.time.LocalDate;

/** 관리자 목록용 조회 행. 제출, 사용자, 메뉴를 한 번에 읽는다. */
public record AdminWasteSubmissionRow(
        Long id,
        String userName,
        String userPhone,
        Long menuId,
        String menuName,
        LocalDate serviceDate,
        WasteSubmissionStatus status,
        Integer earnedPoints,
        Boolean couponIssued,
        Instant createdAt,
        Instant capturedAt,
        Instant reviewedAt) {}
