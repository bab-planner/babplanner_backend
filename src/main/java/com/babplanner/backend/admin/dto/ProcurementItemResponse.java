package com.babplanner.backend.admin.dto;

import com.babplanner.backend.procurement.domain.ProcurementItem;

/**
 * OpenAPI ProcurementItem.
 *
 * <p>저장된 추천 한 줄. 수량은 AI 서비스가 돌려준 값을 그대로 담는다. 보기
 * 좋으라고 반올림하면 화면의 숫자와 실제 발주할 값이 달라진다.
 *
 * <p>대시보드의 procurementPreview는 아직 이 값을 쓰지 않는다. 대시보드에는
 * mealType이 없어서 어느 끼니의 추천을 보여 줄지 정해진 적이 없기 때문이다.
 */
public record ProcurementItemResponse(
        String ingredientName,
        String unit,
        Double plannedQuantity,
        double recommendedQuantity,
        Double adjustmentRate,
        String reason) {

    public static ProcurementItemResponse from(ProcurementItem item) {
        return new ProcurementItemResponse(
                item.getIngredientName(),
                item.getUnit(),
                item.getPlannedQuantity(),
                item.getRecommendedQuantity(),
                item.getAdjustmentRate(),
                item.getReason());
    }
}
