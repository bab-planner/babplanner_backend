package com.babplanner.backend.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 식단 목록/상세. seed 날짜는 2026-08-26(다수)과 2026-08-27(1건)이다. */
class MenuApiTest extends ApiIntegrationTest {

    private static final String SEED_DATE = "2026-08-26";

    private JsonNode listData(String query) throws Exception {
        return body(getJson("/api/v1/menus?" + query)).path("data");
    }

    private List<String> namesOf(JsonNode data) {
        List<String> names = new ArrayList<>();
        data.forEach(node -> names.add(node.path("name").asText()));
        return names;
    }

    private long menuIdByName(String name) throws Exception {
        for (JsonNode node : listData("date=" + SEED_DATE)) {
            if (node.path("name").asText().equals(name)) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("seed 메뉴를 찾을 수 없습니다: " + name);
    }

    @Test
    @DisplayName("익명으로 목록을 조회한다")
    void listIsAnonymous() throws Exception {
        MvcResult result = getJson("/api/v1/menus?date=" + SEED_DATE);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
        assertThat(json.path("data").isArray()).isTrue();
    }

    @Test
    @DisplayName("date가 없으면 400 검증 오류다")
    void dateIsRequired() throws Exception {
        MvcResult result = getJson("/api/v1/menus");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode json = body(result);
        assertThat(json.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.path("error").path("details").path("fields").has("date")).isTrue();
    }

    @Test
    @DisplayName("date 형식이 잘못되면 400이다")
    void invalidDateIsRejected() throws Exception {
        MvcResult result = getJson("/api/v1/menus?date=not-a-date");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("date로 필터링한다")
    void filtersByDate() throws Exception {
        assertThat(namesOf(listData("date=" + SEED_DATE))).contains("제육덮밥 정식");
        assertThat(namesOf(listData("date=2026-08-27"))).containsExactly("전복 비빔밥 정식");
        assertThat(listData("date=2026-01-01")).isEmpty();
    }

    @Test
    @DisplayName("mealType으로 필터링한다")
    void filtersByMealType() throws Exception {
        JsonNode dinner = listData("date=" + SEED_DATE + "&mealType=DINNER");

        assertThat(namesOf(dinner)).containsExactly("치킨 스테이크 정식");
        dinner.forEach(node -> assertThat(node.path("mealType").asText()).isEqualTo("DINNER"));
    }

    @Test
    @DisplayName("category로 필터링한다")
    void filtersByCategory() throws Exception {
        JsonNode snack = listData("date=" + SEED_DATE + "&category=SNACK");

        assertThat(namesOf(snack)).containsExactly("떡볶이 세트");
    }

    @Test
    @DisplayName("siteId로 필터링한다")
    void filtersBySiteId() throws Exception {
        long staffSiteId = 0;
        for (JsonNode node : listData("date=" + SEED_DATE)) {
            if (node.path("restaurantName").asText().equals("교직원 식당")) {
                staffSiteId = node.path("siteId").asLong();
            }
        }
        assertThat(staffSiteId).isPositive();

        JsonNode staffOnly = listData("date=" + SEED_DATE + "&siteId=" + staffSiteId);

        assertThat(staffOnly).isNotEmpty();
        for (JsonNode node : staffOnly) {
            assertThat(node.path("restaurantName").asText()).isEqualTo("교직원 식당");
        }
    }

    @Test
    @DisplayName("여러 조건을 AND로 건다")
    void combinesFilters() throws Exception {
        JsonNode data = listData("date=" + SEED_DATE + "&mealType=LUNCH&category=KOREAN");

        assertThat(namesOf(data)).containsExactlyInAnyOrder("제육덮밥 정식", "얼큰 온쫄면 정식");
    }

    @Test
    @DisplayName("조건에 맞는 식단이 없으면 빈 배열이다")
    void emptyResultIsEmptyArray() throws Exception {
        MvcResult result = getJson("/api/v1/menus?date=" + SEED_DATE + "&category=JAPANESE");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).isEmpty();
    }

    @Test
    @DisplayName("잘못된 mealType은 400이다")
    void invalidMealTypeIsRejected() throws Exception {
        MvcResult result = getJson("/api/v1/menus?date=" + SEED_DATE + "&mealType=BRUNCH");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("목록 정렬은 사이트 순서 → id 순으로 항상 같다")
    void orderIsDeterministic() throws Exception {
        List<String> first = namesOf(listData("date=" + SEED_DATE));
        List<String> second = namesOf(listData("date=" + SEED_DATE));

        assertThat(first).isEqualTo(second);
        // 학생식당(sortOrder 1)이 교직원식당(2)보다 앞선다.
        assertThat(first.indexOf("제육덮밥 정식")).isLessThan(first.indexOf("치킨 스테이크 정식"));
    }

    @Test
    @DisplayName("목록 항목은 OpenAPI MenuSummary 키만 갖는다")
    void summaryKeysMatchContract() throws Exception {
        JsonNode item = listData("date=" + SEED_DATE).get(0);

        assertThat(item.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "id",
                        "siteId",
                        "restaurantName",
                        "serviceDate",
                        "mealType",
                        "category",
                        "name",
                        "items",
                        "caloriesKcal",
                        "imageUrl");
    }

    @Test
    @DisplayName("목록에 상세 전용 필드를 넣지 않는다")
    void summaryOmitsDetailFields() throws Exception {
        JsonNode item = listData("date=" + SEED_DATE).get(0);

        assertThat(item.has("nutrition")).isFalse();
        assertThat(item.has("allergens")).isFalse();
        assertThat(item.has("description")).isFalse();
    }

    @Test
    @DisplayName("id는 실제 BIGINT 서버 id다")
    void idIsServerBigint() throws Exception {
        for (JsonNode node : listData("date=" + SEED_DATE)) {
            assertThat(node.path("id").isIntegralNumber()).isTrue();
            assertThat(node.path("id").asLong()).isPositive();
        }
    }

    @Test
    @DisplayName("items 순서를 보존한다")
    void itemsKeepOrder() throws Exception {
        for (JsonNode node : listData("date=" + SEED_DATE)) {
            if (node.path("name").asText().equals("제육덮밥 정식")) {
                List<String> items = new ArrayList<>();
                node.path("items").forEach(item -> items.add(item.asText()));
                assertThat(items).containsExactly("제육덮밥", "김치", "콩나물국", "요구르트");
                return;
            }
        }
        throw new AssertionError("제육덮밥 정식 seed를 찾지 못했다");
    }

    @Test
    @DisplayName("상세를 실제 id로 조회한다")
    void detailByServerId() throws Exception {
        long id = menuIdByName("제육덮밥 정식");

        MvcResult result = getJson("/api/v1/menus/" + id);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("id").asLong()).isEqualTo(id);
        assertThat(data.path("name").asText()).isEqualTo("제육덮밥 정식");
        assertThat(data.path("description").asText()).contains("제육볶음");
    }

    @Test
    @DisplayName("존재하지 않는 메뉴는 404다")
    void detailNotFound() throws Exception {
        MvcResult result = getJson("/api/v1/menus/999999");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("error").path("code").asText()).isEqualTo("MENU_NOT_FOUND");
    }

    @Test
    @DisplayName("영양정보를 계약 키로 반환한다")
    void nutritionKeysMatchContract() throws Exception {
        long id = menuIdByName("제육덮밥 정식");

        JsonNode nutrition = body(getJson("/api/v1/menus/" + id)).path("data").path("nutrition");

        assertThat(nutrition.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "caloriesKcal", "proteinG", "carbohydrateG", "fatG", "sodiumMg");
        assertThat(nutrition.path("caloriesKcal").asInt()).isEqualTo(716);
        assertThat(nutrition.path("proteinG").asDouble()).isEqualTo(30.9);
        assertThat(nutrition.path("sodiumMg").asInt()).isEqualTo(1100);
    }

    @Test
    @DisplayName("영양정보가 없으면 0이 아니라 null로 유지한다")
    void missingNutritionStaysNull() throws Exception {
        long id = menuIdByName("떡볶이 세트");

        JsonNode data = body(getJson("/api/v1/menus/" + id)).path("data");

        assertThat(data.path("caloriesKcal").isNull()).isTrue();
        JsonNode nutrition = data.path("nutrition");
        assertThat(nutrition.path("caloriesKcal").isNull()).isTrue();
        assertThat(nutrition.path("proteinG").isNull()).isTrue();
        assertThat(nutrition.path("carbohydrateG").isNull()).isTrue();
        assertThat(nutrition.path("fatG").isNull()).isTrue();
        assertThat(nutrition.path("sodiumMg").isNull()).isTrue();
    }

    @Test
    @DisplayName("imageUrl이 없으면 null이다")
    void missingImageUrlIsNull() throws Exception {
        JsonNode item = listData("date=" + SEED_DATE).get(0);

        assertThat(item.has("imageUrl")).isTrue();
        assertThat(item.path("imageUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("알레르기를 id/name으로 반환한다")
    void allergensUseSharedMaster() throws Exception {
        long id = menuIdByName("제육덮밥 정식");

        JsonNode allergens = body(getJson("/api/v1/menus/" + id)).path("data").path("allergens");

        assertThat(allergens).hasSize(3);
        List<String> names = new ArrayList<>();
        allergens.forEach(node -> {
            assertThat(node.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name");
            assertThat(node.path("id").asLong()).isPositive();
            names.add(node.path("name").asText());
        });
        assertThat(names).containsExactlyInAnyOrder("돼지고기", "대두", "밀");
    }

    @Test
    @DisplayName("알레르기가 없으면 빈 배열이다")
    void noAllergensIsEmptyArray() throws Exception {
        long id = menuIdByName("떡볶이 세트");

        assertThat(body(getJson("/api/v1/menus/" + id)).path("data").path("allergens")).isEmpty();
    }

    @Test
    @DisplayName("재고/픽업/예약 필드를 만들지 않는다")
    void noInventoryOrPickupFields() throws Exception {
        String list = getJson("/api/v1/menus?date=" + SEED_DATE).getResponse().getContentAsString();
        long id = menuIdByName("제육덮밥 정식");
        String detail = getJson("/api/v1/menus/" + id).getResponse().getContentAsString();

        for (String forbidden :
                List.of("stock", "remaining", "pickup", "reservation", "인분", "잔여")) {
            assertThat(list).doesNotContain(forbidden);
            assertThat(detail).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("엔티티 내부 필드가 직렬화되지 않는다")
    void entityInternalsAreNotSerialized() throws Exception {
        long id = menuIdByName("제육덮밥 정식");
        String detail = getJson("/api/v1/menus/" + id).getResponse().getContentAsString();

        for (String forbidden : List.of("createdAt", "updatedAt", "site\":", "code\":", "sortOrder")) {
            assertThat(detail).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("메뉴 쓰기 API를 만들지 않았다")
    void noWriteApi() throws Exception {
        assertThat(postJson("/api/v1/menus", java.util.Map.of()).getResponse().getStatus())
                .isEqualTo(401);
    }
}
