package com.babplanner.backend.admin.dto;

import com.babplanner.backend.demand.client.DemandForecastAiResponse;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.MenuCategory;
import java.time.Instant;
import java.util.List;

/**
 * 관리자 식수 예측 결과.
 *
 * <p>AI 서버 응답을 그대로 내보내지 않고 이 백엔드의 응답으로 한 번 감싼다. AI 서버의
 * 내부 계약이 바뀌어도 관리자 화면의 계약은 여기서 지킨다.
 *
 * <p><b>{@code model.syntheticData}와 {@code disclaimer}를 빼지 않는다.</b> 지금 모델은
 * 합성데이터로 학습한 PoC다. 화면이 그 사실을 알 수 있어야 한다.
 *
 * <p>나중에 예측을 DB에 저장한다면 {@code forecastDate}, {@code predictedMealCount},
 * {@code model.version}, {@code model.syntheticData}, {@code predictedAt}(저장 시
 * createdAt)을 함께 남긴다. 어느 모델이 언제 낸 숫자인지 모르면 저장된 값을 해석할
 * 수 없다. 그래서 지금도 응답에서 이 값들을 버리지 않는다.
 *
 * @param conditions 관리자가 보낸 조건. 요일은 AI 서버가 날짜에서 계산한 값이다
 * @param predictedAt 이 백엔드가 결과를 받은 시각 (ISO-8601 UTC)
 */
public record DemandForecastResponse(
        String forecastDate,
        MealType mealType,
        int predictedMealCount,
        Reference reference,
        Conditions conditions,
        Model model,
        String disclaimer,
        List<String> warnings,
        String predictedAt) {

    /** 예측에 쓴 과거 식수. AI 서버가 예측일 이전 기록에서만 만든 값이다. */
    public record Reference(
            int previousDay,
            int sameWeekdayPreviousWeek,
            int rolling7DayAverage,
            String historyAsOf) {}

    public record Conditions(
            String weekday,
            int semester,
            int semesterWeek,
            boolean examPeriod,
            boolean holiday,
            boolean campusEvent,
            double temperature,
            double rainfall,
            double humidity,
            MenuCategory menuCategory,
            String mainMenu,
            double menuPopularity,
            boolean specialMenu) {}

    /** @param version 모델 파일 해시 앞 12자리. 같은 값이면 같은 모델이다 */
    public record Model(String name, String version, boolean syntheticData, String trainedAt) {}

    /** AI 응답은 {@code DemandForecastAiClient}가 계약을 확인한 뒤의 것이어야 한다. */
    public static DemandForecastResponse of(
            DemandForecastRequest request, DemandForecastAiResponse response, Instant predictedAt) {
        DemandForecastAiResponse.Reference reference = response.reference();
        DemandForecastAiResponse.Model model = response.model();
        return new DemandForecastResponse(
                response.forecastDate(),
                MealType.valueOf(response.mealType()),
                response.predictedMealCount(),
                new Reference(
                        reference.previousDay(),
                        reference.sameWeekdayPreviousWeek(),
                        reference.rolling7DayAverage(),
                        reference.historyAsOf()),
                new Conditions(
                        response.conditions() == null ? null : response.conditions().weekday(),
                        request.semester(),
                        request.semesterWeek(),
                        request.examPeriod(),
                        request.holiday(),
                        request.campusEvent(),
                        request.temperature(),
                        request.rainfall(),
                        request.humidity(),
                        request.menuCategory(),
                        request.mainMenu(),
                        request.menuPopularity(),
                        request.specialMenu()),
                new Model(model.name(), model.version(), model.syntheticData(), model.trainedAt()),
                response.disclaimer(),
                List.copyOf(response.warnings()),
                predictedAt.toString());
    }
}
