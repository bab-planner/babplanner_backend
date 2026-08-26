package com.babplanner.backend.waste.controller;

import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.security.AuthenticatedUser;
import com.babplanner.backend.waste.dto.WasteSubmissionResponse;
import com.babplanner.backend.waste.service.WasteSubmissionService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 잔반 인증. 모두 bearerAuth가 필요하며 본인 데이터만 다룬다. */
@RestController
@RequestMapping("/api/v1/waste-submissions")
public class WasteSubmissionController {

    private final WasteSubmissionService wasteSubmissionService;

    public WasteSubmissionController(WasteSubmissionService wasteSubmissionService) {
        this.wasteSubmissionService = wasteSubmissionService;
    }

    /**
     * 사진을 올린다.
     *
     * <p>작성자는 principal에서만 얻는다. 폼에 userId가 실려 와도 쓰지 않는다.
     *
     * <p>verificationToken은 계약에 있어 폼 필드로 받되, 현장 인증 방식이 아직
     * 정해지지 않아 서버가 해석하거나 저장하거나 기록하지 않는다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<WasteSubmissionResponse>> createSubmission(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("menuId") Long menuId,
            @RequestParam("image") MultipartFile image,
            @RequestParam("capturedAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant capturedAt,
            @RequestParam(value = "verificationToken", required = false) String verificationToken) {
        // OpenAPI가 201을 정의한다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        wasteSubmissionService.create(principal.id(), menuId, image, capturedAt)));
    }

    @GetMapping("/{submissionId}")
    public ApiResponse<WasteSubmissionResponse> getSubmission(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long submissionId) {
        return ApiResponse.success(wasteSubmissionService.get(principal.id(), submissionId));
    }
}
