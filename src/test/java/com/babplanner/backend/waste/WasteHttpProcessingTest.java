package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.analysis.HttpWasteAnalyzer;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.service.WasteProcessingDispatcher;
import com.babplanner.backend.waste.service.WasteSubmissionProcessor;
import com.babplanner.backend.waste.service.WasteSubmissionStateStore;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 분석 서비스를 붙였을 때의 제출 처리.
 *
 * <p>서비스가 뭐라고 답하든 제출이 올바른 상태로 끝나는지 본다. 실제 연결
 * 자체는 WasteAnalyzerSocketTest에서 따로 확인한다.
 */
class WasteHttpProcessingTest extends ApiIntegrationTest {

    private static final byte[] IMAGE_BYTES = "fake-image".getBytes(StandardCharsets.UTF_8);
    private static final String TOKEN = "test-only-service-token-not-a-real-secret";
    private static final String BASE_URL = "http://ai.test";
    private static final String EXPECTED_URL = BASE_URL + "/internal/v1/ai/waste/analyze";

    @Autowired private WasteSubmissionStateStore stateStore;
    @Autowired private WasteImageStorage imageStorage;
    @Autowired private MenuRepository menuRepository;

    private Long userId;
    private MockRestServiceServer aiService;

    /** 분석 서비스로 향하는 실제 어댑터를 붙인 처리 경로. */
    private WasteProcessingDispatcher dispatcher() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        aiService = MockRestServiceServer.bindTo(builder).build();
        return dispatcherWith(new HttpWasteAnalyzer(builder.build(), imageStorage, TOKEN));
    }

    /** 아무도 듣고 있지 않은 주소를 향하는 처리 경로. */
    private WasteProcessingDispatcher offlineDispatcher() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        return dispatcherWith(new HttpWasteAnalyzer(
                RestClient.builder()
                        .baseUrl("http://127.0.0.1:1")
                        .requestFactory(factory)
                        .build(),
                imageStorage,
                TOKEN));
    }

    private WasteProcessingDispatcher dispatcherWith(HttpWasteAnalyzer analyzer) {
        return new WasteProcessingDispatcher(
                new SyncTaskExecutor(),
                new WasteSubmissionProcessor(stateStore, imageStorage, analyzer),
                stateStore);
    }

    private void respond(HttpStatus status, String body) {
        aiService
                .expect(requestTo(EXPECTED_URL))
                .andRespond(
                        status.is2xxSuccessful()
                                ? withSuccess(body, MediaType.APPLICATION_JSON)
                                : withStatus(status)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(body));
    }

    private long userId() throws Exception {
        if (userId == null) {
            JsonNode auth = signup();
            userId = auth.path("user").path("id").asLong();
        }
        return userId;
    }

    private long newSubmission() throws Exception {
        long owner = userId();
        String storageKey = imageStorage
                .store(new WasteImageUpload(
                        () -> new ByteArrayInputStream(IMAGE_BYTES),
                        "a.jpg",
                        "image/jpeg",
                        IMAGE_BYTES.length))
                .storageKey();
        Instant now = clock.instant();
        return wasteSubmissionRepository
                .save(new WasteSubmission(
                        owner,
                        menuRepository.findAll().getFirst().getId(),
                        storageKey,
                        now,
                        now))
                .getId();
    }

    private WasteSubmission stored(long id) {
        return wasteSubmissionRepository.findById(id).orElseThrow();
    }

    private String approvedBodyFor(long submissionId) {
        return """
                {"requestId":"waste-%d","decision":"APPROVED","wasteRate":0.15,
                 "confidence":0.92,"modelVersion":"fastapi-fixture-waste-v1"}
                """
                .formatted(submissionId);
    }

    @Test
    @DisplayName("분석 서비스가 승인하면 APPROVED로 끝난다")
    void approvedResponseApprovesTheSubmission() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.OK, approvedBodyFor(id));

        dispatcher.dispatch(id);

        WasteSubmission submission = stored(id);
        assertThat(submission.getStatus()).isEqualTo(WasteSubmissionStatus.APPROVED);
        assertThat(submission.getWasteRate()).isEqualTo(0.15);
        assertThat(submission.getConfidence()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("분석 서비스가 거절하면 REJECTED로 끝난다")
    void rejectedResponseRejectsTheSubmission() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(
                HttpStatus.OK,
                """
                {"requestId":"waste-%d","decision":"REJECTED","wasteRate":null,
                 "confidence":null,"modelVersion":"fastapi-fixture-waste-v1"}
                """
                        .formatted(id));

        dispatcher.dispatch(id);

        assertThat(stored(id).getStatus()).isEqualTo(WasteSubmissionStatus.REJECTED);
    }

    @Test
    @DisplayName("모델 버전을 DB에 남긴다")
    void modelVersionIsPersisted() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.OK, approvedBodyFor(id));

        dispatcher.dispatch(id);

        assertThat(stored(id).getAnalysisModelVersion()).isEqualTo("fastapi-fixture-waste-v1");
    }

    @Test
    @DisplayName("분석 서비스가 꺼져 있으면 FAILED로 끝난다")
    void serviceDownFailsTheSubmission() throws Exception {
        long id = newSubmission();

        offlineDispatcher().dispatch(id);

        assertThat(stored(id).getStatus()).isEqualTo(WasteSubmissionStatus.FAILED);
    }

    @Test
    @DisplayName("깨진 응답이면 FAILED로 끝난다")
    void malformedResponseFailsTheSubmission() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.OK, "{ this is not json");

        dispatcher.dispatch(id);

        assertThat(stored(id).getStatus()).isEqualTo(WasteSubmissionStatus.FAILED);
    }

    @Test
    @DisplayName("토큰이 맞지 않아 401이 오면 FAILED로 끝난다")
    void unauthorizedFailsTheSubmission() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.UNAUTHORIZED, "{\"detail\":\"서비스 토큰이 올바르지 않습니다.\"}");

        dispatcher.dispatch(id);

        assertThat(stored(id).getStatus()).isEqualTo(WasteSubmissionStatus.FAILED);
    }

    @Test
    @DisplayName("요청 식별자가 다른 응답은 저장하지 않는다")
    void requestIdMismatchFailsTheSubmission() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.OK, approvedBodyFor(id + 1000));

        dispatcher.dispatch(id);

        WasteSubmission submission = stored(id);
        assertThat(submission.getStatus()).isEqualTo(WasteSubmissionStatus.FAILED);
        assertThat(submission.getWasteRate()).isNull();
        assertThat(submission.getAnalysisModelVersion()).isNull();
    }

    @Test
    @DisplayName("실패해도 리워드는 움직이지 않는다")
    void failureLeavesRewardAlone() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.SERVICE_UNAVAILABLE, "{}");

        dispatcher.dispatch(id);

        assertThat(stored(id).getStatus()).isEqualTo(WasteSubmissionStatus.FAILED);
        assertThat(stored(id).getEarnedPoints()).isNull();
        assertThat(stored(id).getCouponIssued()).isNull();
        assertThat(rewardTransactionRepository.findAll()).isEmpty();
        assertThat(couponRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("승인돼도 리워드는 움직이지 않는다")
    void approvalLeavesRewardAlone() throws Exception {
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.OK, approvedBodyFor(id));

        dispatcher.dispatch(id);

        assertThat(stored(id).getStatus()).isEqualTo(WasteSubmissionStatus.APPROVED);
        assertThat(stored(id).getEarnedPoints()).isNull();
        assertThat(stored(id).getCouponIssued()).isNull();
        assertThat(rewardAccountRepository.findAll()).isEmpty();
        assertThat(rewardTransactionRepository.findAll()).isEmpty();
        assertThat(couponRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("모델 버전은 사용자 응답에 나가지 않는다")
    void modelVersionStaysInternal() throws Exception {
        JsonNode auth = signup();
        userId = auth.path("user").path("id").asLong();
        long id = newSubmission();
        WasteProcessingDispatcher dispatcher = dispatcher();
        respond(HttpStatus.OK, approvedBodyFor(id));
        dispatcher.dispatch(id);

        var result = getJson("/api/v1/waste-submissions/" + id, accessTokenOf(auth));

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("modelVersion")
                .doesNotContain("fastapi-fixture-waste-v1");
    }
}
