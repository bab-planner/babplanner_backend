package com.babplanner.backend.notice.dto;

import com.babplanner.backend.notice.domain.Notice;

/**
 * OpenAPI Notice.
 *
 * <p>published를 함께 내려준다. 관리자 목록에는 미공개 공지도 들어 있는데,
 * 이 값이 없으면 목록만 보고는 어느 것이 사용자에게 보이는지 알 수 없다.
 */
public record NoticeResponse(
        Long id,
        String title,
        String content,
        boolean important,
        boolean published,
        String publishedAt) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isImportant(),
                notice.isPublished(),
                // OpenAPI format: date-time (ISO-8601 UTC)
                notice.getPublishedAt().toString());
    }
}
