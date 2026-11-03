package com.babplanner.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공개 시연 배포용 스위치.
 *
 * <p>{@code SMS_MODE=noop} 은 인증번호를 응답에 싣는다. 인터넷에 공개하는 시연
 * 서버에서 회원가입 경로가 열려 있으면 누구나 가입할 수 있다. 그래서
 * {@code disableSignup=true} 이면 휴대폰 인증과 회원가입 경로를 서버에서 막는다.
 *
 * <p>기본은 false(기존 동작). 공개 배포에서는 반드시 true로 설정한다.
 */
@ConfigurationProperties(prefix = "bapplanner.demo")
public class DemoProperties {

    private boolean disableSignup = false;

    public boolean isDisableSignup() {
        return disableSignup;
    }

    public void setDisableSignup(boolean disableSignup) {
        this.disableSignup = disableSignup;
    }
}
