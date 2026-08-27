package com.babplanner.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 설정값이 실제로 Clock bean까지 도달하는지.
 *
 * <p>단위 테스트는 메서드를 직접 부르므로 property 이름이 틀려도 통과한다.
 * 여기서는 이름을 포함한 실제 binding을 확인한다.
 */
@SpringBootTest(properties = "bapplanner.business-zone-id=America/New_York")
@ActiveProfiles("test")
class BusinessZoneBindingTest {

    /** 테스트용 Clock이 아니라 운영 설정이 만든 bean을 본다. */
    @Autowired
    @Qualifier("clock")
    private Clock clock;

    @Test
    @DisplayName("설정한 시간대가 Clock bean에 반영된다")
    void configuredZoneReachesTheClockBean() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/New_York"));
    }
}
