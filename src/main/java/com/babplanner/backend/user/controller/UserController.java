package com.babplanner.backend.user.controller;

import com.babplanner.backend.allergy.dto.AllergyResponse;
import com.babplanner.backend.allergy.dto.AllergyUpdateRequest;
import com.babplanner.backend.allergy.service.AllergyService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.security.AuthenticatedUser;
import com.babplanner.backend.user.dto.AccountDeletionRequest;
import com.babplanner.backend.user.dto.UserResponse;
import com.babplanner.backend.user.service.AccountDeletionService;
import com.babplanner.backend.user.dto.UserUpdateRequest;
import com.babplanner.backend.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보와 내 알레르기 설정.
 *
 * <p>사용자 식별은 항상 인증 principal에서 온다. request body나 query로
 * userId를 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final AllergyService allergyService;
    private final AccountDeletionService accountDeletionService;

    public UserController(
            UserService userService,
            AllergyService allergyService,
            AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
        this.userService = userService;
        this.allergyService = allergyService;
    }

    @GetMapping
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(userService.getMe(principal.id()));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateMe(principal.id(), request));
    }

    /** 회원 탈퇴. 성공하면 본문 없이 204다. 비밀번호 불일치는 400이다(401 아님). */
    @PostMapping("/deletion")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AccountDeletionRequest request) {
        accountDeletionService.delete(principal.id(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/allergies")
    public ApiResponse<List<AllergyResponse>> getMyAllergies(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(allergyService.getMyAllergies(principal.id()));
    }

    @PutMapping("/allergies")
    public ApiResponse<List<AllergyResponse>> updateMyAllergies(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AllergyUpdateRequest request) {
        return ApiResponse.success(
                allergyService.updateMyAllergies(principal.id(), request.allergyIds()));
    }
}
