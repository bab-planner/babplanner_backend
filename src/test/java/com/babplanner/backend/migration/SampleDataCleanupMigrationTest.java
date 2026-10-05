package com.babplanner.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V29 샘플 데이터 정리가 시드 표식과 정확히 일치하는 행만 지우는지 본다.
 *
 * <p>Flyway는 컨텍스트 시작 때 이미 V29를 적용했다. 여기서는 샘플 행과 실제 행을 나란히
 * 다시 넣고 V29 스크립트를 한 번 더 실행해 결과를 확인한다.
 */
class SampleDataCleanupMigrationTest extends ApiIntegrationTest {

    private static final Path SCRIPT =
            Path.of("src/main/resources/db/migration/V29__remove_dev_sample_data.sql");
    private static final String SEED_TS = "TIMESTAMP '2026-08-26 02:30:00'";
    private static final String REAL_TITLE = "[실제] 개강 안내 (cleanup-test)";
    private static final String EDITED_SAMPLE_TITLE = "[개발 샘플] 일반 공지 최신";

    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void removeTestRows() {
        jdbc.update("delete from notices where title = ?", REAL_TITLE);
        jdbc.update("delete from notices where title like '[개발 샘플]%'");
        jdbc.update("delete from congestion_observations where display_message = 'cleanup-test'");
        jdbc.update("delete from congestion_status where display_message = 'cleanup-test'");
    }

    private void runScript() throws Exception {
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(SCRIPT)) {
            if (!line.stripLeading().startsWith("--")) {
                sql.append(line).append('\n');
            }
        }
        Arrays.stream(sql.toString().split(";"))
                .map(String::strip)
                .filter(statement -> !statement.isEmpty())
                .forEach(jdbc::execute);
    }

    private long siteId(String code) {
        return jdbc.queryForObject("select id from dining_sites where code = ?", Long.class, code);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void insertNotice(String title, String createdAt, String updatedAt) {
        jdbc.update("insert into notices (title, content, important, published_at, created_at,"
                + " updated_at) values (?, 'c', false, TIMESTAMP '2026-08-25 01:00:00',"
                + " TIMESTAMP '" + createdAt + "', TIMESTAMP '" + updatedAt + "')", title);
    }

    @Test
    @DisplayName("공지는 시드와 제목과 시각이 모두 같은 행만 지운다")
    void removesOnlySampleNotices() throws Exception {
        jdbc.update("delete from notices where title like '[개발 샘플]%'");
        insertNotice("[개발 샘플] 시스템 점검 안내", "2026-08-20 01:00:00", "2026-08-20 01:00:00");
        insertNotice("[개발 샘플] 일반 공지 이전", "2026-08-18 01:00:00", "2026-08-18 01:00:00");
        // 같은 제목이지만 관리자가 고친 행(updated_at 다름)은 남긴다.
        insertNotice(EDITED_SAMPLE_TITLE, "2026-08-25 01:00:00", "2026-09-01 09:00:00");
        // 실제 공지.
        insertNotice(REAL_TITLE, "2026-08-20 01:00:00", "2026-08-20 01:00:00");
        long beforeMenus = count("select count(*) from menus");
        long beforeSites = count("select count(*) from dining_sites");
        long beforeAllergies = count("select count(*) from allergies");

        runScript();

        assertThat(count("select count(*) from notices where title = '[개발 샘플] 시스템 점검 안내'"))
                .isZero();
        assertThat(count("select count(*) from notices where title = '[개발 샘플] 일반 공지 이전'"))
                .isZero();
        assertThat(count("select count(*) from notices where title = ?", EDITED_SAMPLE_TITLE))
                .isEqualTo(1);
        assertThat(count("select count(*) from notices where title = ?", REAL_TITLE)).isEqualTo(1);
        assertThat(count("select count(*) from menus")).isEqualTo(beforeMenus);
        assertThat(count("select count(*) from dining_sites")).isEqualTo(beforeSites);
        assertThat(count("select count(*) from allergies")).isEqualTo(beforeAllergies);
    }

    @Test
    @DisplayName("혼잡도는 DEV_SEED 시드 행만 지우고 실제 수집 행은 남긴다")
    void removesOnlySampleCongestion() throws Exception {
        long student = siteId("STUDENT");
        long staff = siteId("STAFF");
        List<Long> sites = List.of(student, staff);
        jdbc.update("delete from congestion_status where site_id in (?, ?)", student, staff);
        // 시드 그대로인 현재값(지워져야 함)과 수집기가 갱신한 현재값(남아야 함).
        jdbc.update("insert into congestion_status (site_id, level, people_count, display_message,"
                + " source, observed_at, updated_at) values (?, 'NORMAL', 42, 'cleanup-test',"
                + " 'DEV_SEED', " + SEED_TS + ", " + SEED_TS + ")", student);
        jdbc.update("insert into congestion_status (site_id, level, people_count, display_message,"
                + " source, observed_at, updated_at) values (?, 'BUSY', 90, 'cleanup-test',"
                + " 'CCTV', TIMESTAMP '2026-09-10 03:00:00', TIMESTAMP '2026-09-10 03:00:00')",
                staff);
        // 관측 이력: V22 백필(지워져야 함), 장비가 보낸 같은 표식 행(남아야 함), 다른 출처(남아야 함).
        String obs = "insert into congestion_observations (site_id, level, people_count,"
                + " display_message, source, observed_at, created_at, device_id)"
                + " values (?, 'NORMAL', 42, 'cleanup-test', ?, " + SEED_TS + ", " + SEED_TS + ", ?)";
        jdbc.update(obs, student, "DEV_SEED", null);
        jdbc.update(obs, student, "DEV_SEED", "cam-1");
        jdbc.update(obs, staff, "CCTV", null);

        runScript();

        assertThat(count("select count(*) from congestion_status where display_message ="
                        + " 'cleanup-test' and site_id = ?", student))
                .isZero();
        assertThat(count("select count(*) from congestion_status where display_message ="
                        + " 'cleanup-test' and site_id = ?", staff))
                .isEqualTo(1);
        assertThat(count("select count(*) from congestion_observations where display_message ="
                        + " 'cleanup-test'"))
                .isEqualTo(2);
        assertThat(count("select count(*) from congestion_observations where display_message ="
                        + " 'cleanup-test' and device_id is null and source = 'DEV_SEED'"))
                .isZero();
        assertThat(sites).hasSize(2);
    }

    @Test
    @DisplayName("해당 행이 없어도 스크립트가 아무 일 없이 끝난다")
    void idempotent() throws Exception {
        long before = count("select count(*) from menus");

        runScript();
        runScript();

        assertThat(count("select count(*) from menus")).isEqualTo(before);
    }
}
