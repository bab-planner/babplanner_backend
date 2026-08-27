package com.babplanner.backend.procurement.client;

import java.util.List;

/**
 * 검증을 마친 추천 결과.
 *
 * <p>목록은 요청에 들어온 순서 그대로다. 검증은 경계에서 한 번만 하고,
 * 저장 코드는 여기까지 온 값을 다시 검사하지 않는다.
 */
public record ProcurementRecommendationResult(
        List<RecommendedIngredient> recommendations, String modelVersion) {

    public record RecommendedIngredient(
            String ingredientName,
            String unit,
            double plannedQuantity,
            double recommendedQuantity,
            Double adjustmentRate,
            String reason) {}
}
