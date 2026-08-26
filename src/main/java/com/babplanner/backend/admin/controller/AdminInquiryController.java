package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.InquiryAdminUpdateRequest;
import com.babplanner.backend.admin.service.AdminInquiryService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.inquiry.domain.InquiryStatus;
import com.babplanner.backend.inquiry.dto.InquiryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 문의 관리. 모든 사용자의 문의를 다룬다. */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    public AdminInquiryController(AdminInquiryService adminInquiryService) {
        this.adminInquiryService = adminInquiryService;
    }

    @GetMapping
    public ApiResponse<List<InquiryResponse>> getInquiries(
            @RequestParam(required = false) InquiryStatus status) {
        return ApiResponse.success(adminInquiryService.getInquiries(status));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryResponse> getInquiry(@PathVariable Long inquiryId) {
        return ApiResponse.success(adminInquiryService.getInquiry(inquiryId));
    }

    @PatchMapping("/{inquiryId}")
    public ApiResponse<InquiryResponse> updateInquiry(
            @PathVariable Long inquiryId, @RequestBody InquiryAdminUpdateRequest request) {
        return ApiResponse.success(adminInquiryService.update(inquiryId, request));
    }
}
