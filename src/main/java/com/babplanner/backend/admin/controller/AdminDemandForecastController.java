package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.DemandForecastRequest;
import com.babplanner.backend.admin.dto.DemandForecastResponse;
import com.babplanner.backend.admin.dto.DemandForecastStatusResponse;
import com.babplanner.backend.admin.service.AdminDemandForecastService;
import com.babplanner.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 식수 예측 (합성데이터 PoC).
 *
 * <p>관리자 화면은 이 경로만 부른다. FastAPI의 내부 경로
 * ({@code /internal/v1/ai/demand-forecast/**})는 백엔드만 부르고 밖으로 드러내지 않는다.
 *
 * <p>권한은 SecurityConfig의 {@code /api/v1/admin/**} 규칙이 판단한다.
 *
 * <p>조건을 받아 예측값을 돌려주기만 하고 저장하지 않는다. 그래서 201이 아니라
 * 200이다.
 */
@RestController
@RequestMapping("/api/v1/admin/demand-forecasts")
public class AdminDemandForecastController {

    private final AdminDemandForecastService forecastService;

    public AdminDemandForecastController(AdminDemandForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @PostMapping("/predict")
    public ApiResponse<DemandForecastResponse> predict(
            @Valid @RequestBody DemandForecastRequest request) {
        return ApiResponse.success(forecastService.predict(request));
    }

    /** 모델이 떠 있는지. AI 서버에 닿지 못하면 503이다. */
    @GetMapping("/status")
    public ApiResponse<DemandForecastStatusResponse> status() {
        return ApiResponse.success(forecastService.status());
    }
}
