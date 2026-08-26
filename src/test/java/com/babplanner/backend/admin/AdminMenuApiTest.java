package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.babplanner.backend.allergy.repository.AllergyRepository;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class AdminMenuApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/menus";
    private static final String SERVICE_DATE = "2026-09-15";

    @Autowired private MenuRepository menuRepository;
    @Autowired private DiningSiteRepository siteRepository;
    @Autowired private AllergyRepository allergyRepository;

    /**
     * 이 테스트가 만든 식단만 지운다.
     *
     * <p>공용 정리는 사용자·문의 같은 것만 비운다. 여기서 만든 식단이 남으면
     * 다음 테스트가 남의 데이터를 세게 된다. 씨앗 데이터는 다른 날짜라 건드리지
     * 않는다.
     */
    @BeforeEach
    void clearTestMenus() {
        menuRepository.deleteAll(
                menuRepository.search(java.time.LocalDate.parse(SERVICE_DATE), null, null, null));
        menuRepository.flush();
    }

    private long siteId() {
        return siteRepository.findAll().getFirst().getId();
    }

    private String siteName() {
        return siteRepository.findAll().getFirst().getName();
    }

    private Map<String, Object> payload(Map<String, Object> overrides) {
        Map<String, Object> body = new HashMap<>();
        body.put("siteId", siteId());
        body.put("restaurantName", siteName());
        body.put("serviceDate", SERVICE_DATE);
        body.put("mealType", "LUNCH");
        body.put("category", "KOREAN");
        body.put("name", "김치찌개 정식");
        body.put("items", List.of("김치찌개", "쌀밥", "계란말이"));
        body.putAll(overrides);
        return body;
    }

    private JsonNode created(String token, Map<String, Object> overrides) throws Exception {
        MvcResult result = postJson(PATH, payload(overrides), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data");
    }

    private MvcResult deleteMenu(long menuId, String token) throws Exception {
        return perform(delete(PATH + "/" + menuId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    @DisplayName("날짜로 조회한다")
    void listsByDate() throws Exception {
        String token = adminToken();
        created(token, Map.of());

        JsonNode data = body(getJson(PATH + "?date=" + SERVICE_DATE, token)).path("data");

        assertThat(data).isNotEmpty();
        assertThat(data.get(0).path("serviceDate").asText()).isEqualTo(SERVICE_DATE);
    }

    @Test
    @DisplayName("식사 시간대로 좁힌다")
    void filtersByMealType() throws Exception {
        String token = adminToken();
        created(token, Map.of("mealType", "DINNER", "name", "저녁 정식"));

        JsonNode lunch =
                body(getJson(PATH + "?date=" + SERVICE_DATE + "&mealType=LUNCH", token))
                        .path("data");
        JsonNode dinner =
                body(getJson(PATH + "?date=" + SERVICE_DATE + "&mealType=DINNER", token))
                        .path("data");

        assertThat(lunch).isEmpty();
        assertThat(dinner).hasSize(1);
    }

    @Test
    @DisplayName("등록은 201이고 서버가 id를 만든다")
    void createGeneratesServerId() throws Exception {
        JsonNode data = created(adminToken(), Map.of());

        assertThat(data.path("id").isNumber()).isTrue();
        assertThat(data.path("id").asLong()).isPositive();
        assertThat(data.path("name").asText()).isEqualTo("김치찌개 정식");
    }

    @Test
    @DisplayName("요청에 id를 넣어도 무시된다")
    void clientCannotChooseTheId() throws Exception {
        JsonNode data = created(adminToken(), Map.of("id", 999999));

        assertThat(data.path("id").asLong()).isNotEqualTo(999999L);
    }

    @Test
    @DisplayName("없는 식당은 거부한다")
    void unknownSiteIsRejected() throws Exception {
        MvcResult result =
                postJson(PATH, payload(Map.of("siteId", 999999)), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("UNKNOWN_DINING_SITE");
    }

    @Test
    @DisplayName("식당 이름이 다르면 거부한다")
    void mismatchedRestaurantNameIsRejected() throws Exception {
        String originalName = siteName();

        MvcResult result = postJson(
                PATH, payload(Map.of("restaurantName", "다른 식당")), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("SITE_NAME_MISMATCH");
        // 요청 한 건 때문에 공유 식당의 이름을 바꾸지 않는다.
        assertThat(siteName()).isEqualTo(originalName);
    }

    @Test
    @DisplayName("영양 정보가 없으면 null로 남는다")
    void nutritionStaysNull() throws Exception {
        JsonNode data = created(adminToken(), Map.of());

        JsonNode nutrition = data.path("nutrition");
        // 0으로 채우면 "열량 0kcal"이라는 뜻이 된다.
        assertThat(nutrition.path("caloriesKcal").isNull()).isTrue();
        assertThat(nutrition.path("proteinG").isNull()).isTrue();
    }

    @Test
    @DisplayName("영양 정보를 일부만 줘도 그대로 저장한다")
    void partialNutritionIsKept() throws Exception {
        Map<String, Object> nutrition = new HashMap<>();
        nutrition.put("caloriesKcal", 716);
        nutrition.put("proteinG", 28.5);
        nutrition.put("carbohydrateG", null);
        nutrition.put("fatG", null);
        nutrition.put("sodiumMg", null);

        JsonNode data = created(adminToken(), Map.of("nutrition", nutrition));

        assertThat(data.path("nutrition").path("caloriesKcal").asInt()).isEqualTo(716);
        assertThat(data.path("nutrition").path("proteinG").asDouble()).isEqualTo(28.5);
        assertThat(data.path("nutrition").path("carbohydrateG").isNull()).isTrue();
    }

    @Test
    @DisplayName("구성 메뉴를 순서대로 저장한다")
    void itemsKeepTheirOrder() throws Exception {
        JsonNode data = created(adminToken(), Map.of());

        List<String> items = new java.util.ArrayList<>();
        data.path("items").forEach(node -> items.add(node.asText()));
        assertThat(items).containsExactly("김치찌개", "쌀밥", "계란말이");
    }

    @Test
    @DisplayName("알레르기 성분을 저장한다")
    void allergensAreStored() throws Exception {
        List<Long> ids = allergyRepository.findAll().stream().limit(3).map(a -> a.getId()).toList();

        JsonNode data = created(adminToken(), Map.of("allergenIds", ids));

        assertThat(data.path("allergens")).hasSize(3);
    }

    @Test
    @DisplayName("모르는 알레르기 id가 하나라도 있으면 전부 저장하지 않는다")
    void unknownAllergenRejectsTheWholeRequest() throws Exception {
        long known = allergyRepository.findAll().getFirst().getId();

        MvcResult result = postJson(
                PATH, payload(Map.of("allergenIds", List.of(known, 999999L))), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("UNKNOWN_ALLERGY");
        // 일부만 저장되면 관리자는 다 넣은 줄 안다.
        assertThat(menuRepository.search(java.time.LocalDate.parse(SERVICE_DATE), null, null, null))
                .isEmpty();
    }

    @Test
    @DisplayName("수정은 전체를 교체한다")
    void updateReplacesEverything() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of()).path("id").asLong();

        MvcResult result = putJson(
                PATH + "/" + id,
                payload(Map.of("name", "된장찌개 정식", "items", List.of("된장찌개", "쌀밥"))),
                token);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("id").asLong()).isEqualTo(id);
        assertThat(data.path("name").asText()).isEqualTo("된장찌개 정식");
        assertThat(data.path("items")).hasSize(2);
    }

    @Test
    @DisplayName("수정에서 뺀 알레르기 성분은 사라진다")
    void updateReplacesAllergens() throws Exception {
        String token = adminToken();
        List<Long> ids = allergyRepository.findAll().stream().limit(3).map(a -> a.getId()).toList();
        long id = created(token, Map.of("allergenIds", ids)).path("id").asLong();

        JsonNode data = body(putJson(
                        PATH + "/" + id,
                        payload(Map.of("allergenIds", List.of(ids.getFirst()))),
                        token))
                .path("data");

        // 남겨 두면 성분 하나를 지울 방법이 없어진다.
        assertThat(data.path("allergens")).hasSize(1);
    }

    @Test
    @DisplayName("없는 메뉴 수정은 404다")
    void updatingUnknownMenuIsNotFound() throws Exception {
        MvcResult result = putJson(PATH + "/999999", payload(Map.of()), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("MENU_NOT_FOUND");
    }

    @Test
    @DisplayName("참조되지 않은 메뉴는 삭제된다")
    void unreferencedMenuIsDeleted() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of()).path("id").asLong();

        assertThat(deleteMenu(id, token).getResponse().getStatus()).isEqualTo(204);
        assertThat(menuRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("잔반 인증이 참조하는 메뉴는 지우지 않는다")
    void referencedMenuIsProtected() throws Exception {
        String token = adminToken();
        long menuId = created(token, Map.of()).path("id").asLong();
        JsonNode user = signup();
        Instant now = clock.instant();
        wasteSubmissionRepository.save(new WasteSubmission(
                user.path("user").path("id").asLong(), menuId, "storage-key", now, now));

        MvcResult result = deleteMenu(menuId, token);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("MENU_IN_USE");
        // 관리자 실수로 사용자가 남긴 기록이 사라지면 안 된다.
        assertThat(wasteSubmissionRepository.findAll()).hasSize(1);
        assertThat(menuRepository.findById(menuId)).isPresent();
    }

    @Test
    @DisplayName("없는 메뉴 삭제는 404다")
    void deletingUnknownMenuIsNotFound() throws Exception {
        assertThat(deleteMenu(999999L, adminToken()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("등록한 메뉴가 사용자 목록에 보인다")
    void createdMenuAppearsInThePublicList() throws Exception {
        long id = created(adminToken(), Map.of()).path("id").asLong();

        JsonNode data = body(getJson("/api/v1/menus?date=" + SERVICE_DATE)).path("data");

        assertThat(data).isNotEmpty();
        assertThat(data.get(0).path("id").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("수정한 내용이 사용자 상세에 보인다")
    void updatedMenuAppearsInThePublicDetail() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of()).path("id").asLong();
        putJson(PATH + "/" + id, payload(Map.of("name", "바뀐 이름")), token);

        JsonNode data = body(getJson("/api/v1/menus/" + id)).path("data");

        // 관리자용 사본을 따로 두면 이 값이 갈라진다.
        assertThat(data.path("name").asText()).isEqualTo("바뀐 이름");
    }

    @Test
    @DisplayName("삭제한 메뉴는 사용자 상세에서도 사라진다")
    void deletedMenuIsGoneFromThePublicDetail() throws Exception {
        String token = adminToken();
        long id = created(token, Map.of()).path("id").asLong();
        deleteMenu(id, token);

        assertThat(getJson("/api/v1/menus/" + id).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("필수 값이 빠지면 400이다")
    void missingRequiredFieldsAreRejected() throws Exception {
        Map<String, Object> body = payload(Map.of());
        body.remove("name");

        MvcResult result = postJson(PATH, body, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("날짜 없이 조회하면 400이다")
    void listWithoutDateIsRejected() throws Exception {
        assertThat(getJson(PATH, adminToken()).getResponse().getStatus()).isEqualTo(400);
    }
}
