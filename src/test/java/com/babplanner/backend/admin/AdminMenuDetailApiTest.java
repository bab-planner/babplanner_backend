package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.domain.MenuCategory;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리자용 식단 상세.
 *
 * <p>관리 화면이 공개 조회에 기대지 않게 경로를 나눈 것이다. 지금은 내용이
 * 같지만, 미공개 식단 같은 개념이 생겨 공개 조회가 좁아지면 관리 화면이
 * 같이 망가진다.
 */
class AdminMenuDetailApiTest extends ApiIntegrationTest {

    private static final String ADMIN_PATH = "/api/v1/admin/menus";
    private static final String PUBLIC_PATH = "/api/v1/menus";
    private static final LocalDate DAY = LocalDate.parse("2026-09-25");
    private static final String MARK = "[상세테스트] ";

    @Autowired private MenuRepository menuRepository;
    @Autowired private DiningSiteRepository siteRepository;

    @BeforeEach
    @AfterEach
    void removeMenusCreatedHere() {
        menuRepository.deleteAll(menuRepository.search(DAY, null, null, null).stream()
                .filter(menu -> menu.getName().startsWith(MARK))
                .toList());
        menuRepository.flush();
    }

    private Menu seedMenu() {
        return menuRepository.save(new Menu(
                siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst(),
                DAY,
                MealType.LUNCH,
                MenuCategory.KOREAN,
                MARK + "김치찌개 정식",
                List.of("김치찌개", "쌀밥"),
                clock.instant()));
    }

    @Test
    @DisplayName("관리자가 상세를 읽는다")
    void adminReadsTheDetail() throws Exception {
        Menu menu = seedMenu();

        MvcResult result = getJson(ADMIN_PATH + "/" + menu.getId(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("id").asLong()).isEqualTo(menu.getId());
        assertThat(data.path("name").asText()).isEqualTo(MARK + "김치찌개 정식");
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void normalUserIsForbidden() throws Exception {
        Menu menu = seedMenu();
        String token = accessTokenOf(signup());

        assertThat(getJson(ADMIN_PATH + "/" + menu.getId(), token).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        Menu menu = seedMenu();

        assertThat(getJson(ADMIN_PATH + "/" + menu.getId()).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("없는 식단은 404다")
    void unknownMenuIsNotFound() throws Exception {
        MvcResult result = getJson(ADMIN_PATH + "/999999", adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("MENU_NOT_FOUND");
    }

    @Test
    @DisplayName("수정 화면이 채울 값을 모두 담는다")
    void detailCarriesEverythingTheFormNeeds() throws Exception {
        Menu menu = seedMenu();

        JsonNode data = body(getJson(ADMIN_PATH + "/" + menu.getId(), adminToken())).path("data");

        // PUT이 전체 교체라 여기서 빠진 값은 저장할 때 지워진다.
        for (String field : List.of(
                "id",
                "siteId",
                "restaurantName",
                "serviceDate",
                "mealType",
                "category",
                "name",
                "items",
                "description",
                "nutrition",
                "allergens")) {
            assertThat(data.has(field)).as(field).isTrue();
        }
    }

    @Test
    @DisplayName("공개 상세와 같은 것을 읽는다")
    void adminAndPublicReadTheSameRow() throws Exception {
        Menu menu = seedMenu();

        JsonNode admin = body(getJson(ADMIN_PATH + "/" + menu.getId(), adminToken())).path("data");
        JsonNode publicDetail = body(getJson(PUBLIC_PATH + "/" + menu.getId())).path("data");

        // 관리자용 사본을 따로 두지 않았다. 경로만 나뉘어 있다.
        assertThat(admin).isEqualTo(publicDetail);
    }

    @Test
    @DisplayName("공개 상세 조회는 그대로 동작한다")
    void publicDetailStillWorks() throws Exception {
        Menu menu = seedMenu();

        MvcResult result = getJson(PUBLIC_PATH + "/" + menu.getId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("id").asLong()).isEqualTo(menu.getId());
    }

    @Test
    @DisplayName("관리자용 식단 엔티티를 새로 만들지 않았다")
    void noSeparateAdminMenuEntity() throws Exception {
        String service = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/babplanner/backend/admin/service/AdminMenuService.java"));

        // 사본을 두면 한쪽만 고쳤을 때 두 화면이 다른 것을 보여 준다.
        assertThat(service).contains("findWithDetailById");
        assertThat(service).contains("menuRepository");
        // 관리자 전용 엔티티를 새로 정의하지 않았다.
        assertThat(service).doesNotContain("@Entity").doesNotContain("@Table");
    }
}
