package com.babplanner.backend.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

/**
 * 실제로 떠 있는 FastAPI 식수 예측 서버를 향한 종단 확인.
 *
 * <p>관리자 → Spring(MockMvc, 보안 규칙과 컨트롤러 그대로) → 진짜 소켓 → FastAPI →
 * 저장된 joblib artifact. 같은 조건으로 FastAPI를 직접 부른 값과 Spring을 거친 값이
 * 같은지 본다.
 *
 * <p>{@code AI_SERVICE_LIVE_BASE_URL} 과 {@code AI_SERVICE_TOKEN} 이 있을 때만 돈다.
 * FastAPI에 식수 예측 artifact가 로드돼 있어야 한다.
 */
@EnabledIfEnvironmentVariable(named = "AI_SERVICE_LIVE_BASE_URL", matches = ".+")
class LiveDemandForecastE2ETest extends ApiIntegrationTest {

    private static final String PREDICT = "/api/v1/admin/demand-forecasts/predict";
    private static final String STATUS = "/api/v1/admin/demand-forecasts/status";

    @DynamicPropertySource
    static void liveAiService(DynamicPropertyRegistry registry) {
        registry.add("bapplanner.ai.base-url", () -> System.getenv("AI_SERVICE_LIVE_BASE_URL"));
        registry.add("bapplanner.ai.service-token", () -> System.getenv("AI_SERVICE_TOKEN"));
    }

    private static Map<String, Object> adminPayload() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", "2026-09-15");
        body.put("semester", 2);
        body.put("semesterWeek", 3);
        body.put("examPeriod", false);
        body.put("holiday", false);
        body.put("campusEvent", false);
        body.put("temperature", 24.0);
        body.put("rainfall", 0.0);
        body.put("humidity", 60.0);
        body.put("menuCategory", "WESTERN");
        body.put("mainMenu", "돈까스");
        body.put("specialMenu", false);
        return body;
    }

    /** 같은 조건을 FastAPI 계약으로. 메뉴 분류만 한글 라벨이다. */
    private static Map<String, Object> fastApiPayload() {
        Map<String, Object> body = adminPayload();
        body.put("menuCategory", "양식");
        return body;
    }

    private JsonNode callFastApiDirectly() {
        return RestClient.create(System.getenv("AI_SERVICE_LIVE_BASE_URL"))
                .post()
                .uri("/internal/v1/ai/demand-forecast/predict")
                .header("X-Service-Token", System.getenv("AI_SERVICE_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(fastApiPayload())
                .retrieve()
                .body(JsonNode.class);
    }

    @Test
    @DisplayName("Spring을 거친 예측이 FastAPI 직접 호출과 같다")
    void springMatchesTheDirectFastApiCall() throws Exception {
        JsonNode direct = callFastApiDirectly();

        MvcResult result = postJson(PREDICT, adminPayload(), adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("predictedMealCount").asInt())
                .isEqualTo(direct.path("predictedMealCount").asInt());
        assertThat(data.path("model").path("version").asText())
                .isEqualTo(direct.path("model").path("version").asText());
        assertThat(data.path("model").path("syntheticData").asBoolean())
                .isEqualTo(direct.path("model").path("syntheticData").asBoolean());
        assertThat(data.path("disclaimer").asText()).isEqualTo(direct.path("disclaimer").asText());
        assertThat(data.path("reference").path("previousDay").asInt())
                .isEqualTo(direct.path("reference").path("previousDay").asInt());
        assertThat(data.path("conditions").path("weekday").asText()).isEqualTo("화요일");
    }

    @Test
    @DisplayName("status가 실제 모델 상태를 전한다")
    void statusReachesTheRealService() throws Exception {
        MvcResult result = getJson(STATUS, adminToken());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).path("data");
        assertThat(data.path("available").asBoolean()).isTrue();
        assertThat(data.path("modelLoaded").asBoolean()).isTrue();
        // 지금 모델은 합성데이터 PoC다. 그 사실이 관리자에게까지 와야 한다.
        assertThat(data.path("syntheticData").asBoolean()).isTrue();
    }
}
