package com.babplanner.backend.admin.dto;

import com.babplanner.backend.congestion.dto.CongestionResponse;

/**
 * 관리자 대시보드.
 *
 * <p>실제로 저장돼 있는 것만 담는다. 값이 없으면 null이고 화면은 그대로 보여 주면 된다.
 *
 * @param currentCongestion 저장된 현재값. 없으면 null이고 404로 만들지 않는다
 * @param wasteSummary 오늘 제공된 메뉴 기준 요약
 */
public record AdminDashboardResponse(
        CongestionResponse currentCongestion, WasteSummaryResponse wasteSummary) {}
