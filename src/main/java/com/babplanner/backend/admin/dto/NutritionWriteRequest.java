package com.babplanner.backend.admin.dto;

/**
 * OpenAPI Nutrition.
 *
 * <p>전부 nullable이다. 값이 없으면 없는 채로 저장한다. 0으로 채우면
 * "열량이 0kcal"이라는 뜻이 되어 버린다.
 */
public record NutritionWriteRequest(
        Integer caloriesKcal,
        Double proteinG,
        Double carbohydrateG,
        Double fatG,
        Integer sodiumMg) {}
