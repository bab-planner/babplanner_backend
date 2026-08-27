package com.babplanner.backend.admin.dto;

import com.babplanner.backend.menu.domain.MealType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * OpenAPI DemandPredictionRequest.
 *
 * <p>예측 인원을 관리자가 직접 보내지 않는다. 보낼 수 있게 하면 예측 서비스를
 * 우회해 원하는 숫자를 이력에 남길 수 있고, 그러면 저장된 예측이 무엇을
 * 뜻하는지 알 수 없어진다.
 */
public record DemandPredictionRequest(
        @NotNull Long siteId, @NotNull LocalDate serviceDate, @NotNull MealType mealType) {}
