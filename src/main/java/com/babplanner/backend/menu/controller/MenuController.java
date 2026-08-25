package com.babplanner.backend.menu.controller;

import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.MenuCategory;
import com.babplanner.backend.menu.dto.MenuDetailResponse;
import com.babplanner.backend.menu.dto.MenuSummaryResponse;
import com.babplanner.backend.menu.service.MenuService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 식단 조회. OpenAPI에 security가 없는 공개 API다. */
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ApiResponse<List<MenuSummaryResponse>> getMenus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MealType mealType,
            @RequestParam(required = false) MenuCategory category,
            @RequestParam(required = false) Long siteId) {
        return ApiResponse.success(menuService.getMenus(date, mealType, category, siteId));
    }

    @GetMapping("/{menuId}")
    public ApiResponse<MenuDetailResponse> getMenuDetail(@PathVariable Long menuId) {
        return ApiResponse.success(menuService.getMenuDetail(menuId));
    }
}
