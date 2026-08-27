package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.babplanner.backend.admin.dto.DemandPredictionRequest;
import com.babplanner.backend.admin.dto.DemandPredictionResponse;
import com.babplanner.backend.admin.service.AdminDemandService;
import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.config.AiServiceProperties;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.demand.client.HttpDemandPredictor;
import com.babplanner.backend.demand.repository.DemandPredictionRepository;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

/**
 * 관리자 수요 예측.
 *
 * <p>조회는 실제 HTTP 계층까지 통과시킨다. 실행은 예측 서비스를 흉내 낸
 * 서버를 붙여 직접 부른다. MockMvc로 POST까지 태우면 Spring이 만든 어댑터가
 * 진짜 소켓을 열려고 하기 때문이다.
 */
class AdminDemandApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/demand-predictions";
    private static final String BASE_URL = "http://ai.test";
    private static final String EXPECTED_URL = BASE_URL + "/internal/v1/ai/demand/predict";
    private static final String TOKEN = "test-only-ai-service-token-not-a-real-secret";
    private static final LocalDate DAY = LocalDate.parse("2026-09-15");

    @Autowired private DemandPredictionRepository predictionRepository;
    @Autowired private DiningSiteRepository siteRepository;

    private MockRestServiceServer aiService;
    private String capturedRequestBody;

    @BeforeEach
    void clearPredictions() {
        predictionRepository.deleteAll();
        predictionRepository.flush();
    }

    private long siteId() {
        return siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst().getId();
    }

    /** 예측 서비스를 흉내 낸 서버에 붙인 실제 어댑터. */
    private AdminDemandService serviceWithMockAi() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        aiService = MockRestServiceServer.bindTo(builder).build();
        return serviceWith(builder.build());
    }

    /** 아무도 듣고 있지 않은 주소를 향하는 어댑터. */
    private AdminDemandService offlineService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        return serviceWith(
                RestClient.builder().baseUrl("http://127.0.0.1:1").requestFactory(factory).build());
    }

    private AdminDemandService serviceWith(RestClient client) {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceToken(TOKEN);
        // 실제 소켓을 열지 않도록 클라이언트만 바꿔 끼운다. 검증 코드는 운영과
        // 같은 것을 지난다.
        AiRestClientProvider provider = new AiRestClientProvider(properties) {
            @Override
            public RestClient client() {
                return client;
            }
        };
        return new AdminDemandService(
                predictionRepository, siteRepository, new HttpDemandPredictor(provider), clock);
    }

    private void respond(HttpStatus status, String body) {
        aiService
                .expect(requestTo(EXPECTED_URL))
                .andExpect(header("X-Service-Token", TOKEN))
                .andExpect(request -> capturedRequestBody = request.getBody().toString())
                .andRespond(
                        status.is2xxSuccessful()
                                ? withSuccess(body, MediaType.APPLICATION_JSON)
                                : withStatus(status)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(body));
    }

    private String successBody(int people) {
        return "{\"requestId\":\"__ECHO__\",\"predictedPeople\":" + people
                + ",\"modelVersion\":\"fastapi-fixture-demand-v1\"}";
    }

    /** 응답의 requestId를 요청에서 그대로 되비추게 한다. */
    private void respondEchoing(int people) {
        aiService
                .expect(requestTo(EXPECTED_URL))
                .andExpect(header("X-Service-Token", TOKEN))
                .andRespond(request -> {
                    capturedRequestBody = request.getBody().toString();
                    String requestId = capturedRequestBody.split("\"requestId\":\"")[1].split("\"")[0];
                    return withSuccess(
                                    successBody(people).replace("__ECHO__", requestId),
                                    MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
    }

    private DemandPredictionRequest request() {
        return new DemandPredictionRequest(siteId(), DAY, MealType.LUNCH);
    }

    private DemandPredictionResponse predictOnce(int people) {
        AdminDemandService service = serviceWithMockAi();
        respondEchoing(people);
        return service.predict(request());
    }

    // ---------- 실행 ----------

    @Test
    @DisplayName("예측 결과를 저장한다")
    void predictionIsPersisted() {
        DemandPredictionResponse response = predictOnce(128);

        assertThat(response.predictedPeople()).isEqualTo(128);
        assertThat(response.modelVersion()).isEqualTo("fastapi-fixture-demand-v1");
        assertThat(response.siteId()).isEqualTo(siteId());
        assertThat(predictionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("요청에 사용자 정보를 담지 않는다")
    void requestCarriesNoUserData() throws Exception {
        signup();
        predictOnce(100);

        for (String forbidden : List.of(
                "userId", "phone", "studentId", "name", "accessToken", "Bearer", PHONE)) {
            assertThat(capturedRequestBody).as(forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("요청은 무엇을 예측할지만 담는다")
    void requestCarriesOnlyTheTarget() {
        predictOnce(100);

        assertThat(capturedRequestBody)
                .contains("\"siteId\"")
                .contains("\"serviceDate\":\"2026-09-15\"")
                .contains("\"mealType\":\"LUNCH\"")
                .contains("\"requestId\":\"demand-");
    }

    @Test
    @DisplayName("정의되지 않은 과거 배열을 보내지 않는다")
    void ambiguousHistoryArraysAreNotSent() {
        predictOnce(100);

        // 어느 기간의 무엇을 집계한 값인지 정의된 적이 없다. 채워 보내면
        // 받는 쪽은 그것이 의미 있는 입력이라고 믿는다.
        for (String forbidden :
                List.of("recentPeopleCounts", "recentWasteRates", "recentUsageCounts")) {
            assertThat(capturedRequestBody).as(forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("상관 ID가 사용자 식별자가 아니다")
    void requestIdIsNotAUserIdentifier() {
        predictOnce(100);

        String requestId = predictionRepository.findAll().getFirst().getRequestId();
        assertThat(requestId).startsWith("demand-");
        assertThat(requestId).doesNotContain(PHONE).doesNotContain(STUDENT_ID);
    }

    @Test
    @DisplayName("다시 실행하면 이력이 한 줄 늘어난다")
    void rerunAppendsHistory() {
        predictOnce(100);
        predictOnce(120);

        // 덮어쓰면 그때 무엇을 보고 발주했는지 확인할 수 없다.
        assertThat(predictionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 식당은 422다")
    void unknownSiteIsRejected() {
        AdminDemandService service = serviceWithMockAi();

        Assertions.assertThatThrownBy(() -> service.predict(
                        new DemandPredictionRequest(999999L, DAY, MealType.LUNCH)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode().name())
                .isEqualTo("UNKNOWN_DINING_SITE");
        assertThat(predictionRepository.count()).isZero();
    }

    // ---------- 응답 검증 ----------

    private void assertRejects(String body) {
        AdminDemandService service = serviceWithMockAi();
        respond(HttpStatus.OK, body);

        Assertions.assertThatThrownBy(() -> service.predict(request()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode().name())
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(predictionRepository.count()).isZero();
    }

    @Test
    @DisplayName("상관 ID가 다른 응답을 저장하지 않는다")
    void requestIdMismatchIsRejected() {
        // 다른 요청의 결과를 이 실행으로 저장하면 안 된다.
        assertRejects(successBody(100).replace("__ECHO__", "demand-someone-else"));
    }

    @Test
    @DisplayName("음수 예측 인원을 저장하지 않는다")
    void negativePredictionIsRejected() {
        assertRejects(
                "{\"requestId\":\"__ECHO__\",\"predictedPeople\":-1,"
                        + "\"modelVersion\":\"m\"}"
                        .replace("__ECHO__", "x"));
    }

    @Test
    @DisplayName("모델 버전이 없는 응답을 저장하지 않는다")
    void blankModelVersionIsRejected() {
        assertRejects("{\"requestId\":\"x\",\"predictedPeople\":100,\"modelVersion\":\"  \"}");
    }

    @Test
    @DisplayName("망가진 응답을 저장하지 않는다")
    void malformedResponseIsRejected() {
        assertRejects("not json at all");
    }

    @Test
    @DisplayName("빈 응답을 저장하지 않는다")
    void emptyResponseIsRejected() {
        assertRejects("");
    }

    // ---------- 실패 처리 ----------

    private void assertUnavailable(HttpStatus status) {
        AdminDemandService service = serviceWithMockAi();
        respond(status, "{\"detail\":\"nope\"}");

        Assertions.assertThatThrownBy(() -> service.predict(request()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode().name())
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(predictionRepository.count()).isZero();
    }

    @Test
    @DisplayName("AI가 401이면 실행이 실패한다")
    void aiUnauthorizedFails() {
        assertUnavailable(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AI가 422면 실행이 실패한다")
    void aiUnprocessableFails() {
        assertUnavailable(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("AI가 503이면 실행이 실패한다")
    void aiUnavailableFails() {
        assertUnavailable(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("AI가 500이면 실행이 실패한다")
    void aiServerErrorFails() {
        assertUnavailable(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("연결 자체가 안 되면 실행이 실패한다")
    void connectionFailureFails() {
        AdminDemandService service = offlineService();

        Assertions.assertThatThrownBy(() -> service.predict(request()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode().name())
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(predictionRepository.count()).isZero();
    }

    @Test
    @DisplayName("실패해도 다시 보내지 않는다")
    void failureIsNotRetried() {
        AdminDemandService service = serviceWithMockAi();
        // 기대를 한 번만 등록한다. 두 번 부르면 MockRestServiceServer가
        // 검증에서 실패한다.
        respond(HttpStatus.SERVICE_UNAVAILABLE, "{}");

        Assertions.assertThatThrownBy(() -> service.predict(request()))
                .isInstanceOf(ApiException.class);

        aiService.verify();
    }

    @Test
    @DisplayName("실패한 실행은 이력에 남지 않는다")
    void failedRunLeavesNoRow() {
        predictOnce(100);
        long before = predictionRepository.count();

        AdminDemandService service = offlineService();
        Assertions.assertThatThrownBy(() -> service.predict(request()))
                .isInstanceOf(ApiException.class);

        assertThat(predictionRepository.count()).isEqualTo(before);
    }

    // ---------- 조회 ----------

    @Test
    @DisplayName("관리자가 목록을 본다")
    void adminReadsTheList() throws Exception {
        predictOnce(111);

        JsonNode data = body(getJson(
                        PATH + "?siteId=" + siteId() + "&date=" + DAY, adminToken()))
                .path("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("predictedPeople").asInt()).isEqualTo(111);
        assertThat(data.get(0).path("siteId").asLong()).isEqualTo(siteId());
    }

    @Test
    @DisplayName("최신 실행이 먼저 온다")
    void newestRunComesFirst() throws Exception {
        predictOnce(100);
        clock.advance(java.time.Duration.ofMinutes(1));
        predictOnce(200);

        JsonNode data = body(getJson(
                        PATH + "?siteId=" + siteId() + "&date=" + DAY, adminToken()))
                .path("data");

        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("predictedPeople").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("끼니로 걸러 볼 수 있다")
    void mealTypeFilters() throws Exception {
        predictOnce(100);
        AdminDemandService service = serviceWithMockAi();
        respondEchoing(300);
        service.predict(new DemandPredictionRequest(siteId(), DAY, MealType.DINNER));

        String token = adminToken();
        JsonNode all = body(getJson(PATH + "?siteId=" + siteId() + "&date=" + DAY, token))
                .path("data");
        JsonNode dinner = body(getJson(
                        PATH + "?siteId=" + siteId() + "&date=" + DAY + "&mealType=DINNER", token))
                .path("data");

        assertThat(all).hasSize(2);
        assertThat(dinner).hasSize(1);
        assertThat(dinner.get(0).path("predictedPeople").asInt()).isEqualTo(300);
    }

    @Test
    @DisplayName("결과가 없으면 빈 배열이다")
    void emptyResultIsAnEmptyArray() throws Exception {
        MvcResult result = getJson(PATH + "?siteId=" + siteId() + "&date=" + DAY, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).isEmpty();
    }

    @Test
    @DisplayName("조회는 AI 서비스가 없어도 동작한다")
    void readingDoesNotNeedTheAiService() throws Exception {
        predictOnce(150);

        // 이미 저장된 예측을 보는 일은 예측 서비스와 무관하다.
        JsonNode data = body(getJson(
                        PATH + "?siteId=" + siteId() + "&date=" + DAY, adminToken()))
                .path("data");

        assertThat(data.get(0).path("predictedPeople").asInt()).isEqualTo(150);
    }

    @Test
    @DisplayName("siteId나 date가 없으면 400이다")
    void missingQueryParametersAreRejected() throws Exception {
        String token = adminToken();

        assertThat(getJson(PATH + "?date=" + DAY, token).getResponse().getStatus()).isEqualTo(400);
        assertThat(getJson(PATH + "?siteId=" + siteId(), token).getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("서비스 날짜를 서버 시간대로 다시 해석하지 않는다")
    void serviceDateIsStoredAsGiven() {
        predictOnce(100);

        assertThat(predictionRepository.findAll().getFirst().getServiceDate()).isEqualTo(DAY);
    }

    // ---------- 권한 ----------

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        assertThat(getJson(PATH + "?siteId=1&date=" + DAY).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(postJson(PATH, Map.of()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void normalUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(getJson(PATH + "?siteId=1&date=" + DAY, token).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(postJson(PATH, body(), token).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("실행 요청에 예측 인원을 담아도 무시한다")
    void clientSuppliedPredictionIsIgnored() throws Exception {
        // 직접 보낼 수 있으면 예측 서비스를 우회해 원하는 숫자를 남길 수 있다.
        Map<String, Object> payload = body();
        payload.put("predictedPeople", 9999);

        MvcResult result = postJson(PATH, payload, adminToken());

        // 계약에 없는 필드다. 저장 경로까지 가지 않는다.
        assertThat(result.getResponse().getStatus()).isNotEqualTo(201);
        assertThat(predictionRepository.count()).isZero();
    }

    private Map<String, Object> body() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("siteId", siteId());
        payload.put("serviceDate", DAY.toString());
        payload.put("mealType", "LUNCH");
        return payload;
    }
}
