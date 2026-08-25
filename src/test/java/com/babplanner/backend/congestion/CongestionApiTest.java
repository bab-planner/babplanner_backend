package com.babplanner.backend.congestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.congestion.domain.CongestionLevel;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import com.babplanner.backend.site.repository.DiningSiteRepository;

class CongestionApiTest extends ApiIntegrationTest {

    @Autowired private DiningSiteRepository diningSiteRepository;

    @Test
    @DisplayName("익명으로 현재 혼잡도를 조회한다")
    void currentIsAnonymous() throws Exception {
        MvcResult result = getJson("/api/v1/congestion/current");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("error").isNull()).isTrue();
        assertThat(json.path("data").isObject()).isTrue();
    }

    @Test
    @DisplayName("siteId가 없으면 노출 순서가 가장 앞선 사이트를 돌려준다")
    void defaultsToFirstSite() throws Exception {
        long studentSiteId = diningSiteRepository.findByCode("STUDENT").orElseThrow().getId();

        JsonNode data = body(getJson("/api/v1/congestion/current")).path("data");

        assertThat(data.path("siteId").asLong()).isEqualTo(studentSiteId);
        assertThat(data.path("level").asText()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("siteId를 주면 해당 사이트를 돌려준다")
    void filtersBySiteId() throws Exception {
        long staffSiteId = diningSiteRepository.findByCode("STAFF").orElseThrow().getId();

        JsonNode data =
                body(getJson("/api/v1/congestion/current?siteId=" + staffSiteId)).path("data");

        assertThat(data.path("siteId").asLong()).isEqualTo(staffSiteId);
        assertThat(data.path("level").asText()).isEqualTo("COMFORTABLE");
    }

    @Test
    @DisplayName("응답 키가 OpenAPI Congestion과 같다")
    void keysMatchContract() throws Exception {
        JsonNode data = body(getJson("/api/v1/congestion/current")).path("data");

        assertThat(data.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "siteId", "peopleCount", "level", "displayMessage", "observedAt", "source");
    }

    @Test
    @DisplayName("level 문자열이 계약 enum과 정확히 같다")
    void levelStringsMatchEnum() {
        // Flutter CongestionLevel.jsonValue와 문자열이 정확히 일치해야 한다.
        assertThat(Arrays.stream(CongestionLevel.values()).map(Enum::name).toList())
                .containsExactly("COMFORTABLE", "NORMAL", "CROWDED", "VERY_CROWDED");
    }

    @Test
    @DisplayName("displayMessage는 nullable이다")
    void displayMessageIsNullable() throws Exception {
        long staffSiteId = diningSiteRepository.findByCode("STAFF").orElseThrow().getId();

        JsonNode data =
                body(getJson("/api/v1/congestion/current?siteId=" + staffSiteId)).path("data");

        assertThat(data.has("displayMessage")).isTrue();
        assertThat(data.path("displayMessage").isNull()).isTrue();
    }

    @Test
    @DisplayName("observedAt은 ISO-8601 UTC다")
    void observedAtIsIso8601() throws Exception {
        JsonNode data = body(getJson("/api/v1/congestion/current")).path("data");

        String observedAt = data.path("observedAt").asText();
        assertThat(observedAt).endsWith("Z");
        // Flutter가 DateTime.parse로 읽을 수 있어야 한다.
        assertThat(java.time.Instant.parse(observedAt)).isNotNull();
    }

    @Test
    @DisplayName("상태가 없는 사이트는 404다")
    void missingStatusIsNotFound() throws Exception {
        MvcResult result = getJson("/api/v1/congestion/current?siteId=999999");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        JsonNode json = body(result);
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("error").path("code").asText()).isEqualTo("CONGESTION_NOT_AVAILABLE");
    }

    @Test
    @DisplayName("상태가 없을 때 임의의 기본 단계를 지어내지 않는다")
    void noFabricatedDefault() throws Exception {
        String content =
                getJson("/api/v1/congestion/current?siteId=999999")
                        .getResponse()
                        .getContentAsString();

        for (String level : List.of("COMFORTABLE", "NORMAL", "CROWDED", "VERY_CROWDED")) {
            assertThat(content).doesNotContain(level);
        }
    }

    @Test
    @DisplayName("인원수로 단계를 계산하는 로직이 없다")
    void noThresholdLogic() throws IOException {
        for (String file :
                List.of(
                        "src/main/java/com/babplanner/backend/congestion/service/CongestionService.java",
                        "src/main/java/com/babplanner/backend/congestion/dto/CongestionResponse.java")) {
            String source = Files.readString(Path.of(file));
            // peopleCount를 읽어 단계를 유추하는 코드가 있으면 안 된다.
            assertThat(source).doesNotContain("CongestionLevel.COMFORTABLE");
            assertThat(source).doesNotContain("CongestionLevel.NORMAL");
            assertThat(source).doesNotContain("CongestionLevel.CROWDED");
        }
    }

    @Test
    @DisplayName("혼잡도 쓰기 API를 만들지 않았다")
    void noWriteApi() throws Exception {
        assertThat(
                        postJson("/api/v1/congestion/current", java.util.Map.of())
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);
    }
}
