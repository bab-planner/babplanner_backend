package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 잔반 집계.
 *
 * <p>표본이 무엇인지가 이 API의 전부다. 분석되지 않은 건까지 세면 평균은
 * 실제로 잰 적 없는 값이 되고, 기간을 제출 시각으로 세면 늦게 올린 사진이
 * 다음 날 급식의 잔반으로 잡힌다.
 */
class AdminWasteAnalyticsApiTest extends ApiIntegrationTest {

    private static final String SUMMARY = "/api/v1/admin/waste/summary";
    private static final String BY_MENU = "/api/v1/admin/waste/by-menu";
    private static final LocalDate DAY = LocalDate.parse("2026-09-15");
    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    @Autowired private MenuRepository menuRepository;
    @Autowired private DiningSiteRepository siteRepository;

    /** 이 테스트가 만든 식단만 지운다. 씨앗 식단은 다른 날짜라 건드리지 않는다. */
    @BeforeEach
    void clearTestMenus() {
        for (LocalDate date : List.of(DAY.minusDays(1), DAY, DAY.plusDays(1))) {
            menuRepository.deleteAll(menuRepository.search(date, null, null, null));
        }
        menuRepository.flush();
    }

    private Menu menu(String name, LocalDate serviceDate) {
        return menuRepository.save(new Menu(
                siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst(),
                serviceDate,
                MealType.LUNCH,
                MenuCategory.KOREAN,
                name,
                List.of(name),
                clock.instant()));
    }

    private long userId() {
        return userRepository.findByPhone(PHONE).orElseThrow().getId();
    }

    /** 분석이 끝난 제출. 상태 전이는 제품 코드가 정한 순서를 그대로 따른다. */
    private void analyzed(Menu menu, long userId, double wasteRate) {
        WasteSubmission submission = new WasteSubmission(
                userId, menu.getId(), "key-" + wasteRate, clock.instant(), clock.instant());
        submission.transitionTo(WasteSubmissionStatus.VERIFYING, clock.instant());
        submission.transitionTo(WasteSubmissionStatus.ANALYZING, clock.instant());
        submission.applyAnalysis(wasteRate, 0.9, "test-model", clock.instant());
        submission.transitionTo(WasteSubmissionStatus.APPROVED, clock.instant());
        wasteSubmissionRepository.save(submission);
    }

    /** 잔반률이 나오지 않은 제출. 표본에 들어가면 안 된다. */
    private void unanalyzed(Menu menu, long userId, WasteSubmissionStatus status) {
        WasteSubmission submission = new WasteSubmission(
                userId, menu.getId(), "key-" + status, clock.instant(), clock.instant());
        submission.transitionTo(WasteSubmissionStatus.VERIFYING, clock.instant());
        if (status != WasteSubmissionStatus.VERIFYING) {
            submission.transitionTo(WasteSubmissionStatus.ANALYZING, clock.instant());
        }
        if (status == WasteSubmissionStatus.REJECTED || status == WasteSubmissionStatus.FAILED) {
            submission.transitionTo(status, clock.instant());
        }
        wasteSubmissionRepository.save(submission);
    }

    /**
     * 평균 비교.
     *
     * <p>정확히 같은지 묻지 않는다. avg의 마지막 자리는 DB 엔진마다 다르다.
     * MySQL은 0.2와 0.4의 평균을 0.30000000000000004로 준다. 값을 자르지
     * 않고 그대로 내보내기로 했으니 이 차이도 그대로 나온다.
     */
    private void assertAverage(JsonNode data, double expected) {
        assertThat(data.path("averageWasteRate").asDouble()).isCloseTo(expected, TOLERANCE);
    }

    private String range(LocalDate from, LocalDate to) {
        return "?from=" + from + "&to=" + to;
    }

    private JsonNode summary(LocalDate from, LocalDate to, String token) throws Exception {
        return body(getJson(SUMMARY + range(from, to), token)).path("data");
    }

    private JsonNode byMenu(LocalDate from, LocalDate to, String token) throws Exception {
        return body(getJson(BY_MENU + range(from, to), token)).path("data");
    }

    @Test
    @DisplayName("분석된 건수와 평균을 돌려준다")
    void summarisesAnalyzedSubmissions() throws Exception {
        String token = accessTokenOf(signup());
        Menu menu = menu("김치찌개", DAY);
        analyzed(menu, userId(), 0.2);
        analyzed(menu, userId(), 0.4);

        JsonNode data = summary(DAY, DAY, adminToken());

        assertThat(data.path("totalSubmissions").asLong()).isEqualTo(2);
        assertAverage(data, 0.3);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("표본이 없으면 평균이 null이다")
    void averageIsNullWithoutSamples() throws Exception {
        JsonNode data = summary(DAY, DAY, adminToken());

        assertThat(data.path("totalSubmissions").asLong()).isZero();
        // 0.0으로 내리면 "아무도 남기지 않았다"로 읽힌다. 잰 적이 없는 것과 다르다.
        assertThat(data.path("averageWasteRate").isNull()).isTrue();
    }

    @Test
    @DisplayName("반복 잔반 메뉴 수는 아직 기준이 없어 null이다")
    void repeatedWasteMenuCountIsNull() throws Exception {
        signup();
        Menu menu = menu("김치찌개", DAY);
        analyzed(menu, userId(), 0.9);

        JsonNode data = summary(DAY, DAY, adminToken());

        assertThat(data.has("repeatedWasteMenuCount")).isTrue();
        assertThat(data.path("repeatedWasteMenuCount").isNull()).isTrue();
    }

    @Test
    @DisplayName("분석되지 않은 제출은 표본에서 뺀다")
    void unanalyzedSubmissionsAreExcluded() throws Exception {
        signup();
        Menu menu = menu("김치찌개", DAY);
        analyzed(menu, userId(), 0.5);
        unanalyzed(menu, userId(), WasteSubmissionStatus.VERIFYING);
        unanalyzed(menu, userId(), WasteSubmissionStatus.ANALYZING);
        unanalyzed(menu, userId(), WasteSubmissionStatus.REJECTED);
        unanalyzed(menu, userId(), WasteSubmissionStatus.FAILED);

        JsonNode data = summary(DAY, DAY, adminToken());

        assertThat(data.path("totalSubmissions").asLong()).isEqualTo(1);
        assertAverage(data, 0.5);
    }

    @Test
    @DisplayName("기간은 메뉴 제공일로 세고 양 끝을 포함한다")
    void rangeFollowsServiceDateInclusively() throws Exception {
        signup();
        analyzed(menu("어제", DAY.minusDays(1)), userId(), 0.1);
        analyzed(menu("오늘", DAY), userId(), 0.2);
        analyzed(menu("내일", DAY.plusDays(1)), userId(), 0.3);

        String token = adminToken();

        assertThat(summary(DAY, DAY, token).path("totalSubmissions").asLong()).isEqualTo(1);
        assertThat(summary(DAY.minusDays(1), DAY.plusDays(1), token)
                        .path("totalSubmissions")
                        .asLong())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("평균을 임의로 자르지 않는다")
    void averageIsNotRounded() throws Exception {
        signup();
        Menu menu = menu("김치찌개", DAY);
        analyzed(menu, userId(), 0.1);
        analyzed(menu, userId(), 0.2);
        analyzed(menu, userId(), 0.25);

        double average = summary(DAY, DAY, adminToken()).path("averageWasteRate").asDouble();

        // 0.18333...을 0.18로 자르면 화면의 숫자와 실제 값이 달라진다.
        assertThat(average).isCloseTo((0.1 + 0.2 + 0.25) / 3, TOLERANCE);
    }

    @Test
    @DisplayName("메뉴별로 잔반률을 나눈다")
    void groupsByMenu() throws Exception {
        signup();
        Menu low = menu("샐러드", DAY);
        Menu high = menu("고등어구이", DAY);
        analyzed(low, userId(), 0.1);
        analyzed(high, userId(), 0.7);
        analyzed(high, userId(), 0.9);

        JsonNode data = byMenu(DAY, DAY, adminToken());

        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("menuName").asText()).isEqualTo("고등어구이");
        assertThat(data.get(0).path("averageWasteRate").asDouble()).isCloseTo(0.8, TOLERANCE);
        assertThat(data.get(0).path("sampleCount").asLong()).isEqualTo(2);
        assertThat(data.get(1).path("menuName").asText()).isEqualTo("샐러드");
        assertThat(data.get(1).path("menuId").asLong()).isEqualTo(low.getId());
    }

    @Test
    @DisplayName("잔반률이 높은 메뉴가 먼저 온다")
    void ordersByWasteRateDescending() throws Exception {
        signup();
        analyzed(menu("가", DAY), userId(), 0.2);
        analyzed(menu("나", DAY), userId(), 0.8);
        analyzed(menu("다", DAY), userId(), 0.5);

        JsonNode data = byMenu(DAY, DAY, adminToken());

        assertThat(List.of(
                        data.get(0).path("menuName").asText(),
                        data.get(1).path("menuName").asText(),
                        data.get(2).path("menuName").asText()))
                .containsExactly("나", "다", "가");
    }

    @Test
    @DisplayName("잔반률이 같으면 표본이 많은 쪽이 먼저다")
    void tiesBreakBySampleCount() throws Exception {
        signup();
        Menu few = menu("적은표본", DAY);
        Menu many = menu("많은표본", DAY);
        analyzed(few, userId(), 0.5);
        analyzed(many, userId(), 0.5);
        analyzed(many, userId(), 0.5);

        JsonNode data = byMenu(DAY, DAY, adminToken());

        assertThat(data.get(0).path("menuId").asLong()).isEqualTo(many.getId());
        assertThat(data.get(1).path("menuId").asLong()).isEqualTo(few.getId());
    }

    @Test
    @DisplayName("표본이 없는 메뉴는 목록에 없다")
    void menusWithoutSamplesAreAbsent() throws Exception {
        signup();
        menu("아무도 안 낸 메뉴", DAY);
        analyzed(menu("낸 메뉴", DAY), userId(), 0.3);

        JsonNode data = byMenu(DAY, DAY, adminToken());

        // 표본 0건을 잔반률 0으로 넣으면 가장 잘 먹은 메뉴처럼 보인다.
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("menuName").asText()).isEqualTo("낸 메뉴");
    }

    @Test
    @DisplayName("메뉴별 조회도 표본이 없으면 빈 배열이다")
    void byMenuIsEmptyWithoutSamples() throws Exception {
        MvcResult result = getJson(BY_MENU + range(DAY, DAY), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).isEmpty();
    }

    @Test
    @DisplayName("기간이 뒤집히면 두 조회 모두 422다")
    void reversedRangeIsRejected() throws Exception {
        String token = adminToken();

        for (String path : List.of(SUMMARY, BY_MENU)) {
            MvcResult result = getJson(path + range(DAY.plusDays(1), DAY), token);

            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(422);
            assertThat(body(result).path("error").path("code").asText())
                    .isEqualTo("INVALID_DATE_RANGE");
        }
    }

    @Test
    @DisplayName("날짜가 없으면 400이다")
    void missingRangeIsRejected() throws Exception {
        String token = adminToken();

        assertThat(getJson(SUMMARY + "?from=" + DAY, token).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(getJson(BY_MENU + "?to=" + DAY, token).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("날짜 형식이 틀리면 400이다")
    void malformedRangeIsRejected() throws Exception {
        assertThat(getJson(SUMMARY + "?from=2026-13-45&to=" + DAY, adminToken())
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        assertThat(getJson(SUMMARY + range(DAY, DAY)).getResponse().getStatus()).isEqualTo(401);
        assertThat(getJson(BY_MENU + range(DAY, DAY)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void normalUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(getJson(SUMMARY + range(DAY, DAY), token).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(getJson(BY_MENU + range(DAY, DAY), token).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("집계가 리워드를 건드리지 않는다")
    void analyticsGrantNoRewards() throws Exception {
        signup();
        analyzed(menu("김치찌개", DAY), userId(), 0.1);
        String token = adminToken();
        long couponsBefore = couponRepository.count();
        long transactionsBefore = rewardTransactionRepository.count();

        summary(DAY, DAY, token);
        byMenu(DAY, DAY, token);

        // 조회가 지급을 만들면 관리자가 화면을 열 때마다 포인트가 생긴다.
        assertThat(couponRepository.count()).isEqualTo(couponsBefore);
        assertThat(rewardTransactionRepository.count()).isEqualTo(transactionsBefore);
    }
}
