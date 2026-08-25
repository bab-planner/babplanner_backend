package com.babplanner.backend.menu.dto;

import com.babplanner.backend.allergy.dto.AllergyResponse;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.domain.MenuCategory;
import java.util.List;

/**
 * OpenAPI MenuDetail (= MenuSummary + description/nutrition/allergens).
 *
 * <p>allergens는 Stage 13의 AllergyResponse를 그대로 재사용한다. 사용자
 * 알레르기 설정과 같은 master를 가리키므로 id가 서로 맞물린다.
 */
public record MenuDetailResponse(
        Long id,
        Long siteId,
        String restaurantName,
        String serviceDate,
        MealType mealType,
        MenuCategory category,
        String name,
        List<String> items,
        Integer caloriesKcal,
        String imageUrl,
        String description,
        NutritionResponse nutrition,
        List<AllergyResponse> allergens) {

    public static MenuDetailResponse from(Menu menu) {
        return new MenuDetailResponse(
                menu.getId(),
                menu.getSite().getId(),
                menu.getSite().getName(),
                menu.getServiceDate().toString(),
                menu.getMealType(),
                menu.getCategory(),
                menu.getName(),
                List.copyOf(menu.getItems()),
                menu.getCaloriesKcal(),
                menu.getImageUrl(),
                menu.getDescription(),
                NutritionResponse.from(menu),
                menu.getAllergies().stream()
                        .sorted(java.util.Comparator.comparing(
                                com.babplanner.backend.allergy.domain.Allergy::getSortOrder))
                        .map(AllergyResponse::from)
                        .toList());
    }
}
