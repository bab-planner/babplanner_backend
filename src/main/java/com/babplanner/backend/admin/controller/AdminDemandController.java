package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.DemandPredictionRequest;
import com.babplanner.backend.admin.dto.DemandPredictionResponse;
import com.babplanner.backend.admin.service.AdminDemandService;
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

/** 관리자 수요 예측. 예측은 site/date/mealType 단위다. */
@RestController
@RequestMapping("/api/v1/admin/demand-predictions")
public class AdminDemandController {

    private final AdminDemandService adminDemandService;

    public AdminDemandController(AdminDemandService adminDemandService) {
        this.adminDemandService = adminDemandService;
    }

    /**
     * 실행 이력. 최신 실행이 먼저다.
     *
     * <p>siteId를 필수로 받는다. 예측은 식당마다 다른 값이라, 없이 조회하면
     * 서로 다른 식당의 숫자가 한 목록에 섞인다.
     */
    @GetMapping
    public ApiResponse<List<DemandPredictionResponse>> getPredictions(
            @RequestParam Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MealType mealType) {
        return ApiResponse.success(adminDemandService.getPredictions(siteId, date, mealType));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DemandPredictionResponse>> predict(
            @Valid @RequestBody DemandPredictionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminDemandService.predict(request)));
    }
}
