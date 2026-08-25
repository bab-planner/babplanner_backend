package com.babplanner.backend.menu.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.MenuCategory;
import com.babplanner.backend.menu.dto.MenuDetailResponse;
import com.babplanner.backend.menu.dto.MenuSummaryResponse;
import com.babplanner.backend.menu.repository.MenuRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 식단 조회. 필터 조합과 정렬 책임을 가진다. */
@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    /**
     * 조건에 맞는 식단 목록.
     *
     * <p>조건에 맞는 식단이 없으면 빈 배열이다. 다른 날짜의 식단으로 대체하지
     * 않는다.
     */
    @Transactional(readOnly = true)
    public List<MenuSummaryResponse> getMenus(
            LocalDate date, MealType mealType, MenuCategory category, Long siteId) {
        return menuRepository.search(date, mealType, category, siteId).stream()
                .map(MenuSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuDetailResponse getMenuDetail(Long menuId) {
        return menuRepository
                .findWithDetailById(menuId)
                .map(MenuDetailResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));
    }
}
