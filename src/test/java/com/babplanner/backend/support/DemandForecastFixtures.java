package com.babplanner.backend.support;

/**
 * 식수 예측 테스트가 함께 쓰는 FastAPI 응답.
 *
 * <p>FastAPI가 실제 artifact로 돌려준 응답과 같은 모양이다. 값은 테스트용이다.
 */
public final class DemandForecastFixtures {

    private DemandForecastFixtures() {}

    public static final String SUCCESS_BODY = """
            {
              "forecastDate": "2026-09-15",
              "mealType": "LUNCH",
              "predictedMealCount": 431,
              "reference": {
                "previousDay": 350,
                "sameWeekdayPreviousWeek": 375,
                "rolling7DayAverage": 367,
                "historyAsOf": "2026-09-11"
              },
              "conditions": {
                "weekday": "화요일", "semester": 2, "semesterWeek": 3,
                "examPeriod": false, "holiday": false, "campusEvent": false,
                "menuCategory": "양식", "mainMenu": "돈까스", "menuPopularity": 0.9,
                "specialMenu": false, "temperature": 24.0, "rainfall": 0.0, "humidity": 60.0
              },
              "model": {
                "name": "lightgbm",
                "version": "54453980807e",
                "syntheticData": true,
                "trainedAt": "2026-09-12T23:43:37"
              },
              "warnings": ["직전 평일(2026-09-14) 기록이 이력에 없어 가장 최근 기록(2026-09-11)을 기준으로 과거 feature를 만들었습니다."],
              "disclaimer": "합성데이터 기반 PoC 예측값이며 실제 급식 운영 정확도를 의미하지 않습니다."
            }
            """;
}
