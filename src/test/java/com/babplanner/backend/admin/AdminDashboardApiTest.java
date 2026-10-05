package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.congestion.repository.CongestionStatusRepository;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.domain.MenuCategory;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.data.Offset;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리자 대시보드.
 *
 * <p>여기서 확인하는 것은 대부분 "채우지 않았다"는 사실이다. 예측 숫자가
 * 한 번 화면에 뜨면 관리자는 그 숫자로 식수를 잡고 식재료를 주문한다.
 */
class AdminDashboardApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/dashboard";

    /** 테스트 Clock이 멈춰 있는 날. 대시보드가 말하는 "오늘"이다. */
    private static final LocalDate TODAY = LocalDate.parse("2026-08-26");

    @Autowired private MenuRepository menuRepository;
    @Autowired private DiningSiteRepository siteRepository;
    @Autowired private CongestionStatusRepository congestionStatusRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.babplanner.backend.demand.repository.DemandPredictionRepository
            demandPredictionRepository;

    /**
     * 이 테스트가 만든 식단에만 붙이는 표시.
     *
     * <p>오늘 날짜에는 씨앗 식단이 이미 있다. 날짜로 싹 지우면 그것을 세는
     * 다른 테스트가 무너지므로 내가 만든 것만 골라 지운다.
     */
    private static final String MARK = "[대시보드테스트] ";

    @BeforeEach
    @AfterEach
    void removeMenusCreatedHere() {
        // 제출이 남아 있으면 FK가 걸려 식단을 지울 수 없다. 공용 정리는 테스트
        // 앞에서만 돌기 때문에 뒷정리에서는 여기서 먼저 비운다.
        wasteSubmissionRepository.deleteAll();
        wasteSubmissionRepository.flush();
        for (LocalDate date : List.of(TODAY.minusDays(1), TODAY)) {
            menuRepository.deleteAll(menuRepository.search(date, null, null, null).stream()
                    .filter(menu -> menu.getName().startsWith(MARK))
                    .toList());
        }
        menuRepository.flush();
    }

    private Menu menu(LocalDate serviceDate) {
        return menuRepository.save(new Menu(
                siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst(),
                serviceDate,
                MealType.LUNCH,
                MenuCategory.KOREAN,
                MARK + "김치찌개 정식",
                List.of("김치찌개"),
                clock.instant()));
    }

    private void analyzed(Menu menu, double wasteRate) {
        long userId = userRepository.findByPhone(PHONE).orElseThrow().getId();
        WasteSubmission submission = new WasteSubmission(
                userId, menu.getId(), "key-" + wasteRate, clock.instant(), clock.instant());
        submission.applyMeasurement(wasteRate, 0.9, clock.instant());
        submission.markReviewed(WasteSubmissionStatus.APPROVED, 100, null, clock.instant());
        wasteSubmissionRepository.save(submission);
    }

    private JsonNode dashboard() throws Exception {
        return body(getJson(PATH, adminToken())).path("data");
    }

    @Test
    @DisplayName("예상 식수 인원을 지어내지 않는다")
    void predictionsAreNull() throws Exception {
        JsonNode data = dashboard();

        assertThat(data.has("todayPredictedPeople")).isTrue();
        assertThat(data.has("tomorrowPredictedPeople")).isTrue();
        assertThat(data.path("todayPredictedPeople").isNull()).isTrue();
        assertThat(data.path("tomorrowPredictedPeople").isNull()).isTrue();
    }

    @Test
    @DisplayName("발주 추천은 빈 배열이다")
    void procurementPreviewIsEmpty() throws Exception {
        JsonNode preview = dashboard().path("procurementPreview");

        assertThat(preview.isArray()).isTrue();
        assertThat(preview).isEmpty();
    }

    @Test
    @DisplayName("현재 혼잡도는 저장된 값을 그대로 쓴다")
    void currentCongestionComesFromStorage() throws Exception {
        var stored = congestionStatusRepository
                .findFirstByOrderBySiteSortOrderAscSiteIdAsc()
                .orElseThrow();

        JsonNode congestion = dashboard().path("currentCongestion");

        assertThat(congestion.path("siteId").asLong()).isEqualTo(stored.getSite().getId());
        assertThat(congestion.path("level").asText()).isEqualTo(stored.getLevel().name());
        assertThat(congestion.path("source").asText()).isEqualTo(stored.getSource());
    }

    @Test
    @DisplayName("siteId로 다른 식당의 혼잡도를 본다")
    void congestionFollowsSiteId() throws Exception {
        var sites = siteRepository.findAllByOrderBySortOrderAscIdAsc();
        long otherSiteId = sites.get(1).getId();

        JsonNode data = body(getJson(PATH + "?siteId=" + otherSiteId, adminToken())).path("data");

        assertThat(data.path("currentCongestion").path("siteId").asLong()).isEqualTo(otherSiteId);
    }

    @Test
    @DisplayName("혼잡도가 없으면 null이고 404가 아니다")
    void missingCongestionDoesNotFailTheDashboard() throws Exception {
        // 씨앗 상태를 지우면 이 JVM의 다른 테스트까지 영향을 받는다. 대신
        // 아직 상태가 없는 식당을 하나 만들어 그쪽을 본다. 엔티티에 테스트만
        // 쓰는 생성자를 뚫지 않으려고 SQL로 넣는다.
        jdbcTemplate.update(
                "insert into dining_sites (name, code, active, sort_order) values (?, ?, ?, ?)",
                "신규 식당",
                "TEST_NEW",
                true,
                99);
        Long freshId = jdbcTemplate.queryForObject(
                "select id from dining_sites where code = ?", Long.class, "TEST_NEW");
        try {
            assertThat(congestionStatusRepository.findBySiteId(freshId)).isEmpty();

            MvcResult result = getJson(PATH + "?siteId=" + freshId, adminToken());

            // 화면 한 칸이 비었다고 대시보드 전체를 못 열게 만들지 않는다.
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(body(result).path("data").path("currentCongestion").isNull()).isTrue();
        } finally {
            jdbcTemplate.update("delete from dining_sites where code = ?", "TEST_NEW");
        }
    }

    @Test
    @DisplayName("잔반 요약은 오늘 제공된 메뉴만 센다")
    void wasteSummaryCoversToday() throws Exception {
        signup();
        analyzed(menu(TODAY), 0.4);
        analyzed(menu(TODAY.minusDays(1)), 0.9);

        JsonNode summary = dashboard().path("wasteSummary");

        assertThat(summary.path("totalSubmissions").asLong()).isEqualTo(1);
        assertThat(summary.path("averageWasteRate").asDouble())
                .isCloseTo(0.4, Offset.offset(1e-9));
    }

    @Test
    @DisplayName("오늘 표본이 없으면 평균이 null이다")
    void wasteSummaryIsHonestWhenEmpty() throws Exception {
        JsonNode summary = dashboard().path("wasteSummary");

        assertThat(summary.path("totalSubmissions").asLong()).isZero();
        assertThat(summary.path("averageWasteRate").isNull()).isTrue();
        assertThat(summary.path("repeatedWasteMenuCount").isNull()).isTrue();
    }

    @Test
    @DisplayName("저장된 예측이 있어도 대시보드는 여전히 null이다")
    void storedPredictionsDoNotReachTheDashboard() throws Exception {
        var site = siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst();
        var prediction = demandPredictionRepository.save(
                new com.babplanner.backend.demand.domain.DemandPrediction(
                        site,
                        TODAY,
                        com.babplanner.backend.menu.domain.MealType.LUNCH,
                        528,
                        "fastapi-fixture-demand-v1",
                        "demand-dashboard-check",
                        clock.instant()));
        try {
            JsonNode data = dashboard();

            // 버그가 아니다. 대시보드에는 mealType이 없어서 어느 끼니의 예측을
            // 한 숫자로 보여 줄지 정해진 적이 없다. 아무거나 고르면 관리자는
            // 그것이 무엇인지 모른 채 발주한다.
            assertThat(data.path("todayPredictedPeople").isNull()).isTrue();
            assertThat(data.path("tomorrowPredictedPeople").isNull()).isTrue();
            assertThat(data.path("procurementPreview")).isEmpty();
        } finally {
            demandPredictionRepository.delete(prediction);
            demandPredictionRepository.flush();
        }
    }

    @Test
    @DisplayName("Figma 예시 숫자가 응답에 없다")
    void figmaSampleNumbersAreAbsent() throws Exception {
        String json = getJson(PATH, adminToken()).getResponse().getContentAsString();

        // 528, 370은 디자인 시안과 OpenAPI example에 적힌 값이다.
        assertThat(json).doesNotContain("528").doesNotContain("370");
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        assertThat(getJson(PATH).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void normalUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(getJson(PATH, token).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("대시보드 조회가 리워드를 만들지 않는다")
    void dashboardGrantsNoRewards() throws Exception {
        signup();
        analyzed(menu(TODAY), 0.4);
        long couponsBefore = couponRepository.count();
        long transactionsBefore = rewardTransactionRepository.count();

        dashboard();

        assertThat(couponRepository.count()).isEqualTo(couponsBefore);
        assertThat(rewardTransactionRepository.count()).isEqualTo(transactionsBefore);
    }
}
