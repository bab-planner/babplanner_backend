package com.babplanner.backend.common.client;

/**
 * AI 서비스를 부르지 못했거나, 돌아온 응답을 계약대로 읽을 수 없다.
 *
 * <p>연결 실패, 시간 초과, 인증 실패, 계약 위반을 하나로 묶는다. 관리자
 * 입장에서는 전부 "지금은 실행할 수 없다"는 같은 상황이고, 원인마다 다른
 * 사용자용 코드를 만들면 화면이 알려줄 수 있는 것보다 많은 구분이 생긴다.
 * 구체적인 원인은 서버 로그에만 남는다.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
