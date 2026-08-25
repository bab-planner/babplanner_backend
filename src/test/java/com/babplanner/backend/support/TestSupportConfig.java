package com.babplanner.backend.support;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트 전용 bean.
 *
 * <p>실제 SMS 발송과 실제 시계를 모두 대체한다. 어떤 테스트도 외부 네트워크를
 * 사용하지 않는다. 운영 bean과 이름이 겹치지 않게 두고 @Primary로 우선한다.
 */
@TestConfiguration
public class TestSupportConfig {

    /** SmsSender 타입으로도 주입되므로 NoOpSmsSender 대신 이 구현이 쓰인다. */
    @Bean
    @Primary
    public FakeSmsSender fakeSmsSender() {
        return new FakeSmsSender();
    }

    @Bean
    public MutableTestClock mutableTestClock() {
        return new MutableTestClock();
    }

    @Bean
    @Primary
    public Clock testClock(MutableTestClock mutableTestClock) {
        return mutableTestClock;
    }
}
