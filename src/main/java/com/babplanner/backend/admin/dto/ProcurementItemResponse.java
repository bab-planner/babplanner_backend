package com.babplanner.backend.admin.dto;

/**
 * OpenAPI ProcurementItem.
 *
 * <p>모양만 맞춰 둔 것이고 이번 단계에서는 아무도 만들지 않는다. 발주 추천은
 * 재고와 단가를 아는 모델이 있어야 나오는 값이라, 그 전에 채워 넣으면 관리자가
 * 추천이라고 믿고 실제 발주를 넣게 된다.
 */
public record ProcurementItemResponse(
        String ingredientName,
        String unit,
        Double plannedQuantity,
        double recommendedQuantity,
        Double adjustmentRate,
        String reason) {}
