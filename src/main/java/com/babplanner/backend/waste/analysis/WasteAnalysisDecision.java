package com.babplanner.backend.waste.analysis;

/**
 * 분석기가 내릴 수 있는 판단.
 *
 * <p>리워드 지급 여부는 여기 없다. 지급은 분석 모델의 책임이 아니다.
 */
public enum WasteAnalysisDecision {
    APPROVED,
    REJECTED
}
