package com.babplanner.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * OpenAPI PlannedIngredientRequest.
 *
 * <p>계획 발주량을 관리자가 명시한다. 백엔드에는 식재료별 계획 수량의 출처가
 * 없기 때문이다. 식단의 구성 항목을 식재료로 읽지 않는 것도 같은 이유다.
 * 거기에는 조리된 음식 이름이 들어갈 수 있고 구매 단위도 수량도 없다.
 *
 * <p>수량과 단위를 나눠 받는다. "10kg" 같은 문자열은 읽는 쪽마다 다르게
 * 해석되고, 단위만 바꾸려 해도 문자열을 다시 뜯어야 한다.
 *
 * @param plannedQuantity 0은 "사지 않겠다"는 뜻이라 계획 목록에 들어갈 이유가
 *     없다. 그래서 0보다 커야 한다
 */
public record PlannedIngredientRequest(
        @NotBlank String ingredientName,
        @NotBlank String unit,
        @NotNull @Positive Double plannedQuantity) {

    public String normalizedName() {
        return ingredientName == null ? null : ingredientName.strip();
    }

    public String normalizedUnit() {
        return unit == null ? null : unit.strip();
    }
}
