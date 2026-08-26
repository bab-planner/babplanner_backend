package com.babplanner.backend.admin.dto;

import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.MenuCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * OpenAPI MenuWriteRequest.
 *
 * <p>PUT과 POST가 같은 모양을 쓴다. 수정은 부분 변경이 아니라 전체 교체다.
 *
 * @param restaurantName 계약에 있어 받되, 실제 식당 이름의 출처는 siteId다.
 *     두 값이 어긋나면 무엇이 맞는지 알 수 없으므로 거부한다.
 */
public record MenuWriteRequest(
        @NotNull Long siteId,
        @NotBlank String restaurantName,
        @NotNull LocalDate serviceDate,
        @NotNull MealType mealType,
        @NotNull MenuCategory category,
        @NotBlank String name,
        List<String> items,
        String description,
        String imageUrl,
        NutritionWriteRequest nutrition,
        List<Long> allergenIds) {

    public List<String> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }

    public List<Long> allergenIdsOrEmpty() {
        return allergenIds == null ? List.of() : allergenIds;
    }
}
