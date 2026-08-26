package com.babplanner.backend.waste.analysis;

/**
 * 잔반 이미지 분석 경계.
 *
 * <p>이번 단계는 이 추상화까지다. 실제 비전 모델이나 외부 AI 서버 호출은
 * 이 인터페이스 뒤에서 별도 어댑터로 구현한다.
 */
public interface WasteAnalyzer {

    WasteAnalysisResult analyze(WasteAnalysisInput input);
}
