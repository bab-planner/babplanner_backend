package com.babplanner.backend.auth.dto;

/**
 * OpenAPI LogoutRequest. 본문 자체가 선택이며 refreshToken도 없을 수 있다.
 */
public record LogoutRequest(String refreshToken) {}
