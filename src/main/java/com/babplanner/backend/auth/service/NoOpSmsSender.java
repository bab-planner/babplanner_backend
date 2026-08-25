package com.babplanner.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 사업자 연동 전까지 쓰는 기본 구현.
 *
 * <p>발송을 시도하지 않는다. 이 구현이 없으면 애플리케이션이 기동하지 못하므로
 * 자리만 채운다. 인증번호는 어떤 로그 레벨로도 출력하지 않는다.
 */
@Component
public class NoOpSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsSender.class);

    @Override
    public void sendVerificationCode(String phone, String code) {
        // 번호와 코드 모두 기록하지 않는다. 발송 시도가 있었다는 사실만 남긴다.
        log.info("인증번호 발송 요청을 받았지만 SMS 연동이 아직 구성되지 않았습니다.");
    }
}
