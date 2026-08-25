package com.babplanner.backend.auth.dto;

/** OpenAPI PhoneVerificationSendData. 인증번호 자체는 담지 않는다. */
public record PhoneSendCodeResponse(int expiresInSeconds, int resendAvailableInSeconds) {}
