package com.babplanner.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAPI PhoneVerificationSendData.
 *
 * <p>developmentCode는 SMS 모드가 noop일 때만 채워지는 개발 전용 값이다. 그 외에는
 * null이라 JSON에 키 자체가 없다. noop은 공개 환경에서 절대 쓰면 안 된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PhoneSendCodeResponse(
        int expiresInSeconds, int resendAvailableInSeconds, String developmentCode) {}
