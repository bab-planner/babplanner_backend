package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.MenuWasteResponse;
import com.babplanner.backend.admin.dto.WasteSummaryResponse;
import com.babplanner.backend.admin.service.AdminWasteService;
import com.babplanner.backend.common.api.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 잔반 분석. 기간은 메뉴 제공일 기준이고 양 끝을 포함한다. */
@RestController
@RequestMapping("/api/v1/admin/waste")
public class AdminWasteController {

    private final AdminWasteService adminWasteService;

    public AdminWasteController(AdminWasteService adminWasteService) {
        this.adminWasteService = adminWasteService;
    }

    @GetMapping("/summary")
    public ApiResponse<WasteSummaryResponse> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(adminWasteService.getSummary(from, to));
    }

    @GetMapping("/by-menu")
    public ApiResponse<List<MenuWasteResponse>> getByMenu(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(adminWasteService.getByMenu(from, to));
    }
}
