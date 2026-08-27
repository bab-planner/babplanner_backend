package com.babplanner.backend.admin.dto;

import com.babplanner.backend.waste.repository.WasteRateAggregate;

/**
 * OpenAPI WasteSummary.
 *
 * @param totalSubmissions 평균에 들어간 제출 건수. 기간 내 제출 전체가 아니라
 *     분석이 끝나 잔반률이 나온 건수다.
 * @param averageWasteRate 표본이 0건이면 null. 0.0으로 내리지 않는다.
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
