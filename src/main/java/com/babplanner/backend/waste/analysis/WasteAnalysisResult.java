package com.babplanner.backend.waste.analysis;

/**
 * 분석 결과.
 *
 * <p>earnedPoints/couponIssued를 담지 않는다. 분석기가 리워드를 결정하지
 * 않는다는 뜻이다.
 *
 * @param wasteRate 0~1. 알 수 없으면 null.
 * @param confidence 0~1. 알 수 없으면 null.
 * @param modelVersion 이 판정을 낸 구현. 어떤 결과가 어디서 나왔는지 나중에
 *     되짚기 위한 내부 값이며 사용자 응답에는 넣지 않는다.
 */
public record WasteAnalysisResult(
        WasteAnalysisDecision decision,
        Double wasteRate,
        Double confidence,
        String modelVersion) {}
