package com.babplanner.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.allergy.repository.AllergyRepository;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.MenuCategory;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Flutter Stage 12 RealRepository의 parser가 기대하는 shape와 일치하는지 확인한다.
 *
 * <p>Flutter 코드를 실행하지는 않는다. 아래 기대값은 Flutter 소스
 * (menu_model.dart / congestion.dart / notice.dart)의 fromJson을 직접 읽어
 * 옮긴 것이다.
 */
class FlutterContractTest extends ApiIntegrationTest {

    @Autowired private AllergyRepository allergyRepository;

    private static final String SEED_DATE = "2026-08-26";

    @Test
    @DisplayName("mealType 문자열이 Flutter MealType.fromJson과 같다")
    void mealTypeStringsMatch() {
        assertThat(Arrays.stream(MealType.values()).map(Enum::name).toList())
                .containsExactly("BREAKFAST", "LUNCH", "DINNER");
    }

    @Test
    @DisplayName("category 문자열이 Flutter MenuCategory.fromJson과 같다")
    void categoryStringsMatch() {
        // Flutter는 OTHER까지 파싱한다. 필터 칩이 5종이라고 enum을 줄이지 않는다.
        assertThat(Arrays.stream(MenuCategory.values()).map(Enum::name).toList())
                .containsExactly("KOREAN", "SNACK", "CHINESE", "JAPANESE", "WESTERN", "OTHER");
    }

    @Test
    @DisplayName("Menu 목록이 Flutter fromSummaryJson이 읽는 키를 모두 제공한다")
    void menuSummaryProvidesParserKeys() throws Exception {
        JsonNode item = body(getJson("/api/v1/menus?date=" + SEED_DATE)).path("data").get(0);

        // Menu.fromSummaryJson이 non-null로 캐스팅하는 필드들
        assertThat(item.path("id").isNumber()).isTrue();
        assertThat(item.path("restaurantName").isTextual()).isTrue();
        assertThat(item.path("serviceDate").isTextual()).isTrue();
        assertThat(item.path("mealType").isTextual()).isTrue();
        assertThat(item.path("category").isTextual()).isTrue();
        assertThat(item.path("name").isTextual()).isTrue();
        // nullable로 읽는 필드들은 키가 존재해야 한다.
        assertThat(item.has("siteId")).isTrue();
        assertThat(item.has("items")).isTrue();
        assertThat(item.has("caloriesKcal")).isTrue();
        assertThat(item.has("imageUrl")).isTrue();
    }

    @Test
    @DisplayName("serviceDate가 Flutter가 기대하는 yyyy-MM-dd다")
    void serviceDateFormatMatches() throws Exception {
        JsonNode item = body(getJson("/api/v1/menus?date=" + SEED_DATE)).path("data").get(0);

        // RealMenuRepository._formatDate가 만드는 형식과 같아야 왕복이 성립한다.
        assertThat(item.path("serviceDate").asText()).isEqualTo(SEED_DATE);
        assertThat(item.path("serviceDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    @DisplayName("Menu 상세가 Flutter fromDetailJson이 읽는 키를 제공한다")
    void menuDetailProvidesParserKeys() throws Exception {
        long id = 0;
        for (JsonNode node : body(getJson("/api/v1/menus?date=" + SEED_DATE)).path("data")) {
            if (node.path("name").asText().equals("제육덮밥 정식")) {
                id = node.path("id").asLong();
            }
        }

        JsonNode data = body(getJson("/api/v1/menus/" + id)).path("data");

        assertThat(data.has("description")).isTrue();
        assertThat(data.path("nutrition").isObject()).isTrue();
        assertThat(data.path("allergens").isArray()).isTrue();
        // NutritionInfo.fromJson이 읽는 키
        for (String key : List.of("caloriesKcal", "proteinG", "carbohydrateG", "fatG", "sodiumMg")) {
            assertThat(data.path("nutrition").has(key)).as("nutrition.%s", key).isTrue();
        }
    }

    @Test
    @DisplayName("메뉴 알레르기 id가 사용자 알레르기 master와 같은 소스다")
    void menuAllergyIdsShareUserAllergyMaster() throws Exception {
        long menuId = 0;
        for (JsonNode node : body(getJson("/api/v1/menus?date=" + SEED_DATE)).path("data")) {
            if (node.path("name").asText().equals("제육덮밥 정식")) {
                menuId = node.path("id").asLong();
            }
        }
        JsonNode allergens = body(getJson("/api/v1/menus/" + menuId)).path("data").path("allergens");

        List<Long> menuAllergyIds = new ArrayList<>();
        allergens.forEach(node -> menuAllergyIds.add(node.path("id").asLong()));

        // 공개 catalog가 돌려주는 id 집합에 포함되어야 앱이 id 기준으로 교차 비교할 수 있다.
        List<Long> catalogIds = new ArrayList<>();
        body(getJson("/api/v1/allergies")).path("data")
                .forEach(node -> catalogIds.add(node.path("id").asLong()));

        assertThat(catalogIds).containsAll(menuAllergyIds);
        assertThat(allergyRepository.findAllById(menuAllergyIds)).hasSameSizeAs(menuAllergyIds);
    }

    @Test
    @DisplayName("Congestion이 Flutter Congestion.fromJson이 읽는 키를 제공한다")
    void congestionProvidesParserKeys() throws Exception {
        JsonNode data = body(getJson("/api/v1/congestion/current")).path("data");

        // level은 non-null이어야 한다(없으면 Flutter가 FormatException을 던진다).
        assertThat(data.path("level").isTextual()).isTrue();
        for (String key : List.of("siteId", "peopleCount", "displayMessage", "observedAt", "source")) {
            assertThat(data.has(key)).as("%s", key).isTrue();
        }
    }

    @Test
    @DisplayName("Notice가 Flutter Notice.fromJson이 읽는 키를 제공한다")
    void noticeProvidesParserKeys() throws Exception {
        JsonNode item = body(getJson("/api/v1/notices")).path("data").get(0);

        assertThat(item.path("id").isNumber()).isTrue();
        assertThat(item.path("title").isTextual()).isTrue();
        assertThat(item.path("content").isTextual()).isTrue();
        assertThat(item.path("important").isBoolean()).isTrue();
        assertThat(item.path("publishedAt").isTextual()).isTrue();
    }

    @Test
    @DisplayName("모든 공개 응답이 success/data/error 세 키를 갖는다")
    void allPublicResponsesUseEnvelope() throws Exception {
        for (String path :
                List.of(
                        "/api/v1/menus?date=" + SEED_DATE,
                        "/api/v1/congestion/current",
                        "/api/v1/notices",
                        "/api/v1/allergies")) {
            JsonNode json = body(getJson(path));
            assertThat(json.fieldNames())
                    .as("%s", path)
                    .toIterable()
                    .containsExactlyInAnyOrder("success", "data", "error");
        }
    }

    @Test
    @DisplayName("Auth/User 회귀가 없다")
    void authAndUserStillWork() throws Exception {
        JsonNode auth = signup();

        assertThat(getJson("/api/v1/users/me", accessTokenOf(auth)).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(
                        postJson("/api/v1/auth/login", java.util.Map.of("phone", PHONE, "password", PASSWORD))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
    }
}
