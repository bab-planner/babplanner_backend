package com.babplanner.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 문자를 실제로 보내지 않는 구현.
 *
 * <p>component scan 대상이 아니다. bapplanner.sms.mode=noop 을 명시한
 * 로컬/개발/테스트 환경에서만 SmsConfig가 등록한다. 인증번호와 번호는 어떤
 * 로그 레벨로도 출력하지 않는다.
 */
public class NoOpSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsSender.class);

    @Override
    public void sendVerificationCode(String phone, String code) {
        // 번호와 코드 모두 기록하지 않는다. 발송 시도가 있었다는 사실만 남긴다.
        log.info("인증번호 발송 요청을 받았지만 SMS 연동이 아직 구성되지 않았습니다.");
    }
}
