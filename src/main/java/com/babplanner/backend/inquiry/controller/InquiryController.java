package com.babplanner.backend.inquiry.controller;

import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.security.AuthenticatedUser;
import com.babplanner.backend.inquiry.dto.InquiryCreateRequest;
import com.babplanner.backend.inquiry.dto.InquiryResponse;
import com.babplanner.backend.inquiry.service.InquiryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 문의/건의. 모두 bearerAuth가 필요하며 본인 데이터만 다룬다. */
@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @GetMapping
    public ApiResponse<List<InquiryResponse>> getMyInquiries(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(inquiryService.getMyInquiries(principal.id()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryResponse>> createInquiry(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody InquiryCreateRequest request) {
        // OpenAPI가 201을 정의한다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(inquiryService.createInquiry(principal.id(), request)));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryResponse> getInquiry(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long inquiryId) {
        return ApiResponse.success(inquiryService.getInquiry(principal.id(), inquiryId));
    }
}
