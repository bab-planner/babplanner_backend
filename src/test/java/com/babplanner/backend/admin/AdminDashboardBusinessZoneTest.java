package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.admin.dto.AdminDashboardResponse;
import com.babplanner.backend.admin.service.AdminDashboardService;
import com.babplanner.backend.admin.service.AdminWasteService;
import com.babplanner.backend.congestion.service.CongestionService;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.domain.MenuCategory;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 대시보드가 말하는 "오늘".
 *
 * <p>{@code Menu.serviceDate}는 급식소 현지 영업일이다. UTC로 날짜를 끊으면
 * 한국에서 자정부터 오전 9시 사이에 관리자가 보는 오늘의 잔반 요약이 전날
 * 것이 된다. 화면에는 숫자가 떠 있으니 틀린 줄도 모른다.
 *
 * <p>같은 순간을 세 시간대에서 보게 해서, 서비스가 설정된 시간대를 따르는지
 * 확인한다. Clock만 바꿔 끼우므로 조회 경로는 실제 그대로다.
 */
class AdminDashboardBusinessZoneTest extends ApiIntegrationTest {

    /** 서울로는 8월 27일 01:30, UTC와 뉴욕으로는 아직 8월 26일인 순간. */
    private static final Instant MIDNIGHT_CROSSING = Instant.parse("2026-08-26T16:30:00Z");

    private static final LocalDate SEOUL_DAY = LocalDate.parse("2026-08-27");
    private static final LocalDate UTC_DAY = LocalDate.parse("2026-08-26");

    private static final double SEOUL_DAY_RATE = 0.42;
    private static final double UTC_DAY_RATE = 0.17;

    /** 이 테스트가 만든 식단에만 붙이는 표시. 씨앗 식단이 같은 날짜에 있다. */
    private static final String MARK = "[시간대테스트] ";

    @Autowired private MenuRepository menuRepository;
    @Autowired private DiningSiteRepository siteRepository;
    @Autowired private CongestionService congestionService;
    @Autowired private AdminWasteService adminWasteService;

    @BeforeEach
    @AfterEach
    void removeMenusCreatedHere() {
        wasteSubmissionRepository.deleteAll();
        wasteSubmissionRepository.flush();
        for (LocalDate date : List.of(UTC_DAY, SEOUL_DAY)) {
            menuRepository.deleteAll(menuRepository.search(date, null, null, null).stream()
                    .filter(menu -> menu.getName().startsWith(MARK))
                    .toList());
        }
        menuRepository.flush();
    }

    /** 두 날짜에 서로 다른 잔반률을 심는다. 어느 날을 봤는지 값으로 드러난다. */
    private void seedBothDays() throws Exception {
        signup();
        long userId = userRepository.findByPhone(PHONE).orElseThrow().getId();
        analyzed(menu(UTC_DAY), userId, UTC_DAY_RATE);
        analyzed(menu(SEOUL_DAY), userId, SEOUL_DAY_RATE);
    }

    private Menu menu(LocalDate serviceDate) {
        return menuRepository.save(new Menu(
                siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst(),
                serviceDate,
                MealType.LUNCH,
                MenuCategory.KOREAN,
                MARK + serviceDate,
                List.of("김치찌개"),
                clock.instant()));
    }

    private void analyzed(Menu menu, long userId, double wasteRate) {
        WasteSubmission submission = new WasteSubmission(
                userId, menu.getId(), "key-" + wasteRate, clock.instant(), clock.instant());
        submission.transitionTo(WasteSubmissionStatus.VERIFYING, clock.instant());
        submission.transitionTo(WasteSubmissionStatus.ANALYZING, clock.instant());
        submission.applyAnalysis(wasteRate, 0.9, "test-model", clock.instant());
        submission.transitionTo(WasteSubmissionStatus.APPROVED, clock.instant());
        wasteSubmissionRepository.save(submission);
    }

    /** 시간대만 다른 대시보드. 조회 경로는 운영과 같은 bean을 그대로 쓴다. */
    private AdminDashboardResponse dashboardIn(ZoneId zone) {
        Clock fixed = Clock.fixed(MIDNIGHT_CROSSING, zone);
        return new AdminDashboardService(congestionService, adminWasteService, fixed)
                .getDashboard(null);
    }

    @Test
    @DisplayName("서울 자정을 넘겼으면 그날 급식을 본다")
    void seoulPastMidnightUsesTheNewDay() throws Exception {
        seedBothDays();

        var summary = dashboardIn(ZoneId.of("Asia/Seoul")).wasteSummary();

        // UTC로 끊으면 0.17이 나온다. 그 경우 이 단언이 실패한다.
        assertThat(summary.totalSubmissions()).isEqualTo(1);
        assertThat(summary.averageWasteRate()).isCloseTo(SEOUL_DAY_RATE, Offset.offset(1e-9));
    }

    @Test
    @DisplayName("같은 순간이라도 UTC로 보면 전날이다")
    void utcStillSeesThePreviousDay() throws Exception {
        seedBothDays();

        var summary = dashboardIn(ZoneOffset.UTC).wasteSummary();

        assertThat(summary.totalSubmissions()).isEqualTo(1);
        assertThat(summary.averageWasteRate()).isCloseTo(UTC_DAY_RATE, Offset.offset(1e-9));
    }

    @Test
    @DisplayName("한국 전용이 아니라 설정된 시간대를 따른다")
    void anotherZoneIsRespected() throws Exception {
        seedBothDays();

        // 같은 순간이 뉴욕에서는 8월 26일 12:30이다.
        var summary = dashboardIn(ZoneId.of("America/New_York")).wasteSummary();

        assertThat(summary.averageWasteRate()).isCloseTo(UTC_DAY_RATE, Offset.offset(1e-9));
    }

    @Test
    @DisplayName("그날 표본이 없으면 평균은 여전히 null이다")
    void honestEmptinessSurvivesTheZoneChange() throws Exception {
        signup();
        long userId = userRepository.findByPhone(PHONE).orElseThrow().getId();
        analyzed(menu(UTC_DAY), userId, UTC_DAY_RATE);

        var summary = dashboardIn(ZoneId.of("Asia/Seoul")).wasteSummary();

        assertThat(summary.totalSubmissions()).isZero();
        assertThat(summary.averageWasteRate()).isNull();
    }

    @Test
    @DisplayName("서비스가 시간대를 따로 받지 않는다")
    void serviceTakesNoZoneOfItsOwn() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/babplanner/backend/admin/service/AdminDashboardService.java"));

        // 업무 시각의 출처는 Clock 하나다. 서비스가 다시 시간대를 붙이면
        // 설정과 코드가 갈라진다.
        assertThat(source).contains("LocalDate.now(clock)");
        assertThat(source).doesNotContain("ZoneId").doesNotContain("ZoneOffset");
    }
}
