package com.babplanner.backend.auth.dto;

import com.babplanner.backend.user.dto.UserResponse;

/** OpenAPI AuthData. */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {}
