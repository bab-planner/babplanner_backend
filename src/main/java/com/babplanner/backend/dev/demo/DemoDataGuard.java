package com.babplanner.backend.dev.demo;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 시연 데이터 설정이 켜졌는데 실행할 수 없는 환경이면 기동을 멈춘다.
 *
 * <p>local/dev가 아닌 프로필(예: production)에서는 시더 bean이 아예 만들어지지 않는다.
 * 그런 곳에서 설정만 켜 두고 조용히 아무 일도 안 일어나면 "시연 데이터가 들어갔다"고
 * 착각하기 쉬우므로, 켜진 설정은 오류로 드러낸다.
 */
@Component
public class DemoDataGuard {

    public DemoDataGuard(DemoDataProperties properties, Environment environment) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!environment.acceptsProfiles(Profiles.of("local", "dev"))) {
            throw new IllegalStateException(
                    "bapplanner.demo-data.enabled 는 local 또는 dev 프로필에서만 쓸 수 있습니다.");
        }
        String action = properties.getAction();
        if (!DemoDataProperties.ACTION_SEED.equals(action)
                && !DemoDataProperties.ACTION_CLEANUP.equals(action)) {
            throw new IllegalStateException(
                    "bapplanner.demo-data.action 은 seed 또는 cleanup 으로 명시해야 합니다.");
        }
    }
}
