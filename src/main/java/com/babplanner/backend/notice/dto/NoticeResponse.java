package com.babplanner.backend.notice.dto;

import com.babplanner.backend.notice.domain.Notice;

/** OpenAPI Notice. */
public record NoticeResponse(
        Long id, String title, String content, boolean important, String publishedAt) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isImportant(),
                // OpenAPI format: date-time (ISO-8601 UTC)
                notice.getPublishedAt().toString());
    }
}
