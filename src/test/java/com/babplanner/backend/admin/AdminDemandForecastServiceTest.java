package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.admin.dto.DemandForecastRequest;
import com.babplanner.backend.admin.dto.DemandForecastResponse;
import com.babplanner.backend.admin.dto.DemandForecastStatusResponse;
import com.babplanner.backend.admin.service.AdminDemandForecastService;
import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.config.AiServiceProperties;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.demand.client.DemandForecastAiClient;
import com.babplanner.backend.demand.client.DemandForecastAiRequest;
import com.babplanner.backend.demand.client.DemandForecastAiResponse;
import com.babplanner.backend.demand.client.DemandForecastAiStatus;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.MenuCategory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 식수 예측 서비스.
 *
 * <p>AI 서버 대신 요청을 기록하고 정해진 응답을 주는 클라이언트를 끼운다. 확인하려는
 * 것은 옮기는 과정에서 잃는 것이 없는지, 그리고 실패를 숫자로 위장하지 않는지다.
 */
class AdminDemandForecastServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T01:00:00Z");

    /** AI 서버 대신 쓰는 클라이언트. 받은 요청을 기록한다. */
    private static final class RecordingClient extends DemandForecastAiClient {
        DemandForecastAiRequest received;
        DemandForecastAiResponse response;
        DemandForecastAiStatus status;
        RuntimeException failure;

        RecordingClient() {
            super(new AiRestClientProvider(new AiServiceProperties()));
        }

        @Override
        public DemandForecastAiResponse predict(DemandForecastAiRequest request) {
            received = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public DemandForecastAiStatus status() {
            if (failure != null) {
                throw failure;
            }
            return status;
        }
    }

    private final RecordingClient client = new RecordingClient();
    private final AdminDemandForecastService service =
            new AdminDemandForecastService(client, Clock.fixed(NOW, ZoneOffset.UTC));

    static DemandForecastRequest request() {
        return new DemandForecastRequest(
                LocalDate.parse("2026-09-15"), 2, 3, false, false, false, 24.0, 0.0, 60.0,
                MenuCategory.WESTERN, "돈까스", false);
    }

    static DemandForecastAiResponse aiResponse(boolean synthetic, List<String> warnings) {
        return new DemandForecastAiResponse(
                "2026-09-15",
                "LUNCH",
                431,
                new DemandForecastAiResponse.Reference(350, 375, 367, "2026-09-11"),
                new DemandForecastAiResponse.Conditions(
                        "화요일", 2, 3, false, false, false, "양식", "돈까스", false, 24.0, 0.0,
                        60.0),
                new DemandForecastAiResponse.Model(
                        "lightgbm", "54453980807e", synthetic, "2026-09-12T23:43:37"),
                warnings,
                "합성데이터 기반 PoC 예측값이며 실제 급식 운영 정확도를 의미하지 않습니다.");
    }

    @Test
    @DisplayName("관리자 요청을 FastAPI 계약으로 빠짐없이 옮긴다")
    void requestIsMappedToTheAiContract() {
        client.response = aiResponse(true, List.of());

        service.predict(new DemandForecastRequest(
                LocalDate.parse("2026-09-16"), 1, 15, true, true, true, -3.5, 12.5, 88.0,
                MenuCategory.SNACK, "  떡볶이  ", true));

        DemandForecastAiRequest sent = client.received;
        assertThat(sent.date()).isEqualTo("2026-09-16");
        assertThat(sent.semester()).isEqualTo(1);
        assertThat(sent.semesterWeek()).isEqualTo(15);
        assertThat(sent.examPeriod()).isTrue();
        assertThat(sent.holiday()).isTrue();
        assertThat(sent.campusEvent()).isTrue();
        assertThat(sent.temperature()).isEqualTo(-3.5);
        assertThat(sent.rainfall()).isEqualTo(12.5);
        assertThat(sent.humidity()).isEqualTo(88.0);
        assertThat(sent.menuCategory()).isEqualTo("분식");
        assertThat(sent.mainMenu()).isEqualTo("떡볶이");
        assertThat(sent.specialMenu()).isTrue();
    }

    @Test
    @DisplayName("메뉴 분류는 FastAPI의 한글 라벨로 바뀐다")
    void everyMenuCategoryHasItsOwnKoreanLabel() {
        Set<String> labels = Arrays.stream(MenuCategory.values())
                .map(DemandForecastAiRequest::menuCategoryLabel)
                .collect(Collectors.toSet());

        assertThat(labels).containsExactlyInAnyOrder("한식", "중식", "양식", "일식", "분식", "기타");
        assertThat(DemandForecastAiRequest.menuCategoryLabel(MenuCategory.KOREAN)).isEqualTo("한식");
        assertThat(DemandForecastAiRequest.menuCategoryLabel(MenuCategory.WESTERN)).isEqualTo("양식");
    }

    @Test
    @DisplayName("예측 결과와 모델 정보를 잃지 않는다")
    void responseKeepsPredictionAndModelMetadata() {
        client.response = aiResponse(true, List.of("직전 평일 기록이 없어 가장 최근 기록을 사용"));

        DemandForecastResponse response = service.predict(request());

        assertThat(response.forecastDate()).isEqualTo("2026-09-15");
        assertThat(response.mealType()).isEqualTo(MealType.LUNCH);
        assertThat(response.predictedMealCount()).isEqualTo(431);
        assertThat(response.reference().previousDay()).isEqualTo(350);
        assertThat(response.reference().sameWeekdayPreviousWeek()).isEqualTo(375);
        assertThat(response.reference().rolling7DayAverage()).isEqualTo(367);
        assertThat(response.reference().historyAsOf()).isEqualTo("2026-09-11");
        // 나중에 저장한다면 함께 남겨야 하는 값들.
        assertThat(response.model().version()).isEqualTo("54453980807e");
        assertThat(response.model().syntheticData()).isTrue();
        assertThat(response.predictedAt()).isEqualTo("2026-09-13T01:00:00Z");
        assertThat(response.model().name()).isEqualTo("lightgbm");
        assertThat(response.disclaimer()).isEqualTo(
                "합성데이터 기반 PoC 예측값이며 실제 급식 운영 정확도를 의미하지 않습니다.");
        assertThat(response.warnings()).containsExactly("직전 평일 기록이 없어 가장 최근 기록을 사용");
    }

    @Test
    @DisplayName("조건은 관리자가 보낸 값이고 요일만 AI 서버가 계산한 값이다")
    void conditionsEchoTheRequestWithTheComputedWeekday() {
        client.response = aiResponse(true, List.of());

        DemandForecastResponse.Conditions conditions = service.predict(request()).conditions();

        assertThat(conditions.weekday()).isEqualTo("화요일");
        assertThat(conditions.menuCategory()).isEqualTo(MenuCategory.WESTERN);
        assertThat(conditions.mainMenu()).isEqualTo("돈까스");
        assertThat(conditions.semester()).isEqualTo(2);
        assertThat(conditions.semesterWeek()).isEqualTo(3);
    }

    @Test
    @DisplayName("실데이터 모델이면 syntheticData=false를 그대로 전한다")
    void realDataFlagIsPreservedToo() {
        client.response = aiResponse(false, List.of());

        assertThat(service.predict(request()).model().syntheticData()).isFalse();
    }

    @Test
    @DisplayName("AI 서버를 쓸 수 없으면 숫자 없이 503이다")
    void unavailableAiBecomes503WithoutANumber() {
        client.failure = new AiServiceException("down");

        assertThatThrownBy(() -> service.predict(request()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.AI_SERVICE_UNAVAILABLE.status().value()).isEqualTo(503);
    }

    @Test
    @DisplayName("status를 옮긴다")
    void statusIsMapped() {
        client.status = new DemandForecastAiStatus(
                true, true, true, "lightgbm", "54453980807e", "meal_count", true,
                List.of("2023-03-13", "2026-09-11"), null, null);

        DemandForecastStatusResponse status = service.status();

        assertThat(status.available()).isTrue();
        assertThat(status.modelLoaded()).isTrue();
        assertThat(status.syntheticData()).isTrue();
        assertThat(status.modelVersion()).isEqualTo("54453980807e");
        assertThat(status.warnings()).isEmpty();
    }

    @Test
    @DisplayName("모델이 없다는 status는 이유와 함께 그대로 전한다")
    void unavailableStatusKeepsTheReason() {
        client.status = new DemandForecastAiStatus(
                false, false, true, null, null, null, null, null, "model artifact not found", null);

        DemandForecastStatusResponse status = service.status();

        assertThat(status.available()).isFalse();
        assertThat(status.reason()).isEqualTo("model artifact not found");
    }

    @Test
    @DisplayName("AI 서버에 닿지 못하면 status도 503이다")
    void unreachableStatusBecomes503() {
        client.failure = new AiServiceException("down");

        assertThatThrownBy(service::status)
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
}
