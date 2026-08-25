package com.babplanner.backend.auth.service;

import com.babplanner.backend.common.config.AuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 인증 관련 구현체 선택.
 *
 * <p>NoOpSmsSender는 실제로 문자를 보내지 않는다. 운영에서 이것이 자동으로
 * 선택되면 send-code가 200을 돌려주는데 아무 문자도 가지 않는 상태가 되므로,
 * {@code bapplanner.sms.mode=noop}을 명시했을 때만 등록한다. 아무 구현도
 * 없으면 기동 단계에서 분명한 메시지와 함께 실패한다.
 */
@Configuration
public class SmsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bapplanner.sms", name = "mode", havingValue = "noop")
    public SmsSender noOpSmsSender() {
        return new NoOpSmsSender();
    }

    /** 어떤 SmsSender도 구성되지 않은 경우. 조용히 넘어가지 않는다. */
    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender unconfiguredSmsSender() {
        throw new IllegalStateException(
                "SmsSender가 구성되지 않았습니다. 운영에서는 실제 SMS 어댑터를 등록하고, "
                        + "로컬/개발에서는 bapplanner.sms.mode=noop 을 명시하세요.");
    }

    @Bean
    public VerificationCodeHasher verificationCodeHasher(AuthProperties properties) {
        String hmacKey = properties.getPhoneVerificationHmacKey();
        if (hmacKey != null && hmacKey.equals(properties.getSigningKey())) {
            throw new IllegalStateException(
                    "phone-verification-hmac-key는 JWT signing-key와 달라야 합니다.");
        }
        return new HmacVerificationCodeHasher(hmacKey);
    }
}
