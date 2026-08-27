package com.babplanner.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.common.config.AppConfig;
import com.babplanner.backend.common.config.BusinessTimeProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 급식 운영 시간대 설정.
 *
 * <p>확인하려는 것은 두 가지다. 설정이 없거나 틀렸을 때 조용히 넘어가지
 * 않는가, 그리고 시간대를 바꿔도 저장되는 시각 자체는 그대로인가.
 */
class BusinessTimeZoneTest {

    private BusinessTimeProperties properties(String zoneId) {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        properties.setBusinessZoneId(zoneId);
        return properties;
    }

    private Clock clockFor(String zoneId) {
        return new AppConfig().clock(properties(zoneId));
    }

    @Test
    @DisplayName("설정한 시간대로 Clock을 만든다")
    void buildsClockInConfiguredZone() {
        assertThat(clockFor("Asia/Seoul").getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("한국 전용이 아니다")
    void anyValidZoneWorks() {
        // 코드가 특정 지역을 아는 것이 아니라 설정을 따른다는 뜻이다.
        for (String zoneId : List.of("America/New_York", "Europe/Berlin", "Pacific/Auckland")) {
            assertThat(clockFor(zoneId).getZone()).as(zoneId).isEqualTo(ZoneId.of(zoneId));
        }
    }

    @Test
    @DisplayName("앞뒤 공백은 다듬어 받는다")
    void trimsSurroundingWhitespace() {
        assertThat(clockFor("  Asia/Seoul  ").getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("값이 없으면 기동에 실패한다")
    void missingZoneFailsFast() {
        // UTC로 조용히 넘어가면 지금의 버그가 그대로 숨는다.
        assertThatThrownBy(() -> clockFor(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bapplanner.business-zone-id");
    }

    @Test
    @DisplayName("빈 값도 기동에 실패한다")
    void blankZoneFailsFast() {
        for (String blank : List.of("", "   ", "\t")) {
            assertThatThrownBy(() -> clockFor(blank))
                    .as("%s", blank.isEmpty() ? "empty" : "whitespace")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bapplanner.business-zone-id");
        }
    }

    @Test
    @DisplayName("해석할 수 없는 시간대는 기동에 실패한다")
    void invalidZoneFailsFast() {
        for (String invalid : List.of("Mars/Seoul", "Korea", "UTC+9???", "Asia/Seoul/Gangnam")) {
            assertThatThrownBy(() -> clockFor(invalid))
                    .as("%s", invalid)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bapplanner.business-zone-id");
        }
    }

    @Test
    @DisplayName("시간대를 바꿔도 instant는 그대로다")
    void zoneDoesNotShiftInstants() {
        // 토큰 만료나 생성 시각은 Instant로만 다룬다. 시간대 설정이 그 값을
        // 밀어 버리면 이번 변경이 저장된 시각의 의미를 바꾸는 셈이 된다.
        Instant utc = Clock.systemUTC().instant();
        Instant seoul = clockFor("Asia/Seoul").instant();
        Instant newYork = clockFor("America/New_York").instant();

        assertThat(Math.abs(seoul.toEpochMilli() - utc.toEpochMilli())).isLessThan(1000);
        assertThat(Math.abs(newYork.toEpochMilli() - utc.toEpochMilli())).isLessThan(1000);
    }

    @Test
    @DisplayName("같은 순간이라도 시간대에 따라 날짜가 다르다")
    void sameInstantYieldsDifferentBusinessDates() {
        // 이 전제가 깨지면 시간대를 설정으로 뺀 의미가 없다.
        Instant instant = Instant.parse("2026-08-26T16:30:00Z");

        assertThat(LocalDate.now(Clock.fixed(instant, ZoneId.of("Asia/Seoul"))))
                .isEqualTo(LocalDate.parse("2026-08-27"));
        assertThat(LocalDate.now(Clock.fixed(instant, ZoneOffset.UTC)))
                .isEqualTo(LocalDate.parse("2026-08-26"));
    }

    @Test
    @DisplayName("설정 파일에 기본 시간대를 적어 두지 않았다")
    void configurationHasNoDefaultZone() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("business-zone-id: ${BAPPLANNER_BUSINESS_ZONE_ID:}");
    }

    @Test
    @DisplayName("운영 Clock이 UTC에 묶여 있지 않다")
    void productionClockIsNotPinnedToUtc() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/babplanner/backend/common/config/AppConfig.java"));

        assertThat(source).doesNotContain("Clock.systemUTC()");
        assertThat(source).contains("Clock.system(");
    }

    @Test
    @DisplayName("서비스 코드에 시간대를 박아 두지 않았다")
    void noHardcodedZoneInProductionCode() throws Exception {
        Path main = Path.of("src/main/java");
        Path config = main.resolve("com/babplanner/backend/common/config/BusinessTimeProperties.java");

        try (var paths = Files.walk(main)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (path.equals(config)) {
                    // 시간대를 해석하는 곳은 여기 하나뿐이다.
                    continue;
                }
                String code = Files.readAllLines(path).stream()
                        .filter(line -> {
                            String trimmed = line.stripLeading();
                            return !trimmed.startsWith("//")
                                    && !trimmed.startsWith("*")
                                    && !trimmed.startsWith("/*");
                        })
                        .reduce("", (a, b) -> a + "\n" + b);

                assertThat(code)
                        .as("%s", path)
                        .doesNotContain("ZoneId.of(")
                        .doesNotContain("Asia/Seoul")
                        .doesNotContain("ZoneOffset.UTC");
            }
        }
    }

    @Test
    @DisplayName("업무 날짜를 Clock 없이 구하지 않는다")
    void businessDatesAlwaysComeFromTheClock() throws Exception {
        Path main = Path.of("src/main/java");

        try (var paths = Files.walk(main)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path);

                // 인자 없는 now()는 서버 JVM의 시간대를 본다. 배포 장비가
                // 바뀌면 급식 날짜가 조용히 따라 바뀐다.
                assertThat(code)
                        .as("%s", path)
                        .doesNotContain("LocalDate.now()")
                        .doesNotContain("LocalDateTime.now()")
                        .doesNotContain("Instant.now()");
            }
        }
    }

    @Test
    @DisplayName("Clock을 만드는 데 예외가 없다")
    void validZoneBuildsWithoutThrowing() {
        assertThatCode(() -> clockFor("Asia/Seoul")).doesNotThrowAnyException();
    }
}
