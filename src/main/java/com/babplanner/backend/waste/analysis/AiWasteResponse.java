package com.babplanner.backend.waste.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 분석 서비스 응답. OpenAPI {@code AIWasteResponse} 와 대응한다.
 *
 * <p>decision을 문자열로 받는다. 모르는 값이 오면 enum 변환 실패가 아니라
 * 우리 검증에서 걸러 실패로 처리하기 위해서다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiWasteResponse(
        String requestId,
        String decision,
        Double wasteRate,
        Double confidence,
        String modelVersion) {}
