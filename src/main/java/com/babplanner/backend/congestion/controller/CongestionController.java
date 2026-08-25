package com.babplanner.backend.congestion.controller;

import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.congestion.dto.CongestionResponse;
import com.babplanner.backend.congestion.service.CongestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 현재 혼잡도 조회. OpenAPI에 security가 없는 공개 API다. */
@RestController
@RequestMapping("/api/v1/congestion")
public class CongestionController {

    private final CongestionService congestionService;

    public CongestionController(CongestionService congestionService) {
        this.congestionService = congestionService;
    }

    @GetMapping("/current")
    public ApiResponse<CongestionResponse> getCurrent(
            @RequestParam(required = false) Long siteId) {
        return ApiResponse.success(congestionService.getCurrent(siteId));
    }
}
