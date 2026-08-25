package com.babplanner.backend.congestion.dto;

import com.babplanner.backend.congestion.domain.CongestionLevel;
import com.babplanner.backend.congestion.domain.CongestionStatus;

/** OpenAPI Congestion. */
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
}
