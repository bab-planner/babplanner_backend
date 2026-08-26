package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.NoticeWriteRequest;
import com.babplanner.backend.admin.service.AdminNoticeService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.notice.dto.NoticeResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 공지 관리. 공개되지 않은 공지까지 다룬다. */
@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    public AdminNoticeController(AdminNoticeService adminNoticeService) {
        this.adminNoticeService = adminNoticeService;
    }

    @GetMapping
    public ApiResponse<List<NoticeResponse>> getNotices() {
        return ApiResponse.success(adminNoticeService.getNotices());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoticeResponse>> createNotice(
            @Valid @RequestBody NoticeWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminNoticeService.create(request)));
    }

    @PutMapping("/{noticeId}")
    public ApiResponse<NoticeResponse> updateNotice(
            @PathVariable Long noticeId, @Valid @RequestBody NoticeWriteRequest request) {
        return ApiResponse.success(adminNoticeService.update(noticeId, request));
    }

    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Long noticeId) {
        adminNoticeService.delete(noticeId);
        return ResponseEntity.noContent().build();
    }
}
