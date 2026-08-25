package com.babplanner.backend.auth.service;

/**
 * 인증번호 발송 경계.
 *
 * <p>실제 SMS 사업자 연동은 이 인터페이스 뒤에서만 이뤄진다. 구현체는 절대
 * 인증번호를 로그로 남기지 않는다.
 */
public interface SmsSender {

    void sendVerificationCode(String phone, String code);
}
