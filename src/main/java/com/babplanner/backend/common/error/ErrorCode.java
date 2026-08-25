package com.babplanner.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * 클라이언트가 분기에 사용할 수 있는 안정적인 오류 코드.
 *
 * <p>사용자에게 보이는 문구는 여기서 정하고, 내부 예외 메시지는 절대 노출하지
 * 않는다.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값을 다시 확인해 주세요."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증번호가 올바르지 않거나 만료되었습니다."),
    INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "휴대폰 인증 정보가 유효하지 않습니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 가입할 수 있습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "휴대폰 번호 또는 비밀번호를 확인해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),

    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 메뉴입니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공지사항입니다."),
    CONGESTION_NOT_AVAILABLE(HttpStatus.NOT_FOUND, "현재 혼잡도 정보를 제공할 수 없습니다."),

    DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호입니다."),
    DUPLICATE_STUDENT_ID(HttpStatus.CONFLICT, "이미 가입된 학번입니다."),

    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_ENTITY, "요청을 처리할 수 없습니다."),
    UNKNOWN_ALLERGY(HttpStatus.UNPROCESSABLE_ENTITY, "존재하지 않는 알레르기 항목이 포함되어 있습니다."),

    RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 인증번호를 요청해 주세요."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
