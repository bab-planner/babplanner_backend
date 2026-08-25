package com.babplanner.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 개발용 시더가 운영에서 돌지 않는지 확인한다.
 *
 * <p>동작 자체는 local/dev 프로필에서만 일어나므로, 프로필 제한과 안전 장치가
 * 소스에 실제로 걸려 있는지를 고정한다.
 */
class DevMenuSeederTest {

    private String source() throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/babplanner/backend/dev/DevMenuSeeder.java"));
    }

    @Test
    @DisplayName("local/dev 프로필에서만 등록된다")
    void restrictedToLocalAndDevProfiles() throws IOException {
        assertThat(source()).contains("@Profile({\"local\", \"dev\"})");
    }

    @Test
    @DisplayName("이미 오늘 데이터가 있으면 아무것도 하지 않는다")
    void idempotentGuardExists() throws IOException {
        assertThat(source()).contains("SELECT COUNT(*) FROM menus WHERE service_date = ?");
    }

    @Test
    @DisplayName("production migration에는 동적 날짜가 없다")
    void migrationsHaveNoDynamicDates() throws IOException {
        try (var paths = Files.walk(Path.of("src/main/resources/db/migration"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".sql")).toList()) {
                // 주석은 제외한다. 설명에 CURRENT_DATE 같은 단어가 나올 수 있다.
                StringBuilder code = new StringBuilder();
                for (String line : Files.readAllLines(path)) {
                    if (!line.stripLeading().startsWith("--")) {
                        code.append(line).append('\n');
                    }
                }
                String sql = code.toString().toUpperCase();
                assertThat(sql).as("%s", path).doesNotContain("CURRENT_DATE");
                assertThat(sql).as("%s", path).doesNotContain("NOW()");
                assertThat(sql).as("%s", path).doesNotContain("CURRENT_TIMESTAMP");
            }
        }
    }

    @Test
    @DisplayName("테스트 프로필에서는 시더가 돌지 않아 seed 날짜가 그대로다")
    void testProfileKeepsFixedSeedDates() throws IOException {
        String seed =
                Files.readString(Path.of("src/main/resources/db/migration/V11__seed_sample_menus.sql"));

        assertThat(seed).contains("DATE '2026-08-26'");
        assertThat(seed).contains("DATE '2026-08-27'");
    }
}
