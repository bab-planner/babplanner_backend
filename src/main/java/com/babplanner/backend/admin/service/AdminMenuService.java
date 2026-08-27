package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.MenuWriteRequest;
import com.babplanner.backend.admin.dto.NutritionWriteRequest;
import com.babplanner.backend.allergy.domain.Allergy;
import com.babplanner.backend.allergy.repository.AllergyRepository;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.dto.MenuDetailResponse;
import com.babplanner.backend.menu.dto.MenuSummaryResponse;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 식단 관리.
 *
 * <p>사용자가 보는 것과 같은 Menu를 쓴다. 관리자 전용 사본을 두면 둘이
 * 갈라지고, 관리자가 고친 것이 사용자에게 보이지 않는 일이 생긴다.
 */
@Service
public class AdminMenuService {

    private final MenuRepository menuRepository;
    private final DiningSiteRepository siteRepository;
    private final AllergyRepository allergyRepository;
    private final WasteSubmissionRepository wasteSubmissionRepository;
    private final Clock clock;

    public AdminMenuService(
            MenuRepository menuRepository,
            DiningSiteRepository siteRepository,
            AllergyRepository allergyRepository,
            WasteSubmissionRepository wasteSubmissionRepository,
            Clock clock) {
        this.menuRepository = menuRepository;
        this.siteRepository = siteRepository;
        this.allergyRepository = allergyRepository;
        this.wasteSubmissionRepository = wasteSubmissionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MenuSummaryResponse> getMenus(LocalDate date, MealType mealType) {
        return menuRepository.search(date, mealType, null, null).stream()
                .map(MenuSummaryResponse::from)
                .toList();
    }

    /**
     * 수정 화면이 채울 현재 값.
     *
     * <p>공개 조회와 같은 저장소를 읽는다. 관리자용 사본을 따로 두면 한쪽만
     * 고쳤을 때 두 화면이 다른 것을 보여 준다.
     *
     * <p>내용이 공개 조회와 같더라도 경로는 나눈다. 관리 화면이 공개
     * endpoint에 기대고 있으면, 나중에 미공개 식단 같은 개념이 생겨 공개
     * 조회가 좁아지는 순간 관리 화면이 같이 망가진다.
     */
    @Transactional(readOnly = true)
    public MenuDetailResponse getMenu(Long menuId) {
        return menuRepository
                .findWithDetailById(menuId)
                .map(MenuDetailResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));
    }

    @Transactional
    public MenuDetailResponse create(MenuWriteRequest request) {
        DiningSite site = resolveSite(request);
        Set<Allergy> allergies = resolveAllergies(request.allergenIdsOrEmpty());

        Menu menu = new Menu(
                site,
                request.serviceDate(),
                request.mealType(),
                request.category(),
                request.name(),
                request.itemsOrEmpty(),
                clock.instant());
        apply(menu, request, site, allergies);

        return detailOf(menuRepository.save(menu).getId());
    }

    @Transactional
    public MenuDetailResponse update(Long menuId, MenuWriteRequest request) {
        Menu menu = menuRepository
                .findWithDetailById(menuId)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));

        DiningSite site = resolveSite(request);
        Set<Allergy> allergies = resolveAllergies(request.allergenIdsOrEmpty());
        apply(menu, request, site, allergies);

        return detailOf(menu.getId());
    }

    /**
     * 식단을 지운다.
     *
     * <p>사용자가 이 메뉴로 올린 잔반 인증이 있으면 지우지 않는다. 참조를
     * 끊으려고 그 기록까지 함께 지우면 사용자가 남긴 것이 관리자 실수로
     * 사라진다. 무엇을 먹고 얼마나 남겼는지는 메뉴보다 오래 남아야 한다.
     */
    @Transactional
    public void delete(Long menuId) {
        Menu menu = menuRepository
                .findById(menuId)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));

        if (wasteSubmissionRepository.existsByMenuId(menuId)) {
            throw new ApiException(ErrorCode.MENU_IN_USE);
        }
        menuRepository.delete(menu);
    }

    private void apply(
            Menu menu, MenuWriteRequest request, DiningSite site, Set<Allergy> allergies) {
        menu.applyWrite(
                site,
                request.serviceDate(),
                request.mealType(),
                request.category(),
                request.name(),
                request.itemsOrEmpty(),
                request.description(),
                request.imageUrl(),
                allergies,
                clock.instant());

        NutritionWriteRequest nutrition = request.nutrition();
        menu.applyNutrition(
                nutrition == null ? null : nutrition.caloriesKcal(),
                nutrition == null ? null : nutrition.proteinG(),
                nutrition == null ? null : nutrition.carbohydrateG(),
                nutrition == null ? null : nutrition.fatG(),
                nutrition == null ? null : nutrition.sodiumMg(),
                clock.instant());
    }

    /**
     * 식당을 찾는다.
     *
     * <p>계약이 siteId와 restaurantName을 둘 다 받는데, 둘이 어긋나면 어느
     * 쪽이 맞는지 알 수 없다. 식당 이름의 출처는 siteId 하나이고, 메뉴 등록
     * 한 건 때문에 공유 식당의 이름을 바꾸지 않는다. 어긋나면 거부한다.
     */
    private DiningSite resolveSite(MenuWriteRequest request) {
        DiningSite site = siteRepository
                .findById(request.siteId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_DINING_SITE));

        if (!site.getName().equals(request.restaurantName())) {
            throw new ApiException(ErrorCode.SITE_NAME_MISMATCH);
        }
        return site;
    }

    /**
     * 알레르기 성분을 찾는다.
     *
     * <p>하나라도 모르는 id가 있으면 나머지도 저장하지 않는다. 일부만 저장하면
     * 관리자는 다 넣은 줄 아는데 실제로는 빠진 성분이 생긴다.
     */
    private Set<Allergy> resolveAllergies(List<Long> allergenIds) {
        if (allergenIds.isEmpty()) {
            return Set.of();
        }
        List<Long> distinct = allergenIds.stream().distinct().toList();
        List<Allergy> found = allergyRepository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            throw new ApiException(ErrorCode.UNKNOWN_ALLERGY);
        }
        return new LinkedHashSet<>(found);
    }

    /** 저장 직후의 상세. 사용자가 보는 것과 같은 표현을 쓴다. */
    private MenuDetailResponse detailOf(Long menuId) {
        menuRepository.flush();
        return menuRepository
                .findWithDetailById(menuId)
                .map(MenuDetailResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));
    }
}
