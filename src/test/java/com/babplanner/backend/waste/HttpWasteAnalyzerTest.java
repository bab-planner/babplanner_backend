package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.babplanner.backend.waste.analysis.HttpWasteAnalyzer;
import com.babplanner.backend.waste.analysis.WasteAnalysisDecision;
import com.babplanner.backend.waste.analysis.WasteAnalysisException;
import com.babplanner.backend.waste.analysis.WasteAnalysisInput;
import com.babplanner.backend.waste.analysis.WasteAnalysisResult;
import com.babplanner.backend.waste.storage.LocalWasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 분석 서비스 어댑터.
 *
 * <p>계약 검증은 실제 socket 없이 한다. 이 환경은 연결을 새로 열 때
 * 간헐적으로 실패하는데, 그 실패가 어댑터 결함처럼 보이면 안 된다. 실제
 * 연결이 필요한 것은 WasteAnalyzerSocketTest 두 건뿐이다.
 */
class HttpWasteAnalyzerTest {

    private static final byte[] IMAGE_BYTES = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String TOKEN = "test-only-service-token-not-a-real-secret";
    private static final String BASE_URL = "http://ai.test";
    private static final String EXPECTED_URL = BASE_URL + "/internal/v1/ai/waste/analyze";

    @TempDir Path storageRoot;

    private WasteImageStorage storage;
    private String storageKey;
    private MockRestServiceServer aiService;
    private HttpWasteAnalyzer analyzer;

    /** 어댑터가 실제로 보낸 요청. */
    private final AtomicReference<String> sentBody = new AtomicReference<>();
    private final AtomicReference<String> sentToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        storage = new LocalWasteImageStorage(storageRoot);
        storageKey = storage
                .store(new WasteImageUpload(
                        () -> new ByteArrayInputStream(IMAGE_BYTES),
                        "lunch.jpg",
                        "image/jpeg",
                        IMAGE_BYTES.length))
                .storageKey();

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        aiService = MockRestServiceServer.bindTo(builder).build();
        analyzer = new HttpWasteAnalyzer(builder.build(), storage, TOKEN);
        sentBody.set(null);
        sentToken.set(null);
    }

    /** 응답을 세우고, 나가는 요청을 붙잡아 둔다. */
    private void respond(HttpStatus status, String body) {
        aiService
                .expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(this::capture)
                .andRespond(
                        status.is2xxSuccessful()
                                ? withSuccess(body, MediaType.APPLICATION_JSON)
                                : withStatus(status)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(body));
    }

    private void capture(ClientHttpRequest request) throws IOException {
        sentToken.set(request.getHeaders().getFirst("X-Service-Token"));
        sentBody.set(((MockClientHttpRequest) request).getBodyAsString());
    }

    private void respondApproved() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"APPROVED","wasteRate":0.15,
                 "confidence":0.92,"modelVersion":"fastapi-fixture-waste-v1"}
                """);
    }

    private WasteAnalysisResult analyze() {
        return analyzer.analyze(new WasteAnalysisInput(7L, 3L, storageKey));
    }

    @Test
    @DisplayName("계약이 정한 경로로 보낸다")
    void postsToTheContractPath() {
        respondApproved();

        analyze();

        // 경로나 method가 다르면 기대와 맞지 않아 여기서 드러난다.
        aiService.verify();
    }

    @Test
    @DisplayName("서비스 토큰 헤더를 붙인다")
    void sendsServiceToken() {
        respondApproved();

        analyze();

        assertThat(sentToken.get()).isEqualTo(TOKEN);
    }

    @Test
    @DisplayName("multipart로 보낸다")
    void sendsMultipart() {
        respondApproved();

        analyze();

        assertThat(sentBody.get()).contains("Content-Disposition: form-data");
    }

    @Test
    @DisplayName("제출별 상관 ID를 보낸다")
    void sendsCorrelationId() {
        respondApproved();

        analyze();

        assertThat(sentBody.get()).contains("name=\"requestId\"").contains("waste-7");
    }

    @Test
    @DisplayName("실제 메뉴 id를 보낸다")
    void sendsMenuId() {
        respondApproved();

        analyze();

        assertThat(sentBody.get()).contains("name=\"menuId\"").contains("3");
    }

    @Test
    @DisplayName("이미지 바이트를 보낸다")
    void sendsImageBytes() {
        respondApproved();

        analyze();

        assertThat(sentBody.get())
                .contains("name=\"image\"")
                .contains(new String(IMAGE_BYTES, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("사용자 식별 정보를 보내지 않는다")
    void sendsNoUserIdentifiers() {
        respondApproved();

        analyze();

        for (String forbidden : List.of(
                "userId",
                "phone",
                "studentId",
                "accessToken",
                "refreshToken",
                "verificationToken",
                "Authorization",
                "points",
                "coupon")) {
            assertThat(sentBody.get()).as("%s", forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("업로드한 원본 파일 이름을 흘려보내지 않는다")
    void sendsNoOriginalFilename() {
        respondApproved();

        analyze();

        assertThat(sentBody.get()).doesNotContain("lunch.jpg");
    }

    @Test
    @DisplayName("APPROVED 응답을 그대로 옮긴다")
    void mapsApproved() {
        respondApproved();

        WasteAnalysisResult result = analyze();

        assertThat(result.decision()).isEqualTo(WasteAnalysisDecision.APPROVED);
        assertThat(result.wasteRate()).isEqualTo(0.15);
        assertThat(result.confidence()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("REJECTED 응답을 그대로 옮긴다")
    void mapsRejected() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"REJECTED","wasteRate":null,
                 "confidence":null,"modelVersion":"fastapi-fixture-waste-v1"}
                """);

        WasteAnalysisResult result = analyze();

        assertThat(result.decision()).isEqualTo(WasteAnalysisDecision.REJECTED);
        assertThat(result.wasteRate()).isNull();
        assertThat(result.confidence()).isNull();
    }

    @Test
    @DisplayName("모델 버전을 옮긴다")
    void mapsModelVersion() {
        respondApproved();

        assertThat(analyze().modelVersion()).isEqualTo("fastapi-fixture-waste-v1");
    }

    @Test
    @DisplayName("모델 버전을 손대지 않고 그대로 둔다")
    void modelVersionIsPassedThroughUntouched() {
        // 어느 구현이 판정했는지는 분석 서비스가 아는 사실이다. 어댑터가
        // prefix를 붙이거나 바꿔 쓰면 그 사실이 왜곡된다.
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"APPROVED","wasteRate":0.15,
                 "confidence":0.92,"modelVersion":"waste-resnet-2026.08"}
                """);

        assertThat(analyze().modelVersion()).isEqualTo("waste-resnet-2026.08");
    }

    @Test
    @DisplayName("서버 값을 이 프로세스의 fixture 이름으로 바꾸지 않는다")
    void adapterNeverSubstitutesItsOwnFixtureName() {
        respondApproved();

        assertThat(analyze().modelVersion()).isNotEqualTo("spring-fixture-waste-v1");
    }

    @Test
    @DisplayName("요청 식별자가 다르면 결과로 받아들이지 않는다")
    void requestIdMismatchFails() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-999","decision":"APPROVED","wasteRate":0.15,
                 "confidence":0.92,"modelVersion":"fastapi-fixture-waste-v1"}
                """);

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("승인인데 값이 없으면 실패다")
    void approvedWithoutValuesFails() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"APPROVED","wasteRate":null,
                 "confidence":null,"modelVersion":"fastapi-fixture-waste-v1"}
                """);

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("범위를 벗어난 잔반률은 실패다")
    void outOfRangeRateFails() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"APPROVED","wasteRate":1.5,
                 "confidence":0.92,"modelVersion":"fastapi-fixture-waste-v1"}
                """);

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("범위를 벗어난 신뢰도는 실패다")
    void outOfRangeConfidenceFails() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"APPROVED","wasteRate":0.15,
                 "confidence":-0.1,"modelVersion":"fastapi-fixture-waste-v1"}
                """);

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("알 수 없는 판정은 실패다")
    void unknownDecisionFails() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"MAYBE","wasteRate":0.15,
                 "confidence":0.92,"modelVersion":"fastapi-fixture-waste-v1"}
                """);

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("판정이 없으면 실패다")
    void missingDecisionFails() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","wasteRate":0.15,"confidence":0.92,
                 "modelVersion":"fastapi-fixture-waste-v1"}
                """);

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("모델 버전이 비면 실패다")
    void blankModelVersionFails() {
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-7","decision":"APPROVED","wasteRate":0.15,
                 "confidence":0.92,"modelVersion":"  "}
                """);

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("깨진 JSON은 실패다")
    void malformedJsonFails() {
        respond(HttpStatus.OK, "{ this is not json");

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("401은 실패다")
    void unauthorizedFails() {
        respond(HttpStatus.UNAUTHORIZED, "{\"detail\":\"서비스 토큰이 올바르지 않습니다.\"}");

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("422는 실패다")
    void unprocessableFails() {
        respond(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":\"이미지를 해석할 수 없습니다.\"}");

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("503은 실패다")
    void unavailableFails() {
        respond(HttpStatus.SERVICE_UNAVAILABLE, "{\"detail\":\"분석기를 사용할 수 없습니다.\"}");

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("500은 실패다")
    void serverErrorFails() {
        respond(HttpStatus.INTERNAL_SERVER_ERROR, "{}");

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("실패해도 다시 부르지 않는다")
    void neverRetries() {
        // 기대 요청을 한 건만 세운다. 재시도가 있으면 두 번째 호출에서 드러난다.
        respond(HttpStatus.SERVICE_UNAVAILABLE, "{}");

        assertThatThrownBy(this::analyze).isInstanceOf(WasteAnalysisException.class);

        aiService.verify();
    }

    @Test
    @DisplayName("성공해도 한 번만 부른다")
    void callsOnceOnSuccess() {
        respondApproved();

        analyze();

        aiService.verify();
    }

    @Test
    @DisplayName("이미지를 읽지 못하면 호출조차 하지 않는다")
    void missingImageFailsBeforeCalling() {
        assertThatThrownBy(() -> analyzer.analyze(
                        new WasteAnalysisInput(7L, 3L, "00000000000000000000000000000000")))
                .isInstanceOf(WasteAnalysisException.class);

        // 기대 요청을 세우지 않았으므로 호출이 있었다면 여기서 드러난다.
        aiService.verify();
    }

    @Test
    @DisplayName("상관 ID에 사용자 정보가 들어가지 않는다")
    void correlationIdCarriesNoUserData() {
        assertThat(HttpWasteAnalyzer.correlationIdFor(42L)).isEqualTo("waste-42");
    }
}
