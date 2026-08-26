package com.babplanner.backend.waste.analysis;

import com.babplanner.backend.waste.storage.WasteImageStorage;
import java.io.IOException;
import java.io.InputStream;

/**
 * 개발용 분석기.
 *
 * <p><b>이 값들은 AI 분석 결과가 아니라 고정된 개발 fixture다.</b> 이미지
 * 내용을 실제로 판독하지 않고, 저장소에서 읽을 수 있는지만 확인한 뒤 정해진
 * 결과를 돌려준다.
 *
 * <p>component scan 대상이 아니며 명시적 설정이 있을 때만 등록된다.
 */
public class FakeWasteAnalyzer implements WasteAnalyzer {

    /** OpenAPI가 0~1로 정의한 범위 안의 고정 개발값. */
    private static final double FIXED_WASTE_RATE = 0.15;

    private static final double FIXED_CONFIDENCE = 0.92;

    private final WasteImageStorage storage;

    public FakeWasteAnalyzer(WasteImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public WasteAnalysisResult analyze(WasteAnalysisInput input) {
        // 이미지를 실제로 판독하지는 않지만, 읽을 수 없는 이미지는 분석할 수 없다.
        try (InputStream stream = storage.read(input.imageStorageKey())) {
            if (stream.read() == -1) {
                return new WasteAnalysisResult(WasteAnalysisDecision.REJECTED, null, null);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("이미지를 읽지 못했습니다.", exception);
        }
        return new WasteAnalysisResult(
                WasteAnalysisDecision.APPROVED, FIXED_WASTE_RATE, FIXED_CONFIDENCE);
    }
}
