package com.babplanner.backend.waste.analysis;

/**
 * 분석을 수행하지 못했다.
 *
 * <p>입력을 승인하지 않은 것(REJECTED)과 구분한다. 이쪽은 제출이 FAILED로
 * 끝난다. 원인 예외는 감싸서 전달하되, 메시지에 이미지나 토큰 같은 값을
 * 담지 않는다.
 */
public class WasteAnalysisException extends RuntimeException {

    public WasteAnalysisException(String message) {
        super(message);
    }

    public WasteAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
