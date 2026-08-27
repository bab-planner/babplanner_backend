package com.babplanner.backend.admin.dto;

import com.babplanner.backend.menu.domain.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * OpenAPI ProcurementRequest.
 *
 * <p>예상 인원을 받지 않는다. 관리자가 직접 보낼 수 있게 하면 저장된 예측을
 * 우회해 원하는 숫자로 추천을 받을 수 있고, 그러면 "무엇을 근거로 이렇게
 * 발주했는가"에 답할 수 없다. 인원은 저장된 가장 최근 예측에서 가져온다.
 *
 * @param plannedIngredients 최소 한 줄. 아무것도 살 계획이 없으면 추천할
 *     것도 없다
 */
public record ProcurementRunRequest(
        @NotNull Long siteId,
        @NotNull LocalDate serviceDate,
        @NotNull MealType mealType,
        @NotEmpty @Valid List<PlannedIngredientRequest> plannedIngredients) {}
