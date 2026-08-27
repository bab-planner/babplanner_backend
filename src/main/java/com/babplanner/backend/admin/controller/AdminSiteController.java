package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.AdminSiteResponse;
import com.babplanner.backend.admin.service.AdminSiteService;
import com.babplanner.backend.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자용 식당 목록. 조회만 한다. */
@RestController
@RequestMapping("/api/v1/admin/sites")
public class AdminSiteController {

    private final AdminSiteService adminSiteService;

    public AdminSiteController(AdminSiteService adminSiteService) {
        this.adminSiteService = adminSiteService;
    }

    @GetMapping
    public ApiResponse<List<AdminSiteResponse>> getSites() {
        return ApiResponse.success(adminSiteService.getSites());
    }
}
