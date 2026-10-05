package com.babplanner.backend.demand.client;

import java.util.List;

/**
 * FastAPI {@code DemandForecastResponse}.
 *
 * <p>값은 전부 nullable 박스 타입으로 받는다. 빠진 필드가 0이나 false로 조용히
 * 채워지면 계약 위반을 알아챌 수 없다. 필수 여부는 {@link DemandForecastAiClient}가
 * 확인한다.
 *
 * <p>여기 없는 필드가 응답에 더 있어도 읽기에는 실패하지 않는다. AI 서버가
 * 필드를 늘리는 것은 계약 위반이 아니다.
 */
public record DemandForecastAiResponse(
        String forecastDate,
        String mealType,
        Integer predictedMealCount,
        Reference reference,
        Conditions conditions,
        Model model,
        List<String> warnings,
        String disclaimer) {

    /** 예측에 쓴 과거 식수. AI 서버가 예측일 이전 기록에서만 만든 값이다. */
    public record Reference(
            Integer previousDay,
            Integer sameWeekdayPreviousWeek,
            Integer rolling7DayAverage,
            String historyAsOf) {}

    /** AI 서버가 실제로 쓴 조건. 요일은 AI 서버가 날짜에서 계산한다. */
    public record Conditions(
            String weekday,
            Integer semester,
            Integer semesterWeek,
            Boolean examPeriod,
            Boolean holiday,
            Boolean campusEvent,
            String menuCategory,
            String mainMenu,
            Boolean specialMenu,
            Double temperature,
            Double rainfall,
            Double humidity) {}

    /**
     * @param version 모델 파일 해시 앞 12자리. 같은 값이면 같은 모델이다
     * @param syntheticData 합성데이터로 학습한 모델인지. 반드시 명시돼야 한다
     */
    public record Model(String name, String version, Boolean syntheticData, String trainedAt) {}
}
