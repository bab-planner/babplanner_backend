package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class WasteSubmissionApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/waste-submissions";
    private static final String CAPTURED_AT = "2026-08-26T03:00:00Z";
    private static final byte[] IMAGE_BYTES =
            "fake-jpeg-bytes-for-test".getBytes(StandardCharsets.UTF_8);

    @Autowired private MenuRepository menuRepository;
    @Autowired private WasteImageStorage imageStorage;

    private long anyMenuId() {
        return menuRepository.findAll().getFirst().getId();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "lunch.jpg", "image/jpeg", IMAGE_BYTES);
    }

    private MockMultipartHttpServletRequestBuilder submission(MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder builder = multipart(PATH);
        if (file != null) {
            builder.file(file);
        }
        return builder;
    }

    private MvcResult post(String accessToken, long menuId) throws Exception {
        return perform(submission(image())
                .param("menuId", String.valueOf(menuId))
                .param("capturedAt", CAPTURED_AT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private long createSubmission(JsonNode auth) throws Exception {
        return body(post(accessTokenOf(auth), anyMenuId())).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("업로드는 인증이 필요하다")
    void createRequiresAuth() throws Exception {
        MvcResult result = perform(submission(image())
                .param("menuId", String.valueOf(anyMenuId()))
                .param("capturedAt", CAPTURED_AT));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(wasteSubmissionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("조회는 인증이 필요하다")
    void getRequiresAuth() throws Exception {
        JsonNode auth = signup();
        long id = createSubmission(auth);

        assertThat(getJson(PATH + "/" + id).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("업로드는 201이고 계약 키만 돌려준다")
    void createReturnsContractShape() throws Exception {
        JsonNode auth = signup();

        MvcResult result = post(accessTokenOf(auth), anyMenuId());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).path("data").fieldNames())
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
    @DisplayName("접수 직후 상태는 RECEIVED다")
    void initialStatusIsReceived() throws Exception {
        JsonNode auth = signup();

        JsonNode data = body(post(accessTokenOf(auth), anyMenuId())).path("data");

        assertThat(data.path("status").asText()).isEqualTo("RECEIVED");
        assertThat(data.path("wasteRate").isNull()).isTrue();
        assertThat(data.path("confidence").isNull()).isTrue();
        assertThat(data.path("createdAt").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("응답이 저장소 키나 서버 경로를 노출하지 않는다")
    void responseHidesStorageDetails() throws Exception {
        JsonNode auth = signup();

        MvcResult result = post(accessTokenOf(auth), anyMenuId());

        String json = result.getResponse().getContentAsString();
        String storageKey = wasteSubmissionRepository.findAll().getFirst().getImageStorageKey();
        assertThat(json)
                .doesNotContain(storageKey)
                .doesNotContain("imageStorageKey")
                .doesNotContain("storageKey")
                .doesNotContain("lunch.jpg")
                .doesNotContain("C:\\")
                .doesNotContain("/tmp/");
    }

    @Test
    @DisplayName("이미지가 저장소에 실제로 남는다")
    void imageIsStored() throws Exception {
        JsonNode auth = signup();

        post(accessTokenOf(auth), anyMenuId());

        String key = wasteSubmissionRepository.findAll().getFirst().getImageStorageKey();
        assertThat(imageStorage.exists(key)).isTrue();
    }

    @Test
    @DisplayName("작성자는 principal에서만 온다")
    void ownerComesFromPrincipal() throws Exception {
        JsonNode owner = signup();
        JsonNode other = signup("01098765432", "20205678");
        long otherId = other.path("user").path("id").asLong();

        perform(submission(image())
                .param("menuId", String.valueOf(anyMenuId()))
                .param("capturedAt", CAPTURED_AT)
                .param("userId", String.valueOf(otherId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(owner)));

        assertThat(wasteSubmissionRepository.findAll().getFirst().getUserId())
                .isEqualTo(owner.path("user").path("id").asLong());
    }

    @Test
    @DisplayName("촬영 시각을 그대로 저장한다")
    void capturedAtIsStored() throws Exception {
        JsonNode auth = signup();

        post(accessTokenOf(auth), anyMenuId());

        assertThat(wasteSubmissionRepository.findAll().getFirst().getCapturedAt())
                .isEqualTo(Instant.parse(CAPTURED_AT));
    }

    @Test
    @DisplayName("없는 메뉴는 422다")
    void unknownMenuIsUnprocessable() throws Exception {
        JsonNode auth = signup();

        MvcResult result = post(accessTokenOf(auth), 999999L);

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_WASTE_SUBMISSION");
        assertThat(wasteSubmissionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("이미지가 비어 있으면 422다")
    void emptyImageIsUnprocessable() throws Exception {
        JsonNode auth = signup();

        MvcResult result = perform(submission(
                        new MockMultipartFile("image", "lunch.jpg", "image/jpeg", new byte[0]))
                .param("menuId", String.valueOf(anyMenuId()))
                .param("capturedAt", CAPTURED_AT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth)));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("INVALID_WASTE_IMAGE");
    }

    @Test
    @DisplayName("이미지가 아닌 파일은 422다")
    void nonImageIsUnprocessable() throws Exception {
        JsonNode auth = signup();

        MvcResult result = perform(submission(new MockMultipartFile(
                        "image", "a.pdf", "application/pdf", IMAGE_BYTES))
                .param("menuId", String.valueOf(anyMenuId()))
                .param("capturedAt", CAPTURED_AT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth)));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(wasteSubmissionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("필수 필드가 빠지면 400이다")
    void missingFieldsAreBadRequest() throws Exception {
        JsonNode auth = signup();
        String token = accessTokenOf(auth);

        MvcResult noMenu = perform(submission(image())
                .param("capturedAt", CAPTURED_AT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        MvcResult noCapturedAt = perform(submission(image())
                .param("menuId", String.valueOf(anyMenuId()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        MvcResult badCapturedAt = perform(submission(image())
                .param("menuId", String.valueOf(anyMenuId()))
                .param("capturedAt", "어제")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        assertThat(noMenu.getResponse().getStatus()).isEqualTo(400);
        assertThat(noCapturedAt.getResponse().getStatus()).isEqualTo(400);
        assertThat(badCapturedAt.getResponse().getStatus()).isEqualTo(400);
        assertThat(wasteSubmissionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("verificationToken이 있어도 없어도 접수된다")
    void verificationTokenIsOptional() throws Exception {
        JsonNode auth = signup();

        MvcResult withToken = perform(submission(image())
                .param("menuId", String.valueOf(anyMenuId()))
                .param("capturedAt", CAPTURED_AT)
                .param("verificationToken", "opaque-token-for-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth)));

        assertThat(withToken.getResponse().getStatus()).isEqualTo(201);
        assertThat(post(accessTokenOf(auth), anyMenuId()).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    @DisplayName("verificationToken 원문이 저장되지 않는다")
    void verificationTokenIsNotPersisted() throws Exception {
        JsonNode auth = signup();
        String token = "opaque-token-for-test";

        MvcResult result = perform(submission(image())
                .param("menuId", String.valueOf(anyMenuId()))
                .param("capturedAt", CAPTURED_AT)
                .param("verificationToken", token)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf(auth)));

        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
        WasteSubmission stored = wasteSubmissionRepository.findAll().getFirst();
        assertThat(stored.getImageStorageKey()).doesNotContain(token);
        // 담을 컬럼 자체가 없다. 현장 인증 방식이 정해지기 전에는 원문을 보관하지 않는다.
        assertThat(java.util.Arrays.stream(WasteSubmission.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName)
                        .map(String::toLowerCase)
                        .filter(name -> name.contains("verification") || name.contains("token"))
                        .toList())
                .isEmpty();
    }

    @Test
    @DisplayName("본인 제출을 조회한다")
    void getOwnSubmission() throws Exception {
        JsonNode auth = signup();
        long id = createSubmission(auth);

        MvcResult result = getJson(PATH + "/" + id, accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data").path("id").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("남의 제출은 404이고 내용이 새지 않는다")
    void foreignSubmissionIsNotFound() throws Exception {
        JsonNode owner = signup();
        long id = createSubmission(owner);
        JsonNode other = signup("01098765432", "20205678");

        MvcResult result = getJson(PATH + "/" + id, accessTokenOf(other));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("WASTE_SUBMISSION_NOT_FOUND");
        assertThat(body(result).path("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("없는 제출도 남의 제출과 같은 응답이다")
    void unknownSubmissionIsNotFound() throws Exception {
        JsonNode auth = signup();

        MvcResult result = getJson(PATH + "/999999", accessTokenOf(auth));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("WASTE_SUBMISSION_NOT_FOUND");
    }

    @Test
    @DisplayName("응답 봉투가 세 키를 갖는다")
    void envelopeShape() throws Exception {
        JsonNode auth = signup();
        long id = createSubmission(auth);

        assertThat(body(getJson(PATH + "/" + id, accessTokenOf(auth))).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("success", "data", "error");
    }

    @Test
    @DisplayName("처리가 끝나면 종료 상태가 된다")
    void processingReachesTerminalStatus() throws Exception {
        JsonNode auth = signup();
        long id = createSubmission(auth);

        JsonNode data = body(getJson(PATH + "/" + id, accessTokenOf(auth))).path("data");

        assertThat(data.path("status").asText()).isEqualTo("APPROVED");
        assertThat(data.path("wasteRate").asDouble()).isBetween(0.0, 1.0);
        assertThat(data.path("confidence").asDouble()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("승인되어도 리워드 필드는 비어 있다")
    void approvedDoesNotGrantReward() throws Exception {
        JsonNode auth = signup();
        long id = createSubmission(auth);

        JsonNode data = body(getJson(PATH + "/" + id, accessTokenOf(auth))).path("data");

        assertThat(data.path("status").asText()).isEqualTo("APPROVED");
        // 지급 규칙이 없으므로 0이나 false로 채우지도 않는다.
        assertThat(data.path("earnedPoints").isNull()).isTrue();
        assertThat(data.path("couponIssued").isNull()).isTrue();
    }

    @Test
    @DisplayName("DB에 실제로 저장된다")
    void persistsToDatabase() throws Exception {
        JsonNode auth = signup();
        long menuId = anyMenuId();
        long id = body(post(accessTokenOf(auth), menuId)).path("data").path("id").asLong();

        WasteSubmission stored = wasteSubmissionRepository.findById(id).orElseThrow();
        assertThat(stored.getMenuId()).isEqualTo(menuId);
        assertThat(stored.getStatus()).isEqualTo(WasteSubmissionStatus.APPROVED);
        assertThat(stored.getImageStorageKey()).isNotBlank();
        assertThat(stored.getEarnedPoints()).isNull();
        assertThat(stored.getCouponIssued()).isNull();
    }

    @Test
    @DisplayName("제출을 수정하거나 지우는 API가 없다")
    void noMutationApi() throws Exception {
        JsonNode auth = signup();
        long id = createSubmission(auth);

        MvcResult patched = patchJson(
                PATH + "/" + id,
                java.util.Map.of("status", "REJECTED", "earnedPoints", 100),
                accessTokenOf(auth));

        assertThat(patched.getResponse().getStatus()).isNotEqualTo(200);
        WasteSubmission stored = wasteSubmissionRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(WasteSubmissionStatus.APPROVED);
        assertThat(stored.getEarnedPoints()).isNull();
    }
}
