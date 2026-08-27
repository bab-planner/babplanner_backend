package com.babplanner.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.babplanner.backend.admin.dto.PlannedIngredientRequest;
import com.babplanner.backend.admin.dto.ProcurementItemResponse;
import com.babplanner.backend.admin.dto.ProcurementRunRequest;
import com.babplanner.backend.admin.service.AdminProcurementService;
import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.config.AiServiceProperties;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.demand.domain.DemandPrediction;
import com.babplanner.backend.demand.repository.DemandPredictionRepository;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.procurement.client.HttpProcurementRecommender;
import com.babplanner.backend.procurement.repository.ProcurementRunRepository;
import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
 * 관리자 발주 추천.
 *
 * <p>가장 중요한 것은 응답 검증이다. 재료가 하나라도 빠지거나 늘거나 겹친
 * 채로 저장되면, 그 목록을 보고 발주하는 사람이 잘못 산다.
 */
class AdminProcurementApiTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/admin/procurement-recommendations";
    private static final String BASE_URL = "http://ai.test";
    private static final String EXPECTED_URL = BASE_URL + "/internal/v1/ai/procurement/recommend";
    private static final String TOKEN = "test-only-ai-service-token-not-a-real-secret";
    private static final LocalDate DAY = LocalDate.parse("2026-09-15");
    private static final String MODEL = "fastapi-fixture-procurement-v1";

    @Autowired private ProcurementRunRepository runRepository;
    @Autowired private DemandPredictionRepository predictionRepository;
    @Autowired private DiningSiteRepository siteRepository;

    private MockRestServiceServer aiService;
    private String capturedRequestBody;

    @BeforeEach
    @AfterEach
    void clearRuns() {
        runRepository.deleteAll();
        runRepository.flush();
        predictionRepository.deleteAll();
        predictionRepository.flush();
    }

    private DiningSite site() {
        return siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst();
    }

    private DemandPrediction seedPrediction(int people) {
        return predictionRepository.save(new DemandPrediction(
                site(), DAY, MealType.LUNCH, people, "fastapi-fixture-demand-v1",
                "demand-seed-" + people, clock.instant()));
    }

    private AdminProcurementService serviceWithMockAi() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        aiService = MockRestServiceServer.bindTo(builder).build();
        return serviceWith(builder.build());
    }

    private AdminProcurementService offlineService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        return serviceWith(
                RestClient.builder().baseUrl("http://127.0.0.1:1").requestFactory(factory).build());
    }

    private AdminProcurementService serviceWith(RestClient client) {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceToken(TOKEN);
        AiRestClientProvider provider = new AiRestClientProvider(properties) {
            @Override
            public RestClient client() {
                return client;
            }
        };
        return new AdminProcurementService(
                runRepository,
                predictionRepository,
                siteRepository,
                new HttpProcurementRecommender(provider),
                clock);
    }

    private PlannedIngredientRequest planned(String name, String unit, double quantity) {
        return new PlannedIngredientRequest(name, unit, quantity);
    }

    private ProcurementRunRequest request(PlannedIngredientRequest... items) {
        return new ProcurementRunRequest(site().getId(), DAY, MealType.LUNCH, List.of(items));
    }

    /** 계획 수량을 그대로 돌려주는 응답. FastAPI fixture와 같은 모양이다. */
    private void respondEchoing() {
        aiService
                .expect(requestTo(EXPECTED_URL))
                .andExpect(header("X-Service-Token", TOKEN))
                .andRespond(request -> {
                    capturedRequestBody = request.getBody().toString();
                    String requestId =
                            capturedRequestBody.split("\"requestId\":\"")[1].split("\"")[0];
                    String planned = capturedRequestBody
                            .split("\"plannedIngredients\":")[1]
                            .replaceAll("\\}$", "");
                    String recommendations = planned.replaceAll(
                            "\"plannedQuantity\":([0-9.]+)",
                            "\"plannedQuantity\":$1,\"recommendedQuantity\":$1,"
                                    + "\"adjustmentRate\":0.0,\"reason\":\"fixture\"");
                    return withSuccess(
                                    "{\"requestId\":\"" + requestId + "\",\"recommendations\":"
                                            + recommendations + ",\"modelVersion\":\"" + MODEL
                                            + "\"}",
                                    MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
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

    private String errorCodeOf(Throwable throwable) {
        return ((ApiException) throwable).errorCode().name();
    }

    private List<ProcurementItemResponse> runOnce() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respondEchoing();
        return service.recommend(request(planned("돼지고기", "kg", 12.5)));
    }

    // ---------- 예측 의존 ----------

    @Test
    @DisplayName("예측이 없으면 409다")
    void demandPredictionIsRequired() {
        AdminProcurementService service = serviceWithMockAi();

        assertThatThrownBy(() -> service.recommend(request(planned("돼지고기", "kg", 12.5))))
                .isInstanceOf(ApiException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo("DEMAND_PREDICTION_REQUIRED");
        assertThat(runRepository.count()).isZero();
    }

    @Test
    @DisplayName("가장 최근 예측을 근거로 삼는다")
    void latestPredictionIsUsed() {
        seedPrediction(100);
        clock.advance(java.time.Duration.ofMinutes(1));
        DemandPrediction latest = seedPrediction(250);

        AdminProcurementService service = serviceWithMockAi();
        respondEchoing();
        service.recommend(request(planned("돼지고기", "kg", 12.5)));

        assertThat(capturedRequestBody).contains("\"predictedPeople\":250");
        assertThat(runRepository.findAll().getFirst().getDemandPrediction().getId())
                .isEqualTo(latest.getId());
    }

    @Test
    @DisplayName("관리자가 예상 인원을 직접 정할 수 없다")
    void adminCannotSupplyPredictedPeople() throws Exception {
        seedPrediction(120);
        Map<String, Object> payload = body();
        payload.put("predictedPeople", 9999);

        // 계약에 없는 필드다. 저장 경로까지 가지 않는다.
        MvcResult result = postJson(PATH, payload, adminToken());

        assertThat(result.getResponse().getStatus()).isNotEqualTo(201);
    }

    // ---------- 요청 검증 ----------

    @Test
    @DisplayName("계획 재료가 없으면 400이다")
    void emptyIngredientListIsRejected() throws Exception {
        Map<String, Object> payload = body();
        payload.put("plannedIngredients", List.of());

        assertThat(postJson(PATH, payload, adminToken()).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("재료 이름이나 단위가 비면 400이다")
    void blankNameOrUnitIsRejected() throws Exception {
        String token = adminToken();

        for (Map<String, Object> item : List.<Map<String, Object>>of(
                Map.of("ingredientName", "  ", "unit", "kg", "plannedQuantity", 1.0),
                Map.of("ingredientName", "쌀", "unit", "  ", "plannedQuantity", 1.0))) {
            Map<String, Object> payload = body();
            payload.put("plannedIngredients", List.of(item));

            assertThat(postJson(PATH, payload, token).getResponse().getStatus()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("수량이 0 이하면 400이다")
    void nonPositiveQuantityIsRejected() throws Exception {
        String token = adminToken();

        for (double quantity : List.of(0.0, -1.5)) {
            Map<String, Object> payload = body();
            payload.put(
                    "plannedIngredients",
                    List.of(Map.of(
                            "ingredientName", "쌀", "unit", "kg", "plannedQuantity", quantity)));

            assertThat(postJson(PATH, payload, token).getResponse().getStatus())
                    .as("%s", quantity)
                    .isEqualTo(400);
        }
    }

    @Test
    @DisplayName("같은 재료와 단위가 두 번 오면 422다")
    void duplicateIngredientIsRejected() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();

        assertThatThrownBy(() -> service.recommend(
                        request(planned("쌀", "kg", 10), planned(" 쌀 ", "KG", 20))))
                .isInstanceOf(ApiException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo("INVALID_PROCUREMENT_REQUEST");
        assertThat(runRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 재료라도 단위가 다르면 다른 줄이다")
    void sameNameDifferentUnitIsAllowed() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respondEchoing();

        List<ProcurementItemResponse> items =
                service.recommend(request(planned("쌀", "kg", 40), planned("쌀", "포", 3)));

        // 쌀 40kg과 쌀 3포는 서로 다른 구매 단위다. 여기서 환산하지 않는다.
        assertThat(items).hasSize(2);
        assertThat(items.get(0).unit()).isEqualTo("kg");
        assertThat(items.get(1).unit()).isEqualTo("포");
    }

    @Test
    @DisplayName("없는 식당은 422다")
    void unknownSiteIsRejected() {
        AdminProcurementService service = serviceWithMockAi();

        assertThatThrownBy(() -> service.recommend(new ProcurementRunRequest(
                        999999L, DAY, MealType.LUNCH, List.of(planned("쌀", "kg", 10)))))
                .isInstanceOf(ApiException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo("UNKNOWN_DINING_SITE");
    }

    // ---------- 보내는 값 ----------

    @Test
    @DisplayName("요청에 사용자 정보를 담지 않는다")
    void requestCarriesNoUserData() throws Exception {
        signup();
        runOnce();

        for (String forbidden :
                List.of("userId", "phone", "studentId", "accessToken", "Bearer", PHONE)) {
            assertThat(capturedRequestBody).as(forbidden).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("정의되지 않은 잔반 집계를 보내지 않는다")
    void menuWasteHistoryIsNotSent() {
        runOnce();

        assertThat(capturedRequestBody).doesNotContain("menuWasteHistory");
    }

    @Test
    @DisplayName("계획 수량을 그대로 보낸다")
    void plannedQuantitiesAreSentAsGiven() {
        runOnce();

        assertThat(capturedRequestBody)
                .contains("\"ingredientName\":\"돼지고기\"")
                .contains("\"unit\":\"kg\"")
                .contains("\"plannedQuantity\":12.5")
                .contains("\"requestId\":\"procurement-");
    }

    // ---------- 응답 검증 ----------

    private void assertRejects(String body) {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respond(HttpStatus.OK, body);

        assertThatThrownBy(() -> service.recommend(request(planned("쌀", "kg", 10))))
                .isInstanceOf(ApiException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(runRepository.count()).isZero();
    }

    private String item(String name, String unit, double planned, double recommended) {
        return "{\"ingredientName\":\"" + name + "\",\"unit\":\"" + unit
                + "\",\"plannedQuantity\":" + planned + ",\"recommendedQuantity\":" + recommended
                + ",\"adjustmentRate\":0.0,\"reason\":\"r\"}";
    }

    private String responseWith(String items) {
        return "{\"requestId\":\"x\",\"recommendations\":[" + items + "],\"modelVersion\":\""
                + MODEL + "\"}";
    }

    @Test
    @DisplayName("항목 수가 다르면 저장하지 않는다")
    void countMismatchIsRejected() {
        assertRejects(responseWith(""));
    }

    @Test
    @DisplayName("보내지 않은 재료가 오면 저장하지 않는다")
    void unknownIngredientIsRejected() {
        // 그대로 저장하면 사지 않아도 될 것을 사게 된다.
        assertRejects(responseWith(item("설탕", "kg", 10, 8)));
    }

    @Test
    @DisplayName("같은 재료가 두 번 오면 저장하지 않는다")
    void duplicateResponseIngredientIsRejected() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respond(
                HttpStatus.OK,
                responseWith(item("쌀", "kg", 10, 9) + "," + item("쌀", "kg", 10, 8)));

        assertThatThrownBy(() -> service.recommend(
                        request(planned("쌀", "kg", 10), planned("양파", "kg", 5))))
                .isInstanceOf(ApiException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(runRepository.count()).isZero();
    }

    @Test
    @DisplayName("빠진 재료가 있으면 저장하지 않는다")
    void missingIngredientIsRejected() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respond(HttpStatus.OK, responseWith(item("쌀", "kg", 10, 9)));

        // 빠진 채로 저장되면 그 재료를 사지 않는다.
        assertThatThrownBy(() -> service.recommend(
                        request(planned("쌀", "kg", 10), planned("양파", "kg", 5))))
                .isInstanceOf(ApiException.class);
        assertThat(runRepository.count()).isZero();
    }

    @Test
    @DisplayName("음수 추천 수량을 저장하지 않는다")
    void negativeRecommendedQuantityIsRejected() {
        assertRejects(responseWith(item("쌀", "kg", 10, -1)));
    }

    @Test
    @DisplayName("계획 수량을 바꿔 돌려주면 저장하지 않는다")
    void changedPlannedQuantityIsRejected() {
        assertRejects(responseWith(item("쌀", "kg", 99, 9)));
    }

    @Test
    @DisplayName("상관 ID가 다르면 저장하지 않는다")
    void requestIdMismatchIsRejected() {
        assertRejects(
                "{\"requestId\":\"procurement-other\",\"recommendations\":["
                        + item("쌀", "kg", 10, 9) + "],\"modelVersion\":\"" + MODEL + "\"}");
    }

    @Test
    @DisplayName("모델 버전이 없으면 저장하지 않는다")
    void blankModelVersionIsRejected() {
        assertRejects(
                "{\"requestId\":\"x\",\"recommendations\":[" + item("쌀", "kg", 10, 9)
                        + "],\"modelVersion\":\"  \"}");
    }

    @Test
    @DisplayName("망가진 응답을 저장하지 않는다")
    void malformedResponseIsRejected() {
        assertRejects("not json");
    }

    // ---------- 실패 ----------

    @Test
    @DisplayName("AI가 내려가면 새 실행이 남지 않는다")
    void aiDownLeavesNoRun() {
        seedPrediction(120);
        AdminProcurementService service = offlineService();

        assertThatThrownBy(() -> service.recommend(request(planned("쌀", "kg", 10))))
                .isInstanceOf(ApiException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(runRepository.count()).isZero();
    }

    @Test
    @DisplayName("AI가 401이면 실행이 실패한다")
    void aiUnauthorizedFails() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respond(HttpStatus.UNAUTHORIZED, "{}");

        assertThatThrownBy(() -> service.recommend(request(planned("쌀", "kg", 10))))
                .isInstanceOf(ApiException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo("AI_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("실패해도 다시 보내지 않는다")
    void failureIsNotRetried() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respond(HttpStatus.SERVICE_UNAVAILABLE, "{}");

        assertThatThrownBy(() -> service.recommend(request(planned("쌀", "kg", 10))))
                .isInstanceOf(ApiException.class);

        aiService.verify();
    }

    @Test
    @DisplayName("실패해도 지난 성공 실행은 남는다")
    void previousRunSurvivesLaterFailure() {
        runOnce();
        long before = runRepository.count();

        AdminProcurementService service = offlineService();
        assertThatThrownBy(() -> service.recommend(request(planned("쌀", "kg", 10))))
                .isInstanceOf(ApiException.class);

        assertThat(runRepository.count()).isEqualTo(before);
    }

    // ---------- 저장 ----------

    @Test
    @DisplayName("실행과 항목이 함께 저장된다")
    void runAndItemsAreStoredTogether() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respondEchoing();

        List<ProcurementItemResponse> items = service.recommend(
                request(planned("돼지고기", "kg", 12.5), planned("양파", "kg", 8)));

        assertThat(items).hasSize(2);
        assertThat(runRepository.count()).isEqualTo(1);
        // items를 함께 읽어오는 조회 메서드를 쓴다. findAll은 지연 로딩이라
        // 트랜잭션 밖에서 항목을 건드릴 수 없다.
        assertThat(runRepository
                        .findFirstBySiteIdAndServiceDateAndMealTypeOrderByCreatedAtDescIdDesc(
                                site().getId(), DAY, MealType.LUNCH)
                        .orElseThrow()
                        .getItems())
                .hasSize(2);
    }

    @Test
    @DisplayName("요청 순서를 그대로 보존한다")
    void orderIsPreserved() {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respondEchoing();

        List<ProcurementItemResponse> items = service.recommend(request(
                planned("돼지고기", "kg", 12.5), planned("양파", "kg", 8), planned("간장", "L", 2)));

        assertThat(items.stream().map(ProcurementItemResponse::ingredientName))
                .containsExactly("돼지고기", "양파", "간장");
    }

    @Test
    @DisplayName("fixture는 계획 수량을 그대로 둔다")
    void fixtureDoesNotAdjust() {
        List<ProcurementItemResponse> items = runOnce();

        assertThat(items.getFirst().recommendedQuantity()).isEqualTo(12.5);
        assertThat(items.getFirst().plannedQuantity()).isEqualTo(12.5);
        assertThat(items.getFirst().adjustmentRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("모델 버전을 남긴다")
    void modelVersionIsPersisted() {
        runOnce();

        assertThat(runRepository.findAll().getFirst().getModelVersion()).isEqualTo(MODEL);
    }

    @Test
    @DisplayName("서비스 날짜를 그대로 저장한다")
    void serviceDateIsStoredAsGiven() {
        runOnce();

        assertThat(runRepository.findAll().getFirst().getServiceDate()).isEqualTo(DAY);
    }

    // ---------- 조회 ----------

    @Test
    @DisplayName("가장 최근 실행의 항목만 본다")
    void onlyTheLatestRunIsReturned() throws Exception {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respondEchoing();
        service.recommend(request(planned("옛날재료", "kg", 1)));

        clock.advance(java.time.Duration.ofMinutes(1));
        AdminProcurementService second = serviceWithMockAi();
        respondEchoing();
        second.recommend(request(planned("새재료", "kg", 2)));

        JsonNode data = body(getJson(
                        PATH + "?siteId=" + site().getId() + "&date=" + DAY + "&mealType=LUNCH",
                        adminToken()))
                .path("data");

        // 실행을 섞으면 같은 재료가 두 줄 나오고 어느 쪽이 최신인지 알 수 없다.
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("ingredientName").asText()).isEqualTo("새재료");
    }

    @Test
    @DisplayName("항목이 자리 순서대로 나온다")
    void itemsComeBackInPosition() throws Exception {
        seedPrediction(120);
        AdminProcurementService service = serviceWithMockAi();
        respondEchoing();
        service.recommend(request(
                planned("가", "kg", 1), planned("나", "kg", 2), planned("다", "kg", 3)));

        JsonNode data = body(getJson(
                        PATH + "?siteId=" + site().getId() + "&date=" + DAY + "&mealType=LUNCH",
                        adminToken()))
                .path("data");

        assertThat(List.of(
                        data.get(0).path("ingredientName").asText(),
                        data.get(1).path("ingredientName").asText(),
                        data.get(2).path("ingredientName").asText()))
                .containsExactly("가", "나", "다");
    }

    @Test
    @DisplayName("결과가 없으면 빈 배열이다")
    void emptyResultIsAnEmptyArray() throws Exception {
        MvcResult result = getJson(
                PATH + "?siteId=" + site().getId() + "&date=" + DAY + "&mealType=LUNCH",
                adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("data")).isEmpty();
    }

    @Test
    @DisplayName("끼니가 없으면 400이다")
    void mealTypeIsRequired() throws Exception {
        // 응답이 항목 목록뿐이라 끼니를 빼면 서로 다른 끼니의 재료가 섞인다.
        assertThat(getJson(PATH + "?siteId=" + site().getId() + "&date=" + DAY, adminToken())
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
    }

    // ---------- 권한 ----------

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void anonymousIsRejected() throws Exception {
        assertThat(getJson(PATH + "?siteId=1&date=" + DAY + "&mealType=LUNCH")
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
        assertThat(postJson(PATH, Map.of()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("일반 사용자는 403이다")
    void normalUserIsForbidden() throws Exception {
        String token = accessTokenOf(signup());

        assertThat(getJson(PATH + "?siteId=1&date=" + DAY + "&mealType=LUNCH", token)
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(postJson(PATH, body(), token).getResponse().getStatus()).isEqualTo(403);
    }

    private Map<String, Object> body() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("siteId", site().getId());
        payload.put("serviceDate", DAY.toString());
        payload.put("mealType", "LUNCH");
        payload.put(
                "plannedIngredients",
                List.of(Map.of("ingredientName", "쌀", "unit", "kg", "plannedQuantity", 10.0)));
        return payload;
    }
}
