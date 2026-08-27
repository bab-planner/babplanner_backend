package com.babplanner.backend.admin.dto;

import com.babplanner.backend.demand.domain.DemandPrediction;
import com.babplanner.backend.menu.domain.MealType;

/**
 * OpenAPI DemandPrediction.
 *
 * <p>siteId를 함께 내려준다. 없으면 응답만 보고는 어느 식당의 예측인지 알 수
 * 없다. 조회는 siteId로 하지만, 화면이 여러 식당을 나란히 보여 주기 시작하면
 * 응답 자체가 그 정보를 담고 있어야 한다.
 *
 * @param modelVersion {@code fastapi-fixture-}로 시작하면 개발용 fixture가 낸
 *     값이며 예측이 아니다
 */
public record DemandPredictionResponse(
        Long id,
        Long siteId,
        String serviceDate,
        MealType mealType,
        int predictedPeople,
        String modelVersion,
        String createdAt) {

    public static DemandPredictionResponse from(DemandPrediction prediction) {
        return new DemandPredictionResponse(
                prediction.getId(),
                prediction.getSite().getId(),
                prediction.getServiceDate().toString(),
                prediction.getMealType(),
                prediction.getPredictedPeople(),
                prediction.getModelVersion(),
                // OpenAPI format: date-time (ISO-8601 UTC)
                prediction.getCreatedAt().toString());
    }
}
