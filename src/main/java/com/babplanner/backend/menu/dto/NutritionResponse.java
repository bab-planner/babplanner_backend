package com.babplanner.backend.menu.dto;

import com.babplanner.backend.menu.domain.Menu;

/**
 * OpenAPI Nutrition. 모든 값이 nullable이다.
 *
 * <p>값이 없으면 0이 아니라 null을 그대로 내보낸다.
 */
public record NutritionResponse(
        Integer caloriesKcal, Double proteinG, Double carbohydrateG, Double fatG, Integer sodiumMg) {

    public static NutritionResponse from(Menu menu) {
        return new NutritionResponse(
                menu.getCaloriesKcal(),
                menu.getProteinG(),
                menu.getCarbohydrateG(),
                menu.getFatG(),
                menu.getSodiumMg());
    }
}
