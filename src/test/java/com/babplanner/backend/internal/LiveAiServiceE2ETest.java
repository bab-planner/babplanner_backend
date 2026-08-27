package com.babplanner.backend.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.demand.repository.DemandPredictionRepository;
import com.babplanner.backend.procurement.repository.ProcurementRunRepository;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 실제로 떠 있는 AI 서비스를 향한 종단 확인.
 *
 * <p>다른 테스트는 응답을 흉내 낸 서버를 쓴다. 여기서는 진짜 프로세스에
 * 진짜 소켓으로 붙는다. 계약이 맞다고 믿는 것과 실제로 맞는 것은 다르다.
 *
 * <p>{@code AI_SERVICE_LIVE_BASE_URL} 이 있을 때만 돈다. 없으면 건너뛴다.
 * 붙을 서비스가 없는데 실패로 만들면, 진짜 실패와 구분되지 않는다.
 *
 * <p>바깥쪽 Tomcat은 쓰지 않는다. MockMvc가 컨트롤러와 보안 규칙을 그대로
 * 지나므로 사람이 부르는 경로는 실제와 같고, 나가는 호출만 진짜 소켓이다.
 * 이 환경은 Java가 소켓을 <em>여는</em> 것이 간헐적으로 실패해서, 서버를
 * 띄우지 않는 편이 확인을 더 확실하게 만든다.
 */
@EnabledIfEnvironmentVariable(named = "AI_SERVICE_LIVE_BASE_URL", matches = ".+")
class LiveAiServiceE2ETest extends ApiIntegrationTest {

    private static final String DEMAND_PATH = "/api/v1/admin/demand-predictions";
    private static final String PROCUREMENT_PATH = "/api/v1/admin/procurement-recommendations";
    private static final LocalDate DAY = LocalDate.parse("2026-09-15");

    @Autowired private DemandPredictionRepository predictionRepository;
    @Autowired private ProcurementRunRepository runRepository;
    @Autowired private DiningSiteRepository siteRepository;

    @DynamicPropertySource
    static void liveAiService(DynamicPropertyRegistry registry) {
        registry.add("bapplanner.ai.base-url", () -> System.getenv("AI_SERVICE_LIVE_BASE_URL"));
        registry.add("bapplanner.ai.service-token", () -> System.getenv("AI_SERVICE_TOKEN"));
    }

    @BeforeEach
    @AfterEach
    void clearRuns() {
        runRepository.deleteAll();
        runRepository.flush();
        predictionRepository.deleteAll();
        predictionRepository.flush();
    }

    private long siteId() {
        return siteRepository.findAllByOrderBySortOrderAscIdAsc().getFirst().getId();
    }

    private Map<String, Object> demandBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("siteId", siteId());
        body.put("serviceDate", DAY.toString());
        body.put("mealType", "LUNCH");
        return body;
    }

    private Map<String, Object> procurementBody() {
        Map<String, Object> body = demandBody();
        body.put(
                "plannedIngredients",
                List.of(
                        Map.of("ingredientName", "돼지고기", "unit", "kg", "plannedQuantity", 12.5),
                        Map.of("ingredientName", "양파", "unit", "kg", "plannedQuantity", 8.0)));
        return body;
    }

    @Test
    @DisplayName("예측을 실제 AI 서비스에 물어 저장한다")
    void demandRoundTripReachesTheRealService() throws Exception {
        String token = adminToken();

        MvcResult created = postJson(DEMAND_PATH, demandBody(), token);

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = body(created).path("data");
        assertThat(data.path("predictedPeople").asInt()).isNotNegative();
        // fixture가 낸 값이라는 사실이 저장된 행에 드러나야 한다.
        assertThat(data.path("modelVersion").asText()).isEqualTo("fastapi-fixture-demand-v1");
        assertThat(data.path("siteId").asLong()).isEqualTo(siteId());

        JsonNode listed = body(getJson(
                        DEMAND_PATH + "?siteId=" + siteId() + "&date=" + DAY, token))
                .path("data");
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).path("id").asLong()).isEqualTo(data.path("id").asLong());
    }

    @Test
    @DisplayName("발주 추천이 최신 예측을 근거로 저장된다")
    void procurementRoundTripUsesTheStoredPrediction() throws Exception {
        String token = adminToken();
        postJson(DEMAND_PATH, demandBody(), token);

        MvcResult created = postJson(PROCUREMENT_PATH, procurementBody(), token);

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode items = body(created).path("data");
        assertThat(items).hasSize(2);
        // fixture는 계획 수량을 그대로 둔다. 절감 추천이 아니다.
        assertThat(items.get(0).path("recommendedQuantity").asDouble()).isEqualTo(12.5);
        assertThat(items.get(0).path("adjustmentRate").asDouble()).isZero();

        assertThat(runRepository.findAll().getFirst().getModelVersion())
                .isEqualTo("fastapi-fixture-procurement-v1");

        JsonNode listed = body(getJson(
                        PROCUREMENT_PATH + "?siteId=" + siteId() + "&date=" + DAY
                                + "&mealType=LUNCH",
                        token))
                .path("data");
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).path("ingredientName").asText()).isEqualTo("돼지고기");
    }

    @Test
    @DisplayName("예측 없이 발주를 실행하면 409다")
    void procurementWithoutPredictionIsRejected() throws Exception {
        MvcResult result = postJson(PROCUREMENT_PATH, procurementBody(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("error").path("code").asText())
                .isEqualTo("DEMAND_PREDICTION_REQUIRED");
        assertThat(runRepository.count()).isZero();
    }

    @Test
    @DisplayName("실제 요청에도 사용자 정보가 실리지 않는다")
    void noUserDataCrossesTheProcessBoundary() throws Exception {
        String token = adminToken();
        postJson(DEMAND_PATH, demandBody(), token);

        // 보낸 상관 ID는 저장된 행에 남는다. 그 값이 사람을 가리키면 안 된다.
        String requestId = predictionRepository.findAll().getFirst().getRequestId();
        assertThat(requestId).startsWith("demand-");
        assertThat(requestId).doesNotContain(ADMIN_PHONE).doesNotContain(ADMIN_STUDENT_ID);
    }
}
