package com.babplanner.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.congestion.domain.CongestionObservation;
import com.babplanner.backend.congestion.repository.CongestionObservationRepository;
import com.babplanner.backend.congestion.repository.CongestionStatusRepository;
import com.babplanner.backend.dev.demo.DemoDataCleaner;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.dev.demo.DemoDataSeeder;
import com.babplanner.backend.dev.demo.DemoReport;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 시연 데이터 시더. 테스트 Clock의 시간대는 UTC이고 시더는 Clock의 시간대를 따른다(운영은 Asia/Seoul).
 * 시계는 2026-08-26 15:00(점심 이후)로 맞춘다.
 */
@ActiveProfiles("dev")
class DemoDataSeederTest extends ApiIntegrationTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired DemoDataCleaner cleaner;
    @Autowired JdbcTemplate jdbc;
    @Autowired WasteImageStorage imageStorage;
    @Autowired CongestionObservationRepository observationRepository;
    @Autowired CongestionStatusRepository statusRepository;
    @Autowired DiningSiteRepository siteRepository;

    private String adminToken;

    @BeforeEach
    void prepare() throws Exception {
        clock.advance(Duration.ofHours(15)); // 00:00Z → 15:00Z
        cleaner.cleanup(false);
        jdbc.update("DELETE FROM congestion_status");
        adminToken = adminToken();
    }

    @AfterEach
    void tearDown() {
        cleaner.cleanup(false);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int total(String table) {
        return count("SELECT COUNT(*) FROM " + table);
    }

    @Test
    @DisplayName("첫 실행은 종류별 데이터를 만들고 건수가 시연 범위에 맞는다")
    void firstRunCreatesData() {
        DemoReport report = seeder.seed(false);

        assertThat(report.doneCount("users")).isEqualTo(20);
        assertThat(report.doneCount("surveys")).isEqualTo(2);
        assertThat(report.doneCount("surveyResponses")).isBetween(30, 50);
        assertThat(report.doneCount("wasteSubmissions")).isBetween(80, 120);
        assertThat(report.doneCount("wasteApproved")).isPositive();
        assertThat(report.doneCount("wasteRejected")).isPositive();
        assertThat(report.doneCount("wastePending")).isPositive();
        assertThat(report.doneCount("menus")).isPositive();
        assertThat(report.doneCount("congestionObservations")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM users WHERE student_id LIKE 'DEMO-%'")).isEqualTo(20);
    }

    @Test
    @DisplayName("두 번째 실행은 아무것도 추가하지 않는다")
    void secondRunIsIdempotent() {
        seeder.seed(false);
        Map<String, Integer> before = snapshot();

        DemoReport second = seeder.seed(false);

        assertThat(snapshot()).isEqualTo(before);
        for (String type : List.of("users", "menus", "congestionObservations", "wasteSubmissions",
                "surveys", "surveyResponses", "rewardAccounts")) {
            assertThat(second.doneCount(type)).as(type).isZero();
        }
    }

    private Map<String, Integer> snapshot() {
        return Map.of(
                "users", total("users"),
                "menus", total("menus"),
                "obs", total("congestion_observations"),
                "status", total("congestion_status"),
                "waste", total("waste_submissions"),
                "tx", total("reward_transactions"),
                "accounts", total("reward_accounts"),
                "responses", total("survey_responses"),
                "records", total("demo_seed_records"));
    }

    @Test
    @DisplayName("기존 관리자 계정과 기존 식단은 변하지 않는다")
    void existingDataPreserved() {
        Map<String, Object> adminBefore = jdbc.queryForMap(
                "SELECT id, name, phone, role, password_hash, updated_at FROM users WHERE role = 'ADMIN'");
        List<Map<String, Object>> menusBefore =
                jdbc.queryForList("SELECT * FROM menus WHERE service_date = DATE '2026-08-26' "
                        + "AND site_id = (SELECT id FROM dining_sites WHERE code = 'STUDENT') ORDER BY id");
        int adminsBefore = count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'");

        seeder.seed(false);

        assertThat(jdbc.queryForMap(
                        "SELECT id, name, phone, role, password_hash, updated_at FROM users WHERE role = 'ADMIN'"))
                .isEqualTo(adminBefore);
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")).isEqualTo(adminsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM menus WHERE service_date = DATE '2026-08-26' "
                        + "AND site_id = (SELECT id FROM dining_sites WHERE code = 'STUDENT') AND meal_type = 'LUNCH' "
                        + "ORDER BY id"))
                .isEqualTo(menusBefore.stream()
                        .filter(m -> "LUNCH".equals(m.get("MEAL_TYPE")))
                        .toList());
        // 데모 사용자는 모두 일반 사용자이고 로그인용 해시는 BCrypt다.
        assertThat(count("SELECT COUNT(*) FROM users WHERE student_id LIKE 'DEMO-%' AND role <> 'USER'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE student_id LIKE 'DEMO-%' "
                        + "AND password_hash NOT LIKE '$2%'"))
                .isZero();
    }

    @Test
    @DisplayName("외래키와 상태 규칙이 맞다")
    void referentialIntegrity() {
        seeder.seed(false);

        assertThat(count("SELECT COUNT(*) FROM waste_submissions w WHERE NOT EXISTS "
                        + "(SELECT 1 FROM users u WHERE u.id = w.user_id) OR NOT EXISTS "
                        + "(SELECT 1 FROM menus m WHERE m.id = w.menu_id)"))
                .isZero();
        // 검토된 건은 실제 관리자가 검토했고, 대기 건은 검토 정보가 없다.
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE status IN ('APPROVED','REJECTED') "
                        + "AND (reviewed_at IS NULL OR reviewed_by_user_id NOT IN "
                        + "(SELECT id FROM users WHERE role = 'ADMIN'))"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE status = 'PENDING' "
                        + "AND (reviewed_at IS NOT NULL OR earned_points IS NOT NULL)"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE status = 'REJECTED' "
                        + "AND earned_points <> 0"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE reviewed_at < created_at"))
                .isZero();
        // 오늘(2026-08-26)에도 검토 완료 기록이 있다
        assertThat(count("SELECT COUNT(*) FROM waste_submissions w JOIN menus m ON m.id = w.menu_id "
                        + "WHERE m.service_date = DATE '2026-08-26' AND w.status IN ('APPROVED','REJECTED')"))
                .isPositive();
        // 사용자·메뉴 조합 중복 없음
        assertThat(count("SELECT COUNT(*) FROM (SELECT user_id, menu_id FROM waste_submissions "
                        + "GROUP BY user_id, menu_id HAVING COUNT(*) > 1) d"))
                .isZero();
        // 같은 식당·날짜·끼니·이름 메뉴 중복 없음
        assertThat(count("SELECT COUNT(*) FROM (SELECT site_id, service_date, meal_type, name FROM menus "
                        + "GROUP BY site_id, service_date, meal_type, name HAVING COUNT(*) > 1) d"))
                .isZero();
        // 시연 측정값만 잔반률을 갖고, 모델 버전은 DEMO_SEED로 표시된다
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE waste_rate IS NOT NULL "
                        + "AND (status <> 'APPROVED' OR analysis_model_version <> 'DEMO_SEED' "
                        + "OR confidence IS NOT NULL)"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE waste_rate IS NOT NULL"))
                .isPositive();
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE waste_rate < 0 OR waste_rate > 1"))
                .isZero();
    }

    @Test
    @DisplayName("잔반 승인 포인트는 건당 한 번만 적립되고 잔액이 원장과 맞는다")
    void rewardsConsistent() {
        seeder.seed(false);
        seeder.seed(false);

        int approved = count("SELECT COUNT(*) FROM waste_submissions WHERE status = 'APPROVED' "
                + "AND user_id IN (SELECT id FROM users WHERE student_id LIKE 'DEMO-%')");
        assertThat(count("SELECT COUNT(*) FROM reward_transactions WHERE reference_type = 'WASTE_SUBMISSION'"))
                .isEqualTo(approved);
        assertThat(count("SELECT COUNT(*) FROM (SELECT reference_id FROM reward_transactions "
                        + "WHERE reference_type = 'WASTE_SUBMISSION' GROUP BY reference_id "
                        + "HAVING COUNT(*) > 1) d"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM waste_submissions w WHERE w.status = 'APPROVED' AND w.earned_points <> 100"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM reward_accounts a WHERE a.points <> "
                        + "(SELECT COALESCE(SUM(t.amount), 0) FROM reward_transactions t WHERE t.user_id = a.user_id)"))
                .isZero();
    }

    @Test
    @DisplayName("혼잡도 현재값은 이력의 최신 관측과 같고 출처는 DEMO_SEED다")
    void congestionConsistent() {
        seeder.seed(false);

        assertThat(count("SELECT COUNT(*) FROM congestion_observations WHERE source = 'CCTV_AI'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM congestion_observations WHERE source = 'DEMO_SEED' "
                        + "AND (people_count IS NULL OR people_count <= 0 OR model_version IS NOT NULL)"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM congestion_observations o WHERE o.source = 'DEMO_SEED' "
                        + "AND NOT EXISTS (SELECT 1 FROM dining_sites s WHERE s.id = o.site_id)"))
                .isZero();
        // 시각 비교는 DB 리터럴이 아니라 JPA(UTC)로 읽어 Java에서 한다.
        assertThat(total("congestion_status")).isEqualTo(2);
        Instant now = clock.instant();
        for (var site : siteRepository.findAllByOrderBySortOrderAscIdAsc()) {
            List<CongestionObservation> observations = demoObservations(site.getId());
            assertThat(observations).isNotEmpty();
            // 아직 오지 않은 시각의 관측 없음
            assertThat(observations).allMatch(o -> !o.getObservedAt().isAfter(now));
            // 현재값 = 사이트별 가장 최근 시연 관측
            CongestionObservation latest = observations.get(observations.size() - 1);
            var status = statusRepository.findBySiteId(site.getId()).orElseThrow();
            assertThat(status.getSource()).isEqualTo("DEMO_SEED");
            assertThat(status.getObservedAt()).isEqualTo(latest.getObservedAt());
            assertThat(status.getLevel()).isEqualTo(latest.getLevel());
            assertThat(status.getPeopleCount()).isEqualTo(latest.getPeopleCount());
        }
        // 첫 식당의 하루: 여유 → 보통 → 혼잡 → 보통 → 여유, 인접 관측의 변화가 급격하지 않다
        Long first = siteRepository.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        List<CongestionObservation> day = demoObservations(first).stream()
                .filter(o -> !o.getObservedAt().isBefore(Instant.parse("2026-08-25T10:30:00Z"))
                        && o.getObservedAt().isBefore(Instant.parse("2026-08-25T14:30:00Z")))
                .toList();
        assertThat(day).hasSize(19);
        assertThat(day.get(0).getLevel().name()).isEqualTo("COMFORTABLE");
        assertThat(day.get(day.size() - 1).getLevel().name()).isEqualTo("COMFORTABLE");
        assertThat(day.stream().map(o -> o.getLevel().name()).toList()).contains("NORMAL", "CROWDED");
        for (int i = 1; i < day.size(); i++) {
            assertThat(Math.abs(day.get(i).getPeopleCount() - day.get(i - 1).getPeopleCount()))
                    .isLessThanOrEqualTo(30);
        }
    }

    private List<CongestionObservation> demoObservations(Long siteId) {
        return observationRepository
                .findBySiteIdAndObservedAtBetweenOrderByObservedAtAscIdAsc(
                        siteId, Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"))
                .stream()
                .filter(o -> "DEMO_SEED".equals(o.getSource()))
                .toList();
    }

    @Test
    @DisplayName("실제 수집기가 쓴 현재값은 덮어쓰지 않는다")
    void realStatusNotOverwritten() {
        jdbc.update("INSERT INTO congestion_status (site_id, level, people_count, display_message, source, "
                + "observed_at, updated_at) SELECT id, 'CROWDED', 77, NULL, 'CCTV_AI', "
                + "TIMESTAMP '2026-08-26 05:00:00', TIMESTAMP '2026-08-26 05:00:00' "
                + "FROM dining_sites ORDER BY sort_order, id LIMIT 1");

        seeder.seed(false);

        assertThat(count("SELECT COUNT(*) FROM congestion_status WHERE source = 'CCTV_AI' AND people_count = 77"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 API가 시연 데이터로 채워진 결과를 돌려준다")
    void adminApisReturnData() throws Exception {
        seeder.seed(false);

        JsonNode dashboard = body(getJson("/api/v1/admin/dashboard", adminToken)).path("data");
        assertThat(dashboard.path("currentCongestion").path("level").asText()).isNotBlank();
        assertThat(dashboard.path("wasteSummary").path("totalSubmissions").asLong()).isPositive();

        JsonNode summary = body(getJson(
                        "/api/v1/admin/waste/summary?from=2026-08-20&to=2026-08-26", adminToken))
                .path("data");
        assertThat(summary.path("totalSubmissions").asLong()).isPositive();
        assertThat(summary.path("averageWasteRate").asDouble()).isBetween(0.0, 1.0);
        assertThat(body(getJson("/api/v1/admin/waste/by-menu?from=2026-08-20&to=2026-08-26", adminToken))
                        .path("data").size())
                .isPositive();

        MvcResult series = getJson(
                "/api/v1/admin/congestion/timeseries?from=2026-08-19T00:00:00Z&to=2026-08-27T00:00:00Z",
                adminToken);
        assertThat(series.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(series).path("data").size()).isGreaterThan(50);

        List<Long> surveyIds = jdbc.queryForList(
                "SELECT id FROM surveys WHERE title LIKE '[시연]%' ORDER BY id", Long.class);
        assertThat(surveyIds).hasSize(2);
        long responses = 0;
        for (Long id : surveyIds) {
            JsonNode result = body(getJson("/api/v1/admin/surveys/" + id + "/results", adminToken)).path("data");
            responses += result.path("responseCount").asLong();
            for (JsonNode question : result.path("questionResults")) {
                JsonNode s = question.path("summary");
                if (s.has("average")) {
                    // 전부 만점이 아니다
                    assertThat(s.path("average").asDouble()).isBetween(1.5, 4.5);
                    int nonZero = 0;
                    for (JsonNode n : s.path("distribution")) {
                        if (n.asInt() > 0) {
                            nonZero++;
                        }
                    }
                    assertThat(nonZero).isGreaterThanOrEqualTo(3);
                }
            }
        }
        assertThat(responses).isBetween(30L, 50L);

        MvcResult list = getJson("/api/v1/admin/waste-submissions?limit=200", adminToken);
        JsonNode rows = body(list).path("data");
        assertThat(rows.size()).isBetween(80, 120);
        long firstId = rows.get(0).path("id").asLong();
        var image = perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/admin/waste-submissions/" + firstId + "/image")
                        .header("Authorization", "Bearer " + adminToken))
                .getResponse();
        assertThat(image.getStatus()).isEqualTo(200);
        assertThat(image.getContentType()).isEqualTo("image/png");
        assertThat(image.getContentAsByteArray().length).isGreaterThan(100);
    }

    @Test
    @DisplayName("모든 제출의 이미지 키는 실제 저장소에 존재한다")
    void imagesExist() {
        seeder.seed(false);

        List<String> keys = jdbc.queryForList(
                "SELECT image_storage_key FROM waste_submissions w JOIN demo_seed_records r "
                        + "ON r.entity_type = 'WASTE_SUBMISSION' AND r.entity_id = w.id",
                String.class);
        assertThat(keys).hasSizeBetween(80, 120);
        assertThat(keys).allMatch(imageStorage::exists);
    }

    @Test
    @DisplayName("날짜가 바뀐 뒤 다시 실행해도 중복 없이 새 날짜만 추가된다")
    void nextDayRun() {
        seeder.seed(false);
        int wasteBefore = total("waste_submissions");
        int menusBefore = total("menus");

        clock.advance(Duration.ofDays(1));
        DemoReport report = seeder.seed(false);

        assertThat(report.doneCount("users")).isZero();
        assertThat(report.doneCount("surveys")).isZero();
        assertThat(report.doneCount("menus")).isPositive();
        assertThat(total("menus")).isGreaterThan(menusBefore);
        assertThat(total("waste_submissions")).isBetween(wasteBefore + 5, wasteBefore + 25);
        assertThat(count("SELECT COUNT(*) FROM (SELECT user_id, menu_id FROM waste_submissions "
                        + "GROUP BY user_id, menu_id HAVING COUNT(*) > 1) d"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM (SELECT site_id, service_date, meal_type, name FROM menus "
                        + "GROUP BY site_id, service_date, meal_type, name HAVING COUNT(*) > 1) d"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM (SELECT site_id, observed_at FROM congestion_observations "
                        + "WHERE source = 'DEMO_SEED' GROUP BY site_id, observed_at HAVING COUNT(*) > 1) d"))
                .isZero();
        // 현재값은 새 날짜의 최근 관측으로 이동했다
        for (var site : siteRepository.findAllByOrderBySortOrderAscIdAsc()) {
            assertThat(statusRepository.findBySiteId(site.getId()).orElseThrow().getObservedAt())
                    .isAfter(Instant.parse("2026-08-27T00:00:00Z"));
        }
    }

    @Test
    @DisplayName("dry-run은 아무것도 저장하지 않고 이미지도 만들지 않는다")
    void dryRunWritesNothing() {
        Map<String, Integer> before = snapshot();

        DemoReport report = seeder.seed(true);

        assertThat(report.isDryRun()).isTrue();
        assertThat(report.doneCount("wasteSubmissions")).isBetween(80, 120);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM waste_submissions WHERE image_storage_key = 'dry-run'")).isZero();
    }

    @Test
    @DisplayName("기존 사용자와 충돌하면 아무것도 쓰지 않고 중단한다")
    void conflictAborts() {
        userRepository.save(new UserEntity(
                "실제 사용자", "01000000003", "20209999", null, Role.USER, "x", clock.instant()));
        Map<String, Integer> before = snapshot();

        assertThatThrownBy(() -> seeder.seed(false)).isInstanceOf(IllegalStateException.class);

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("관리자 계정이 없으면 중단한다")
    void requiresAdmin() {
        jdbc.update("DELETE FROM refresh_sessions");
        jdbc.update("DELETE FROM users WHERE role = 'ADMIN'");

        assertThatThrownBy(() -> seeder.seed(false)).isInstanceOf(IllegalStateException.class);
        assertThat(count("SELECT COUNT(*) FROM users WHERE student_id LIKE 'DEMO-%'")).isZero();
    }

    @Test
    @DisplayName("정리는 시연 데이터만 지우고 기존 데이터와 파일을 정확히 처리한다")
    void cleanupOnlyDemoData() {
        int adminsBefore = count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'");
        int menusBefore = total("menus");
        seeder.seed(false);
        List<String> keys = jdbc.queryForList("SELECT image_storage_key FROM waste_submissions", String.class);
        // 실제 사용자 한 명과 그 사용자의 제출(시연 메뉴를 가리킴)을 만든다
        userRepository.save(new UserEntity(
                "실제 사용자", "01099998888", "20201111", null, Role.USER, "x", clock.instant()));
        Long realUser = jdbc.queryForObject("SELECT id FROM users WHERE phone = '01099998888'", Long.class);
        Long demoMenu = jdbc.queryForObject(
                "SELECT m.id FROM menus m JOIN demo_seed_records r ON r.entity_type = 'MENU' AND r.entity_id = m.id "
                        + "WHERE m.meal_type = 'LUNCH' ORDER BY m.id LIMIT 1",
                Long.class);
        jdbc.update("INSERT INTO waste_submissions (user_id, menu_id, status, image_storage_key, captured_at, "
                + "created_at, updated_at) VALUES (?, ?, 'PENDING', 'real-key', TIMESTAMP '2026-08-26 01:00:00', "
                + "TIMESTAMP '2026-08-26 01:00:00', TIMESTAMP '2026-08-26 01:00:00')", realUser, demoMenu);

        DemoReport dry = cleaner.cleanup(true);
        assertThat(dry.doneCount("wasteSubmissions")).isBetween(80, 120);
        assertThat(count("SELECT COUNT(*) FROM users WHERE student_id LIKE 'DEMO-%'")).isEqualTo(20);
        assertThat(keys).allMatch(imageStorage::exists);

        cleaner.cleanup(false);

        assertThat(count("SELECT COUNT(*) FROM users WHERE student_id LIKE 'DEMO-%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")).isEqualTo(adminsBefore);
        assertThat(count("SELECT COUNT(*) FROM users WHERE phone = '01099998888'")).isEqualTo(1);
        // 실제 제출은 남고, 그것이 가리키는 시연 메뉴도 남는다(참조 보호)
        assertThat(count("SELECT COUNT(*) FROM waste_submissions")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM menus WHERE id = ?", demoMenu)).isEqualTo(1);
        assertThat(total("menus")).isEqualTo(menusBefore + 1);
        assertThat(count("SELECT COUNT(*) FROM congestion_observations WHERE source = 'DEMO_SEED'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM surveys WHERE title LIKE '[시연]%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM reward_accounts WHERE user_id IN "
                        + "(SELECT id FROM users WHERE student_id LIKE 'DEMO-%')"))
                .isZero();
        assertThat(keys).noneMatch(imageStorage::exists);
        // 기존 시드 식단(V11)은 그대로
        assertThat(count("SELECT COUNT(*) FROM menus WHERE name = '제육덮밥 정식'")).isEqualTo(1);

        // 두 번 실행해도 안전하다
        cleaner.cleanup(false);
        assertThat(count("SELECT COUNT(*) FROM waste_submissions")).isEqualTo(1);

        jdbc.update("DELETE FROM waste_submissions WHERE image_storage_key = 'real-key'");
        jdbc.update("DELETE FROM users WHERE phone = '01099998888'");
    }
}
