package com.babplanner.backend.allergy.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * OpenAPI AllergyUpdateRequest.
 *
 * <p>전체 교체 semantics다. 빈 배열은 전체 해제를 뜻하므로 허용한다.
 */
public record AllergyUpdateRequest(@NotNull List<Long> allergyIds) {}
