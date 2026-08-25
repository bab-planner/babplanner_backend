package com.babplanner.backend.auth.controller;

import com.babplanner.backend.auth.dto.AuthResponse;
import com.babplanner.backend.auth.dto.LoginRequest;
import com.babplanner.backend.auth.dto.LogoutRequest;
import com.babplanner.backend.auth.dto.PhoneSendCodeRequest;
import com.babplanner.backend.auth.dto.PhoneSendCodeResponse;
import com.babplanner.backend.auth.dto.PhoneVerifyRequest;
import com.babplanner.backend.auth.dto.PhoneVerifyResponse;
import com.babplanner.backend.auth.dto.RefreshRequest;
import com.babplanner.backend.auth.dto.SignupRequest;
import com.babplanner.backend.auth.service.AuthService;
import com.babplanner.backend.auth.service.PhoneVerificationService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PhoneVerificationService phoneVerificationService;

    public AuthController(AuthService authService, PhoneVerificationService phoneVerificationService) {
        this.authService = authService;
        this.phoneVerificationService = phoneVerificationService;
    }

    @PostMapping("/phone/send-code")
    public ApiResponse<PhoneSendCodeResponse> sendCode(@Valid @RequestBody PhoneSendCodeRequest request) {
        return ApiResponse.success(phoneVerificationService.sendCode(request.phone()));
    }

    @PostMapping("/phone/verify")
    public ApiResponse<PhoneVerifyResponse> verify(@Valid @RequestBody PhoneVerifyRequest request) {
        return ApiResponse.success(phoneVerificationService.verify(request.phone(), request.code()));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        // OpenAPI가 201을 정의한다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.signup(request)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        // refreshToken 자체가 credential이므로 Authorization 헤더를 요구하지 않는다.
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) LogoutRequest request) {
        authService.logout(principal.id(), request == null ? null : request.refreshToken());
        // OpenAPI가 204를 정의한다.
        return ResponseEntity.noContent().build();
    }
}
