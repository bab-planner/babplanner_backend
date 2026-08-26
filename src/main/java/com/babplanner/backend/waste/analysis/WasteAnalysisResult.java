package com.babplanner.backend.waste.analysis;

/**
 * 분석 결과.
 *
 * <p>earnedPoints/couponIssued를 담지 않는다. 분석기가 리워드를 결정하지
 * 않는다는 뜻이다.
 *
 * @param wasteRate 0~1. 알 수 없으면 null.
 * @param confidence 0~1. 알 수 없으면 null.
 */
public record WasteAnalysisResult(
        WasteAnalysisDecision decision, Double wasteRate, Double confidence) {}
