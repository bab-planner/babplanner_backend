package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.service.AdminCongestionService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.congestion.dto.CongestionResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 혼잡도 분석. */
@RestController
@RequestMapping("/api/v1/admin/congestion")
public class AdminCongestionController {

    private final AdminCongestionService adminCongestionService;

    public AdminCongestionController(AdminCongestionService adminCongestionService) {
        this.adminCongestionService = adminCongestionService;
    }

    /**
     * 기간 내 관측 목록.
     *
     * <p>offset이 붙은 시각도 받는다. 관리자가 KST로 적어 보내는 쪽이
     * 자연스러운데 Instant로 바로 받으면 Z 말고는 거절된다.
     */
    @GetMapping("/timeseries")
    public ApiResponse<List<CongestionResponse>> getTimeseries(
            @RequestParam(required = false) Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ApiResponse.success(
                adminCongestionService.getTimeseries(siteId, from.toInstant(), to.toInstant()));
    }
}
