package com.babplanner.backend.auth.dto;

/**
 * OpenAPI PhoneVerificationVerifyData.
 *
 * <p>verificationToken 원문은 이 응답으로 한 번만 나가고 DB에는 해시만 남는다.
 */
public record PhoneVerifyResponse(String verificationToken, int expiresInSeconds) {}
