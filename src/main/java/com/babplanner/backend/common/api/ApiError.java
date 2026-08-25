package com.babplanner.backend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAPI v3.1 {@code Error} 스키마.
 *
 * <p>{@code details}는 nullable이며, 검증 오류의 필드 목록처럼 클라이언트가
 * 실제로 쓸 수 있는 구조화된 정보만 담는다. 예외 메시지나 스택 트레이스를
 * 그대로 넣지 않는다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiError(String code, String message, Object details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
