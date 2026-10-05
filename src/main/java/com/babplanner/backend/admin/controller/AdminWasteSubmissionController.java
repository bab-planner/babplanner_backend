package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.AdminWasteSubmissionResponse;
import com.babplanner.backend.admin.service.AdminWasteSubmissionService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.security.AuthenticatedUser;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 잔반 인증 검토. 경로 전체가 ADMIN 전용이다(SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/waste-submissions")
public class AdminWasteSubmissionController {

    private final AdminWasteSubmissionService service;

    public AdminWasteSubmissionController(AdminWasteSubmissionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AdminWasteSubmissionResponse>> list(
            @RequestParam(required = false) WasteSubmissionStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(service.list(status, limit));
    }

    /** 저장된 사진 원본. 사용자 사진이라 캐시하지 않는다. */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        AdminWasteSubmissionService.StoredImage image = service.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AdminWasteSubmissionResponse> approve(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(service.approve(id, principal.id()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AdminWasteSubmissionResponse> reject(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(service.reject(id, principal.id()));
    }
}
