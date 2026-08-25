package com.babplanner.backend.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAPI v3.1의 공통 envelope.
 *
 * <p>성공/실패 어느 쪽이든 {@code success}, {@code data}, {@code error} 세 키를
 * 항상 내려보낸다. Flutter의 공통 파서가 세 키의 존재를 전제로 동작한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
