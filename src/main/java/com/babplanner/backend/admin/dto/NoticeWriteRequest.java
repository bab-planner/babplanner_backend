package com.babplanner.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * OpenAPI NoticeWriteRequest.
 *
 * @param important 계약 기본값 false
 * @param published 계약 기본값 true
 */
public record NoticeWriteRequest(
        @NotBlank String title, @NotBlank String content, Boolean important, Boolean published) {

    public boolean importantOrDefault() {
        return Boolean.TRUE.equals(important);
    }

    public boolean publishedOrDefault() {
        return published == null || published;
    }
}
