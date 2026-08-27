package com.babplanner.backend.congestion.dto;

import com.babplanner.backend.congestion.domain.CongestionLevel;
import com.babplanner.backend.congestion.domain.CongestionObservation;
import com.babplanner.backend.congestion.domain.CongestionStatus;

/**
 * OpenAPI Congestion.
 *
 * <p>현재값과 과거 관측이 같은 모양으로 나간다. 계약이 하나이므로 시계열을
 * 읽는 쪽이 다른 파싱을 하지 않아도 된다.
 */
public record CongestionResponse(
        Long siteId,
        Integer peopleCount,
        CongestionLevel level,
        String displayMessage,
        String observedAt,
        String source) {

    public static CongestionResponse from(CongestionStatus status) {
        return new CongestionResponse(
                status.getSite().getId(),
                status.getPeopleCount(),
                status.getLevel(),
                status.getDisplayMessage(),
                // OpenAPI format: date-time (ISO-8601 UTC)
                status.getObservedAt().toString(),
                status.getSource());
    }

    public static CongestionResponse from(CongestionObservation observation) {
        return new CongestionResponse(
                observation.getSite().getId(),
                observation.getPeopleCount(),
                observation.getLevel(),
                observation.getDisplayMessage(),
                observation.getObservedAt().toString(),
                observation.getSource());
    }
}
