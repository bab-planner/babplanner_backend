package com.babplanner.backend.admin.dto;

import com.babplanner.backend.waste.repository.WasteRateAggregate;

/**
 * OpenAPI WasteSummary.
 *
 * @param totalSubmissions 기간 내 검토가 끝난(APPROVED, REJECTED) 제출 건수.
 * @param averageWasteRate 잔반률이 기록된 APPROVED 건의 평균. 없으면 null이며
 *     0.0으로 내리지 않는다.
 * @param repeatedWasteMenuCount "반복적으로 잔반이 많은 메뉴"의 기준이 아직
 *     정해지지 않아 항상 null이다. 기준 없이 센 0은 "그런 메뉴가 없다"로
 *     읽히므로 0을 쓰지 않는다.
 */
public record WasteSummaryResponse(
        long totalSubmissions, Double averageWasteRate, Integer repeatedWasteMenuCount) {

    public static WasteSummaryResponse from(WasteRateAggregate aggregate) {
        return new WasteSummaryResponse(
                aggregate.sampleCount() == null ? 0L : aggregate.sampleCount(),
                aggregate.averageWasteRate(),
                null);
    }
}
