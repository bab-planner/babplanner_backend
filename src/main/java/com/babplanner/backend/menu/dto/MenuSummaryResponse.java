package com.babplanner.backend.menu.dto;

import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.domain.MenuCategory;
import java.util.List;

/**
 * OpenAPI MenuSummary.
 *
 * <p>목록에는 상세 전용 필드(description/nutrition/allergens)를 담지 않는다.
 */
public record MenuSummaryResponse(
        Long id,
        Long siteId,
        String restaurantName,
        String serviceDate,
        MealType mealType,
        MenuCategory category,
        String name,
        List<String> items,
        Integer caloriesKcal,
        String imageUrl) {

    public static MenuSummaryResponse from(Menu menu) {
        return new MenuSummaryResponse(
                menu.getId(),
                menu.getSite().getId(),
                menu.getSite().getName(),
                // OpenAPI format: date (yyyy-MM-dd)
                menu.getServiceDate().toString(),
                menu.getMealType(),
                menu.getCategory(),
                menu.getName(),
                List.copyOf(menu.getItems()),
                menu.getCaloriesKcal(),
                menu.getImageUrl());
    }
}
