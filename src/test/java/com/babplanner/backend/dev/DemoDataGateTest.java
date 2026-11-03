package com.babplanner.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.dev.demo.DemoDataCleaner;
import com.babplanner.backend.dev.demo.DemoDataGuard;
import com.babplanner.backend.dev.demo.DemoDataProperties;
import com.babplanner.backend.dev.demo.DemoDataRunner;
import com.babplanner.backend.dev.demo.DemoDataSeeder;
import com.babplanner.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/** 시연 데이터 시더가 기본 설정과 production 프로필에서 실행되지 않는다. */
class DemoDataGateTest extends ApiIntegrationTest {

    @Autowired ApplicationContext context;
    @Autowired DemoDataProperties properties;

    @Test
    @DisplayName("기본 설정은 꺼져 있고 local/dev가 아닌 프로필에는 시더 bean이 없다")
    void defaultOffAndNoBeansOutsideDev() {
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isDryRun()).isFalse();
        assertThat(context.getBeanNamesForType(DemoDataSeeder.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DemoDataCleaner.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DemoDataRunner.class)).isEmpty();
    }

    private DemoDataProperties props(boolean enabled, String action) {
        DemoDataProperties p = new DemoDataProperties();
        p.setEnabled(enabled);
        p.setAction(action);
        return p;
    }

    @Test
    @DisplayName("production 등 local/dev가 아닌 프로필에서 켜면 기동이 실패한다")
    void enabledOutsideDevFails() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new DemoDataGuard(props(true, "seed"), prod))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DemoDataGuard(props(true, "cleanup"), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new DemoDataGuard(props(false, ""), prod)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev에서도 action을 seed 또는 cleanup으로 명시해야 한다")
    void actionMustBeExplicit() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThatThrownBy(() -> new DemoDataGuard(props(true, ""), dev))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DemoDataGuard(props(true, "reset"), dev))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new DemoDataGuard(props(true, "SEED"), dev)).doesNotThrowAnyException();
        assertThatCode(() -> new DemoDataGuard(props(true, "cleanup"), dev)).doesNotThrowAnyException();
    }
}
