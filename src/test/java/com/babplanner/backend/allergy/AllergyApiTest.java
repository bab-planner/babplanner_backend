package com.babplanner.backend.allergy;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class AllergyApiTest extends ApiIntegrationTest {

    private List<Long> catalogIds() throws Exception {
        JsonNode data = body(getJson("/api/v1/allergies")).path("data");
        List<Long> ids = new ArrayList<>();
        data.forEach(node -> ids.add(node.path("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("master에 19개 항목이 시드되어 있다")
    void catalogHasNineteenItems() throws Exception {
        MvcResult result = getJson("/api/v1/allergies");

        JsonNode data = body(result).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data).hasSize(19);
    }

    @Test
    @DisplayName("catalog 항목은 id와 name만 노출한다")
    void catalogExposesContractFieldsOnly() throws Exception {
        JsonNode first = body(getJson("/api/v1/allergies")).path("data").get(0);

        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name");
        assertThat(first.path("id").asLong()).isPositive();
        assertThat(first.path("name").asText()).isNotBlank();
    }

    @Test
    @DisplayName("catalog 순서는 시드 순서를 유지한다")
    void catalogOrderIsStable() throws Exception {
        JsonNode data = body(getJson("/api/v1/allergies")).path("data");

        assertThat(data.get(0).path("name").asText()).isEqualTo("난류(계란)");
        assertThat(data.get(18).path("name").asText()).isEqualTo("잣");
    }

    @Test
    @DisplayName("내 알레르기는 처음에 비어 있다")
    void myAllergiesStartEmpty() throws Exception {
        JsonNode auth = signup();

        MvcResult result = getJson("/api/v1/users/me/allergies", accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).isEmpty();
    }

    @Test
    @DisplayName("알레르기 하나를 저장한다")
    void saveSingleAllergy() throws Exception {
        JsonNode auth = signup();
        Long first = catalogIds().getFirst();

        MvcResult result = putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(first)),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("id").asLong()).isEqualTo(first);
    }

    @Test
    @DisplayName("여러 개를 저장하고 다시 읽을 수 있다")
    void saveMultipleAllergies() throws Exception {
        JsonNode auth = signup();
        List<Long> ids = catalogIds().subList(0, 3);

        putJson("/api/v1/users/me/allergies", Map.of("allergyIds", ids), accessTokenOf(auth));

        JsonNode data = body(getJson("/api/v1/users/me/allergies", accessTokenOf(auth))).path("data");
        List<Long> saved = new ArrayList<>();
        data.forEach(node -> saved.add(node.path("id").asLong()));
        assertThat(saved).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    @DisplayName("저장은 전체 교체다")
    void saveReplacesEverything() throws Exception {
        JsonNode auth = signup();
        List<Long> ids = catalogIds();
        putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(ids.get(0), ids.get(1))),
                accessTokenOf(auth));

        MvcResult result = putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(ids.get(1), ids.get(2))),
                accessTokenOf(auth));

        List<Long> saved = new ArrayList<>();
        body(result).path("data").forEach(node -> saved.add(node.path("id").asLong()));
        assertThat(saved).containsExactlyInAnyOrder(ids.get(1), ids.get(2));
    }

    @Test
    @DisplayName("빈 배열이면 전체 해제된다")
    void emptyArrayClearsAll() throws Exception {
        JsonNode auth = signup();
        putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(catalogIds().getFirst())),
                accessTokenOf(auth));

        MvcResult result = putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of()),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).isEmpty();
        assertThat(body(getJson("/api/v1/users/me/allergies", accessTokenOf(auth))).path("data"))
                .isEmpty();
    }

    @Test
    @DisplayName("중복 id는 한 번만 저장된다")
    void duplicateIdsAreNormalized() throws Exception {
        JsonNode auth = signup();
        Long first = catalogIds().getFirst();

        MvcResult result = putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(first, first, first)),
                accessTokenOf(auth));

        assertThat(body(result).path("data")).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 id는 조용히 무시하지 않고 실패시킨다")
    void unknownAllergyIdFails() throws Exception {
        JsonNode auth = signup();

        MvcResult result = putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(999_999L)),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("UNKNOWN_ALLERGY");
    }

    @Test
    @DisplayName("존재하지 않는 id가 섞이면 아무것도 저장되지 않는다")
    void partiallyUnknownRequestIsRejectedAtomically() throws Exception {
        JsonNode auth = signup();
        Long valid = catalogIds().getFirst();

        MvcResult result = putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(valid, 999_999L)),
                accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(getJson("/api/v1/users/me/allergies", accessTokenOf(auth))).path("data"))
                .isEmpty();
    }

    @Test
    @DisplayName("사용자별 알레르기는 서로 격리된다")
    void allergiesAreIsolatedPerUser() throws Exception {
        JsonNode first = signup();
        JsonNode second = signup("01098765432", "20205678");
        List<Long> ids = catalogIds();

        putJson(
                "/api/v1/users/me/allergies",
                Map.of("allergyIds", List.of(ids.get(0))),
                accessTokenOf(first));

        assertThat(body(getJson("/api/v1/users/me/allergies", accessTokenOf(second))).path("data"))
                .isEmpty();
        assertThat(body(getJson("/api/v1/users/me/allergies", accessTokenOf(first))).path("data"))
                .hasSize(1);
    }

    @Test
    @DisplayName("저장에도 인증이 필요하다")
    void updateRequiresAuthentication() throws Exception {
        MvcResult result = perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/users/me/allergies")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"allergyIds\":[]}"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("allergyIds가 없으면 400 검증 오류다")
    void missingAllergyIdsIsValidationError() throws Exception {
        JsonNode auth = signup();

        MvcResult result = putJson("/api/v1/users/me/allergies", Map.of(), accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }
}
