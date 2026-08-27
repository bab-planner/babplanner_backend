package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.AdminDashboardResponse;
import com.babplanner.backend.admin.service.AdminDashboardService;
import com.babplanner.backend.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 대시보드. 아직 채울 수 없는 칸은 비어 있다. */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @RequestParam(required = false) Long siteId) {
        return ApiResponse.success(adminDashboardService.getDashboard(siteId));
    }
}
