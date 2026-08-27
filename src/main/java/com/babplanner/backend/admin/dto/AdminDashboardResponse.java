package com.babplanner.backend.admin.dto;

import com.babplanner.backend.congestion.dto.CongestionResponse;
import java.util.List;

/**
 * OpenAPI AdminDashboard.
 *
 * <p>비어 있는 자리를 그대로 비워 둔다. 예측 모델이 없는데 그럴듯한 숫자를
 * 채우면 관리자는 그 숫자를 보고 식재료를 발주한다. null은 "아직 모른다"는
 * 뜻이고, 화면은 그걸 그대로 보여 주면 된다.
 *
 * @param todayPredictedPeople 수요 예측 모델이 없어 null
 * @param tomorrowPredictedPeople 같은 이유로 null
 * @param currentCongestion 저장된 현재값. 없으면 null이고 404로 만들지 않는다
 * @param wasteSummary 오늘 제공된 메뉴 기준 요약
 * @param procurementPreview 발주 추천 모델이 없어 항상 빈 배열
 */
public record AdminDashboardResponse(
        Integer todayPredictedPeople,
        Integer tomorrowPredictedPeople,
        CongestionResponse currentCongestion,
        WasteSummaryResponse wasteSummary,
        List<ProcurementItemResponse> procurementPreview) {}
