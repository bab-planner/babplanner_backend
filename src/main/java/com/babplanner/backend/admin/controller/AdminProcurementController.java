package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.ProcurementItemResponse;
import com.babplanner.backend.admin.dto.ProcurementRunRequest;
import com.babplanner.backend.admin.service.AdminProcurementService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.menu.domain.MealType;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 발주 추천. 추천은 site/date/mealType 단위다. */
@RestController
@RequestMapping("/api/v1/admin/procurement-recommendations")
public class AdminProcurementController {

    private final AdminProcurementService adminProcurementService;

    public AdminProcurementController(AdminProcurementService adminProcurementService) {
        this.adminProcurementService = adminProcurementService;
    }

    /**
     * 가장 최근 실행의 항목.
     *
     * <p>mealType이 필수다. 응답이 항목 목록뿐이라 끼니를 빼면 서로 다른
     * 끼니의 재료가 한 목록에 섞이고, 같은 재료가 두 줄 나온다.
     */
    @GetMapping
    public ApiResponse<List<ProcurementItemResponse>> getRecommendations(
            @RequestParam Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam MealType mealType) {
        return ApiResponse.success(
                adminProcurementService.getRecommendations(siteId, date, mealType));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<ProcurementItemResponse>>> recommend(
            @Valid @RequestBody ProcurementRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminProcurementService.recommend(request)));
    }
}
