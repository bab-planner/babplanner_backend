package com.babplanner.backend.congestion.domain;

/**
 * OpenAPI v3.1 CongestionLevel 열거값.
 *
 * <p>문자열이 Flutter CongestionLevel.jsonValue와 정확히 같아야 한다.
 * 단계 판정은 서버(향후 AI 수집)가 하고, 앱은 이 값을 그대로 표시한다.
 */
public enum CongestionLevel {
    COMFORTABLE,
    NORMAL,
    CROWDED,
    VERY_CROWDED
}
