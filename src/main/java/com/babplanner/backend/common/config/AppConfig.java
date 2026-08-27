package com.babplanner.backend.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    /**
     * 시간을 주입 가능하게 둔다.
     *
     * <p>TTL 테스트에서 Thread.sleep 대신 고정/이동 가능한 Clock을 쓰기 위함이다.
     *
     * <p>시간대는 설정된 급식 운영 시간대를 따른다. Instant는 시간대와 무관하게
     * 같으므로 토큰 만료나 생성 시각 같은 값은 달라지지 않는다. 바뀌는 것은
     * "오늘이 며칠인가"를 묻는 코드가 보는 날짜뿐이고, 그 날짜는 급식소 현지
     * 영업일이어야 한다.
     *
     * <p>업무 시각의 출처는 이 bean 하나다. 서비스 코드에서 다시 시간대를
     * 붙이지 않는다.
     */
    @Bean
    public Clock clock(BusinessTimeProperties businessTimeProperties) {
        return Clock.system(businessTimeProperties.resolvedZone());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
