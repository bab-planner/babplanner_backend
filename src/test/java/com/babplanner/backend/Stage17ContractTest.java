package com.babplanner.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Flutter Stage 9 RealWasteRepository parser가 기대하는 shape와 맞는지 확인한다.
 *
 * <p>기대값은 Flutter 소스(waste_submission.dart / waste_submission_request.dart)의
 * fromJson과 toMultipartFields를 직접 읽어 옮긴 것이다.
 */
class Stage17ContractTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/waste-submissions";

    @Autowired private MenuRepository menuRepository;

    private MvcResult submit(JsonNode auth) throws Exception {
        // Flutter가 보내는 필드 이름과 형식을 그대로 쓴다.
        return perform(multipart(PATH)
                .file(new MockMultipartFile(
                        "image",
                        "waste_20260826.jpg",
                        "image/jpeg",
                        "fake-image".getBytes(StandardCharsets.UTF_8)))
                .param("menuId", String.valueOf(menuRepository.findAll().getFirst().getId()))
                .param("capturedAt", "2026-08-26T03:00:00.000Z")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth)));
    }

    @Test
    @DisplayName("WasteSubmissionStatus 문자열이 Flutter enum과 같다")
    void statusStringsMatch() {
        assertThat(Arrays.stream(WasteSubmissionStatus.values()).map(Enum::name).toList())
                .containsExactlyInAnyOrder(
                        "PENDING", "RECEIVED", "VERIFYING", "ANALYZING", "APPROVED", "REJECTED",
                        "FAILED");
    }

    @Test
    @DisplayName("업로드 응답이 Flutter parser가 읽는 키를 제공한다")
    void createProvidesParserKeys() throws Exception {
        JsonNode auth = signup();

        JsonNode data = body(submit(auth)).path("data");

        // WasteSubmission.fromJson이 non-null로 읽는 필드
        assertThat(data.path("id").isNumber()).isTrue();
        assertThat(data.path("menuId").isNumber()).isTrue();
        assertThat(data.path("status").isTextual()).isTrue();
        assertThat(Instant.parse(data.path("createdAt").asText())).isNotNull();
        // nullable로 읽는 필드도 키 자체는 있어야 한다.
        assertThat(data.has("wasteRate")).isTrue();
        assertThat(data.has("confidence")).isTrue();
        assertThat(data.has("earnedPoints")).isTrue();
        assertThat(data.has("couponIssued")).isTrue();
    }

    @Test
    @DisplayName("Flutter가 보내는 밀리초 포함 UTC 시각을 받는다")
    void acceptsFlutterCapturedAtFormat() throws Exception {
        JsonNode auth = signup();

        // toUtc().toIso8601String()은 밀리초를 붙인다.
        assertThat(submit(auth).getResponse().getStatus()).isEqualTo(201);
        assertThat(wasteSubmissionRepository.findAll().getFirst().getCapturedAt())
                .isEqualTo(Instant.parse("2026-08-26T03:00:00Z"));
    }

    @Test
    @DisplayName("조회 응답도 같은 키를 제공한다")
    void getProvidesParserKeys() throws Exception {
        JsonNode auth = signup();
        long id = body(submit(auth)).path("data").path("id").asLong();

        JsonNode data = body(getJson(PATH + "/" + id, accessTokenOf(auth))).path("data");

        assertThat(data.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "id",
                        "menuId",
                        "status",
                        "wasteRate",
                        "confidence",
                        "earnedPoints",
                        "couponIssued",
                        "createdAt");
    }

    @Test
    @DisplayName("polling이 같은 id로 반복 조회해도 결과가 같다")
    void repeatedPollingIsStable() throws Exception {
        JsonNode auth = signup();
        long id = body(submit(auth)).path("data").path("id").asLong();

        String first = body(getJson(PATH + "/" + id, accessTokenOf(auth))).path("data").toString();
        String second = body(getJson(PATH + "/" + id, accessTokenOf(auth))).path("data").toString();

        assertThat(first).isEqualTo(second);
        assertThat(wasteSubmissionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("기존 기능에 회귀가 없다")
    void existingFeaturesStillWork() throws Exception {
        JsonNode auth = signup();

        assertThat(getJson("/api/v1/users/me", accessTokenOf(auth)).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(getJson("/api/v1/menus?date=2026-08-26").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(getJson("/api/v1/notices").getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/congestion/current").getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/rewards/summary", accessTokenOf(auth)).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(getJson("/api/v1/inquiries", accessTokenOf(auth)).getResponse().getStatus())
                .isEqualTo(200);
    }
}
