package com.babplanner.backend.admin.controller;

import com.babplanner.backend.admin.dto.MenuWriteRequest;
import com.babplanner.backend.admin.service.AdminMenuService;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.dto.MenuDetailResponse;
import com.babplanner.backend.menu.dto.MenuSummaryResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 식단 관리.
 *
 * <p>접근 권한은 SecurityConfig가 정한다. 이 클래스는 principal의 id를 보고
 * 권한을 판단하지 않는다. 그러면 규칙이 두 곳에 흩어진다.
 */
@RestController
@RequestMapping("/api/v1/admin/menus")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    public AdminMenuController(AdminMenuService adminMenuService) {
        this.adminMenuService = adminMenuService;
    }

    @GetMapping
    public ApiResponse<List<MenuSummaryResponse>> getMenus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MealType mealType) {
        return ApiResponse.success(adminMenuService.getMenus(date, mealType));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MenuDetailResponse>> createMenu(
            @Valid @RequestBody MenuWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminMenuService.create(request)));
    }

    @PutMapping("/{menuId}")
    public ApiResponse<MenuDetailResponse> updateMenu(
            @PathVariable Long menuId, @Valid @RequestBody MenuWriteRequest request) {
        return ApiResponse.success(adminMenuService.update(menuId, request));
    }

    @DeleteMapping("/{menuId}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long menuId) {
        adminMenuService.delete(menuId);
        return ResponseEntity.noContent().build();
    }
}
