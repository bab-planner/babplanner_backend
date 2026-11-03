package com.babplanner.backend.dev.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 기동 시 시연 데이터 생성 또는 정리를 한 번 실행한다.
 *
 * <p>{@code bapplanner.demo-data.enabled=true}이고 local/dev 프로필일 때만 등록된다.
 * 생성과 정리는 {@code action}으로 구분한 별도 명령이며 한 번에 하나만 실행한다.
 * 다른 시더(DevMenuSeeder 등)보다 뒤에 실행해 기존 시드와 충돌하지 않게 한다.
 */
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "bapplanner.demo-data", name = "enabled", havingValue = "true")
@Order(1000)
public class DemoDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);

    private final DemoDataProperties properties;
    private final DemoDataSeeder seeder;
    private final DemoDataCleaner cleaner;

    public DemoDataRunner(
            DemoDataProperties properties, DemoDataSeeder seeder, DemoDataCleaner cleaner) {
        this.properties = properties;
        this.seeder = seeder;
        this.cleaner = cleaner;
    }

    @Override
    public void run(ApplicationArguments args) {
        DemoReport report = DemoDataProperties.ACTION_CLEANUP.equals(properties.getAction())
                ? cleaner.cleanup(properties.isDryRun())
                : seeder.seed(properties.isDryRun());
        log.info("시연 데이터 작업 결과: {}", report.summary());
    }
}
