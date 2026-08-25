package com.babplanner.backend.common.error;

/**
 * 서비스 계층이 던지는 도메인 오류.
 *
 * <p>HTTP 상태와 응답 코드는 {@link ErrorCode}가 결정한다. 예외 메시지는
 * 서버 로그용이며 응답 본문에는 {@code ErrorCode}의 사용자 문구가 나간다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, Object details) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object details() {
        return details;
    }
}
